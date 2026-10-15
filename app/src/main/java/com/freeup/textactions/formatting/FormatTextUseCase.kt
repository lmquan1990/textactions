package com.freeup.textactions.formatting

class FormatTextUseCase(
    private val formatter: TextFormatter = TextFormatter,
    private val markdownConverter: MarkdownConverter = MarkdownConverter()
) {
    fun format(text: String, option: FormatOption, settings: FormatSettings): String {
        val base = when (option) {
            FormatOption.CLEAN_SPACING -> formatter.cleanSpacing(text)
            FormatOption.FIX_BROKEN_LINES -> formatter.fixBrokenLines(text)
            FormatOption.REMOVE_LINE_BREAKS -> formatter.removeLineBreaks(text)
            FormatOption.UPPERCASE -> formatter.toUpperCase(text)
            FormatOption.LOWERCASE -> formatter.toLowerCase(text)
            FormatOption.TITLE_CASE -> formatter.toTitleCase(text)
            FormatOption.SENTENCE_CASE -> formatter.toSentenceCase(text)
            FormatOption.BULLET_LIST -> formatter.toBulletList(text)
            FormatOption.NUMBERED_LIST -> formatter.toNumberedList(text)
            FormatOption.REMOVE_EMOJI -> formatter.removeEmoji(text)
            FormatOption.MARKDOWN -> markdownConverter.convert(text)
        }
        return if (settings.preserveOriginalText && base.isNotBlank() && base != text) {
            "${text.trim()}\n${base.trim()}"
        } else {
            base
        }
    }
}
