package com.freeup.textactions

/**
 * Phone number detection & classification for Smart Actions (Call, SMS, Open Chat App).
 * Prefer false negative over false positive.
 */
enum class PhoneType {
    VN_MOBILE,           // Di động VN: 0/84/+84 + 9x
    VN_LANDLINE,         // Điện thoại bàn VN: 0/84/+84 + 2x
    VN_HOTLINE,          // Hotline VN: 1800/1900 + 4-6 digits
    INTERNATIONAL_PHONE  // Số quốc tế: +xxx (không phải +84)
}

data class PhoneDetectionResult(
    val isPhoneNumber: Boolean,
    val phoneType: PhoneType? = null,
    val normalizedNumber: String? = null
) {
    val canCall: Boolean
        get() = isPhoneNumber && (phoneType == PhoneType.VN_MOBILE || phoneType == PhoneType.INTERNATIONAL_PHONE || phoneType == PhoneType.VN_HOTLINE)

    val canSms: Boolean
        get() = isPhoneNumber && (phoneType == PhoneType.VN_MOBILE || phoneType == PhoneType.INTERNATIONAL_PHONE)
}

object PhoneClassifier {

    private const val MIN_INPUT_LENGTH = 3
    private const val MAX_INPUT_LENGTH = 40
    private const val MIN_DIGITS = 8
    private const val MAX_DIGITS = 15

    // VN mobile: 0/84/+84 + (3|5|7|8|9) + 8-9 digits → 10–11 digits total
    private val VN_MOBILE = Regex("^(?:\\+84|84|0)(3|5|7|8|9)[0-9]{8,9}$")
    // VN landline: 0/84/+84 + 2 + 8–9 digits → 9–11 digits total
    private val VN_LANDLINE = Regex("^(?:\\+84|84|0)2[0-9]{8,9}$")
    // VN hotline: 1800/1900 + 4–6 digits → 8–10 digits total
    private val VN_HOTLINE = Regex("^(1800|1900)[0-9]{4,6}$")
    // International: + followed by 1–9 then 7–14 digits (ITU E.164), không bắt đầu bằng +84
    private val INTERNATIONAL = Regex("^\\+(?!84)[1-9][0-9]{7,14}$")
    // NANP (US/Canada): 10 chữ số (area code 2-9xx, exchange 2-9xx) hoặc 11 chữ số với prefix 1
    private val NANP = Regex("^1?([2-9][0-9]{2}[2-9][0-9]{6})$")
    // Chinese mobile: 11 chữ số bắt đầu 1[3-9], có thể có prefix 86
    private val CN_MOBILE = Regex("^(?:86)?(1[3-9][0-9]{9})$")
    // UK: prefix 44 hoặc 0, rồi 10 chữ số (07xxx mobile / 01xx 02x landline)
    private val UK = Regex("^(?:44|0)(?:7[0-9]{9}|[12][0-9]{9})$")
    // Germany: prefix 49 hoặc 0, mobile 01x (10–11 số) hoặc landline (3–11 số sau 0)
    private val DE = Regex("^(?:49|0)(?:1[5-7][0-9]{9}|[2-9][0-9]{7,10})$")
    // France: prefix 33 hoặc 0, 9 chữ số sau prefix (0[1-9]xxxxxxxx)
    private val FR = Regex("^(?:33|0)[1-9][0-9]{8}$")
    // Japan: prefix 81 hoặc 0, mobile 07x/08x/09x (11 số) hoặc landline (10 số)
    private val JP = Regex("^(?:81|0)(?:[789]0[0-9]{8}|[1-9][0-9]{8,9})$")
    // South Korea: prefix 82 hoặc 0, mobile 01x (10–11 số) hoặc landline 0[2-9]
    private val KR = Regex("^(?:82|0)(?:1[0-9]{9}|[2-9][0-9]{7,9})$")
    // India: prefix 91 hoặc 0, mobile 10 chữ số bắt đầu 6-9
    private val IN = Regex("^(?:91|0)?[6-9][0-9]{9}$")
    // Australia: prefix 61 hoặc 0, mobile 04xx (10 số) hoặc landline 0[2-9] (10 số)
    private val AU = Regex("^(?:61|0)(?:4[0-9]{8}|[2-9][0-9]{8})$")

    /**
     * Preprocess selected text: trim, remove space/dot/dash/parentheses, keep digits and leading +.
     * Returns null if invalid char or digit count < 8 or > 15.
     */
    fun preprocess(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.length !in MIN_INPUT_LENGTH..MAX_INPUT_LENGTH) return null

        // Remove spaces, dots, dashes, parentheses
        val reduced = trimmed
            .replace(" ", "")
            .replace(".", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")

        // Only digits and optional leading + allowed
        val hasLeadingPlus = reduced.startsWith("+")
        val afterPlus = if (hasLeadingPlus) reduced.drop(1) else reduced
        if (afterPlus.any { !it.isDigit() }) return null
        if (afterPlus.length !in MIN_DIGITS..MAX_DIGITS) return null

        return if (hasLeadingPlus) "+$afterPlus" else afterPlus
    }

    /**
     * Detect and classify phone from SELECTED_TEXT.
     * Order: VN_MOBILE → VN_LANDLINE → INTERNATIONAL_PHONE. Only one type is returned.
     */
    fun detectPhone(text: String): PhoneDetectionResult {
        if (text.isBlank()) return PhoneDetectionResult(false)
        if (text.any { it.isLetter() }) return PhoneDetectionResult(false) // edge: no letters
        val normalized = preprocess(text) ?: return PhoneDetectionResult(false)

        return when {
            VN_MOBILE.matches(normalized) -> PhoneDetectionResult(
                isPhoneNumber = true,
                phoneType = PhoneType.VN_MOBILE,
                normalizedNumber = toE164(normalized, isVietnam = true)
            )
            VN_LANDLINE.matches(normalized) -> PhoneDetectionResult(
                isPhoneNumber = true,
                phoneType = PhoneType.VN_LANDLINE,
                normalizedNumber = toE164(normalized, isVietnam = true)
            )
            VN_HOTLINE.matches(normalized) -> PhoneDetectionResult(
                isPhoneNumber = true,
                phoneType = PhoneType.VN_HOTLINE,
                normalizedNumber = normalized // Hotlines don't need +84 prefix usually for tel: intents to work in VN
            )
            INTERNATIONAL.matches(normalized) -> PhoneDetectionResult(
                isPhoneNumber = true,
                phoneType = PhoneType.INTERNATIONAL_PHONE,
                normalizedNumber = normalized
            )
            NANP.matches(normalized) -> PhoneDetectionResult(
                isPhoneNumber = true,
                phoneType = PhoneType.INTERNATIONAL_PHONE,
                normalizedNumber = normalized
            )
            CN_MOBILE.matches(normalized) -> PhoneDetectionResult(
                isPhoneNumber = true,
                phoneType = PhoneType.INTERNATIONAL_PHONE,
                normalizedNumber = normalized
            )
            UK.matches(normalized) -> PhoneDetectionResult(
                isPhoneNumber = true,
                phoneType = PhoneType.INTERNATIONAL_PHONE,
                normalizedNumber = normalized
            )
            DE.matches(normalized) -> PhoneDetectionResult(
                isPhoneNumber = true,
                phoneType = PhoneType.INTERNATIONAL_PHONE,
                normalizedNumber = normalized
            )
            FR.matches(normalized) -> PhoneDetectionResult(
                isPhoneNumber = true,
                phoneType = PhoneType.INTERNATIONAL_PHONE,
                normalizedNumber = normalized
            )
            JP.matches(normalized) -> PhoneDetectionResult(
                isPhoneNumber = true,
                phoneType = PhoneType.INTERNATIONAL_PHONE,
                normalizedNumber = normalized
            )
            KR.matches(normalized) -> PhoneDetectionResult(
                isPhoneNumber = true,
                phoneType = PhoneType.INTERNATIONAL_PHONE,
                normalizedNumber = normalized
            )
            IN.matches(normalized) -> PhoneDetectionResult(
                isPhoneNumber = true,
                phoneType = PhoneType.INTERNATIONAL_PHONE,
                normalizedNumber = normalized
            )
            AU.matches(normalized) -> PhoneDetectionResult(
                isPhoneNumber = true,
                phoneType = PhoneType.INTERNATIONAL_PHONE,
                normalizedNumber = normalized
            )
            else -> PhoneDetectionResult(false)
        }
    }

    private fun toE164(normalized: String, isVietnam: Boolean): String {
        val digits = normalized.filter { it.isDigit() }
        return when {
            digits.startsWith("84") -> "+$digits"
            digits.startsWith("0") -> "+84${digits.drop(1)}"
            isVietnam -> "+84$digits"
            else -> "+$digits"
        }
    }
}
