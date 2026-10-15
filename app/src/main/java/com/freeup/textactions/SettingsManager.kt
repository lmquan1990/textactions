package com.freeup.textactions

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("action_settings", Context.MODE_PRIVATE)
    private val dynamicColorKey = "dynamic_color_enabled"
    private val themeModeKey = "theme_mode"
    private val appLanguageKey = "app_language"
    private val noteLockEnabledKey = "note_lock_enabled"

    fun getThemeMode(): String {
        return prefs.getString(themeModeKey, "system") ?: "system"
    }

    fun setThemeMode(mode: String) {
        prefs.edit().putString(themeModeKey, mode).apply()
    }

    fun getAppLanguage(): String {
        return prefs.getString(appLanguageKey, "system") ?: "system"
    }

    fun setAppLanguage(language: String) {
        prefs.edit().putString(appLanguageKey, language).apply()
    }

    fun isEnabled(actionId: String): Boolean {
        // Smart Extract & Act (ML Kit) luôn bật, không cho user tắt
        if (actionId == "ai_extract") return true
        return prefs.getBoolean("enabled_$actionId", true)
    }

    fun setEnabled(actionId: String, enabled: Boolean) {
        prefs.edit().putBoolean("enabled_$actionId", enabled).apply()
    }

    fun isPinned(actionId: String): Boolean {
        return prefs.getBoolean("pinned_$actionId", false)
    }

    fun setPinned(actionId: String, pinned: Boolean) {
        prefs.edit().putBoolean("pinned_$actionId", pinned).apply()
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }

    fun setBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun isScreenshotBannerEnabled(): Boolean = prefs.getBoolean("screenshot_banner_enabled", true)

    fun setScreenshotBannerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("screenshot_banner_enabled", enabled).apply()
    }

    /** Last screenshot the user dismissed, so the banner doesn't come back after an app restart. */
    fun getDismissedScreenshotUri(): String? = prefs.getString("dismissed_screenshot_uri", null)

    fun setDismissedScreenshotUri(uri: String?) {
        prefs.edit().putString("dismissed_screenshot_uri", uri).apply()
    }

    fun isNoteLockEnabled(): Boolean {
        return prefs.getBoolean(noteLockEnabledKey, false)
    }

    fun setNoteLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(noteLockEnabledKey, enabled).apply()
    }

    fun isDynamicColorEnabled(): Boolean {
        return prefs.getBoolean(dynamicColorKey, true)
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(dynamicColorKey, enabled).apply()
    }
    
    // Action ordering methods
    fun getRecommendedActionOrder(): List<String> {
        val orderString = prefs.getString("recommended_action_order", null)
        return orderString?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    }
    
    fun saveRecommendedActionOrder(order: List<String>) {
        prefs.edit()
            .putString("recommended_action_order", order.joinToString(","))
            .apply()
    }
    
    fun getUtilityActionOrder(): List<String> {
        val orderString = prefs.getString("utility_action_order", null)
        return orderString?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    }
    
    fun saveUtilityActionOrder(order: List<String>) {
        prefs.edit()
            .putString("utility_action_order", order.joinToString(","))
            .apply()
    }
    
    fun getTimeActionOrder(): List<String> {
        val orderString = prefs.getString("time_action_order", null)
        return orderString?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    }
    
    fun saveTimeActionOrder(order: List<String>) {
        prefs.edit()
            .putString("time_action_order", order.joinToString(","))
            .apply()
    }
    
    fun getAIActionOrder(): List<String> {
        val orderString = prefs.getString("ai_action_order", null)
        return orderString?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    }
    
    fun saveAIActionOrder(order: List<String>) {
        prefs.edit()
            .putString("ai_action_order", order.joinToString(","))
            .apply()
    }

    fun getLastTranslateTargetLanguage(): String? {
        return prefs.getString("last_translate_target_language", null)
    }

    fun setLastTranslateTargetLanguage(langCode: String) {
        prefs.edit().putString("last_translate_target_language", langCode).apply()
    }
}
