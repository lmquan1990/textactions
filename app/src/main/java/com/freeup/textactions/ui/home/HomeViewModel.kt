package com.freeup.textactions.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeup.textactions.data.repository.HomeRepository
import com.freeup.textactions.domain.model.ActionHistory
import com.freeup.textactions.history.isLinkedTo
import com.freeup.textactions.history.isNoteEntry
import com.freeup.textactions.domain.model.AiState
import com.freeup.textactions.domain.model.AiStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.freeup.textactions.data.local.db.NoteEntity

enum class HomeView { PUBLIC, LOCKED, ARCHIVED }

data class HomeUiState(
    val aiStatus: AiStatus = AiStatus(AiState.READY),
    val snippets: List<NoteEntity> = emptyList(),
    val currentView: HomeView = HomeView.PUBLIC,
    val recentActions: List<ActionHistory> = emptyList(),
    /** Locked or archived notes (not shown on Home). */
    val hiddenNotes: List<NoteEntity> = emptyList(),
    /** True until the database has delivered its first snapshot, so the UI can show a skeleton instead of an empty state. */
    val isLoading: Boolean = true
)

class HomeViewModel(private val repository: HomeRepository) : ViewModel() {

    private val _currentView = MutableStateFlow(HomeView.PUBLIC)
    val currentView: StateFlow<HomeView> = _currentView.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val snippetsFlow = _currentView.flatMapLatest { view ->
        when (view) {
            HomeView.LOCKED -> repository.getLockedSnippets()
            HomeView.ARCHIVED -> repository.getArchivedNotes()
            HomeView.PUBLIC -> repository.getPublicSnippets()
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        repository.aiStatusFlow,
        snippetsFlow,
        _currentView,
        repository.getAllActions(),
        repository.getHiddenNotes()
    ) { aiStatus, snippets, currentView, allActions, hiddenNotes ->
        // A note moved to the vault or archive moves out of Home: drop its "Save as Note" history entry too
        val recentActions = allActions.filterNot { action ->
            action.isNoteEntry() && hiddenNotes.any { action.isLinkedTo(it) }
        }
        HomeUiState(aiStatus, snippets, currentView, recentActions, hiddenNotes, isLoading = false)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun setView(view: HomeView) {
        _currentView.value = view
    }

    fun toggleLockedSnippets(show: Boolean) {
        _currentView.value = if (show) HomeView.LOCKED else HomeView.PUBLIC
    }

    fun toggleArchivedSnippets(show: Boolean) {
        _currentView.value = if (show) HomeView.ARCHIVED else HomeView.PUBLIC
    }

    fun lockSnippet(snippet: NoteEntity) {
        viewModelScope.launch {
            repository.updateNote(snippet.copy(isLocked = true))
        }
    }

    fun unlockSnippet(snippet: NoteEntity) {
        viewModelScope.launch {
            repository.updateNote(snippet.copy(isLocked = false))
        }
    }

    fun addAction(action: ActionHistory) {
        viewModelScope.launch {
            repository.insertAction(action)
        }
    }

    fun clearAllActions() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun setActionPinned(id: Long, pinned: Boolean) {
        viewModelScope.launch {
            repository.setActionPinned(id, pinned)
        }
    }

    fun deleteAction(id: Long) {
        viewModelScope.launch {
            repository.deleteAction(id)
        }
    }

    fun updateAiStatus(status: AiStatus) {
        viewModelScope.launch {
            repository.updateAiStatus(status)
        }
    }

    fun createSnippet(
        title: String,
        content: String,
        category: String = "",
        isLocked: Boolean = false,
        isPinned: Boolean = false,
        isArchived: Boolean = false
    ) {
        val trimmedContent = content.trim()
        if (trimmedContent.isBlank()) return
        viewModelScope.launch {
            val note = NoteEntity(
                id = java.util.UUID.randomUUID().toString(),
                title = title.trim(),
                content = trimmedContent,
                createdAt = System.currentTimeMillis(),
                category = category.ifBlank {
                    com.freeup.textactions.util.NoteCategorizer.categorize(trimmedContent)
                },
                isLocked = isLocked,
                isPinned = isPinned,
                isArchived = isArchived,
                pinnedAt = if (isPinned) System.currentTimeMillis() else null,
                detectedType = "TEXT"
            )
            repository.insertNote(note)
        }
    }

    fun updateSnippet(
        id: String,
        title: String,
        content: String,
        category: String,
        isLocked: Boolean,
        isPinned: Boolean,
        isArchived: Boolean
    ) {
        val trimmedContent = content.trim()
        if (trimmedContent.isBlank()) return
        viewModelScope.launch {
            val existing = repository.getNoteById(id)
            if (existing == null) {
                // Item came from history and has no backing note yet: create it
                createSnippet(title, content, category, isLocked, isPinned, isArchived)
                return@launch
            }
            repository.updateNote(
                existing.copy(
                    title = title.trim(),
                    content = trimmedContent,
                    category = category,
                    isLocked = isLocked,
                    isPinned = isPinned,
                    isArchived = isArchived,
                    pinnedAt = when {
                        isPinned && existing.pinnedAt == null -> System.currentTimeMillis()
                        isPinned -> existing.pinnedAt
                        else -> null
                    }
                )
            )
        }
    }

    fun unarchiveSnippet(id: String) {
        viewModelScope.launch {
            repository.unarchiveNote(id)
        }
    }

    fun archiveSnippet(id: String) {
        viewModelScope.launch {
            repository.archiveNote(id)
        }
    }

    fun deleteSnippet(id: String) {
        viewModelScope.launch {
            repository.deleteNote(id)
        }
    }
}
