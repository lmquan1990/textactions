package com.freeup.textactions.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.freeup.textactions.formatting.DefaultListStyle
import com.freeup.textactions.formatting.FormatSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.textFormattingDataStore: DataStore<Preferences> by preferencesDataStore(name = "text_formatting_prefs")

class TextFormattingDataStore(private val context: Context) {
    private val autoSuggestKey = booleanPreferencesKey("format_auto_suggest")
    private val ocrFixKey = booleanPreferencesKey("format_enable_ocr_fix")
    private val defaultListKey = stringPreferencesKey("format_default_list_style")
    private val preserveOriginalKey = booleanPreferencesKey("format_preserve_original")

    val settingsFlow: Flow<FormatSettings> = context.textFormattingDataStore.data.map { prefs ->
        FormatSettings(
            autoSuggest = prefs[autoSuggestKey] ?: true,
            enableOcrFix = prefs[ocrFixKey] ?: true,
            defaultListStyle = when (prefs[defaultListKey]) {
                "numbered" -> DefaultListStyle.NUMBERED
                else -> DefaultListStyle.BULLET
            },
            preserveOriginalText = prefs[preserveOriginalKey] ?: false
        )
    }

    suspend fun setAutoSuggest(enabled: Boolean) {
        context.textFormattingDataStore.edit { it[autoSuggestKey] = enabled }
    }

    suspend fun setOcrFixEnabled(enabled: Boolean) {
        context.textFormattingDataStore.edit { it[ocrFixKey] = enabled }
    }

    suspend fun setDefaultListStyle(style: DefaultListStyle) {
        context.textFormattingDataStore.edit {
            it[defaultListKey] = if (style == DefaultListStyle.NUMBERED) "numbered" else "bullet"
        }
    }

    suspend fun setPreserveOriginal(enabled: Boolean) {
        context.textFormattingDataStore.edit { it[preserveOriginalKey] = enabled }
    }
}
