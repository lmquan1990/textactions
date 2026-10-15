package com.freeup.textactions

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeup.textactions.domain.CreateReminderUseCase
import com.freeup.textactions.domain.ExtractEntitiesUseCase
import com.freeup.textactions.domain.TranslateTextUseCase
import com.freeup.textactions.domain.SummarizeTextUseCase
import com.freeup.textactions.domain.SummaryResult
import com.freeup.textactions.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import com.freeup.textactions.ui.theme.AppIcons

import com.freeup.textactions.data.repository.HomeRepository
import com.freeup.textactions.domain.model.ActionHistory
import com.freeup.textactions.data.local.db.NoteEntity
import com.freeup.textactions.data.local.datastore.TextFormattingDataStore
import com.freeup.textactions.formatting.FormatPreviewState
import com.freeup.textactions.formatting.FormatSettings
import com.freeup.textactions.formatting.FormatSuggestion
import com.freeup.textactions.formatting.FormatSuggestionDetector
import com.freeup.textactions.formatting.FormatTextUseCase
import com.freeup.textactions.formatting.FormatOption
import java.time.ZoneId

class ProcessTextViewModel(
    private val context: Context,
    private val extractEntitiesUseCase: ExtractEntitiesUseCase,
    private val translateTextUseCase: TranslateTextUseCase,
    private val summarizeTextUseCase: SummarizeTextUseCase,
    private val createReminderUseCase: CreateReminderUseCase,
    private val repository: HomeRepository,
    private val formatTextUseCase: FormatTextUseCase,
    private val formattingStore: TextFormattingDataStore
) : ViewModel() {

    data class TextActionState(
        val text: String,
        val inputSource: InputSource,
        val originalImageUri: android.net.Uri?,
        val isCropped: Boolean
    )

    data class FormatUiState(
        val showPanel: Boolean = false,
        val showPreview: Boolean = false,
        val preview: FormatPreviewState? = null,
        val suggestions: List<FormatSuggestion> = emptyList(),
        val settings: FormatSettings = FormatSettings()
    )

    sealed class UiEvent {
        object RequestRescanCamera : UiEvent()
        object RequestRescanGallery : UiEvent()
        data class ShowToast(val message: String) : UiEvent()
    }

    private val _actions = MutableStateFlow<List<TextActionItem>>(emptyList())
    val actions = _actions.asStateFlow()

    private val _translation = MutableStateFlow<TranslationResult?>(null)
    val translation = _translation.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isTranslating = MutableStateFlow(false)
    val isTranslating = _isTranslating.asStateFlow()

    private val _summary = MutableStateFlow<SummaryResult?>(null)
    val summary = _summary.asStateFlow()

    private val _isSummarizing = MutableStateFlow(false)
    val isSummarizing = _isSummarizing.asStateFlow()

    private val _translatedSummary = MutableStateFlow<SummaryResult?>(null)
    val translatedSummary = _translatedSummary.asStateFlow()

    private val _translatedSummarySource = MutableStateFlow<String?>(null)
    val translatedSummarySource = _translatedSummarySource.asStateFlow()

    private val _isSummarizingTranslation = MutableStateFlow(false)
    val isSummarizingTranslation = _isSummarizingTranslation.asStateFlow()

    private val _selectedTargetLanguage = MutableStateFlow<String?>(
        SettingsManager(context).getLastTranslateTargetLanguage()
    )
    val selectedTargetLanguage = _selectedTargetLanguage.asStateFlow()

    private val _originalTranslationText = MutableStateFlow<String?>(null)
    private var cachedSourceLanguage: String? = null

    private val _textState = MutableStateFlow(
        TextActionState(
            text = "",
            inputSource = InputSource.SHARE,
            originalImageUri = null,
            isCropped = false
        )
    )
    val textState = _textState.asStateFlow()

    private val _uiEvent = MutableStateFlow<UiEvent?>(null)
    val uiEvent = _uiEvent.asStateFlow()

    private val _formatUiState = MutableStateFlow(FormatUiState())
    val formatUiState = _formatUiState.asStateFlow()

    init {
        viewModelScope.launch {
            formattingStore.settingsFlow.collect { settings ->
                _formatUiState.value = _formatUiState.value.copy(settings = settings)
                updateFormatSuggestions(_textState.value.text, settings)
            }
        }
    }

    fun initializeTextState(
        text: String,
        inputSource: InputSource,
        originalImageUri: android.net.Uri?
    ) {
        cachedSourceLanguage = null
        _textState.value = TextActionState(
            text = text,
            inputSource = inputSource,
            originalImageUri = originalImageUri,
            isCropped = false
        )
        analyzeText(text)
        updateFormatSuggestions(text, _formatUiState.value.settings)
    }

    fun analyzeText(text: String) {
        viewModelScope.launch {
            _isLoading.value = true
            
            // Tier 1: Regex Analysis (Immediate)
            val regexInstances = ActionRegistry.getApplicableActions(context, text)
            val regexActions = regexInstances.map { it.toTextActionItem(context) }
            _actions.value = regexActions
            
            // Tier 2: ML Kit Analysis (Asynchronous)
            val mlKitEntities = extractEntitiesUseCase.execute(text)
            
            // Task 2: Check for reminder-specific entities from ML Kit
            val reminderTime = createReminderUseCase.parseReminderTime(text)
            val detections = TextAnalyzer().analyze(text)
            val shouldShowCalendar = shouldShowCalendarAction(text, detections)
            val shouldShowReminder = shouldShowReminderAction(text, detections) && !shouldShowCalendar
            val hasReminderInRegex = regexActions.any { it.id.substringBefore(":") == "reminder" }
            val hasCalendarInRegex = regexActions.any { it.id.substringBefore(":") == "calendar" }
            val mlKitActions = buildList {
                addAll(mlKitEntities)
                if (!hasCalendarInRegex && shouldShowCalendar) {
                    add(buildCalendarAction(text, reminderTime, detections))
                }
                if (!hasReminderInRegex && shouldShowReminder) {
                    add(buildReminderAction(text, reminderTime))
                }
            }

            // Merge and deduplicate
            var merged = (regexActions + mlKitActions)
                .filterNot { it.id.substringBefore(":") == "calendar" && shouldShowCalendar }
                .distinctBy { it.id }

            // Deduplicate save_contact: keep only one (ML Kit "Save contact" or regex "Save X to contacts")
            val saveContactActions = merged.filter { it.id == "save_contact" || it.id.startsWith("save_contact:") }
            if (saveContactActions.size > 1) {
                val preferred = saveContactActions.firstOrNull { it.id == "save_contact" }
                    ?: saveContactActions.first()
                merged = merged.filter { it.id != "save_contact" && !it.id.startsWith("save_contact:") } + listOf(preferred)
            }

            val reminderActions = merged.filter { it.id.substringBefore(":") == "reminder" }
            val mergedWithReminder = if (reminderActions.size > 1) {
                val preferred = reminderActions.maxByOrNull { action ->
                    if (action.subtitle?.contains("-") == true || action.subtitle?.contains(":") == true) 1 else 0
                }
                merged.filter { it.id.substringBefore(":") != "reminder" } + listOfNotNull(preferred)
            } else merged
            
            // Tier 3: Substring deduplication (e.g., keep longest address)
            val finalActions = mergedWithReminder.filter { current ->
                val isRedundant = mergedWithReminder.any { other ->
                    current !== other && 
                    current.id.substringBefore(":") == other.id.substringBefore(":") &&
                    other.subtitle?.contains(current.subtitle ?: "", ignoreCase = true) == true &&
                    other.subtitle.length > (current.subtitle?.length ?: 0)
                }
                !isRedundant
            }

            // Tier 4: Collapse duplicates for specific recommended actions (e.g. address-related).
            // We may get the same action type from both Regex (ActionRegistry) and ML Kit (ExtractEntitiesUseCase)
            // with slightly different extracted strings -> looks like duplicate rows in UI.
            val collapsedActions = collapseRecommendedDuplicates(finalActions)
            
            _actions.value = collapsedActions
            
            _isLoading.value = false
            updateFormatSuggestions(text, _formatUiState.value.settings)
        }
    }

    fun onRescanClicked() {
        val source = _textState.value.inputSource
        _uiEvent.value = when (source) {
            InputSource.CAMERA -> UiEvent.RequestRescanCamera
            InputSource.GALLERY -> UiEvent.RequestRescanGallery
            else -> null
        }
    }

    fun onUiEventHandled() {
        _uiEvent.value = null
    }

    fun updateTextFromOcr(
        newText: String,
        inputSource: InputSource,
        originalImageUri: android.net.Uri?,
        isCropped: Boolean
    ) {
        if (newText.isBlank()) return
        _textState.value = TextActionState(
            text = newText,
            inputSource = inputSource,
            originalImageUri = originalImageUri,
            isCropped = isCropped
        )
        clearSmartSuggestions()
        analyzeText(newText)
        updateFormatSuggestions(newText, _formatUiState.value.settings)
    }

    fun openFormatPanel() {
        _formatUiState.value = _formatUiState.value.copy(showPanel = true, showPreview = false)
    }

    fun closeFormatPanel() {
        _formatUiState.value = _formatUiState.value.copy(showPanel = false)
    }

    fun onFormatOptionSelected(option: FormatOption) {
        val currentText = _textState.value.text
        val settings = _formatUiState.value.settings
        val formatted = formatTextUseCase.format(currentText, option, settings)
        _formatUiState.value = _formatUiState.value.copy(
            showPanel = false,
            showPreview = true,
            preview = FormatPreviewState(before = currentText, after = formatted, option = option)
        )
    }

    fun dismissFormatPreview() {
        _formatUiState.value = _formatUiState.value.copy(showPreview = false, preview = null)
    }

    fun applyFormatPreview() {
        val preview = _formatUiState.value.preview ?: return
        _textState.value = _textState.value.copy(text = preview.after)
        analyzeText(preview.after)
        _formatUiState.value = _formatUiState.value.copy(showPreview = false, preview = null)
    }

    fun applyFormatSuggestion(option: FormatOption) {
        val currentText = _textState.value.text
        val settings = _formatUiState.value.settings
        val formatted = formatTextUseCase.format(currentText, option, settings)
        _textState.value = _textState.value.copy(text = formatted)
        analyzeText(formatted)
        if (option == FormatOption.CLEAN_SPACING) {
            _uiEvent.value = UiEvent.ShowToast(context.getString(R.string.format_clean_spacing_done))
        }
    }

    private fun updateFormatSuggestions(text: String, settings: FormatSettings) {
        val suggestions = FormatSuggestionDetector.suggest(text, settings)
        _formatUiState.value = _formatUiState.value.copy(suggestions = suggestions)
    }

    fun runOcrFromImageUri(
        ocrImageUri: android.net.Uri,
        originalImageUri: android.net.Uri?,
        inputSource: InputSource,
        isCropped: Boolean
    ) {
        com.freeup.textactions.processImageFromUri(context, ocrImageUri, "ocr") { text ->
            updateTextFromOcr(
                newText = text,
                inputSource = inputSource,
                originalImageUri = originalImageUri,
                isCropped = isCropped
            )
        }
    }

    private fun clearSmartSuggestions() {
        _actions.value = emptyList()
        _summary.value = null
        _translation.value = null
        _translatedSummary.value = null
        _translatedSummarySource.value = null
    }

    /** Set when reopening a Translate history record so viewing it doesn't create a new record. */
    var suppressHistoryLog = false
    private var lastLoggedTranslateInput: String? = null

    private var cachedTranslation: Pair<String, TranslationResult.Success>? = null

    /** Preload a saved translation (from history) so reopening it doesn't re-translate. */
    fun preloadTranslation(input: String, result: TranslationResult.Success) {
        cachedTranslation = input to result
        _selectedTargetLanguage.value = result.targetLang
        cachedSourceLanguage = result.sourceLang
    }

    fun translateSelection(text: String, allowDownload: Boolean = false) {
        cachedTranslation?.let { (input, saved) ->
            cachedTranslation = null
            if (input == text && saved.targetLang == _selectedTargetLanguage.value) {
                _originalTranslationText.value = text
                _translation.value = saved
                _translatedSummary.value = null
                _translatedSummarySource.value = null
                return
            }
        }
        viewModelScope.launch {
            _isTranslating.value = true
            _originalTranslationText.value = text
            android.util.Log.d("ProcessTextViewModel", "translateSelection called with text length: ${text.length}")
            android.util.Log.d("ProcessTextViewModel", "Text preview (first 100 chars): ${text.take(100)}")
            try {
                val result = translateTextUseCase.execute(text, _selectedTargetLanguage.value, cachedSourceLanguage, allowDownload)
                if (result is TranslationResult.Success) {
                    cachedSourceLanguage = result.sourceLang
                }
                _translation.value = result
                _translatedSummary.value = null
                _translatedSummarySource.value = null

                // Log translate action to history (once per input per session)
                if (result !is TranslationResult.ModelsMissing && !suppressHistoryLog && lastLoggedTranslateInput != text) try {
                    lastLoggedTranslateInput = text
                    val resultText = (result as? TranslationResult.Success)?.text
                    val history = ActionHistory(
                        id = 0,
                        actionType = "TRANSLATE",
                        inputText = text,
                        resultText = resultText,
                        metadata = (result as? TranslationResult.Success)?.let {
                            org.json.JSONObject()
                                .put("sourceLang", it.sourceLang)
                                .put("targetLang", it.targetLang)
                                .toString()
                        },
                        createdAt = System.currentTimeMillis()
                    )
                    repository.insertAction(history)
                } catch (e: Exception) {
                    android.util.Log.e("ProcessTextViewModel", "Error logging translate action: ${e.message}", e)
                }
            } finally {
                _isTranslating.value = false
            }
        }
    }

    /** User confirmed downloading the missing models: translate again, this time allowing download. */
    fun downloadModelsAndTranslate() {
        _originalTranslationText.value?.let { translateSelection(it, allowDownload = true) }
    }

    fun changeTargetLanguage(languageCode: String) {
        _selectedTargetLanguage.value = languageCode
        SettingsManager(context).setLastTranslateTargetLanguage(languageCode)
        // Re-translate if there's existing text
        _originalTranslationText.value?.let { text ->
            translateSelection(text)
        }
    }

    fun summarizeSelection(text: String) {
        viewModelScope.launch {
            _isSummarizing.value = true
            try {
                _summary.value = summarizeTextUseCase.execute(text)
            } finally {
                _isSummarizing.value = false
            }
        }
    }

    fun summarizeTranslation(text: String) {
        val cleaned = text.trim()
        if (cleaned.isBlank()) return
        if (_translatedSummarySource.value == cleaned && _translatedSummary.value != null) return
        viewModelScope.launch {
            _isSummarizingTranslation.value = true
            try {
                _translatedSummary.value = summarizeTextUseCase.execute(cleaned)
                _translatedSummarySource.value = cleaned
            } finally {
                _isSummarizingTranslation.value = false
            }
        }
    }

    fun clearSummary() {
        _summary.value = null
    }

    fun clearTranslation() {
        _translation.value = null
        _translatedSummary.value = null
        _translatedSummarySource.value = null
    }

    fun clearTranslatedSummary() {
        _translatedSummary.value = null
        _translatedSummarySource.value = null
    }

    fun logAction(actionItem: TextActionItem, inputText: String) {
        viewModelScope.launch {
            try {
                val actionType = actionItem.id.substringBefore(":").uppercase()
                val history = ActionHistory(
                    id = 0, // Room will auto-generate
                    actionType = actionType,
                    inputText = inputText,
                    resultText = actionItem.subtitle,
                    createdAt = System.currentTimeMillis()
                )
                repository.insertAction(history)
                android.util.Log.d("ProcessTextViewModel", "Action logged: $actionType - $inputText")
            } catch (e: Exception) {
                android.util.Log.e("ProcessTextViewModel", "Error logging action: ${e.message}", e)
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun saveNote(
        title: String,
        content: String,
        _inputText: String,
        sourceAppName: String? = null,
        sourcePackageName: String? = null
    ) {
        val now = System.currentTimeMillis()
        if (repository.hasNoteWithContent(content, now - 60_000L)) return
        val type = com.freeup.textactions.util.NoteCategorizer.detectType(content)
        val url = com.freeup.textactions.util.NoteCategorizer.extractSourceUrl(content)

        repository.insertNote(
            NoteEntity(
                id = java.util.UUID.randomUUID().toString(),
                title = title,
                content = content,
                createdAt = now,
                category = "General",
                isLocked = false,
                sourceAppName = sourceAppName,
                sourcePackageName = sourcePackageName,
                sourceUrl = url,
                detectedType = type,
                isArchived = false,
                isPinned = false,
                pinnedAt = null
            )
        )

        val historyText = if (title.isBlank()) content.take(100).trim() else title.trim()
        repository.insertAction(
            ActionHistory(
                id = 0,
                actionType = "SAVE_NOTE",
                inputText = historyText,
                resultText = null,
                metadata = null,
                createdAt = now
            )
        )
    }

    private fun buildReminderAction(text: String, time: java.time.LocalDateTime?): TextActionItem {
        val title = suggestReminderTitle(text)
        val subtitle = time?.toString() ?: "Select date & time"
        return TextActionItem(
            id = "reminder",
            title = "Create Reminder",
            subtitle = subtitle,
            icon = AppIcons.Bell,
            action = {
                val intent = android.content.Intent(android.content.Intent.ACTION_INSERT).apply {
                    data = android.provider.CalendarContract.Events.CONTENT_URI
                    putExtra(android.provider.CalendarContract.Events.TITLE, title)
                    if (time != null) {
                        val startMillis = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                        putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, startMillis + 60 * 60 * 1000)
                    }
                }
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        )
    }

    private fun buildCalendarAction(
        text: String,
        time: java.time.LocalDateTime?,
        detections: List<Detection>
    ): TextActionItem {
        val title = suggestEventTitle(text)
        val location = detections.firstOrNull { it.type == TextType.ADDRESS }?.value
        val hasExplicitTime = createReminderUseCase.hasExplicitTime(text)
        val subtitle = time?.toString() ?: "Select date & time"
        return TextActionItem(
            id = "calendar",
            title = "Create Calendar Event",
            subtitle = subtitle,
            icon = AppIcons.Calendar,
            action = {
                val intent = android.content.Intent(android.content.Intent.ACTION_INSERT).apply {
                    data = android.provider.CalendarContract.Events.CONTENT_URI
                    putExtra(android.provider.CalendarContract.Events.TITLE, title)
                    if (!location.isNullOrBlank()) {
                        putExtra(android.provider.CalendarContract.Events.EVENT_LOCATION, location)
                    }
                    if (time != null) {
                        val zone = ZoneId.systemDefault()
                        if (!hasExplicitTime) {
                            val start = time.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
                            val end = time.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                            putExtra(android.provider.CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                            putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                            putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, end)
                        } else {
                            val startMillis = time.atZone(zone).toInstant().toEpochMilli()
                            putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                            putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, startMillis + 60 * 60 * 1000)
                        }
                    }
                }
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        )
    }

    private fun suggestEventTitle(text: String): String {
        val cleaned = text.replace(Regex("\\s+"), " ").trim()
        if (cleaned.isBlank()) return "Event"
        return cleaned.split(" ").take(8).joinToString(" ")
    }

    private fun suggestReminderTitle(text: String): String {
        val cleaned = text.replace(Regex("\\s+"), " ").trim()
        if (cleaned.isBlank()) return "Reminder"
        return cleaned.split(" ").take(8).joinToString(" ")
    }
}

private fun collapseRecommendedDuplicates(actions: List<TextActionItem>): List<TextActionItem> {
    // Only collapse a small set of IDs where duplicates are common/annoying.
    val collapseIds = setOf("copy_address", "map")

    fun canonicalize(value: String?): String {
        if (value == null) return ""
        // Normalize whitespace and trim trailing punctuation that often differs between detectors.
        return value
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd(',', '.', ';', ':')
    }

    fun score(item: TextActionItem): Int {
        // Prefer actions that show the real value (address) in subtitle, and prefer longer/more complete strings.
        val subtitle = canonicalize(item.subtitle)
        val hasConcreteValue = subtitle.isNotBlank() &&
            !subtitle.equals("save to clipboard", ignoreCase = true) &&
            !subtitle.equals("send to other apps", ignoreCase = true)
        val base = if (hasConcreteValue) 1000 else 0
        return base + subtitle.length
    }

    val mapValue = actions
        .firstOrNull { it.id.substringBefore(":") == "map" }
        ?.let { canonicalize(it.subtitle?.ifBlank { it.id.substringAfter(":", "") }) }
        .orEmpty()

    val bestByType = actions
        .filter { it.id.substringBefore(":") in collapseIds }
        .groupBy { it.id.substringBefore(":") }
        .mapValues { (_, group) ->
            // For copy_address: prefer the one matching the map value (so the address string is consistent),
            // otherwise fall back to best-scored (usually the longest/most concrete).
            if (group.first().id.substringBefore(":") == "copy_address" && mapValue.isNotBlank()) {
                group
                    .sortedWith(
                        compareByDescending<TextActionItem> { canonicalize(it.subtitle).equals(mapValue, ignoreCase = true) }
                            .thenByDescending { score(it) }
                    )
                    .first()
            } else {
                group.maxByOrNull { score(it) } ?: group.first()
            }
        }

    return actions.filterNot { it.id.substringBefore(":") in collapseIds } +
        collapseIds.mapNotNull { bestByType[it] }
}
