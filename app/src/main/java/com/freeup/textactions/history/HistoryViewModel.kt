package com.freeup.textactions.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeup.textactions.domain.model.ActionHistory
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

sealed class HistoryUiEvent {
    data class ShowToast(val message: String) : HistoryUiEvent()
    data class ShowSnackbar(val message: String) : HistoryUiEvent()
    data class ShowEntities(val items: List<com.freeup.textactions.TextActionItem>) : HistoryUiEvent()
}

class HistoryViewModel(
    private val reRunner: ActionReRunner
) : ViewModel() {

    private val _events = MutableSharedFlow<HistoryUiEvent>()
    val events: SharedFlow<HistoryUiEvent> = _events

    fun rerun(item: ActionHistory) {
        viewModelScope.launch {
            when (val outcome = reRunner.rerun(item)) {
                is ReRunOutcome.ToastMessage -> _events.emit(HistoryUiEvent.ShowToast(outcome.message))
                is ReRunOutcome.SnackbarMessage -> _events.emit(HistoryUiEvent.ShowSnackbar(outcome.message))
                is ReRunOutcome.EntityResults -> _events.emit(HistoryUiEvent.ShowEntities(outcome.items))
                ReRunOutcome.None -> Unit
            }
        }
    }
}
