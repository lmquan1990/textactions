package com.freeup.textactions.backup

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.freeup.textactions.data.local.db.NoteEntity
import com.freeup.textactions.data.repository.HomeRepository

class BackupViewModel(
    private val repository: HomeRepository,
    private val exporter: NotesExporter,
    private val importer: NotesImporter
) : ViewModel() {

    suspend fun loadAllNotes(): List<NoteEntity> = repository.getAllNotes()

    suspend fun exportAllToUri(contentResolver: ContentResolver, uri: Uri) {
        exporter.exportAllToUri(contentResolver, uri)
    }

    suspend fun exportSingleToUri(contentResolver: ContentResolver, uri: Uri, noteId: String) {
        exporter.exportSingleToUri(contentResolver, uri, noteId)
    }

    suspend fun exportSelectedToUri(contentResolver: ContentResolver, uri: Uri, noteIds: List<String>) {
        exporter.exportSelectedToUri(contentResolver, uri, noteIds)
    }

    suspend fun importFromUri(contentResolver: ContentResolver, uri: Uri): Int {
        return importer.importFromUri(contentResolver, uri)
    }
}
