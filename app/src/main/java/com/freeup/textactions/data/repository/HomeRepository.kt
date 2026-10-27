package com.freeup.textactions.data.repository

import com.freeup.textactions.data.local.datastore.AiStatusDataStore
import com.freeup.textactions.data.local.db.ActionHistoryDao
import com.freeup.textactions.data.local.db.NoteDao
import com.freeup.textactions.data.local.db.NoteEntity
import com.freeup.textactions.data.mapper.toDomain
import com.freeup.textactions.data.mapper.toEntity
import com.freeup.textactions.domain.model.ActionHistory
import com.freeup.textactions.domain.model.AiStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.freeup.textactions.widget.WidgetSyncManager

class HomeRepository(
    private val context: android.content.Context,
    private val actionHistoryDao: ActionHistoryDao,
    private val noteDao: NoteDao,
    private val aiStatusDataStore: AiStatusDataStore
) {
    fun getRecentActions(limit: Int = 50): Flow<List<ActionHistory>> {
        return actionHistoryDao.getRecentActions(limit).map { entities ->
            entities.map { it.toDomain() }.filter { it.inputText.isNotBlank() }
        }
    }

    fun getAllActions(): Flow<List<ActionHistory>> {
        return actionHistoryDao.getAllActions().map { entities ->
            entities.map { it.toDomain() }.filter { it.inputText.isNotBlank() }
        }
    }

    suspend fun insertAction(action: ActionHistory) {
        // Skip duplicates: same action on the same text within the last minute
        val since = action.createdAt - DUPLICATE_WINDOW_MS
        if (actionHistoryDao.countRecent(action.actionType, action.inputText, since) > 0) return
        actionHistoryDao.insert(action.toEntity())
    }

    suspend fun setActionPinned(id: Long, pinned: Boolean) {
        actionHistoryDao.setPinned(id, pinned, if (pinned) System.currentTimeMillis() else null)
    }

    suspend fun deleteAction(id: Long) {
        actionHistoryDao.deleteById(id)
    }

    suspend fun hasNoteWithContent(content: String, since: Long = 0L): Boolean =
        noteDao.countByContentSince(content, since) > 0

    suspend fun insertNote(note: NoteEntity) {
        noteDao.insert(note)
        WidgetSyncManager.updateWidget(context)
    }

    fun getPublicSnippets(): Flow<List<NoteEntity>> {
        return noteDao.getPublicSnippets()
    }

    fun getLockedSnippets(): Flow<List<NoteEntity>> {
        return noteDao.getLockedSnippets()
    }

    fun getArchivedNotes(): Flow<List<NoteEntity>> {
        return noteDao.getArchivedNotes()
    }

    suspend fun getAllNotes(): List<NoteEntity> {
        return noteDao.getAllNotes()
    }

    suspend fun getNoteById(id: String): NoteEntity? {
        return noteDao.getNoteById(id)
    }

    suspend fun updateNote(note: NoteEntity) {
        noteDao.update(note)
        WidgetSyncManager.updateWidget(context)
    }

    /** Notes that are not shown on Home: locked ones and archived ones. */
    fun getHiddenNotes(): Flow<List<NoteEntity>> =
        kotlinx.coroutines.flow.combine(noteDao.getLockedSnippets(), noteDao.getArchivedNotes()) { locked, archived ->
            (locked + archived).distinctBy { it.id }
        }

    suspend fun deleteNote(id: String) {
        // The history entry of a deleted note would otherwise reappear on Home once the note is gone
        noteDao.getNoteById(id)?.let { note ->
            actionHistoryDao.deleteNoteHistory(
                noteId = note.id,
                content = note.content.trim(),
                titleAndContent = "${note.title}\n\n${note.content}".trim()
            )
        }
        noteDao.deleteById(id)
        WidgetSyncManager.updateWidget(context)
    }

    suspend fun archiveNote(id: String) {
        val note = noteDao.getNoteById(id)
        note?.let {
            noteDao.update(it.copy(isArchived = true))
            WidgetSyncManager.updateWidget(context)
        }
    }

    suspend fun unarchiveNote(id: String) {
        val note = noteDao.getNoteById(id)
        note?.let {
            noteDao.update(it.copy(isArchived = false))
            WidgetSyncManager.updateWidget(context)
        }
    }

    suspend fun mergeNotes(noteIds: List<String>) {
        if (noteIds.size < 2) return
        val notes = noteIds.mapNotNull { noteDao.getNoteById(it) }.sortedBy { it.createdAt }
        if (notes.size < 2) return

        val mergedContent = notes.joinToString("\n") { it.content }
        val firstNote = notes.first()
        val category = com.freeup.textactions.util.NoteCategorizer.categorize(mergedContent)

        val mergedNote = NoteEntity(
            id = java.util.UUID.randomUUID().toString(),
            title = firstNote.title,
            content = mergedContent,
            createdAt = firstNote.createdAt,
            category = category,
            isLocked = notes.any { it.isLocked },
            sourceAppName = firstNote.sourceAppName,
            sourcePackageName = firstNote.sourcePackageName,
            sourceUrl = firstNote.sourceUrl,
            detectedType = firstNote.detectedType,
            isArchived = false,
            isPinned = false,
            pinnedAt = null
        )

        noteDao.insert(mergedNote)
        // Delete the original notes
        noteIds.forEach { noteDao.deleteById(it) }
        WidgetSyncManager.updateWidget(context)
    }

    suspend fun clearHistory() {
        actionHistoryDao.clearAll()
    }

    val aiStatusFlow: Flow<AiStatus> = aiStatusDataStore.aiStatusFlow

    suspend fun updateAiStatus(status: AiStatus) {
        aiStatusDataStore.updateAiStatus(status)
    }

    private companion object {
        const val DUPLICATE_WINDOW_MS = 60_000L
    }
}
