package com.freeup.textactions.ui.actions

import android.content.Context
import com.freeup.textactions.ActionLabels
import com.freeup.textactions.R
import com.freeup.textactions.SettingsManager
import com.freeup.textactions.data.local.datastore.ActionSettingsDataStore
import com.freeup.textactions.domain.model.ActionCategory
import com.freeup.textactions.domain.model.ActionSetting
import com.freeup.textactions.ui.theme.AppIcons
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ActionsUiState(
    val actions: List<ActionSetting> = emptyList()
)

class ActionsViewModel(
    private val context: Context,
    private val dataStore: ActionSettingsDataStore
) : ViewModel() {

    private fun buildDefaultActions() = listOf(
            // Recommended: liên hệ & địa chỉ (khi chọn SĐT, email, địa chỉ, link)
            ActionSetting("call", ActionLabels.getLabel(context, "call"), context.getString(R.string.action_desc_call), AppIcons.Phone, true, ActionCategory.RECOMMENDED),
            ActionSetting("sms", ActionLabels.getLabel(context, "sms"), context.getString(R.string.action_desc_sms), AppIcons.Sms, true, ActionCategory.RECOMMENDED),
            ActionSetting("save_contact", ActionLabels.getLabel(context, "save_contact"), context.getString(R.string.action_desc_save_contact), AppIcons.UserPlus, true, ActionCategory.RECOMMENDED),
            ActionSetting("email", ActionLabels.getLabel(context, "email"), context.getString(R.string.action_desc_email), AppIcons.Envelope, true, ActionCategory.RECOMMENDED),
            ActionSetting("map", ActionLabels.getLabel(context, "map"), context.getString(R.string.action_desc_map), AppIcons.Map, true, ActionCategory.RECOMMENDED),
            ActionSetting("copy_address", ActionLabels.getLabel(context, "copy_address"), context.getString(R.string.action_desc_copy_address), AppIcons.Copy, true, ActionCategory.RECOMMENDED),
            ActionSetting("open_link", ActionLabels.getLabel(context, "open_link"), context.getString(R.string.action_desc_open_link), AppIcons.Link, true, ActionCategory.RECOMMENDED),
            // Time: lịch & nhắc
            ActionSetting("calendar", ActionLabels.getLabel(context, "calendar"), context.getString(R.string.action_desc_calendar), AppIcons.Calendar, true, ActionCategory.TIME),
            ActionSetting("reminder", ActionLabels.getLabel(context, "reminder"), context.getString(R.string.action_desc_reminder), AppIcons.Alarm, true, ActionCategory.TIME),
            // Utilities: văn bản (copy, share, search, translate...)
            ActionSetting("copy", ActionLabels.getLabel(context, "copy"), context.getString(R.string.action_desc_copy), AppIcons.Copy, true, ActionCategory.UTILITIES),
            ActionSetting("save_note", ActionLabels.getLabel(context, "save_note"), context.getString(R.string.action_desc_save_note), AppIcons.NotePencil, true, ActionCategory.UTILITIES),
            ActionSetting("share", ActionLabels.getLabel(context, "share"), context.getString(R.string.action_desc_share), AppIcons.Share, true, ActionCategory.UTILITIES),
            ActionSetting("search", ActionLabels.getLabel(context, "search"), context.getString(R.string.action_desc_search), AppIcons.Search, true, ActionCategory.UTILITIES),
            ActionSetting("ask_ai", ActionLabels.getLabel(context, "ask_ai"), context.getString(R.string.action_desc_ask_ai), AppIcons.Sparkle, true, ActionCategory.UTILITIES),
            ActionSetting("media", ActionLabels.getLabel(context, "media"), context.getString(R.string.action_desc_media), AppIcons.PlayCircle, true, ActionCategory.UTILITIES),
            ActionSetting("format_text", ActionLabels.getLabel(context, "format_text"), context.getString(R.string.action_desc_format_text), AppIcons.TextFormat, true, ActionCategory.UTILITIES),
            ActionSetting("open_chat_app", ActionLabels.getLabel(context, "open_chat_app"), context.getString(R.string.action_desc_open_chat_app), AppIcons.Chat, true, ActionCategory.UTILITIES),
            ActionSetting("create_qr", ActionLabels.getLabel(context, "create_qr"), context.getString(R.string.action_desc_create_qr), AppIcons.QrCode, true, ActionCategory.UTILITIES),
            ActionSetting("translate", ActionLabels.getLabel(context, "translate"), context.getString(R.string.action_desc_translate), AppIcons.Translate, true, ActionCategory.UTILITIES),
            // Temporarily hidden
            // ActionSetting("tts", "Read Aloud", "...", AppIcons.Volume, true, ActionCategory.UTILITIES),
        )

    private val _uiState = MutableStateFlow(ActionsUiState(actions = buildDefaultActions()))
    val uiState: StateFlow<ActionsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val settingsManager = SettingsManager(context)
            
            // Load enabled states for all actions from DataStore
            val enabledStates = mutableMapOf<String, Boolean>()
            
            // Load enabled state for each action (take first value only)
            buildDefaultActions().forEach { action ->
                dataStore.isActionEnabled(action.id)
                    .take(1)
                    .collect { enabled ->
                        enabledStates[action.id] = enabled
                        // Sync to SettingsManager for ActionRegistry compatibility
                        settingsManager.setEnabled(action.id, enabled)
                    }
            }
            
            // Also check SettingsManager for any missing values (backward compatibility)
            buildDefaultActions().forEach { action ->
                if (!enabledStates.containsKey(action.id)) {
                    val enabled = settingsManager.isEnabled(action.id)
                    enabledStates[action.id] = enabled
                }
            }
            
            // Apply loaded enabled states to default actions
            val updatedActions = buildDefaultActions().map { action ->
                action.copy(isEnabled = enabledStates[action.id] ?: action.isEnabled)
            }

            // Load saved orders
            val recommendedOrder = settingsManager.getRecommendedActionOrder()
            val timeOrder = settingsManager.getTimeActionOrder()
            val utilityOrder = settingsManager.getUtilityActionOrder()
            val aiOrder = settingsManager.getAIActionOrder()

            // Sort actions based on saved order within their categories
            // We need to preserve the category grouping, so we sort items relative to their category's order list
            // Actions not in the saved list (new actions) will appear at the end of their category
            val sortedActions = updatedActions.sortedWith(
                Comparator { a, b ->
                    if (a.category != b.category) {
                        // Keep categories separate (though UI groups them manually, this keeps list clean)
                        a.category.ordinal.compareTo(b.category.ordinal)
                    } else {
                        val orderList = when (a.category) {
                            ActionCategory.RECOMMENDED -> recommendedOrder
                            ActionCategory.TIME -> timeOrder
                            ActionCategory.UTILITIES -> utilityOrder
                            ActionCategory.AI -> aiOrder
                            else -> emptyList()
                        }
                        
                        val indexA = orderList.indexOf(a.id)
                        val indexB = orderList.indexOf(b.id)
                        
                        when {
                            indexA != -1 && indexB != -1 -> indexA.compareTo(indexB)
                            indexA != -1 -> -1 // A is in list, B is not -> A comes first
                            indexB != -1 -> 1  // B is in list, A is not -> B comes first
                            else -> 0 // Both not in list -> keep default order
                        }
                    }
                }
            )
            
            _uiState.update { it.copy(actions = sortedActions) }
            
            // Listen for changes to enabled states and update UI
            buildDefaultActions().forEach { action ->
                launch {
                    dataStore.isActionEnabled(action.id)
                        .drop(1) // Skip the first value (already loaded)
                        .collect { enabled ->
                            // Sync to SettingsManager for ActionRegistry compatibility
                            settingsManager.setEnabled(action.id, enabled)
                            _uiState.update { state ->
                                state.copy(actions = state.actions.map { 
                                    if (it.id == action.id) it.copy(isEnabled = enabled) else it 
                                })
                            }
                        }
                }
            }
        }
    }

    /** Re-resolve action titles and descriptions after a locale change. */
    fun reloadLabels() {
        val fresh = buildDefaultActions()
        _uiState.update { state ->
            state.copy(actions = state.actions.map { existing ->
                val f = fresh.find { it.id == existing.id }
                if (f != null) existing.copy(title = f.title, description = f.description) else existing
            })
        }
    }

    fun toggleAction(id: String, enabled: Boolean) {
        viewModelScope.launch {
            // Save to DataStore
            dataStore.setActionEnabled(id, enabled)
            // Also save to SettingsManager for ActionRegistry compatibility
            val settingsManager = SettingsManager(context)
            settingsManager.setEnabled(id, enabled)
            // Local update for immediate feedback
            _uiState.update { state ->
                state.copy(actions = state.actions.map { 
                    if (it.id == id) it.copy(isEnabled = enabled) else it 
                })
            }
        }
    }

    fun resetDefaults() {
        viewModelScope.launch {
            dataStore.resetDefaults()
            val settingsManager = SettingsManager(context)
            settingsManager.saveRecommendedActionOrder(emptyList())
            settingsManager.saveTimeActionOrder(emptyList())
            settingsManager.saveUtilityActionOrder(emptyList())
            _uiState.update { it.copy(actions = buildDefaultActions()) }
        }
    }
    
    fun saveRecommendedOrder(order: List<String>) {
        val settingsManager = SettingsManager(context)
        settingsManager.saveRecommendedActionOrder(order)
    }
    
    fun saveUtilityOrder(order: List<String>) {
        val settingsManager = SettingsManager(context)
        settingsManager.saveUtilityActionOrder(order)
    }
    
    fun saveTimeOrder(order: List<String>) {
        val settingsManager = SettingsManager(context)
        settingsManager.saveTimeActionOrder(order)
    }
    
    fun saveAIOrder(order: List<String>) {
        val settingsManager = SettingsManager(context)
        settingsManager.saveAIActionOrder(order)
    }
}
