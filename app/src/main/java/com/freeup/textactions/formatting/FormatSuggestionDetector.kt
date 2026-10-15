package com.freeup.textactions.formatting

import com.freeup.textactions.R

object FormatSuggestionDetector {

    fun suggest(text: String, settings: FormatSettings): List<FormatSuggestion> {
        if (!settings.autoSuggest) return emptyList()
        val suggestions = mutableListOf<FormatSuggestion>()
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val shortLines = lines.count { it.length < 40 }
        val shortLineRatio = shortLines.toFloat() / lines.size.toFloat()
        val hasManyShortLines = shortLines >= 5 && shortLineRatio > 0.6f
        val hasMultiSpaces = Regex("[ \\t]{2,}").containsMatchIn(text)
        val hasListPattern = lines.count { isListLine(it) } >= 2
        val hasTablePattern = lines.count { isNameNumberLine(it) } >= 2

        if (hasMultiSpaces) {
            suggestions.add(FormatSuggestion(FormatOption.CLEAN_SPACING, R.string.format_clean_spacing))
        }
        if (settings.enableOcrFix && hasManyShortLines) {
            suggestions.add(FormatSuggestion(FormatOption.FIX_BROKEN_LINES, R.string.format_fix_broken_lines))
        }
        if (hasListPattern) {
            val option = if (settings.defaultListStyle == DefaultListStyle.NUMBERED) {
                FormatOption.NUMBERED_LIST
            } else {
                FormatOption.BULLET_LIST
            }
            suggestions.add(FormatSuggestion(option, option.labelRes))
        }
        if (hasTablePattern) {
            suggestions.add(FormatSuggestion(FormatOption.MARKDOWN, R.string.format_markdown))
        }
        return suggestions.distinctBy { it.option }
    }

    private fun isListLine(line: String): Boolean {
        return Regex("^\\s*(?:[\\-*•]|\\d+[\\).])\\s+.+").containsMatchIn(line.trim())
    }

    private fun isNameNumberLine(line: String): Boolean {
        val trimmed = line.trim()
        return Regex("^(.+?)\\s*[:\\t]\\s*(\\d+(?:[\\.,]\\d+)?)$").containsMatchIn(trimmed) ||
            Regex("^(.+?)\\s+(\\d+(?:[\\.,]\\d+)?)$").containsMatchIn(trimmed)
    }
}
