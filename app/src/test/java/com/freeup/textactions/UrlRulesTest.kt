package com.freeup.textactions

import com.freeup.textactions.domain.isRealUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlRulesTest {
    @Test fun rejectsLogTagLikeDomains() {
        listOf("vendor.camera", "Camera2CaptureRequest.Builder", "a.b.c", "com.freeup.textactions")
            .forEach { assertFalse(it, isRealUrl(it)) }
    }

    @Test fun rejectsNumbersAndDates() {
        listOf("3.14", "10.14", "15:31:10.173").forEach { assertFalse(it, isRealUrl(it)) }
    }

    @Test fun acceptsExplicitSchemeAndWww() {
        listOf("https://google.com", "http://foo.camera/x", "www.example.com/path", "WWW.Example.com")
            .forEach { assertTrue(it, isRealUrl(it)) }
    }

    @Test fun acceptsCommonTlds() {
        listOf("example.com", "abc.vn", "foo.io", "docs.google.com/a?b=1", "site.org:8080")
            .forEach { assertTrue(it, isRealUrl(it)) }
    }

    @Test fun trimsWhitespace() {
        assertTrue(isRealUrl("  example.com  "))
    }

    @Test fun rejectsBlankAndPlainWords() {
        listOf("", "   ", "hello", "localhost").forEach { assertFalse(it, isRealUrl(it)) }
    }
}
