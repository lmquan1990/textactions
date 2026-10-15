package com.freeup.textactions

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.freeup.textactions.data.local.db.AppDatabase
import com.freeup.textactions.widget.WidgetSyncManager

/**
 * Applies user preferences (theme mode, app language) at app startup so all activities
 * respect the saved preferences from the first launch.
 */
class TextActionsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        applyThemeMode()
        applyAppLocale()
        
        // Start observing database changes for widget synchronization
        val db = AppDatabase.getDatabase(this)
        WidgetSyncManager.startObserving(this, db.noteDao())
    }

    private fun applyThemeMode() {
        val settings = SettingsManager(this)
        val mode = when (settings.getThemeMode()) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun applyAppLocale() {
        val lang = SettingsManager(this).getAppLanguage()
        val locales = if (lang == AppLocale.LANGUAGE_SYSTEM || lang.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(lang)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
