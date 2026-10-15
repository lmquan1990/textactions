package com.freeup.textactions.backup

import android.content.ContentResolver
import android.net.Uri
import com.freeup.textactions.data.local.db.NoteEntity
import com.freeup.textactions.data.repository.HomeRepository
import com.freeup.textactions.util.NoteCategorizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

class NotesImporter(
    private val repository: HomeRepository,
    private val serializer: BackupJsonSerializer
) {

    suspend fun importFromUri(contentResolver: ContentResolver, uri: Uri): Int {
        val json = readFromUri(contentResolver, uri)
        return importFromJson(json)
    }

    suspend fun importFromJson(json: String): Int {
        val payload = serializer.parse(json)
        if (payload.notes.isEmpty()) return 0

        val existingIds = repository.getAllNotes().map { it.id }.toHashSet()
        var imported = 0

        payload.notes.forEach { note ->
            val newId = if (existingIds.contains(note.id)) UUID.randomUUID().toString() else note.id
            existingIds.add(newId)
            val title = deriveTitle(note.content)
            val category = NoteCategorizer.categorize(note.content)
            val detectedType = NoteCategorizer.detectType(note.content)
            val pinnedAt = if (note.pinned) System.currentTimeMillis() else null

            val entity = NoteEntity(
                id = newId,
                title = title,
                content = note.content,
                createdAt = note.createdAt,
                category = category,
                isLocked = note.locked,
                sourceAppName = note.sourceApp,
                sourcePackageName = null,
                sourceUrl = note.sourceUrl,
                detectedType = detectedType,
                isArchived = note.archived,
                isPinned = note.pinned,
                pinnedAt = pinnedAt
            )
            repository.insertNote(entity)
            imported++
        }

        return imported
    }

    private suspend fun readFromUri(contentResolver: ContentResolver, uri: Uri): String {
        return withContext(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    stream.readBytes().toString(Charsets.UTF_8)
                } ?: throw IOException("Unable to open input stream")
            } catch (e: SecurityException) {
                throw IOException("Permission revoked", e)
            }
        }
    }

    private fun deriveTitle(content: String): String {
        return content.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(80) ?: "Note"
    }
}
