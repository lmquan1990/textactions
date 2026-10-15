package com.freeup.textactions.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import com.freeup.textactions.R
import com.freeup.textactions.TextActionItem
import com.freeup.textactions.limitSearchQuery
import com.freeup.textactions.shouldShowSummarizeAction
import com.freeup.textactions.shouldShowSearchAction
import com.freeup.textactions.ui.theme.AppIcons
import com.google.mlkit.nl.entityextraction.*
import kotlinx.coroutines.tasks.await

class ExtractEntitiesUseCase(private val context: Context) {

    private val entityExtractor = EntityExtraction.getClient(
        EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH)
            .build()
    )

    suspend fun execute(text: String): List<TextActionItem> {
        return try {
            entityExtractor.downloadModelIfNeeded().await()
            val annotations = entityExtractor.annotate(text).await()
            
            val detectedPhones = mutableListOf<String>()
            val detectedEmails = mutableListOf<String>()
            val detectedAddresses = mutableListOf<String>()
            val detectedUrls = mutableListOf<String>()
            var detectedName: String? = null
            var detectedOrg: String? = null
            
            // Collect all entities
            for (annotation in annotations) {
                for (entity in annotation.entities) {
                    val value = annotation.annotatedText
                    when (entity.type) {
                        Entity.TYPE_PHONE -> {
                            if (validatePhone(value)) {
                                detectedPhones.add(value)
                            }
                        }
                        Entity.TYPE_EMAIL -> detectedEmails.add(value)
                        Entity.TYPE_ADDRESS -> detectedAddresses.add(value)
                        Entity.TYPE_URL -> detectedUrls.add(value)
                    }
                }
            }

            val items = mutableListOf<TextActionItem>()
            
            // 1. Primary Actions (Call)
            detectedPhones.distinct().forEach { value ->
                items.add(createCallAction(value))
            }
            
            // 2. Primary Actions (Email)
            detectedEmails.distinct().forEach { value ->
                items.add(createEmailAction(value))
            }

            // 3. Smart Save Contact (AGGREGATED)
            val candidate = ContactCandidate(
                phones = detectedPhones.distinct(),
                emails = detectedEmails.distinct(),
                address = detectedAddresses.distinct().firstOrNull(),
                name = detectedName,
                organization = detectedOrg
            )
            if (candidate.isValid()) {
                items.add(createSaveContactAction(candidate))
            }

            // 4. Secondary Actions (Maps, URL)
            detectedAddresses.distinct().forEach { value ->
                if (isMeaningfulAddress(value)) {
                    items.add(createMapAction(value))
                    items.add(createCopyAddressAction(value))
                }
            }
            detectedUrls.distinct().filter { isRealUrl(it) }.forEach { value ->
                items.add(createUrlAction(value))
            }
            
            // Final deduplication by ID to prevent overlap
            items.distinctBy { it.id }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun createCallAction(value: String) = TextActionItem(
        id = "call:${value}",
        title = "Call",
        subtitle = value,
        icon = AppIcons.Phone,
        action = {
            try {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${value.replace(Regex("[^0-9+]"), "")}"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Cannot make call", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    )

    private fun createEmailAction(value: String) = TextActionItem(
        id = "email:${value}",
        title = "Send Email",
        subtitle = value,
        icon = AppIcons.Envelope,
        action = {
            try {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${value}"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Cannot send email", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    )

    private fun createSaveContactAction(candidate: ContactCandidate) = TextActionItem(
        id = "save_contact",
        title = "Save contact",
        subtitle = "Save phone & email to contacts",
        icon = AppIcons.UserPlus,
        action = {
            try {
                val intent = Intent(Intent.ACTION_INSERT).apply {
                    type = ContactsContract.Contacts.CONTENT_TYPE
                    
                    // Pre-fill fields
                    if (candidate.phones.isNotEmpty()) {
                        putExtra(ContactsContract.Intents.Insert.PHONE, candidate.phones[0])
                        if (candidate.phones.size > 1) {
                            putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE, candidate.phones[1])
                        }
                    }
                    if (candidate.emails.isNotEmpty()) {
                        putExtra(ContactsContract.Intents.Insert.EMAIL, candidate.emails[0])
                    }
                    if (candidate.address != null) {
                        putExtra(ContactsContract.Intents.Insert.POSTAL, candidate.address)
                    }
                    if (candidate.name != null) {
                        putExtra(ContactsContract.Intents.Insert.NAME, candidate.name)
                    }
                    if (candidate.organization != null) {
                        putExtra(ContactsContract.Intents.Insert.COMPANY, candidate.organization)
                    }
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Cannot save contact", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    )

    private fun createMapAction(value: String) = TextActionItem(
        id = "map:${value}",
        title = "Open in Maps",
        subtitle = value,
        icon = AppIcons.Map,
        action = {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(value)}"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Cannot open map", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    )

    private fun createCopyAddressAction(value: String) = TextActionItem(
        id = "copy_address:${value}",
        title = "Copy address",
        subtitle = value,
        icon = AppIcons.Copy,
        action = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("address", value))
            android.widget.Toast.makeText(context, context.getString(R.string.copied_address), android.widget.Toast.LENGTH_SHORT).show()
        }
    )

    private fun createUrlAction(value: String) = TextActionItem(
        id = "url:${value}",
        title = "Open Link",
        subtitle = value,
        icon = R.drawable.ic_google,
        action = {
            try {
                var url = value
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://$url"
                }
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Cannot open browser", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    )


    private fun isMeaningfulAddress(value: String): Boolean {
        val trimmed = value.trim()
        // Must be at least 15 chars to avoid short false positives
        if (trimmed.length <= 15) return false

        val lowerValue = trimmed.lowercase()

        // Strong structural keywords — Vietnamese
        val strongKeywordsVi = listOf(
            "đường", "phố", "ngõ", "ngách", "thôn", "xóm",
            "phường", "xã", "quận", "huyện", "tỉnh", "thành phố",
            "tòa nhà", "tổ dân phố"
        )

        // Strong structural keywords — English
        val strongKeywordsEn = listOf(
            "street", "avenue", "boulevard", "road", "lane", "drive",
            "court", "place", "highway", "district", "ward",
            "city", "county", "province", "state"
        )

        // Weak keywords — Vietnamese (only count alongside strong)
        val weakKeywordsVi = listOf("số nhà", "tầng", "khu vực", "khu đô thị", "khu công nghiệp")

        // Weak keywords — English (only count alongside strong)
        val weakKeywordsEn = listOf("apt", "apartment", "suite", "floor", "unit", "building", "block")

        val strongCount = (strongKeywordsVi + strongKeywordsEn).count { lowerValue.contains(it) }
        val weakCount = (weakKeywordsVi + weakKeywordsEn).count { lowerValue.contains(it) }

        // Must have at least 2 strong keywords, OR 1 strong + 1 weak + a digit
        val hasDigit = trimmed.any { it.isDigit() }
        return strongCount >= 2 || (strongCount >= 1 && weakCount >= 1 && hasDigit)
    }

    private fun validatePhone(value: String): Boolean {
        val digitsOnly = value.replace(Regex("[^0-9]"), "")
        
        // Hotline support (1800/1900) - usually 8 or 10 digits
        if (digitsOnly.startsWith("1800") || digitsOnly.startsWith("1900")) {
            return digitsOnly.length in 8..10
        }

        if (digitsOnly.length !in 10..11) return false

        // Remove country code if present (84)
        val normalized = if (digitsOnly.startsWith("84") && digitsOnly.length == 11) {
            "0" + digitsOnly.substring(2)
        } else {
            digitsOnly
        }
        
        if (normalized.length != 10) return false

        // Mobile prefixes: 0 followed by 3, 5, 7, 8, 9
        val isMobile = normalized.matches(Regex("^0[35789]\\d{8}"))
        // Landline prefixes: 02
        val isLandline = normalized.startsWith("02")

        return isMobile || isLandline
    }
}
