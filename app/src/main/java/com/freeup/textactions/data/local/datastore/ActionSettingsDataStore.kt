package com.freeup.textactions.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.actionSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "action_settings_prefs")

class ActionSettingsDataStore(private val context: Context) {
    private val actionOrderKey = stringPreferencesKey("action_order")
    private val actionEnabledPrefix = "action_enabled_"

    val actionOrderFlow: Flow<List<String>> = context.actionSettingsDataStore.data.map { preferences ->
        preferences[actionOrderKey]?.split(",") ?: emptyList()
    }

    fun isActionEnabled(id: String): Flow<Boolean> = context.actionSettingsDataStore.data.map { preferences ->
        preferences[androidx.datastore.preferences.core.booleanPreferencesKey(actionEnabledPrefix + id)] ?: true
    }

    suspend fun setActionEnabled(id: String, enabled: Boolean) {
        context.actionSettingsDataStore.edit { preferences ->
            preferences[androidx.datastore.preferences.core.booleanPreferencesKey(actionEnabledPrefix + id)] = enabled
        }
    }

    suspend fun saveActionOrder(order: List<String>) {
        context.actionSettingsDataStore.edit { preferences ->
            preferences[actionOrderKey] = order.joinToString(",")
        }
    }

    suspend fun resetDefaults() {
        context.actionSettingsDataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
