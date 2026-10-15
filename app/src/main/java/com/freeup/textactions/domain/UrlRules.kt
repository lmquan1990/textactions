package com.freeup.textactions.domain

/**
 * ML Kit flags things like "vendor.camera" (a log tag) as URLs because ".camera" is a valid TLD.
 * Accept only explicit schemes / www., or a domain ending in a common TLD.
 */
fun isRealUrl(value: String): Boolean {
    val v = value.trim().lowercase()
    if (v.startsWith("http://") || v.startsWith("https://") || v.startsWith("www.")) return true
    val host = v.substringBefore("/").substringBefore("?").substringBefore(":")
    val tld = host.substringAfterLast(".", "")
    return host.contains(".") && tld in COMMON_TLDS
}

private val COMMON_TLDS = setOf(
    "com", "net", "org", "edu", "gov", "io", "co", "me", "app", "dev", "ai", "info", "biz",
    "vn", "us", "uk", "de", "fr", "jp", "kr", "cn", "sg", "au", "ca", "in", "ru", "tv", "xyz", "online", "site", "store", "tech"
)
