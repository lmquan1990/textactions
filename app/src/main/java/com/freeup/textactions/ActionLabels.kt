package com.freeup.textactions

import android.content.Context

/**
 * Single source of truth for action display labels.
 * Used by Settings (ActionsSettingsScreen) and History (RecentActionItemCard) so labels stay in sync.
 */
object ActionLabels {

    private val labelResByActionId: Map<String, Int> = mapOf(
        // Recommended
        "call" to R.string.action_label_call,
        "sms" to R.string.action_label_sms,
        "save_contact" to R.string.action_label_save_contact,
        "email" to R.string.action_label_email,
        "map" to R.string.action_label_map,
        "copy_address" to R.string.action_label_copy_address,
        "open_link" to R.string.action_label_open_link,
        "zalo" to R.string.action_label_zalo,
        "direction" to R.string.action_label_direction,
        "ride_hailing" to R.string.action_label_ride_hailing,
        "gps_map" to R.string.action_label_gps_map,
        "shorten_link" to R.string.action_label_shorten_link,
        "reader_mode" to R.string.action_label_reader_mode,
        // Time
        "calendar" to R.string.action_label_calendar,
        "reminder" to R.string.action_label_reminder,
        // Utilities
        "copy" to R.string.action_label_copy,
        "share" to R.string.action_label_share,
        "quick_share" to R.string.action_label_share,
        "search" to R.string.action_label_search,
        "ask_ai" to R.string.action_label_ask_ai,
        "media" to R.string.action_label_media,
        "save_note" to R.string.action_label_save_note,
        "format_text" to R.string.action_label_format_text,
        "open_chat_app" to R.string.action_label_open_chat_app,
        "create_qr" to R.string.action_label_create_qr,
        "translate" to R.string.action_label_translate,
        "summarize" to R.string.action_label_summarize,
        "tts" to R.string.action_label_tts,
        "smart_read_aloud" to R.string.action_label_smart_read_aloud,
        "ocr" to R.string.tool_scan_text,
        "ocr_scan" to R.string.tool_scan_text,
        "qr" to R.string.tool_scan_qr,
        "qr_scan" to R.string.tool_scan_qr,
        "save_to_note" to R.string.action_label_save_note,
    )

    /** Normalize history actionType (e.g. "URL", "QUICK_SHARE") to Settings action id for lookup. */
    private fun historyActionTypeToActionId(actionType: String): String {
        return when (actionType.uppercase()) {
            "URL" -> "open_link"
            "QUICK_SHARE" -> "share"
            "READ_ALOUD" -> "tts"
            "SAVE_TO_NOTE" -> "save_note"
            "OCR_SCAN" -> "ocr"
            "QR_SCAN" -> "qr"
            else -> actionType.lowercase()
        }
    }

    /**
     * Label for Settings and any UI that uses action id (lowercase).
     * Example: getLabel(context, "open_link") -> "Open Link" / "Mở liên kết"
     */
    fun getLabel(context: Context, actionId: String): String {
        val key = actionId.lowercase()
        val resId = labelResByActionId[key]
        return if (resId != null) context.getString(resId)
        else key.replace("_", " ").replaceFirstChar { it.uppercaseChar() }
    }

    /**
     * Label for History list. History stores actionType (e.g. "CALL", "OPEN_LINK", "URL").
     * Normalizes to action id then returns same label as Settings.
     */
    fun getLabelForHistory(context: Context, actionType: String): String {
        val actionId = historyActionTypeToActionId(actionType)
        return getLabel(context, actionId)
    }
}
