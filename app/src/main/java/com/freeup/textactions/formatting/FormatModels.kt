package com.freeup.textactions.formatting

import com.freeup.textactions.R

enum class FormatOption(val labelRes: Int) {
    CLEAN_SPACING(R.string.format_clean_spacing),
    FIX_BROKEN_LINES(R.string.format_fix_broken_lines),
    REMOVE_LINE_BREAKS(R.string.format_remove_line_breaks),
    UPPERCASE(R.string.format_uppercase),
    LOWERCASE(R.string.format_lowercase),
    TITLE_CASE(R.string.format_title_case),
    SENTENCE_CASE(R.string.format_sentence_case),
    BULLET_LIST(R.string.format_bullet_list),
    NUMBERED_LIST(R.string.format_numbered_list),
    REMOVE_EMOJI(R.string.format_remove_emoji),
    MARKDOWN(R.string.format_markdown)
}

enum class DefaultListStyle {
    BULLET,
    NUMBERED
}

data class FormatSettings(
    val autoSuggest: Boolean = true,
    val enableOcrFix: Boolean = true,
    val defaultListStyle: DefaultListStyle = DefaultListStyle.BULLET,
    val preserveOriginalText: Boolean = false
)

data class FormatSuggestion(
    val option: FormatOption,
    val labelRes: Int
)

data class FormatPreviewState(
    val before: String,
    val after: String,
    val option: FormatOption
)
