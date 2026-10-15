package com.freeup.textactions

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import java.util.Locale

object AppLocale {
    const val LANGUAGE_SYSTEM = "system"
    const val LANGUAGE_ENGLISH = "en"
    const val LANGUAGE_VIETNAMESE = "vi"

    fun wrapContext(base: Context): Context {
        val language = SettingsManager(base).getAppLanguage()
        return wrapContext(base, language)
    }

    fun wrapContext(base: Context, language: String): Context {
        if (language.isBlank() || language == LANGUAGE_SYSTEM) {
            // Restore to system locale if we previously forced a different default.
            val systemLocale = Resources.getSystem().configuration.locales[0]
            Locale.setDefault(systemLocale)
            return base
        }

        val locale = Locale(language)
        Locale.setDefault(locale)

        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }
}
