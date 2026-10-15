package com.freeup.textactions

@Suppress("UNUSED_PARAMETER")
fun shouldShowSummarizeAction(_text: String, _detections: List<Detection>): Boolean {
    // Temporarily disabled - feature not working well yet
    return false
    /*
    val cleaned = text.trim()
    if (cleaned.length < 30) return false
    if (cleaned.length > 2000) return false
    if (isPureDirectType(cleaned, detections)) return false
    return true
    */
}

fun shouldShowSummarizeAction(text: String): Boolean {
    return shouldShowSummarizeAction(text, TextAnalyzer().analyze(text))
}

private fun isPureDirectType(text: String, detections: List<Detection>): Boolean {
    val trimmed = text.trim()
    if (trimmed.isBlank()) return true
    val directTypes = setOf(TextType.PHONE_NUMBER, TextType.EMAIL, TextType.URL, TextType.ADDRESS)
    val matching = detections.firstOrNull { it.type in directTypes }
    return matching != null && coversMostOfText(trimmed, matching.range)
}

private fun coversMostOfText(text: String, range: IntRange): Boolean {
    val matchLength = range.last - range.first + 1
    return matchLength >= (text.length * 0.8)
}
