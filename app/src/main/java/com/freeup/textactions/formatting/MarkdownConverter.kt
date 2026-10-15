package com.freeup.textactions.formatting

class MarkdownConverter {

    fun convert(text: String): String {
        val lines = text.lines()
        val output = StringBuilder()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isBlank()) {
                output.append("\n")
                i++
                continue
            }

            // Table block detection (Name + number)
            val tableBlock = extractTableBlock(lines, i)
            if (tableBlock != null) {
                output.append("| Name | Value |\n")
                output.append("| --- | --- |\n")
                tableBlock.forEach { (name, value) ->
                    output.append("| ").append(name).append(" | ").append(value).append(" |\n")
                }
                i += tableBlock.size
                continue
            }

            if (isHeading(line, isFirstLine = (i == 0))) {
                output.append("# ").append(line.removeSuffix(":")).append("\n")
            } else if (isListLine(line)) {
                output.append(normalizeListLine(line)).append("\n")
            } else {
                output.append(line).append("\n")
            }
            i++
        }
        return output.toString().trimEnd()
    }

    private fun isHeading(line: String, isFirstLine: Boolean): Boolean {
        if (line.length in 3..100 && line.all { it.isUpperCase() || it.isWhitespace() || it.isDigit() || it == '-' }) {
            return true
        }
        if (line.endsWith(":") && line.length <= 100) return true
        if (isFirstLine && line.length in 3..100 && !line.matches(Regex(".*[.!?]$"))) return true
        return false
    }

    private fun isListLine(line: String): Boolean {
        return Regex("^\\s*(?:[\\-*•]|\\d+[\\).])\\s+.+").containsMatchIn(line)
    }

    private fun normalizeListLine(line: String): String {
        val trimmed = line.trim()
        return when {
            trimmed.startsWith("• ") -> "- " + trimmed.removePrefix("• ").trim()
            trimmed.startsWith("- ") -> "- " + trimmed.removePrefix("- ").trim()
            Regex("^\\d+[\\).]\\s+").containsMatchIn(trimmed) -> {
                val content = trimmed.replaceFirst(Regex("^\\d+[\\).]\\s+"), "")
                "1. $content"
            }
            else -> "- $trimmed"
        }
    }

    private fun extractTableBlock(lines: List<String>, start: Int): List<Pair<String, String>>? {
        val block = mutableListOf<Pair<String, String>>()
        var index = start
        while (index < lines.size) {
            val line = lines[index].trim()
            if (line.isBlank()) break
            val match = Regex("^(.+?)\\s*[:\\t]\\s*(\\d+(?:[\\.,]\\d+)?)$").find(line)
                ?: Regex("^(.+?)\\s+(\\d+(?:[\\.,]\\d+)?)$").find(line)
            if (match == null) break
            val name = match.groupValues[1].trim()
            val value = match.groupValues[2].trim()
            block.add(name to value)
            index++
        }
        return if (block.size >= 2) block else null
    }
}
