package com.freeup.textactions.history

fun formatHistoryContextText(inputText: String?, maxChars: Int = 80): String {
    // Collapse newlines/runs of spaces so the ellipsis never lands on its own line
    val t = (inputText ?: "").replace(Regex("\\s+"), " ").trim()
    return if (t.length <= maxChars) t else t.take(maxChars).trimEnd() + "…"
}
