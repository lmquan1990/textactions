package com.freeup.textactions.domain

import com.google.mlkit.nl.entityextraction.*
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

class CreateReminderUseCase {

    private val entityExtractor = EntityExtraction.getClient(
        EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build()
    )

    suspend fun parseReminderTime(text: String): LocalDateTime? {
        val annotations = try {
            entityExtractor.annotate(text).await()
        } catch (e: Exception) {
            return null
        }
        
        var date: Date? = null
        
        for (annotation in annotations) {
            for (entity in annotation.entities) {
                if (entity is DateTimeEntity) {
                    date = Date(entity.timestampMillis)
                }
            }
        }

        return date?.let {
            val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(it.time), ZoneId.systemDefault())
            // If only date exists (midnight), default to 9 AM
            if (ldt.hour == 0 && ldt.minute == 0) {
                ldt.withHour(9).withMinute(0)
            } else {
                ldt
            }
        }
    }

    fun hasExplicitTime(text: String): Boolean {
        val timeRegex = Regex(
            "\\b\\d{1,2}(:\\d{2})\\b|\\b\\d{1,2}\\s*(am|pm)\\b|\\b\\d{1,2}\\s*h(\\d{2})?\\b|\\b\\d{1,2}\\s*gi(?:ờ|o)\\b",
            RegexOption.IGNORE_CASE
        )
        return timeRegex.containsMatchIn(text)
    }
}
