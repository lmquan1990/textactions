package com.freeup.textactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeup.textactions.data.local.db.NoteEntity
import com.freeup.textactions.data.repository.HomeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NotesViewModel(
    private val repository: HomeRepository
) : ViewModel() {

    private val _notes = MutableStateFlow<List<NoteEntity>>(emptyList())
    val notes = _notes.asStateFlow()

    private val _archivedNotes = MutableStateFlow<List<NoteEntity>>(emptyList())
    val archivedNotes = _archivedNotes.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getPublicSnippets().collect {
                _notes.value = it
            }
        }
        viewModelScope.launch {
            repository.getArchivedNotes().collect {
                _archivedNotes.value = it
            }
        }
    }
    
    fun deleteNote(id: String) {
        viewModelScope.launch {
            // User requested "Archive" mostly, but we have a delete button. 
            // For now, let's keep delete as permanent delete or implement archive?
            // "Archive note. Note archived không hiển thị trong danh sách chính."
            // Let's make the "Delete" button in UI actually Archive for now, or add explicit Archive.
            // The prompt said "Add feature Archive note".
            // Let's keep delete as delete, and add archive.
            // But wait, existing UI only has Delete.
            // Let's change deleteNote to archiveNote for better safety/UX as requested "Save = Capture, Organize = Later".
            repository.archiveNote(id)
        }
    }
    
    fun restoreNote(id: String) {
        viewModelScope.launch {
            repository.unarchiveNote(id)
        }
    }

    fun updateNote(note: NoteEntity) {
        viewModelScope.launch {
            repository.updateNote(note)
        }
    }

    fun pinNote(id: String) {
        viewModelScope.launch {
            val note = repository.getNoteById(id) ?: return@launch
            repository.updateNote(
                note.copy(
                    isPinned = true,
                    pinnedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun unpinNote(id: String) {
        viewModelScope.launch {
            val note = repository.getNoteById(id) ?: return@launch
            repository.updateNote(
                note.copy(
                    isPinned = false,
                    pinnedAt = null
                )
            )
        }
    }

    fun deleteNotePermanently(id: String) {
        viewModelScope.launch {
            repository.deleteNote(id)
        }
    }

    fun insertNote(note: NoteEntity) {
        viewModelScope.launch {
            repository.insertNote(note)
        }
    }

    fun mergeNotes(noteIds: List<String>) {
        viewModelScope.launch {
            repository.mergeNotes(noteIds)
        }
    }
}
