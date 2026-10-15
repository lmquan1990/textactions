package com.freeup.textactions.domain.model

data class ActionHistory(
    val id: Long = 0,
    val actionType: String,
    val inputText: String,
    val resultText: String? = null,
    val metadata: String? = null,
    val createdAt: Long,
    val isPinned: Boolean = false,
    val pinnedAt: Long? = null
)

enum class ActionType {
    TRANSLATE, CALL, OPEN_MAPS, SAVE_CONTACT, OCR_SCAN, SMS, WEB_LINK, REMINDER
}
