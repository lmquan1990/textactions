package com.freeup.textactions.domain.model

import androidx.compose.ui.graphics.vector.ImageVector

data class ActionSetting(
    val id: String,
    val title: String,
    val description: String,
    val icon: Any, // ImageVector or Int resource
    val isEnabled: Boolean = true,
    val category: ActionCategory,
    val isExperimental: Boolean = false
)

enum class ActionCategory {
    RECOMMENDED,  // Liên hệ & địa chỉ (call, sms, email, map, open link...)
    TIME,         // Lịch & nhắc (calendar, reminder)
    UTILITIES,    // Văn bản (copy, share, search, translate...)
    AI, ADVANCED
}
