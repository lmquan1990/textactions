package com.freeup.textactions

import com.freeup.textactions.history.formatHistoryContextText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryFormatTest {
    @Test fun shortTextUnchanged() {
        assertEquals("BAO", formatHistoryContextText("BAO"))
    }

    @Test fun nullAndBlankGiveEmpty() {
        assertEquals("", formatHistoryContextText(null))
        assertEquals("", formatHistoryContextText("  \n \n "))
    }

    @Test fun collapsesNewlinesAndSpaces() {
        assertEquals("BAO HÔNG BÁO LỊCH", formatHistoryContextText("BAO\n\n\n  HÔNG   BÁO\tLỊCH"))
    }

    @Test fun longTextTruncatedWithEllipsisOnSameLine() {
        val out = formatHistoryContextText("BAO\n\n" + "x".repeat(200))
        assertTrue(out.endsWith("…"))
        assertFalse(out.contains("\n"))
        assertFalse(out.contains(" …"))
        assertTrue(out.length <= 81)
    }

    @Test fun truncationDoesNotLeaveTrailingSpaceBeforeEllipsis() {
        // char 80 is a space after collapsing
        val text = "a".repeat(79) + " " + "b".repeat(20)
        assertEquals("a".repeat(79) + "…", formatHistoryContextText(text))
    }

    @Test fun vietnameseAndEmojiKept() {
        assertEquals("Thánh Rong bie 😀", formatHistoryContextText("Thánh  Rong bie\n😀"))
    }

    @Test fun exactlyMaxCharsNotTruncated() {
        val s = "a".repeat(80)
        assertEquals(s, formatHistoryContextText(s))
    }
}
