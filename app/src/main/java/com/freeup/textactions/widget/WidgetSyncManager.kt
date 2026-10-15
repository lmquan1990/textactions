package com.freeup.textactions.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.freeup.textactions.data.local.db.NoteDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * WidgetSyncManager is a singleton that observes the Room database for any changes
 * to the snippet list and automatically triggers a Home Screen Widget update.
 * This ensures "instant" synchronization without needing manual triggers in repositories.
 */
object WidgetSyncManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isObserving = false

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun startObserving(context: Context, noteDao: NoteDao) {
        if (isObserving) return
        isObserving = true

        scope.launch {
            // Observe the flow of public snippets. Whenever it emits (data changed),
            // we trigger a widget update. 
            // We use debounce to avoid flooding the system with updates during batch operations.
            noteDao.getPublicSnippets()
                .distinctUntilChanged()
                .debounce(50) 
                .collectLatest {
                    try {
                        TextActionsWidget().updateAll(context)
                    } catch (e: Exception) {
                        // Ignore if context is invalid or Glance is not ready
                    }
                }
        }
    }

    /**
     * Manually triggers a widget update. Using suspend ensures callers (like Repository)
     * wait for the update signal to be enqueued before proceeding.
     */
    suspend fun updateWidget(context: Context) {
        try {
            TextActionsWidget().updateAll(context)
        } catch (e: Exception) {
            // Ignore
        }
    }
}
