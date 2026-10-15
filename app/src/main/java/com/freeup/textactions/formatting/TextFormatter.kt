package com.freeup.textactions.formatting

object TextFormatter {

    fun cleanSpacing(text: String): String {
        val lines = text.lines()
        val cleaned = lines.map { line ->
            line.replace(Regex("\\s+"), " ").trim()
        }
        val result = StringBuilder()
        var blankCount = 0
        cleaned.forEach { line ->
            if (line.isBlank()) {
                blankCount += 1
                if (blankCount <= 2) {
                    result.appendLine()
                }
            } else {
                blankCount = 0
                result.append(line).append("\n")
            }
        }
        return result.toString().trimEnd()
    }

    fun fixBrokenLines(text: String): String {
        val lines = text.lines()
        val result = StringBuilder()
        var pending = StringBuilder()

        fun flushPending() {
            if (pending.isNotEmpty()) {
                result.append(pending.toString().trim()).append("\n")
                pending = StringBuilder()
            }
        }

        lines.forEach { raw ->
            val line = raw.trim()
            if (line.isBlank()) {
                flushPending()
                result.append("\n")
                return@forEach
            }

            if (isListLine(line)) {
                flushPending()
                result.append(line).append("\n")
                return@forEach
            }

            if (pending.isEmpty()) {
                pending.append(line)
            } else {
                if (endsWithSentence(line) || endsWithSentence(pending.toString())) {
                    pending.append("\n").append(line)
                } else {
                    pending.append(" ").append(line)
                }
            }
        }
        flushPending()
        return result.toString().trimEnd()
    }

    fun toBulletList(text: String): String {
        val lines = text.lines()
        return lines
            .filter { it.isNotBlank() }
            .joinToString("\n") { "• ${it.trim()}" }
    }

    fun toNumberedList(text: String): String {
        val lines = text.lines().filter { it.isNotBlank() }
        return lines.mapIndexed { index, line ->
            "${index + 1}. ${line.trim()}"
        }.joinToString("\n")
    }

    fun toTitleCase(text: String): String {
        return text.split(Regex("\\s+")).joinToString(" ") { word ->
            if (word.isBlank()) return@joinToString word
            val lower = word.lowercase()
            lower.replaceFirstChar { it.uppercaseChar() }
        }.trim()
    }

    fun toUpperCase(text: String): String = text.uppercase()

    fun toLowerCase(text: String): String = text.lowercase()

    fun toSentenceCase(text: String): String {
        // Split on sentence-ending punctuation and capitalize first letter of each sentence
        val result = StringBuilder()
        val sentenceRegex = Regex("([.!?]\\s+|^)")
        var lastEnd = 0
        val matches = sentenceRegex.findAll(text).toList()

        if (matches.isEmpty()) {
            return text.trimStart().replaceFirstChar { it.uppercaseChar() }
        }

        for (match in matches) {
            val segmentStart = match.range.last + 1
            if (segmentStart >= text.length) continue
            result.append(text.substring(lastEnd, segmentStart))
            val remaining = text.substring(segmentStart)
            val firstNonSpace = remaining.indexOfFirst { !it.isWhitespace() }
            if (firstNonSpace >= 0) {
                result.append(remaining.substring(0, firstNonSpace))
                result.append(remaining[firstNonSpace].uppercaseChar())
                lastEnd = segmentStart + firstNonSpace + 1
            }
        }
        if (lastEnd < text.length) result.append(text.substring(lastEnd))

        val str = result.toString()
        // Capitalize first char if not yet done
        return if (str.isNotBlank()) {
            val idx = str.indexOfFirst { !it.isWhitespace() }
            if (idx >= 0) str.substring(0, idx) + str[idx].uppercaseChar() + str.substring(idx + 1)
            else str
        } else str
    }

    fun removeLineBreaks(text: String): String {
        // Join lines with a single space, collapsing multiple newlines into paragraph breaks
        val paragraphs = text.split(Regex("\\n{2,}"))
        return paragraphs.joinToString("\n\n") { paragraph ->
            paragraph.lines().joinToString(" ") { it.trim() }.trim()
        }.trim()
    }

    fun removeEmoji(text: String): String {
        // Remove emoji and other pictographic characters
        return text.replace(Regex("[\\p{So}\\p{Sk}\\p{Sm}\\p{Cs}]"), "")
            .replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+"), "") // Surrogate pairs (emoji)
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }

    private fun isListLine(line: String): Boolean {
        return Regex("^\\s*(?:[\\-*•]|\\d+[\\).])\\s+.+").containsMatchIn(line)
    }

    private fun endsWithSentence(line: String): Boolean {
        return line.endsWith(".") || line.endsWith("!") || line.endsWith("?")
    }
}
