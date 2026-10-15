package com.freeup.textactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeup.textactions.data.local.db.NoteEntity
import com.freeup.textactions.data.repository.HomeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class EditNoteViewModel(
    private val repository: HomeRepository
) : ViewModel() {

    private val _note = MutableStateFlow<NoteEntity?>(null)
    val note: StateFlow<NoteEntity?> = _note

    fun load(id: String) {
        viewModelScope.launch {
            _note.value = repository.getNoteById(id)
        }
    }

    fun update(note: NoteEntity) {
        viewModelScope.launch {
            repository.updateNote(note)
        }
    }

    fun archive(id: String) {
        viewModelScope.launch {
            repository.archiveNote(id)
        }
    }

    fun unarchive(id: String) {
        viewModelScope.launch {
            repository.unarchiveNote(id)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repository.deleteNote(id)
        }
    }

    fun pin(id: String) {
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

    fun unpin(id: String) {
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
}
