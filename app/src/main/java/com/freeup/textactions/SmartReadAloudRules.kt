package com.freeup.textactions

import android.content.Context
import android.view.accessibility.AccessibilityManager
import com.freeup.textactions.domain.splitSentences

fun shouldShowSmartReadAloudAction(
    context: Context,
    text: String,
    detections: List<Detection>
): Boolean {
    val cleaned = text.trim()
    if (cleaned.length <= 300) return false
    if (isTalkBackEnabled(context)) return false
    if (isPureDirectType(cleaned, detections)) return false
    if (looksLikeCodeOrLog(cleaned) || looksLikeNumericList(cleaned) || looksLikeChat(cleaned)) return false

    val sentences = splitSentences(cleaned)
    if (sentences.size < 3) return false

    val wordCount = cleaned.split(Regex("\\s+")).count { it.isNotBlank() }
    if (wordCount < 60) return false

    return true
}

fun shouldShowReadAloudAction(
    context: Context,
    text: String,
    detections: List<Detection>
): Boolean {
    val cleaned = text.trim()
    if (cleaned.length !in 5..300) return false
    if (isTalkBackEnabled(context)) return false
    if (cleaned.all { it.isDigit() }) return false
    if (looksLikeUrlOrEmail(cleaned)) return false
    if (looksLikePhone(cleaned, detections)) return false
    if (!isMostlyAlphabetic(cleaned)) return false
    if (looksLikeCodeOrLog(cleaned) || looksLikeNumericList(cleaned)) return false
    if (shouldShowSmartReadAloudAction(context, text, detections)) return false

    return true
}

private fun isTalkBackEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    return manager?.isEnabled == true && manager.isTouchExplorationEnabled
}

private fun isPureDirectType(text: String, detections: List<Detection>): Boolean {
    val trimmed = text.trim()
    if (trimmed.isBlank()) return true
    val directTypes = setOf(
        TextType.PHONE_NUMBER,
        TextType.EMAIL,
        TextType.URL,
        TextType.ADDRESS,
        TextType.NUMBER
    )
    val matching = detections.firstOrNull { it.type in directTypes }
    return matching != null && coversMostOfText(trimmed, matching.range)
}

private fun coversMostOfText(text: String, range: IntRange): Boolean {
    val matchLength = range.last - range.first + 1
    return matchLength >= (text.length * 0.8)
}

private fun looksLikeUrlOrEmail(text: String): Boolean {
    return android.util.Patterns.WEB_URL.matcher(text).matches() ||
        text.contains("http", ignoreCase = true) ||
        text.contains("www.", ignoreCase = true) ||
        android.util.Patterns.EMAIL_ADDRESS.matcher(text).matches()
}

private fun looksLikePhone(text: String, detections: List<Detection>): Boolean {
    val direct = detections.firstOrNull { it.type == TextType.PHONE_NUMBER }
    if (direct != null && coversMostOfText(text, direct.range)) return true
    val digits = text.replace(Regex("[^0-9]"), "")
    if (digits.length !in 9..12) return false
    return digits.startsWith("0") || digits.startsWith("84")
}

private fun isMostlyAlphabetic(text: String): Boolean {
    val letters = text.count { it.isLetter() }
    val digits = text.count { it.isDigit() }
    val total = letters + digits
    if (total == 0) return false
    return letters.toDouble() / total >= 0.6
}

private fun looksLikeCodeOrLog(text: String): Boolean {
    val specialCount = text.count { !it.isLetterOrDigit() && !it.isWhitespace() }
    if (specialCount.toDouble() / text.length.coerceAtLeast(1) > 0.2) return true
    val hasJson = text.contains("{") && text.contains("}") && text.contains(":")
    val hasCodeTokens = listOf(";", "==", "->", "::", "{", "}", "[", "]").any { text.contains(it) }
    return hasJson || hasCodeTokens
}

private fun looksLikeNumericList(text: String): Boolean {
    val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
    if (lines.isEmpty()) return false
    val numericLines = lines.count { line ->
        val digits = line.count { it.isDigit() }
        digits > 0 && digits.toDouble() / line.length.coerceAtLeast(1) > 0.6
    }
    return numericLines.toDouble() / lines.size > 0.6
}

private fun looksLikeChat(text: String): Boolean {
    val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
    if (lines.size < 3) return false
    val chatLines = lines.count { line ->
        line.matches(Regex("^.{1,20}:\\s+.+")) || line.startsWith("- ")
    }
    return chatLines.toDouble() / lines.size > 0.5
}
