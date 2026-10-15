package com.freeup.textactions.history

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast
import com.freeup.textactions.R
import com.freeup.textactions.TextActionItem
import com.freeup.textactions.data.local.db.NoteEntity
import com.freeup.textactions.data.repository.HomeRepository
import com.freeup.textactions.domain.CreateReminderUseCase
import com.freeup.textactions.domain.ExtractEntitiesUseCase
import com.freeup.textactions.history.ActionType.Companion.fromRaw
import com.freeup.textactions.ActionRegistry
import com.freeup.textactions.util.NoteCategorizer
import com.freeup.textactions.PhoneClassifier
import com.freeup.textactions.TextAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.ZoneId
import java.util.UUID

sealed class ReRunOutcome {
    data class ToastMessage(val message: String) : ReRunOutcome()
    data class SnackbarMessage(val message: String) : ReRunOutcome()
    data class EntityResults(val items: List<TextActionItem>) : ReRunOutcome()
    object None : ReRunOutcome()
}

class ActionReRunner(
    private val context: Context,
    private val historyRepository: HistoryRepository,
    private val homeRepository: HomeRepository,
    private val reminderUseCase: CreateReminderUseCase,
    private val extractEntitiesUseCase: ExtractEntitiesUseCase
) {

    suspend fun rerun(item: com.freeup.textactions.domain.model.ActionHistory): ReRunOutcome {
        val text = item.inputText.trim()
        if (text.isBlank()) {
            return ReRunOutcome.SnackbarMessage(context.getString(R.string.history_text_empty))
        }

        return when (fromRaw(item.actionType)) {
            ActionType.COPY -> {
                copyToClipboard(text)
                ReRunOutcome.ToastMessage(context.getString(R.string.copied))
            }
            ActionType.TRANSLATE -> {
                val intent = Intent(context, com.freeup.textactions.ProcessTextActivity::class.java).apply {
                    putExtra(Intent.EXTRA_PROCESS_TEXT, text)
                    putExtra("EXTRA_AUTO_TRANSLATE", true)
                    // Reuse the saved translation instead of translating again
                    val savedText = item.resultText
                    val targetLang = parseMetadata(item.metadata, "targetLang")
                    if (!savedText.isNullOrBlank() && targetLang != null) {
                        putExtra("EXTRA_CACHED_TRANSLATION", savedText)
                        putExtra("EXTRA_CACHED_SOURCE_LANG", parseMetadata(item.metadata, "sourceLang") ?: "")
                        putExtra("EXTRA_CACHED_TARGET_LANG", targetLang)
                    }
                }
                openIntentSafe(intent)
                ReRunOutcome.None
            }
            ActionType.EXTRACT_ENTITY -> {
                val results = extractEntitiesUseCase.execute(text)
                ReRunOutcome.EntityResults(results)
            }
            ActionType.CREATE_REMINDER_FROM_TEXT -> {
                val time = reminderUseCase.parseReminderTime(text)
                if (time == null) {
                    ReRunOutcome.SnackbarMessage(context.getString(R.string.history_no_date_detected))
                } else {
                    val startMillis = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val intent = Intent(Intent.ACTION_INSERT).apply {
                        data = CalendarContract.Events.CONTENT_URI
                        putExtra(CalendarContract.Events.TITLE, text)
                        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startMillis + 60 * 60 * 1000)
                    }
                    openIntentSafe(intent)
                    ReRunOutcome.None
                }
            }
            ActionType.CREATE_REMINDER -> {
                val intent = Intent(Intent.ACTION_INSERT).apply {
                    data = CalendarContract.Events.CONTENT_URI
                    putExtra(CalendarContract.Events.TITLE, text)
                }
                openIntentSafe(intent)
                ReRunOutcome.None
            }
            ActionType.SHARE -> {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                openIntentSafe(Intent.createChooser(intent, context.getString(R.string.share_title)))
                ReRunOutcome.None
            }
            ActionType.SAVE_TO_NOTE -> {
                saveNote(text)
                ReRunOutcome.ToastMessage(context.getString(R.string.saved_to_notes))
            }
            ActionType.AI_ASK -> {
                val intent = Intent(context, com.freeup.textactions.ProcessTextActivity::class.java).apply {
                    putExtra(Intent.EXTRA_PROCESS_TEXT, text)
                }
                openIntentSafe(intent)
                ReRunOutcome.None
            }
            ActionType.OPEN_CHAT_APP -> {
                val phone = detectPhone(text)
                if (phone == null) {
                    ReRunOutcome.SnackbarMessage(context.getString(R.string.history_phone_not_valid))
                } else {
                    openChatApp(phone)
                    ReRunOutcome.None
                }
            }
            ActionType.OPEN_MEDIA -> {
                openMedia(text)
                ReRunOutcome.None
            }
            ActionType.UNKNOWN -> {
                val ok = ActionRegistry.runActionFromHistory(context, item)
                if (ok) {
                    ReRunOutcome.None
                } else {
                    ReRunOutcome.SnackbarMessage(context.getString(R.string.history_action_not_supported))
                }
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("History", text))
    }

    private suspend fun saveNote(text: String) {
        withContext(Dispatchers.IO) {
            if (homeRepository.hasNoteWithContent(text)) return@withContext
            val now = System.currentTimeMillis()
            val type = NoteCategorizer.detectType(text)
            val url = NoteCategorizer.extractSourceUrl(text)
            val note = NoteEntity(
                id = UUID.randomUUID().toString(),
                title = "",
                content = text,
                createdAt = now,
                category = "General",
                isLocked = false,
                sourceAppName = null,
                sourcePackageName = null,
                sourceUrl = url,
                detectedType = type,
                isArchived = false,
                isPinned = false,
                pinnedAt = null
            )
            homeRepository.insertNote(note)
        }
    }

    private fun openIntentSafe(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, context.getString(R.string.history_no_app_found), Toast.LENGTH_SHORT).show()
        }
    }

    private fun detectPhone(text: String): String? {
        val detections = TextAnalyzer().analyze(text)
        val candidate = detections.firstOrNull { it.type == com.freeup.textactions.TextType.PHONE_NUMBER }?.value
        val result = if (candidate != null) PhoneClassifier.detectPhone(candidate) else null
        return if (result?.isPhoneNumber == true) result.normalizedNumber else null
    }

    private fun openChatApp(phone: String) {
        val packages = listOf(
            "com.whatsapp",
            "com.zing.zalo",
            "org.telegram.messenger"
        )
        val uri = Uri.parse("https://wa.me/${phone.replace(Regex("[^0-9+]"), "")}")
        val baseIntent = Intent(Intent.ACTION_VIEW, uri)
        val installed = packages.firstOrNull { pkg ->
            context.packageManager.getLaunchIntentForPackage(pkg) != null
        }
        if (installed != null) {
            baseIntent.setPackage(installed)
            openIntentSafe(baseIntent)
        } else {
            openIntentSafe(baseIntent)
        }
    }

    private fun openMedia(text: String) {
        val query = text.trim()
        val youtubeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}"))
        val spotifyIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/${Uri.encode(query)}"))
        val youtubePkg = "com.google.android.youtube"
        val spotifyPkg = "com.spotify.music"
        when {
            context.packageManager.getLaunchIntentForPackage(youtubePkg) != null -> {
                youtubeIntent.setPackage(youtubePkg)
                openIntentSafe(youtubeIntent)
            }
            context.packageManager.getLaunchIntentForPackage(spotifyPkg) != null -> {
                spotifyIntent.setPackage(spotifyPkg)
                openIntentSafe(spotifyIntent)
            }
            else -> openIntentSafe(youtubeIntent)
        }
    }

    private fun parseMetadata(metadata: String?, key: String): String? {
        if (metadata.isNullOrBlank()) return null
        return runCatching {
            val json = JSONObject(metadata)
            json.optString(key).ifBlank { null }
        }.getOrNull()
    }

}
