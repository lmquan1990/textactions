package com.freeup.textactions.util

import android.util.Patterns

object NoteCategorizer {
    const val TYPE_TEXT = "TEXT"
    const val TYPE_LINK = "URL"
    const val TYPE_EMAIL = "EMAIL"
    const val TYPE_PHONE = "PHONE"
    const val TYPE_ADDRESS = "ADDRESS"

    fun categorize(text: String): String {
        return when (detectType(text)) {
            TYPE_EMAIL -> "Contact"
            TYPE_PHONE -> "Contact"
            else -> {
                if (isShoppingList(text)) "Shopping"
                else "General"
            }
        }
    }

    fun detectType(text: String): String {
        return when {
            Patterns.WEB_URL.matcher(text).find() -> TYPE_LINK
            Patterns.EMAIL_ADDRESS.matcher(text).find() -> TYPE_EMAIL
            Patterns.PHONE.matcher(text).find() -> TYPE_PHONE
            // Simple address detection (very basic)
            text.contains(Regex("\\d+\\s+[A-z]+\\s+(St|Street|Ave|Avenue|Rd|Road|Blvd|Boulevard)", RegexOption.IGNORE_CASE)) -> TYPE_ADDRESS
            else -> TYPE_TEXT
        }
    }

    fun extractSourceUrl(text: String): String? {
        val matcher = Patterns.WEB_URL.matcher(text)
        return if (matcher.find()) matcher.group() else null
    }

    private fun isCode(text: String): Boolean {
        val codeIndicators = listOf("{", "}", "function", "val ", "var ", "const ", "import ", "class ", "def ", "return ")
        var score = 0
        codeIndicators.forEach { if (text.contains(it)) score++ }
        return score >= 2
    }

    private fun isShoppingList(text: String): Boolean {
        val lines = text.lines()
        // Must have multiple lines or bullet points
        if (lines.size < 2) return false
        val listIndicators = lines.count { it.trim().startsWith("- ") || it.trim().startsWith("* ") || (it.trim().firstOrNull()?.isDigit() == true && it.trim().contains(".")) }
        return listIndicators.toFloat() / lines.size > 0.5
    }
}
