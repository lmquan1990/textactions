package com.freeup.textactions

import android.graphics.Rect
import com.google.mlkit.vision.text.Text
import kotlin.math.abs
import kotlin.math.max

/**
 * Rebuild readable plain text from ML Kit's positioned lines.
 *
 * `Text.text` is useful for short snippets, but its block order often collapses
 * paragraph spacing and interleaves labels from a table or infographic. Plain
 * text cannot retain fonts, colours or charts, but this keeps the original
 * reading order, line breaks and visible paragraph gaps.
 */
object OcrLayoutFormatter {
    private data class PositionedLine(val text: String, val bounds: Rect)
    private data class Row(val lines: MutableList<PositionedLine>) {
        val top: Int get() = lines.minOf { it.bounds.top }
        val bottom: Int get() = lines.maxOf { it.bounds.bottom }
        val centerY: Float get() = (top + bottom) / 2f
    }

    fun format(result: Text): String {
        val lines = result.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val value = line.text.trim()
                val bounds = line.boundingBox
                if (value.isEmpty() || bounds == null) null else PositionedLine(value, bounds)
            }
        }
        if (lines.isEmpty()) return result.text.trim()

        val typicalHeight = lines.map { it.bounds.height() }.sorted().let { heights ->
            heights[heights.size / 2].coerceAtLeast(1)
        }
        val rows = mutableListOf<Row>()
        lines.sortedBy { it.bounds.top }.forEach { line ->
            val matchingRow = rows.lastOrNull()?.takeIf { row ->
                abs(row.centerY - (line.bounds.top + line.bounds.bottom) / 2f) <= max(typicalHeight * .55f, line.bounds.height() * .45f)
            }
            if (matchingRow == null) rows += Row(mutableListOf(line)) else matchingRow.lines += line
        }

        return buildString {
            var previousBottom: Int? = null
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    val gap = row.top - (previousBottom ?: row.top)
                    // A large vertical gap is intentional whitespace between title,
                    // paragraphs or sections, so keep one empty line in the result.
                    append(if (gap > typicalHeight * .9f) "\n\n" else "\n")
                }
                row.lines.sortedBy { it.bounds.left }.forEachIndexed { lineIndex, line ->
                    if (lineIndex > 0) append("    ")
                    append(line.text)
                }
                previousBottom = row.bottom
            }
        }.trim()
    }
}
