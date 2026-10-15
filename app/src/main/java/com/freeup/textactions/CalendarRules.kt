package com.freeup.textactions

fun shouldShowCalendarAction(text: String, detections: List<Detection>): Boolean {
    val cleaned = text.trim()
    if (cleaned.isBlank()) return false

    val hasDateTime = detections.any { it.type == TextType.DATE_TIME }
    if (!hasDateTime) return false
    if (!containsCalendarKeyword(cleaned)) return false

    if (isPureDirectType(cleaned, detections)) return false
    return true
}

private fun containsCalendarKeyword(text: String): Boolean {
    val lower = text.lowercase()
    val keywords = listOf("họp", "meeting", "lịch", "sự kiện", "event", "phỏng vấn", "hẹn")
    return keywords.any { lower.contains(it) }
}

private fun isPureDirectType(text: String, detections: List<Detection>): Boolean {
    val trimmed = text.trim()
    val directTypes = setOf(TextType.PHONE_NUMBER, TextType.EMAIL, TextType.URL, TextType.ADDRESS)
    val matching = detections.firstOrNull { it.type in directTypes }
    return matching != null && coversMostOfText(trimmed, matching.range)
}

private fun coversMostOfText(text: String, range: IntRange): Boolean {
    val matchLength = range.last - range.first + 1
    return matchLength >= (text.length * 0.8)
}
