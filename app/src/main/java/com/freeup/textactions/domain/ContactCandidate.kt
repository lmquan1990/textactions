package com.freeup.textactions.domain

/**
 * Aggregates detected entities to form a potential contact entry.
 */
data class ContactCandidate(
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val name: String? = null,
    val organization: String? = null,
    val address: String? = null
) {
    fun isValid(): Boolean {
        // If there's a phone number, we offer Save Contact (as per user's latest request)
        return phones.isNotEmpty()
    }
}
