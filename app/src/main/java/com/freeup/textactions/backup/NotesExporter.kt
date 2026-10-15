package com.freeup.textactions.backup

import android.content.ContentResolver
import android.net.Uri
import com.freeup.textactions.data.repository.HomeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class NotesExporter(
    private val repository: HomeRepository,
    private val serializer: BackupJsonSerializer
) {

    suspend fun exportAllToJson(): String {
        val notes = repository.getAllNotes()
        if (notes.isEmpty()) {
            throw IllegalStateException("No notes to export")
        }
        return serializer.serialize(notes, System.currentTimeMillis())
    }

    suspend fun exportSingleToJson(noteId: String): String {
        val note = repository.getNoteById(noteId)
            ?: throw IllegalArgumentException("Note not found")
        return serializer.serialize(listOf(note), System.currentTimeMillis())
    }

    suspend fun exportSelectedToJson(noteIds: List<String>): String {
        val notes = noteIds.mapNotNull { repository.getNoteById(it) }
        if (notes.isEmpty()) throw IllegalStateException("No notes to export")
        return serializer.serialize(notes, System.currentTimeMillis())
    }

    suspend fun exportAllToUri(contentResolver: ContentResolver, uri: Uri) {
        val json = exportAllToJson()
        writeToUri(contentResolver, uri, json)
    }

    suspend fun exportSingleToUri(contentResolver: ContentResolver, uri: Uri, noteId: String) {
        val json = exportSingleToJson(noteId)
        writeToUri(contentResolver, uri, json)
    }

    suspend fun exportSelectedToUri(contentResolver: ContentResolver, uri: Uri, noteIds: List<String>) {
        val json = exportSelectedToJson(noteIds)
        writeToUri(contentResolver, uri, json)
    }

    private suspend fun writeToUri(contentResolver: ContentResolver, uri: Uri, json: String) {
        withContext(Dispatchers.IO) {
            try {
                contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                    stream.flush()
                } ?: throw IOException("Unable to open output stream")
            } catch (e: SecurityException) {
                throw IOException("Permission revoked", e)
            }
        }
    }
}
