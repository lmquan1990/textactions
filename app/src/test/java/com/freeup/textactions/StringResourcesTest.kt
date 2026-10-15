package com.freeup.textactions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StringResourcesTest {
    private fun strings(dir: String): Map<String, String> {
        val f = File("src/main/res/$dir/strings.xml")
        assertTrue("missing ${f.absolutePath}", f.exists())
        val re = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        return re.findAll(f.readText()).associate { it.groupValues[1] to it.groupValues[2] }
    }

    @Test fun appNameIsShort() {
        assertEquals("Image to Text", strings("values")["app_name"])
        assertEquals("Image to Text", strings("values-vi")["app_name"])
    }

    @Test fun noSnippetWordInEnglishUiStrings() {
        val bad = strings("values").filter { it.value.contains("snippet", ignoreCase = true) }
        assertTrue("Still says snippet: ${bad.keys}", bad.isEmpty())
    }

    @Test fun noQuickNoteWordingInVietnamese() {
        val bad = strings("values-vi").filter { it.value.contains("ghi chú nhanh", ignoreCase = true) }
        assertTrue("Still says 'ghi chú nhanh': ${bad.keys}", bad.isEmpty())
    }

    @Test fun translateSubtitleNotHardcodedToVietnamese() {
        assertTrue(!strings("values")["dynamic_desc_translate"]!!.contains("Vietnamese"))
    }

    @Test fun pinnedGroupLabelExists() {
        assertTrue(strings("values").containsKey("time_group_pinned"))
    }
}
