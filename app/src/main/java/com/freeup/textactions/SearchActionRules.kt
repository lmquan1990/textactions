package com.freeup.textactions

import android.util.Patterns

fun sanitizeSearchQuery(text: String): String {
    return text
        .replace(Regex("[\\u0000-\\u001F]"), "")
        .trim()
        .replace(Regex("\\s+"), " ")
}

fun shouldShowSearchAction(text: String, hasDirectActionMatch: Boolean): Boolean {
    val query = sanitizeSearchQuery(text)
    if (query.isBlank()) return false
    if (query.length < 2) return false
    if (hasDirectActionMatch) return false
    if (looksLikePhone(query)) return false
    if (Patterns.EMAIL_ADDRESS.matcher(query).matches()) return false
    if (Patterns.WEB_URL.matcher(query).matches() || looksLikeDomain(query)) return false
    if (isNumericOnly(query)) return false
    return true
}

fun limitSearchQuery(text: String, maxLength: Int = 150): String {
    val query = sanitizeSearchQuery(text)
    return if (query.length > maxLength) query.substring(0, maxLength) else query
}

private fun looksLikePhone(text: String): Boolean {
    if (!text.matches(Regex("^[+()\\d\\s.-]+$"))) return false
    val digitsOnly = text.replace(Regex("[^0-9]"), "")
    return digitsOnly.length in 7..15
}

private fun looksLikeDomain(text: String): Boolean {
    if (text.contains(" ")) return false
    return text.matches(Regex("^[\\p{L}\\p{N}-]+(\\.[\\p{L}\\p{N}-]+)+$"))
}

private fun isNumericOnly(text: String): Boolean {
    return text.matches(Regex("^[0-9]+$"))
}
