package com.freeup.textactions.history

enum class ActionType {
    COPY,
    TRANSLATE,
    EXTRACT_ENTITY,
    CREATE_REMINDER_FROM_TEXT,
    CREATE_REMINDER,
    SHARE,
    SAVE_TO_NOTE,
    AI_ASK,
    OPEN_CHAT_APP,
    OPEN_MEDIA,
    UNKNOWN;

    companion object {
        fun fromRaw(raw: String): ActionType {
            return when (raw.uppercase()) {
                "COPY" -> COPY
                "TRANSLATE" -> TRANSLATE
                "EXTRACT_ENTITY", "ENTITY" -> EXTRACT_ENTITY
                "CREATE_REMINDER_FROM_TEXT", "REMINDER_FROM_TEXT" -> CREATE_REMINDER_FROM_TEXT
                "CREATE_REMINDER", "REMINDER" -> CREATE_REMINDER
                "SHARE", "QUICK_SHARE" -> SHARE
                "SAVE_TO_NOTE", "SAVE_NOTE" -> SAVE_TO_NOTE
                "AI_ASK", "ASK_AI" -> AI_ASK
                "OPEN_CHAT_APP", "CHAT_APP" -> OPEN_CHAT_APP
                "OPEN_MEDIA", "MEDIA" -> OPEN_MEDIA
                else -> UNKNOWN
            }
        }
    }
}
