package com.freeup.textactions

import android.content.Context
import android.content.Intent
import android.net.Uri

import androidx.compose.ui.graphics.vector.ImageVector
import com.freeup.textactions.ui.theme.AppIcons
import com.freeup.textactions.limitSearchQuery

interface TextAction {
    val id: String
    val titleResId: Int
    val descriptionResId: Int
    val icon: Any // Resource ID or ImageVector
    val supportedTextTypes: Set<TextType>
    val priority: Int

    fun isEnabled(context: Context): Boolean = SettingsManager(context).isEnabled(id)
    fun isPinned(context: Context): Boolean = SettingsManager(context).isPinned(id)

    fun getDynamicTitle(context: Context, text: String): String = context.getString(titleResId)
    fun getDynamicDescription(context: Context, text: String): String = context.getString(descriptionResId)

    fun execute(context: Context, text: String)
}

class CallAction : TextAction {
    override val id = "call"
    override val titleResId = R.string.btn_call
    override val descriptionResId = R.string.desc_call
    override val icon = AppIcons.Phone
    override val supportedTextTypes = setOf(TextType.PHONE_NUMBER)
    override val priority = 10

    override fun getDynamicTitle(context: Context, text: String): String = context.getString(R.string.dynamic_call, text)
    override fun getDynamicDescription(context: Context, text: String): String = context.getString(R.string.dynamic_desc_call)

    override fun execute(context: Context, text: String) {
        val normalized = text.replace(Regex("[^0-9+]"), "")
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$normalized"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

class ShortenLinkAction : TextAction {
    override val id = "shorten_link"
    override val titleResId = R.string.btn_shorten_link
    override val descriptionResId = R.string.desc_shorten_link
    override val icon = AppIcons.Flash
    override val supportedTextTypes = setOf(TextType.URL)
    override val priority = 4
 
    override fun getDynamicTitle(context: Context, text: String): String = context.getString(R.string.dynamic_shorten_link)
    override fun getDynamicDescription(context: Context, text: String): String = context.getString(R.string.dynamic_desc_shorten_link)

    override fun execute(context: Context, text: String) {
        val encodedUrl = java.net.URLEncoder.encode(text, "UTF-8")
        val shortenUrl = "https://tinyurl.com/create.php?url=$encodedUrl"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(shortenUrl))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

class FormatTextAction : TextAction {
    override val id = "format_text"
    override val titleResId = R.string.action_label_format_text
    override val descriptionResId = R.string.action_desc_format_text
    override val icon = AppIcons.TextFormat
    override val supportedTextTypes = setOf(TextType.PLAIN_TEXT)
    override val priority = 1

    override fun execute(context: Context, text: String) {
        // Handled in UI (Format Text panel)
    }
}


class SmsAction : TextAction {
    override val id = "sms"
    override val titleResId = R.string.btn_sms
    override val descriptionResId = R.string.desc_sms
    override val icon = AppIcons.Sms
    override val supportedTextTypes = setOf(TextType.PHONE_NUMBER)
    override val priority = 7
 
    override fun getDynamicTitle(context: Context, text: String): String = context.getString(R.string.dynamic_sms, text)
    override fun getDynamicDescription(context: Context, text: String): String = context.getString(R.string.dynamic_desc_sms)

    override fun execute(context: Context, text: String) {
        val normalized = text.replace(Regex("[^0-9+]"), "")
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("sms:$normalized"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

class EmailAction : TextAction {
    override val id = "email"
    override val titleResId = R.string.btn_email
    override val descriptionResId = R.string.desc_email
    override val icon = AppIcons.Envelope
    override val supportedTextTypes = setOf(TextType.EMAIL)
    override val priority = 8

    override fun getDynamicTitle(context: Context, text: String): String = context.getString(R.string.dynamic_email, text)
    override fun getDynamicDescription(context: Context, text: String): String = context.getString(R.string.dynamic_desc_email)

    override fun execute(context: Context, text: String) {
        val emailRegex = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
        val match = emailRegex.find(text)?.value ?: text
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$match"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

class OpenLinkAction : TextAction {
    override val id = "open_link"
    override val titleResId = R.string.btn_open_link
    override val descriptionResId = R.string.desc_open_link
    override val icon = R.drawable.ic_google // Custom drawable
    override val supportedTextTypes = setOf(TextType.URL)
    override val priority = 7
 
    override fun getDynamicTitle(context: Context, text: String): String = context.getString(R.string.dynamic_open_link)
    override fun getDynamicDescription(context: Context, text: String): String = context.getString(R.string.dynamic_desc_open_link, text)

    override fun execute(context: Context, text: String) {
        var url = text
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}


class MapAction : TextAction {
    override val id = "map"
    override val titleResId = R.string.btn_maps
    override val descriptionResId = R.string.desc_maps
    override val icon = AppIcons.Map
    override val supportedTextTypes = setOf(TextType.ADDRESS)
    override val priority = 6

    override fun getDynamicTitle(context: Context, text: String): String = context.getString(R.string.dynamic_maps)
    // Show the exact same address string that will be used for the Maps query.
    override fun getDynamicDescription(context: Context, text: String): String = text

    override fun execute(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(text)}"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

class CopyAddressAction : TextAction {
    override val id = "copy_address"
    override val titleResId = R.string.btn_copy_address
    override val descriptionResId = R.string.desc_copy_address
    override val icon = AppIcons.Copy
    override val supportedTextTypes = setOf(TextType.ADDRESS)
    override val priority = 6

    override fun getDynamicTitle(context: Context, text: String): String = context.getString(R.string.dynamic_copy_address)
    // Show/copy the exact same address string as the Maps action.
    override fun getDynamicDescription(context: Context, text: String): String = text

    override fun execute(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("address", text))
        android.widget.Toast.makeText(context, context.getString(R.string.copied_address), android.widget.Toast.LENGTH_SHORT).show()
    }
}

class SummarizeAction : TextAction {
    override val id = "summarize"
    override val titleResId = R.string.btn_summarize
    override val descriptionResId = R.string.desc_summarize
    override val icon = AppIcons.Summarize
    override val supportedTextTypes = setOf(TextType.PLAIN_TEXT, TextType.NUMBER)
    override val priority = 3

    override fun getDynamicTitle(context: Context, text: String): String = context.getString(R.string.dynamic_summarize)
    override fun getDynamicDescription(context: Context, text: String): String = context.getString(R.string.dynamic_desc_summarize)

    override fun execute(context: Context, text: String) {
        // Handled by in-app bottom sheet flow.
    }
}

class SearchAction : TextAction {
    override val id = "search"
    override val titleResId = R.string.btn_search
    override val descriptionResId = R.string.desc_search
    override val icon = R.drawable.ic_google
    override val supportedTextTypes = setOf(TextType.PLAIN_TEXT, TextType.NUMBER)
    override val priority = 1
 
    override fun getDynamicTitle(context: Context, text: String): String = context.getString(R.string.dynamic_search)
    override fun getDynamicDescription(context: Context, text: String): String = context.getString(R.string.dynamic_desc_search)

    override fun execute(context: Context, text: String) {
        val query = limitSearchQuery(text)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

class QuickShareAction : TextAction {
    override val id = "quick_share"
    override val titleResId = R.string.btn_share
    override val descriptionResId = R.string.desc_share
    override val icon = AppIcons.Share
    override val supportedTextTypes = TextType.entries.toSet()
    override val priority = 10 // Higher priority for quick share

    override fun execute(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(intent, context.getString(R.string.share_title))
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}


class TranslateAction : TextAction {
    override val id = "translate"
    override val titleResId = R.string.btn_translate
    override val descriptionResId = R.string.desc_translate
    override val icon = AppIcons.Translate
    override val supportedTextTypes = setOf(TextType.PLAIN_TEXT)
    override val priority = 5
 
    override fun getDynamicTitle(context: Context, text: String): String = context.getString(R.string.dynamic_translate)
    override fun getDynamicDescription(context: Context, text: String): String = context.getString(R.string.dynamic_desc_translate)

    override fun execute(context: Context, text: String) {
        val url = "https://translate.google.com/?sl=auto&tl=vi&text=${Uri.encode(text)}&op=translate"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

class CopyAction : TextAction {
    override val id = "copy"
    override val titleResId = R.string.btn_copy
    override val descriptionResId = R.string.desc_copy
    override val icon = AppIcons.Copy
    override val supportedTextTypes = TextType.values().toSet()
    override val priority = 100 // High priority as it's a very common action

    override fun execute(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Copied Text", text)
        clipboard.setPrimaryClip(clip)
        
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            android.widget.Toast.makeText(context, context.getString(R.string.copied), android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

class TtsAction : TextAction {
    override val id = "tts"
    override val titleResId = R.string.btn_tts
    override val descriptionResId = R.string.desc_tts
    override val icon = AppIcons.Volume
    override val supportedTextTypes = setOf(TextType.PLAIN_TEXT)
    override val priority = 4

    private var tts: android.speech.tts.TextToSpeech? = null

    override fun execute(context: Context, text: String) {
        val appContext = context.applicationContext
        // We initialize a new instance for now, but in a real app we'd use a singleton service
        // Mobile TTS engines are usually shared system-wide, so this is relatively lightweight
        tts = android.speech.tts.TextToSpeech(appContext) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                tts?.apply {
                    val locale = java.util.Locale("vi")
                    val result = setLanguage(locale)
                    if (result != android.speech.tts.TextToSpeech.LANG_MISSING_DATA && 
                        result != android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED) {
                        speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "text_actions_tts")
                    }
                }
            }
        }
    }
}

class SmartReadAloudAction : TextAction {
    override val id = "smart_read_aloud"
    override val titleResId = R.string.btn_smart_read_aloud
    override val descriptionResId = R.string.desc_smart_read_aloud
    override val icon = AppIcons.Voice
    override val supportedTextTypes = setOf(TextType.PLAIN_TEXT)
    override val priority = 5

    override fun getDynamicTitle(context: Context, text: String): String = context.getString(R.string.dynamic_smart_read)
    override fun getDynamicDescription(context: Context, text: String): String = context.getString(R.string.dynamic_desc_smart_read)

    override fun execute(context: Context, text: String) {
        // Handled by in-app Smart Read Aloud flow.
    }
}

class CalendarAction : TextAction {
    override val id = "calendar"
    override val titleResId = R.string.btn_calendar
    override val descriptionResId = R.string.desc_calendar
    override val icon = AppIcons.Calendar
    override val supportedTextTypes = setOf(TextType.DATE_TIME)
    override val priority = 7

    override fun getDynamicTitle(context: Context, text: String): String = context.getString(R.string.dynamic_calendar)
    override fun getDynamicDescription(context: Context, text: String): String = context.getString(R.string.dynamic_desc_calendar)

    override fun execute(context: Context, text: String) {
        // Handled by in-app selection logic to include title/time/location.
    }
}


class SaveContactAction : TextAction {
    override val id = "save_contact"
    override val titleResId = R.string.btn_save_contact
    override val descriptionResId = R.string.desc_save_contact
    override val icon = AppIcons.UserPlus
    override val supportedTextTypes = setOf(TextType.PHONE_NUMBER, TextType.EMAIL)
    override val priority = 8

    override fun getDynamicTitle(context: Context, text: String): String = context.getString(R.string.dynamic_save_contact, text)
    override fun getDynamicDescription(context: Context, text: String): String = context.getString(R.string.dynamic_desc_save_contact)

    override fun execute(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            val isEmail = text.contains("@")
            type = if (isEmail) android.provider.ContactsContract.RawContacts.CONTENT_TYPE 
                   else android.provider.ContactsContract.Contacts.CONTENT_TYPE
            
            if (isEmail) {
                putExtra(android.provider.ContactsContract.Intents.Insert.EMAIL, text)
            } else {
                val normalized = text.replace(Regex("[^0-9+]"), "")
                putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, normalized)
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

class SaveNoteAction : TextAction {
    override val id = "save_note"
    override val titleResId = R.string.btn_save_note
    override val descriptionResId = R.string.desc_save_note
    override val icon = AppIcons.NotePencil
    override val supportedTextTypes = setOf(TextType.PLAIN_TEXT, TextType.NUMBER, TextType.URL, TextType.ADDRESS, TextType.DATE_TIME, TextType.PHONE_NUMBER, TextType.EMAIL)
    override val priority = 2
 
    override fun getDynamicTitle(context: Context, text: String): String = context.getString(R.string.dynamic_save_note)
    override fun getDynamicDescription(context: Context, text: String): String = context.getString(R.string.dynamic_desc_save_note)

    override fun execute(context: Context, text: String) {
        // Handled by in-app bottom sheet flow.
    }
}

data class ActionInstance(
    val action: TextAction,
    val boundText: String
) {
    fun toTextActionItem(context: Context): TextActionItem {
        return TextActionItem(
            id = "${action.id}:${boundText}",
            title = action.getDynamicTitle(context, boundText),
            subtitle = action.getDynamicDescription(context, boundText),
            icon = action.icon,
            action = { action.execute(context, boundText) }
        )
    }
}

class ReminderAction : TextAction {
    override val id = "reminder"
    override val titleResId = R.string.btn_reminder
    override val descriptionResId = R.string.desc_reminder
    override val icon = AppIcons.Bell
    override val supportedTextTypes = setOf(TextType.DATE_TIME)
    override val priority = 6
 
    override fun getDynamicTitle(context: Context, text: String): String = context.getString(R.string.dynamic_reminder)
    override fun getDynamicDescription(context: Context, text: String): String = context.getString(R.string.dynamic_desc_reminder)

    override fun execute(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = android.provider.CalendarContract.Events.CONTENT_URI
            putExtra(android.provider.CalendarContract.Events.TITLE, context.getString(R.string.reminder_prefix, text))
            putExtra("contentStatus", 1) // Attempting to set as a reminder/tentative
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

class ReaderModeAction : TextAction {
    override val id = "reader_mode"
    override val titleResId = R.string.btn_reader_mode
    override val descriptionResId = R.string.desc_reader_mode
    override val icon = AppIcons.Reader
    override val supportedTextTypes = setOf(TextType.URL)
    override val priority = 5
 
    override fun getDynamicTitle(context: Context, text: String): String = context.getString(R.string.dynamic_reader_mode)
    override fun getDynamicDescription(context: Context, text: String): String = context.getString(R.string.dynamic_desc_reader_mode)

    override fun execute(context: Context, text: String) {
        val encodedUrl = java.net.URLEncoder.encode(text, "UTF-8")
        val readerUrl = "https://txtify.it/$encodedUrl"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(readerUrl))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
class ZaloAction : TextAction {
    override val id = "zalo"
    override val titleResId = R.string.btn_zalo
    override val descriptionResId = R.string.desc_zalo
    override val icon = AppIcons.Send
    override val supportedTextTypes = setOf(TextType.PHONE_NUMBER)
    override val priority = 8

    override fun execute(context: Context, text: String) {
        val normalized = text.replace(Regex("[^0-9]"), "")
        val zaloUrl = "https://zalo.me/$normalized"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(zaloUrl))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

class DirectionAction : TextAction {
    override val id = "direction"
    override val titleResId = R.string.btn_direction
    override val descriptionResId = R.string.desc_direction
    override val icon = AppIcons.Compass
    override val supportedTextTypes = setOf(TextType.ADDRESS, TextType.GPS_COORDINATES)
    override val priority = 7

    override fun getDynamicDescription(context: Context, text: String): String = context.getString(R.string.dynamic_desc_direction, text)

    override fun execute(context: Context, text: String) {
        val uri = if (text.contains(",")) {
            "google.navigation:q=$text"
        } else {
            "google.navigation:q=${Uri.encode(text)}"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        intent.setPackage("com.google.android.apps.maps")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

class RideHailingAction : TextAction {
    override val id = "ride_hailing"
    override val titleResId = R.string.btn_ride_hailing
    override val descriptionResId = R.string.desc_ride_hailing
    override val icon = AppIcons.Car
    override val supportedTextTypes = setOf(TextType.ADDRESS)
    override val priority = 5

    override fun getDynamicDescription(context: Context, text: String): String = context.getString(R.string.dynamic_desc_ride_hailing, text)

    override fun execute(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(text)}"))
        val chooser = Intent.createChooser(intent, context.getString(R.string.btn_ride_hailing))
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}

class GPSMapAction : TextAction {
    override val id = "gps_map"
    override val titleResId = R.string.btn_gps_map
    override val descriptionResId = R.string.desc_gps_map
    override val icon = AppIcons.MapPin
    override val supportedTextTypes = setOf(TextType.GPS_COORDINATES)
    override val priority = 9

    override fun getDynamicDescription(context: Context, text: String): String = context.getString(R.string.dynamic_desc_gps_map, text)

    override fun execute(context: Context, text: String) {
        val normalized = text.replace(Regex("[()\\s]"), "")
        val parts = normalized.split(",")
        if (parts.size != 2) return
        val lat = parts[0]
        val lon = parts[1]
        val uri = "geo:$lat,$lon?q=$lat,$lon"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

class CreateQRAction : TextAction {
    override val id = "create_qr"
    override val titleResId = R.string.btn_create_qr
    override val descriptionResId = R.string.desc_create_qr
    override val icon = AppIcons.QrCode
    override val supportedTextTypes = setOf(TextType.URL, TextType.PHONE_NUMBER, TextType.EMAIL, TextType.ADDRESS, TextType.PLAIN_TEXT)
    override val priority = 3

    override fun getDynamicDescription(context: Context, text: String): String =
        context.getString(R.string.dynamic_desc_qr)

    override fun execute(context: Context, text: String) {
        val intent = Intent(context, CreateQRActivity::class.java).apply {
            putExtra("qr_content", text)
            putExtra("qr_type", detectQRType(text))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun isUrl(text: String): Boolean {
        return text.startsWith("http://") || text.startsWith("https://") || 
               text.startsWith("www.") || text.contains(".")
    }

    private fun isPhoneNumber(text: String): Boolean {
        val normalized = text.replace(Regex("[^0-9+]"), "")
        return normalized.length >= 10 && text.matches(Regex(".*\\d.*"))
    }

    private fun isEmail(text: String): Boolean {
        return text.contains("@") && text.matches(Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"))
    }

    private fun isAddress(text: String): Boolean {
        val addressKeywords = listOf("đường", "phố", "ngõ", "phường", "quận", "tỉnh", "thành phố", "tòa")
        return addressKeywords.any { text.lowercase().contains(it) }
    }

    private fun detectQRType(text: String): String {
        return when {
            isUrl(text) -> "url"
            isPhoneNumber(text) -> "phone"
            isEmail(text) -> "email"
            isAddress(text) -> "address"
            else -> "text"
        }
    }
}

// Helper function to check if text should show Create QR action
@Suppress("UNUSED_PARAMETER")
fun shouldShowCreateQRAction(text: String, _detections: List<Detection>): Boolean {
    // Show for any text with reasonable length (2-300 chars)
    // This ensures it shows even if text type is not detected correctly
    return text.length in 2..300
}
