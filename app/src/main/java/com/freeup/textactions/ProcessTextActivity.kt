package com.freeup.textactions

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.freeup.textactions.domain.CreateReminderUseCase
import com.freeup.textactions.domain.ExtractEntitiesUseCase
import com.freeup.textactions.domain.SummarizeTextUseCase
import com.freeup.textactions.domain.TranslateTextUseCase
import com.freeup.textactions.data.local.db.AppDatabase
import com.freeup.textactions.data.local.datastore.AiStatusDataStore
import com.freeup.textactions.data.local.datastore.TextFormattingDataStore
import com.freeup.textactions.data.repository.HomeRepository
import com.freeup.textactions.formatting.FormatTextUseCase

import com.freeup.textactions.ui.theme.TextActionsTheme
import com.freeup.textactions.SettingsManager

class ProcessTextActivity : BaseActivity() {

    private var isReadOnly: Boolean = false
    private val viewModel: ProcessTextViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = AppDatabase.getDatabase(applicationContext)
                val repository = HomeRepository(
                    applicationContext,
                    db.actionHistoryDao(),
                    db.noteDao(),
                    AiStatusDataStore(applicationContext)
                )
                return ProcessTextViewModel(
                    applicationContext,
                    ExtractEntitiesUseCase(applicationContext),
                    TranslateTextUseCase(),
                    SummarizeTextUseCase(),
                    CreateReminderUseCase(),
                    repository,
                    FormatTextUseCase(),
                    TextFormattingDataStore(applicationContext)
                ) as T
            }
        }
    }

    // Rescan returns its result here so cancelling the camera lands back on this sheet
    private val rescanLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            val newText = data.getStringExtra(Intent.EXTRA_PROCESS_TEXT).orEmpty()
            val source = data.getStringExtra(CameraScanActivity.EXTRA_INPUT_SOURCE)?.let {
                runCatching { InputSource.valueOf(it) }.getOrNull()
            } ?: InputSource.CAMERA
            val imageUri = data.getStringExtra(CameraScanActivity.EXTRA_ORIGINAL_IMAGE_URI)?.let {
                runCatching { android.net.Uri.parse(it) }.getOrNull()
            }
            viewModel.updateTextFromOcr(newText, source, imageUri, false)
        }
    }

    private fun launchRescan() {
        rescanLauncher.launch(Intent(this, CameraScanActivity::class.java).apply {
            putExtra("scan_type", "ocr")
            putExtra(CameraScanActivity.EXTRA_RETURN_RESULT, true)
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        val readOnlyText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT_READONLY)?.toString()
        val contentText = text ?: readOnlyText ?: ""

        android.util.Log.d("ProcessTextActivity", "Received text length: ${contentText.length}")
        android.util.Log.d("ProcessTextActivity", "Text preview (first 100 chars): ${contentText.take(100)}")

        if (contentText.isBlank()) {
            finish()
            return
        }

        isReadOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false) || text == null

        intent.getStringExtra("EXTRA_CACHED_TRANSLATION")?.let { saved ->
            val src = intent.getStringExtra("EXTRA_CACHED_SOURCE_LANG").orEmpty()
            val tgt = intent.getStringExtra("EXTRA_CACHED_TARGET_LANG").orEmpty()
            if (saved.isNotBlank() && tgt.isNotBlank()) {
                viewModel.preloadTranslation(
                    contentText,
                    com.freeup.textactions.domain.TranslationResult.Success(saved, src, tgt)
                )
            }
        }
        viewModel.suppressHistoryLog = intent.getBooleanExtra("EXTRA_AUTO_TRANSLATE", false)
        viewModel.analyzeText(contentText)

        val settingsManager = SettingsManager(this)
        val dynamicColorEnabled = settingsManager.isDynamicColorEnabled()
        val themeMode = settingsManager.getThemeMode()

        setContent {
            val darkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            val inputSource = intent.getStringExtra(EXTRA_INPUT_SOURCE)?.let {
                runCatching { InputSource.valueOf(it) }.getOrNull()
            } ?: InputSource.SHARE
            val originalImageUri = intent.getStringExtra(EXTRA_ORIGINAL_IMAGE_URI)?.let { uri ->
                runCatching { android.net.Uri.parse(uri) }.getOrNull()
            }
            val actions by viewModel.actions.collectAsState()
            val translation by viewModel.translation.collectAsState()
            val isLoading by viewModel.isLoading.collectAsState()
            val isTranslating by viewModel.isTranslating.collectAsState()
            val summary by viewModel.summary.collectAsState()
            val isSummarizing by viewModel.isSummarizing.collectAsState()
            val translatedSummary by viewModel.translatedSummary.collectAsState()
            val translatedSummarySource by viewModel.translatedSummarySource.collectAsState()
            val isSummarizingTranslation by viewModel.isSummarizingTranslation.collectAsState()
            val selectedTargetLanguage by viewModel.selectedTargetLanguage.collectAsState()
            val textState by viewModel.textState.collectAsState()
            val formatUiState by viewModel.formatUiState.collectAsState()
            val coroutineScope = rememberCoroutineScope()
            val uiEvent by viewModel.uiEvent.collectAsState()
            val currentText = if (textState.text.isNotBlank()) textState.text else contentText

            TextActionsTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColorEnabled,
                windowBackgroundColor = androidx.compose.ui.graphics.Color.Transparent
            ) {
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    viewModel.initializeTextState(
                        text = contentText,
                        inputSource = inputSource,
                        originalImageUri = originalImageUri
                    )
                }
                androidx.compose.runtime.LaunchedEffect(uiEvent) {
                    when (uiEvent) {
                        ProcessTextViewModel.UiEvent.RequestRescanCamera,
                        ProcessTextViewModel.UiEvent.RequestRescanGallery -> {
                            launchRescan()
                            viewModel.onUiEventHandled()
                        }
                        is ProcessTextViewModel.UiEvent.ShowToast -> {
                            val event = uiEvent as ProcessTextViewModel.UiEvent.ShowToast
                            android.widget.Toast.makeText(this@ProcessTextActivity, event.message, android.widget.Toast.LENGTH_SHORT).show()
                            viewModel.onUiEventHandled()
                        }
                        null -> Unit
                    }
                }
                val autoAction = intent.getStringExtra("EXTRA_AUTO_ACTION")

                // Auto-save note immediately if triggered as auto-action
                if (autoAction == "save_note") {
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            viewModel.saveNote(
                                "", contentText, contentText,
                                sourceAppName = referrer?.authority,
                                sourcePackageName = callingPackage
                            )
                        }
                        android.widget.Toast.makeText(this@ProcessTextActivity, getString(R.string.saved_to_snippet), android.widget.Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } else {
                    val snippetId = intent.getLongExtra("EXTRA_SNIPPET_ID", -1L)
                    val isSavedSnippet = snippetId != -1L
                    val hideSaveAction = intent.getBooleanExtra("EXTRA_HIDE_SAVE_ACTION", false)
                    
                    ProcessTextScreen(
                    textState = textState,
                    formatUiState = formatUiState,
                    isSavedSnippet = isSavedSnippet,
                    hideSaveAction = hideSaveAction,
                    autoTranslate = intent.getBooleanExtra("EXTRA_AUTO_TRANSLATE", false),
                    actions = actions,
                    translation = translation,
                    isLoading = isLoading,
                    isTranslating = isTranslating,
                    summary = summary,
                    isSummarizing = isSummarizing,
                    translatedSummary = translatedSummary,
                    translatedSummarySource = translatedSummarySource,
                    isSummarizingTranslation = isSummarizingTranslation,
                    selectedTargetLanguage = selectedTargetLanguage,
                    onRescanClick = {
                        launchRescan()
                    },
                    onOpenFormatPanel = { viewModel.openFormatPanel() },
                    onCloseFormatPanel = { viewModel.closeFormatPanel() },
                    onFormatOptionSelected = { option -> viewModel.onFormatOptionSelected(option) },
                    onApplyFormat = { viewModel.applyFormatPreview() },
                    onDismissFormatPreview = { viewModel.dismissFormatPreview() },
                    onSuggestionClick = { option -> viewModel.applyFormatSuggestion(option) },
                    onActionClick = { item ->
                        viewModel.logAction(item, currentText)
                        item.action()
                        // Keep activity alive for share to allow chooser to launch properly
                        val baseId = item.id.substringBefore(":")
                        if (baseId != "quick_share") {
                            finish()
                        }
                    },
                    onSaveSnippet = { title, content ->
                        // Fix: Call suspend function from coroutine scope
                        coroutineScope.launch {
                            viewModel.saveNote(
                                title, content, currentText,
                                sourceAppName = referrer?.authority,
                                sourcePackageName = callingPackage
                            )
                        }
                    },
                    onSummarizeClick = {
                        viewModel.summarizeSelection(currentText)
                    },
                    onTranslateClick = {
                        viewModel.translateSelection(currentText)
                    },
                    onSummarizeTranslation = { translatedText ->
                        viewModel.summarizeTranslation(translatedText)
                    },
                    onCopyTranslate = { translatedText ->
                        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Translated Text", translatedText)
                        clipboard.setPrimaryClip(clip)
                        // Optional: Show toast
                        android.widget.Toast.makeText(this, getString(R.string.copied_translation), android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onCopySummary = { summaryText ->
                        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Summary", summaryText)
                        clipboard.setPrimaryClip(clip)
                        android.widget.Toast.makeText(this, getString(R.string.copied_summary), android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onClearSummary = {
                        viewModel.clearSummary()
                    },
                    onClearTranslate = {
                        viewModel.clearTranslation()
                    },
                    onClearTranslatedSummary = {
                        viewModel.clearTranslatedSummary()
                    },
                    onChangeTargetLanguage = { languageCode ->
                        viewModel.changeTargetLanguage(languageCode)
                    },
                    onDownloadTranslationModels = { viewModel.downloadModelsAndTranslate() },
                    onDismiss = { finish() }
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_INPUT_SOURCE = "EXTRA_INPUT_SOURCE"
        const val EXTRA_ORIGINAL_IMAGE_URI = "EXTRA_ORIGINAL_IMAGE_URI"
    }
}
