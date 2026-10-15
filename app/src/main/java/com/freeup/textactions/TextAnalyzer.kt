package com.freeup.textactions

enum class TextType {
    PHONE_NUMBER,
    EMAIL,
    URL,
    ADDRESS,
    DATE_TIME,
    GPS_COORDINATES,
    NUMBER,
    PLAIN_TEXT
}

data class Detection(
    val type: TextType,
    val value: String,
    val range: IntRange
)

class TextAnalyzer {

    private val rules = mapOf(
        TextType.PHONE_NUMBER to Regex("(?:(?:\\+84|84)[\\s.-]?)?(?:0[35789]\\d{8}|0[2-9]\\d{1,2}(?:[\\s.-]?\\d{3,4}){2})|(?:\\+(?!84)[1-9][0-9]{7,14})|(?:\\(?[2-9][0-9]{2}\\)?[\\s.-]?[2-9][0-9]{2}[\\s.-]?[0-9]{4})|(?:1[3-9][0-9]{9})|\\b(1800|1900)[\\s.-]?\\d{4,6}\\b"),
        TextType.EMAIL to Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"),
        TextType.URL to Regex("^https?://[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)"),
        TextType.ADDRESS to Regex("(?:Tòa|Tầng|Số|\\d+)[a-zA-ZÀ-ỹ0-9 ,./-]+(?:Đường|Phố|Ngõ|Ngách|Thôn|Xóm|Phường|Xã|Quận|Huyện|Tỉnh|Thành phố)[a-zA-ZÀ-ỹ0-9 ,./-]*", RegexOption.IGNORE_CASE),
        TextType.DATE_TIME to Regex("(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})|(\\d{4}-\\d{2}-\\d{2})|((Thứ\\s+[2-7]|Chủ\\s+nhật|Ngày\\s+mai|Hôm\\s+nay))", RegexOption.IGNORE_CASE),
        TextType.GPS_COORDINATES to Regex("\\(?\\s*-?\\d{1,3}\\.\\d+\\s*[,\\s]+\\s*-?\\d{1,3}\\.\\d+\\s*\\)?"),
        TextType.NUMBER to Regex("^-?\\d+(\\.\\d+)?$")
    )

    fun analyze(text: String): List<Detection> {
        if (text.isBlank()) return emptyList()

        val detections = mutableListOf<Detection>()

        for ((type, regex) in rules) {
            regex.findAll(text).forEach { match ->
                val isValid = when (type) {
                    TextType.ADDRESS -> validateAddress(match.value)
                    TextType.DATE_TIME -> validateDate(match.value)
                    TextType.PHONE_NUMBER -> validatePhone(match.value)
                    TextType.GPS_COORDINATES -> validateGpsCoordinates(match.value, text)
                    else -> true
                }
                if (isValid) {
                    detections.add(Detection(type, match.value, match.range))
                }
            }
        }

        return detections.sortedBy { it.range.first }
    }

    private fun validateAddress(value: String): Boolean {
        if (value.length <= 15) return false
        
        val lowerValue = value.lowercase()
        val strongKeywordsVi = listOf("đường", "phố", "ngõ", "ngách", "thôn", "xóm", "phường", "xã", "quận", "huyện", "tỉnh", "thành phố", "tòa nhà", "tổ dân phố")
        val strongKeywordsEn = listOf("street", "avenue", "boulevard", "road", "lane", "drive", "court", "place", "highway", "district", "ward", "city", "county", "province", "state")
        val weakKeywordsVi = listOf("số nhà", "tầng", "khu vực", "khu đô thị", "khu công nghiệp")
        val weakKeywordsEn = listOf("apt", "apartment", "suite", "floor", "unit", "building", "block")
        
        val strongCount = (strongKeywordsVi + strongKeywordsEn).count { lowerValue.contains(it) }
        val weakCount = (weakKeywordsVi + weakKeywordsEn).count { lowerValue.contains(it) }
        
        val hasDigit = value.any { it.isDigit() }
        
        return strongCount >= 2 || (strongCount >= 1 && weakCount >= 1 && hasDigit)
    }

    private fun validateDate(value: String): Boolean {
        // Exclude standalone years (4 digits only)
        if (value.length <= 4 && value.toIntOrNull() != null) return false
        // Must have separators
        return value.contains("/") || value.contains("-")
    }

    private fun validatePhone(value: String): Boolean {
        return PhoneClassifier.detectPhone(value).isPhoneNumber
    }

    private fun validateGpsCoordinates(matchValue: String, fullText: String): Boolean {
        val cleaned = fullText.trim()
        if (cleaned.isBlank()) return false

        val keywordRegex = Regex("\\b(km|m|price|vnd|vnđ)\\b", RegexOption.IGNORE_CASE)
        if (keywordRegex.containsMatchIn(cleaned)) return false
        if (cleaned.any { it.isLetter() }) return false
        if (validatePhone(cleaned)) return false

        val normalizedText = cleaned.removePrefix("(").removeSuffix(")").trim()
        val normalizedMatch = matchValue.removePrefix("(").removeSuffix(")").trim()
        if (normalizedText != normalizedMatch) return false

        val parts = normalizedMatch
            .replace(",", " ")
            .trim()
            .split(Regex("\\s+"))
        if (parts.size != 2) return false

        val lat = parts[0].toDoubleOrNull() ?: return false
        val lon = parts[1].toDoubleOrNull() ?: return false
        if (lat !in -90.0..90.0) return false
        if (lon !in -180.0..180.0) return false
        return true
    }
}
