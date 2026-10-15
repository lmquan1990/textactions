package com.freeup.textactions.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.freeup.textactions.domain.model.AiState
import com.freeup.textactions.domain.model.AiStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_status_prefs")

class AiStatusDataStore(private val context: Context) {

    private val aiStateKey = stringPreferencesKey("ai_state")
    private val aiProgressKey = intPreferencesKey("ai_progress")

    val aiStatusFlow: Flow<AiStatus> = context.dataStore.data.map { preferences ->
        val stateName = preferences[aiStateKey] ?: AiState.READY.name
        val state = try { AiState.valueOf(stateName) } catch (e: Exception) { AiState.READY }
        val progress = preferences[aiProgressKey] ?: 0
        AiStatus(state, progress)
    }

    suspend fun updateAiStatus(status: AiStatus) {
        context.dataStore.edit { preferences ->
            preferences[aiStateKey] = status.state.name
            preferences[aiProgressKey] = status.progress
        }
    }
}
