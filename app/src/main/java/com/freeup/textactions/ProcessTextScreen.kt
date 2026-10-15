package com.freeup.textactions

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freeup.textactions.ui.theme.NunitoFontFamily
import com.freeup.textactions.ui.theme.elevatedCardColor
import com.freeup.textactions.domain.TranslationResult
import com.freeup.textactions.domain.SummaryResult
import com.freeup.textactions.domain.splitSentences
import com.freeup.textactions.domain.SmartReadAloudUseCase
import com.freeup.textactions.ui.theme.LocalExpressiveColors
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

import androidx.compose.foundation.lazy.rememberLazyListState
import com.freeup.textactions.ui.theme.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.util.Patterns
import android.view.accessibility.AccessibilityManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import java.util.Locale

import com.freeup.textactions.data.local.db.NoteEntity
import com.freeup.textactions.formatting.FormatOption

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessTextScreen(
    textState: ProcessTextViewModel.TextActionState,
    formatUiState: ProcessTextViewModel.FormatUiState,
    actions: List<TextActionItem>,
    translation: TranslationResult?,
    isLoading: Boolean,
    isSavedSnippet: Boolean = false,
    hideSaveAction: Boolean = false,
    autoTranslate: Boolean = false,
    isTranslating: Boolean = false,
    summary: SummaryResult?,
    isSummarizing: Boolean = false,
    translatedSummary: SummaryResult?,
    translatedSummarySource: String?,
    isSummarizingTranslation: Boolean = false,
    selectedTargetLanguage: String? = null,
    onRescanClick: () -> Unit,
    onOpenFormatPanel: () -> Unit,
    onCloseFormatPanel: () -> Unit,
    onFormatOptionSelected: (com.freeup.textactions.formatting.FormatOption) -> Unit,
    onApplyFormat: () -> Unit,
    onDismissFormatPreview: () -> Unit,
    onSuggestionClick: (com.freeup.textactions.formatting.FormatOption) -> Unit,
    onActionClick: (TextActionItem) -> Unit,
    onSaveSnippet: suspend (String, String) -> Unit,
    onSummarizeClick: () -> Unit,
    onTranslateClick: () -> Unit,
    onSummarizeTranslation: (String) -> Unit,
    onCopyTranslate: (String) -> Unit,
    onCopySummary: (String) -> Unit,
    onClearSummary: () -> Unit,
    onClearTranslate: () -> Unit,
    onClearTranslatedSummary: () -> Unit,
    onChangeTargetLanguage: (String) -> Unit,
    onDownloadTranslationModels: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val text = textState.text
    val settingsManager = remember { SettingsManager(context) }
    val searchInsideResult = remember(text) { getSearchInsideResult(context, text) }
    var customOrder by remember { mutableStateOf(settingsManager.getRecommendedActionOrder()) }

    val askAiEnabled = remember { settingsManager.isEnabled("ask_ai") }
    val mediaEnabled = remember { settingsManager.isEnabled("media") }
    val showSearchInsideSection = remember(searchInsideResult, askAiEnabled, mediaEnabled) {
        searchInsideResult != null && (
            (askAiEnabled && searchInsideResult.aiApps.isNotEmpty()) ||
            (mediaEnabled && searchInsideResult.mediaApps.isNotEmpty())
        )
    }

    val recommendedActions = remember(actions, customOrder) {
        actions.filter { 
            it.id.startsWith("call") || 
            it.id.startsWith("sms") ||
            it.id.startsWith("email") || 
            it.id.startsWith("map") || 
            it.id.startsWith("gps") ||
            it.id.startsWith("copy_address") ||
            it.id.startsWith("url") || 
            it.id.startsWith("open_link") || 
            it.id.startsWith("reminder") ||
            it.id.startsWith("save_contact")
        }.sortedWith(compareBy(
            { action ->
                val baseId = action.id.substringBefore(":")
                val mappedId = if (baseId == "url") "open_link" else baseId
                val orderIndex = customOrder.indexOf(mappedId)
                if (orderIndex >= 0) orderIndex else Int.MAX_VALUE
            },
            { action -> -getActionPriority(action.id.substringBefore(":")) },
            { action -> actions.indexOf(action) }
        ))
    }
    var showSummarySheet by remember { mutableStateOf(false) }
    var showTranslateSheet by remember { mutableStateOf(false) }
    LaunchedEffect(autoTranslate) {
        if (autoTranslate) {
            showTranslateSheet = true
            onTranslateClick()
        }
    }
    var showAiFallbackSheet by remember { mutableStateOf(false) }
    var aiFallbackApp by remember { mutableStateOf<SearchInsideApp?>(null) }
    var aiFallbackQuery by remember { mutableStateOf("") }
    val chatApps = remember(text, context) { 
        Log.d("ChatApps", "Recomputing chatApps for text: $text")
        getInstalledChatApps(context, text) 
    }
    Log.d("ChatApps", "chatApps computed: ${chatApps.size} apps")
    val colors = LocalExpressiveColors.current
    var alwaysWebFallback by remember {
        mutableStateOf(settingsManager.getBoolean("ask_ai_fallback_web", false))
    }
    
    // Reload order when screen resumes (e.g., returning from Settings)
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                customOrder = settingsManager.getRecommendedActionOrder()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val talkBackEnabled = remember { isTalkBackEnabled(context) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsSpeaking by remember { mutableStateOf(false) }
    val smartReadUseCase = remember { SmartReadAloudUseCase() }
    var smartReadTts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isSmartReading by remember { mutableStateOf(false) }
    var smartReadSource by remember { mutableStateOf<String?>(null) }
    var smartReadContent by remember { mutableStateOf<String?>(null) }
    var showSmartReadSheet by remember { mutableStateOf(false) }
    var isSmartReadPreparing by remember { mutableStateOf(false) }
    var showFormatSheet by remember { mutableStateOf(false) }
    var isTextExpanded by remember { mutableStateOf(false) }
    // Full height of the text content; "Show more" only matters when it exceeds the collapsed box
    var textContentHeightPx by remember { mutableStateOf(0) }
    
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP ||
                event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE
            ) {
                stopTts(tts) { isTtsSpeaking = false }
                stopTts(smartReadTts) { isSmartReading = false }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stopTts(tts) { isTtsSpeaking = false }
            tts?.shutdown()
            tts = null
            stopTts(smartReadTts) { isSmartReading = false }
            smartReadTts?.shutdown()
            smartReadTts = null
        }
    }

    LaunchedEffect(summary) {
        if (summary !is SummaryResult.Success) {
            stopTts(tts) { isTtsSpeaking = false }
        }
    }

    // Smart Read Aloud: chỉ đọc phần text user chọn, dùng SmartReadAloudUseCase lọc nội dung chính (bỏ tag/metadata/râu ria)
    val smartReadAloud = fun() {
        if (text.trim().isBlank()) return
        showSmartReadSheet = true
    }

    LaunchedEffect(showSmartReadSheet) {
        if (!showSmartReadSheet) return@LaunchedEffect
        val selection = text.trim()
        if (selection.isBlank()) return@LaunchedEffect
        if (smartReadSource == selection && !smartReadContent.isNullOrBlank()) return@LaunchedEffect

        isSmartReadPreparing = true
        kotlin.runCatching {
            val result = withContext(Dispatchers.Default) {
                smartReadUseCase.execute(selection)
            }
            smartReadSource = selection
            smartReadContent = result.text.trim().ifBlank { null }
        }
        isSmartReadPreparing = false
    }

    val startSmartReadTts = fun(content: String) {
        val trimmed = content.trim()
        if (trimmed.isBlank()) return
        if (isSmartReading) {
            stopTts(smartReadTts) { isSmartReading = false }
            return
        }
        coroutineScope.launch {
            val locale = resolveTtsLocale(trimmed, Locale.getDefault())
            val appContext = context.applicationContext
            val existing = smartReadTts
            if (existing == null) {
                smartReadTts = TextToSpeech(appContext) { status ->
                    if (status != TextToSpeech.SUCCESS) {
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.tts_not_available),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        return@TextToSpeech
                    }
                    val engine = smartReadTts ?: return@TextToSpeech
                    val spoke = configureAndSpeakSmartReadTts(
                        tts = engine,
                        primaryLocale = locale,
                        fallbackLocale = Locale.getDefault(),
                        text = trimmed,
                        onStart = { isSmartReading = true },
                        onDone = { isSmartReading = false },
                        onError = { isSmartReading = false }
                    )
                    if (!spoke) {
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.tts_not_available),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                val spoke = configureAndSpeakSmartReadTts(
                    tts = existing,
                    primaryLocale = locale,
                    fallbackLocale = Locale.getDefault(),
                    text = trimmed,
                    onStart = { isSmartReading = true },
                    onDone = { isSmartReading = false },
                    onError = { isSmartReading = false }
                )
                if (!spoke) {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.tts_not_available),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    val readAloud = fun() {
        val selected = text.trim()
        if (selected.isBlank()) return
        if (isTtsSpeaking) {
            stopTts(tts) { isTtsSpeaking = false }
            return
        }
        coroutineScope.launch {
            val locale = resolveTtsLocale(selected, Locale.getDefault())
            val appContext = context.applicationContext
            val existing = tts
            if (existing == null) {
                tts = TextToSpeech(appContext) { status ->
                    if (status != TextToSpeech.SUCCESS) {
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.tts_not_available),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        return@TextToSpeech
                    }
                    val engine = tts ?: return@TextToSpeech
                    val spoke = configureAndSpeakReadAloudTts(
                        tts = engine,
                        locale = locale,
                        text = selected,
                        onStart = { isTtsSpeaking = true },
                        onDone = { isTtsSpeaking = false },
                        onError = { isTtsSpeaking = false }
                    )
                    if (!spoke) {
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.tts_not_available),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                val spoke = configureAndSpeakReadAloudTts(
                    tts = existing,
                    locale = locale,
                    text = selected,
                    onStart = { isTtsSpeaking = true },
                    onDone = { isTtsSpeaking = false },
                    onError = { isTtsSpeaking = false }
                )
                if (!spoke) {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.tts_not_available),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    val speakSummary = fun(summaryText: String) {
        val trimmed = summaryText.trim()
        if (trimmed.isBlank()) return
        if (isTtsSpeaking) {
            stopTts(tts) { isTtsSpeaking = false }
            return
        }
        coroutineScope.launch {
            val locale = resolveTtsLocale(trimmed, Locale.getDefault())
            val appContext = context.applicationContext
            val existing = tts
            if (existing == null) {
                tts = TextToSpeech(appContext) { status ->
                    if (status != TextToSpeech.SUCCESS) {
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.tts_not_available),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        return@TextToSpeech
                    }
                    val engine = tts ?: return@TextToSpeech
                    val spoke = configureAndSpeakTts(
                        tts = engine,
                        locale = locale,
                        text = trimmed,
                        onStart = { isTtsSpeaking = true },
                        onDone = { isTtsSpeaking = false },
                        onError = { isTtsSpeaking = false }
                    )
                    if (!spoke) {
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.tts_not_available),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                val spoke = configureAndSpeakTts(
                    tts = existing,
                    locale = locale,
                    text = trimmed,
                    onStart = { isTtsSpeaking = true },
                    onDone = { isTtsSpeaking = false },
                    onError = { isTtsSpeaking = false }
                )
                if (!spoke) {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.tts_not_available),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    val appBackground = MaterialTheme.colorScheme.background
    val cardBackground = elevatedCardColor(
        colorScheme = MaterialTheme.colorScheme
    ) // Item dùng surface để tương phản với nền
    val showImageControls = textState.inputSource == InputSource.CAMERA || textState.inputSource == InputSource.GALLERY
    
    if (!autoTranslate) ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        windowInsets = WindowInsets(0),
        scrimColor = Color.Transparent,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = appBackground,
        tonalElevation = 0.dp
    ) {
        val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val scrollState = rememberScrollState()
        val isDark = isSystemInDarkTheme()
        val scrollbarColor = if (isDark) Color(0xFF3DB89D) else MaterialTheme.colorScheme.primary
        val toolbarHaptic = LocalHapticFeedback.current

        // Short screens (landscape phones): keep the text block small so actions stay reachable
        val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val collapsedTextMax = if (isLandscape) 110.dp else 160.dp
        val expandedTextMax = if (isLandscape) 220.dp else 360.dp

        val wordCount = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
        val charCount = text.length

        // Content blocks, composed either stacked (portrait) or side by side (landscape)
        val titleBlock: @Composable () -> Unit = {
            // Tiêu đề: Image to Text
            Text(
                text = stringResource(R.string.app_name),
                style = TextStyle(
                    fontFamily = NunitoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    letterSpacing = MaterialTheme.typography.headlineMedium.letterSpacing,
                    fontSynthesis = FontSynthesis.Weight
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 4.dp, bottom = 12.dp)
            )

        }
        val inputBlock: @Composable () -> Unit = {
            // ─── PHẦN INPUT (REDESIGNED THEO HÌNH 2) ───────────────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(20.dp),
                color = cardBackground
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Văn bản hiển thị dạng cuộn kèm thanh cuộn màu xanh ngọc
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                            .heightIn(max = if (isTextExpanded) expandedTextMax else collapsedTextMax)
                            .drawWithContent {
                                drawContent()
                                if (scrollState.maxValue > 0) {
                                    val viewportHeight = size.height
                                    val totalHeight = viewportHeight + scrollState.maxValue
                                    val scrollbarHeight = ((viewportHeight / totalHeight) * viewportHeight).coerceAtLeast(24.dp.toPx())
                                    val scrollbarOffset = (scrollState.value.toFloat() / totalHeight) * viewportHeight

                                    drawRoundRect(
                                        color = scrollbarColor,
                                        topLeft = Offset(size.width - 4.dp.toPx(), scrollbarOffset),
                                        size = Size(4.dp.toPx(), scrollbarHeight),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                                    )
                                }
                            }
                            .verticalScroll(scrollState)
                    ) {
                        AnimatedContent(
                            targetState = text,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(180)) togetherWith
                                    fadeOut(animationSpec = tween(180))
                            },
                            label = "ocrTextFade"
                        ) { value ->
                            Text(
                                text = value,
                                style = TextStyle(
                                    fontFamily = NunitoFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 17.sp,
                                    lineHeight = 26.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                onTextLayout = { textContentHeightPx = it.size.height }
                            )
                        }
                    }

                    val collapsedMaxPx = with(androidx.compose.ui.platform.LocalDensity.current) { collapsedTextMax.toPx() }
                    val canExpand = textContentHeightPx > collapsedMaxPx

                    if (showImageControls || canExpand) {
                    Spacer(modifier = Modifier.height(20.dp))

                    // Hàng nút Re-capture & Show more (dạng pill)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showImageControls) {
                            OcrActionButton(
                                icon = if (textState.inputSource == InputSource.CAMERA) AppIcons.Camera else AppIcons.Refresh,
                                label = stringResource(
                                    if (textState.inputSource == InputSource.CAMERA) R.string.ocr_btn_recapture 
                                    else R.string.ocr_btn_rescan
                                ),
                                onClick = {
                                    toolbarHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onRescanClick()
                                }
                            )
                        }

                        if (canExpand) OcrActionButton(
                            icon = if (isTextExpanded) AppIcons.CaretUp else AppIcons.CaretDown,
                            label = stringResource(
                                if (isTextExpanded) R.string.ocr_btn_collapse 
                                else R.string.ocr_btn_expand
                            ),
                            onClick = {
                                toolbarHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isTextExpanded = !isTextExpanded
                            }
                        )
                    }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Thông tin đếm từ và ký tự (nằm riêng 1 dòng bên dưới nút, căn lề trái)
                    Text(
                        text = stringResource(R.string.word_char_count, wordCount, charCount),
                        style = TextStyle(
                            fontFamily = NunitoFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

        }
        val toolbarBlock: @Composable () -> Unit = {
            // Quick OCR Toolbar for fast 1-tap actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Sao chép
                QuickToolbarChip(
                    icon = AppIcons.Copy,
                    label = stringResource(R.string.ocr_quick_copy),
                    onClick = {
                        toolbarHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Text", text))
                        android.widget.Toast.makeText(context, context.getString(R.string.copied), android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
                // 2. Sửa ngắt dòng (Fix Broken Lines)
                QuickToolbarChip(
                    icon = AppIcons.MagicWand,
                    label = stringResource(R.string.ocr_quick_fix_lines),
                    onClick = {
                        toolbarHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSuggestionClick(com.freeup.textactions.formatting.FormatOption.FIX_BROKEN_LINES)
                        android.widget.Toast.makeText(context, context.getString(R.string.ocr_lines_fixed_toast), android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
                // 3. Dịch
                QuickToolbarChip(
                    icon = AppIcons.Translate,
                    label = stringResource(R.string.ocr_quick_translate),
                    onClick = {
                        toolbarHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showTranslateSheet = true
                        onTranslateClick()
                    }
                )
                // 4. Chia sẻ
                QuickToolbarChip(
                    icon = AppIcons.Share,
                    label = stringResource(R.string.ocr_quick_share),
                    onClick = {
                        toolbarHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.share_text)))
                    }
                )
                // 5. Lưu mẫu
                QuickToolbarChip(
                    icon = AppIcons.Bookmark,
                    label = stringResource(R.string.ocr_quick_save),
                    onClick = {
                        toolbarHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        coroutineScope.launch {
                            onSaveSnippet("", text)
                            android.widget.Toast.makeText(context, context.getString(R.string.saved_to_snippet), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

        }
        val actionsList: @Composable (Modifier) -> Unit = { listModifier ->
            LazyColumn(
                modifier = listModifier,
                state = listState
            ) {
                if (isLoading) {
                    item {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            color = colors.titleColor,
                            trackColor = colors.surfaceContainer
                        )
                    }
                }

                // SECTION 1: SMART SUGGESTIONS (Moved to Top)
                if (recommendedActions.isNotEmpty()) {
                    item { SectionHeader(title = stringResource(R.string.section_smart_suggestions)) }
                    itemsIndexed(recommendedActions) { index, item ->
                        val shape = when {
                            recommendedActions.size == 1 -> RoundedCornerShape(20.dp)
                            index == 0 -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 5.dp, bottomEnd = 5.dp)
                            index == recommendedActions.size - 1 -> RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp, topStart = 5.dp, topEnd = 5.dp)
                            else -> RoundedCornerShape(5.dp)
                        }
                        ActionRowItem(item = item, shape = shape, onClick = { onActionClick(item) })
                        if (index < recommendedActions.size - 1) {
                            HorizontalDivider(modifier = Modifier.fillMaxWidth(), thickness = 1.dp, color = MaterialTheme.colorScheme.background)
                        }
                    }
                    item { Spacer(modifier = Modifier.height(12.dp)) }
                }

                // SECTION 2: UTILITIES
                val utilityIds = setOf("copy", "share", "quick_share", "search", "summarize", "save_note", "create_qr", "translate", "tts", "smart_read_aloud", "format_text")
                val utilityActions = actions.filter { it.id.substringBefore(":") in utilityIds }
                
                val coreUtilityIds = listOf("copy", "share", "quick_share", "translate", "create_qr")
                val coreUtilities = actions.filter { it.id.substringBefore(":") in coreUtilityIds }
                
                if (coreUtilities.isNotEmpty() || true) {
                    item {
                        SectionHeader(title = stringResource(R.string.section_utilities))
                    }
                    
                    val copyAction = coreUtilities.find { it.id.substringBefore(":") == "copy" }
                    val otherCoreUtilities = coreUtilities.filter { it.id.substringBefore(":") != "copy" }
                    
                    // 1. Copy Action (if enabled)
                    if (copyAction != null) {
                        item {
                            ActionRowItem(
                                item = copyAction,
                                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 5.dp, bottomEnd = 5.dp),
                                isSmartReadActive = false,
                                isReadAloudActive = false,
                                onClick = { onActionClick(copyAction) }
                            )
                            HorizontalDivider(modifier = Modifier.fillMaxWidth(), thickness = 1.dp, color = MaterialTheme.colorScheme.background)
                        }
                    }
                    
                    // 2. Dynamic Save/Edit button (Always 2nd if Copy exists, or 1st if not)
                    if (!hideSaveAction) {
                        item {
                            val title = if (isSavedSnippet) stringResource(R.string.btn_edit_snippet_title) 
                                       else stringResource(R.string.btn_save_as_quick_snippet_title)
                            val desc = if (isSavedSnippet) stringResource(R.string.btn_edit_snippet_desc)
                                       else stringResource(R.string.btn_save_as_quick_snippet_desc)
                            val icon = if (isSavedSnippet) AppIcons.NotePencil else AppIcons.Save
                            
                            val shape = when {
                                copyAction == null -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 5.dp, bottomEnd = 5.dp)
                                otherCoreUtilities.isEmpty() -> RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp, topStart = 5.dp, topEnd = 5.dp)
                                else -> RoundedCornerShape(5.dp)
                            }
                            
                            ActionRowItem(
                                item = TextActionItem(
                                    id = "save_or_edit",
                                    title = title,
                                    subtitle = desc,
                                    icon = icon,
                                    action = {}
                                ),
                                shape = shape,
                                onClick = {
                                    coroutineScope.launch {
                                        if (isSavedSnippet) {
                                            val intent = Intent(context, EditNoteActivity::class.java).apply {
                                                putExtra("EXTRA_SNIPPET_ID", (context as? android.app.Activity)?.intent?.getLongExtra("EXTRA_SNIPPET_ID", -1L) ?: -1L)
                                                putExtra("EXTRA_CONTENT", text)
                                            }
                                            context.startActivity(intent)
                                        } else {
                                            onSaveSnippet("", text)
                                            android.widget.Toast.makeText(context, context.getString(R.string.saved_to_snippet), android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                        sheetState.hide()
                                        onDismiss()
                                    }
                                }
                            )
                            if (otherCoreUtilities.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.fillMaxWidth(), thickness = 1.dp, color = MaterialTheme.colorScheme.background)
                            }
                        }
                    }
                    
                    // 3. Other Core Utilities
                    itemsIndexed(otherCoreUtilities) { index, item ->
                        val isLast = index == otherCoreUtilities.size - 1
                        val shape = if (isLast) RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp, topStart = 5.dp, topEnd = 5.dp)
                                   else RoundedCornerShape(5.dp)
                                   
                        ActionRowItem(
                            item = item,
                            shape = shape,
                            onClick = {
                                when (item.id.substringBefore(":")) {
                                    "translate" -> {
                                        showTranslateSheet = true
                                        onTranslateClick()
                                    }
                                    else -> onActionClick(item)
                                }
                            }
                        )
                        if (!isLast) {
                            HorizontalDivider(modifier = Modifier.fillMaxWidth(), thickness = 1.dp, color = MaterialTheme.colorScheme.background)
                        }
                    }
                    item { Spacer(modifier = Modifier.height(12.dp)) }
                }

                // SECTION 3: ASK AI / MEDIA
                if (showSearchInsideSection) {
                    val result = searchInsideResult!!
                    item {
                        Column {
                            if (askAiEnabled && result.aiApps.isNotEmpty()) {
                                SectionHeader(title = stringResource(R.string.section_ask_ai))
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 24.dp)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    result.aiApps.forEach { app ->
                                        SearchInsideChip(
                                            app = app,
                                            onClick = { tryOpenSearchInsideApp(context, app, result.aiQuery) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            if (mediaEnabled && result.mediaApps.isNotEmpty()) {
                                SectionHeader(title = stringResource(R.string.section_search_media))
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 24.dp)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    result.mediaApps.forEach { app ->
                                        SearchInsideChip(
                                            app = app,
                                            onClick = { tryOpenSearchInsideApp(context, app, result.mediaQuery) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }

                // SECTION 4: OPEN CHAT
                if (chatApps.isNotEmpty()) {
                    item {
                        SectionHeader(title = stringResource(R.string.section_open_chat))
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            chatApps.forEach { app ->
                                ChatAppChip(app = app, onClick = { openChatApp(context, app) })
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // Remaining Utilities (Search, Summarize, etc.)
                val otherUtilityIds = setOf("search", "summarize", "tts", "smart_read_aloud", "format_text")
                val otherUtilities = utilityActions.filter { it.id.substringBefore(":") in otherUtilityIds }
                if (otherUtilities.isNotEmpty()) {
                    item { SectionHeader(title = stringResource(R.string.section_more_tools)) }
                    itemsIndexed(otherUtilities) { index, item ->
                        val shape = when {
                            otherUtilities.size == 1 -> RoundedCornerShape(20.dp)
                            index == 0 -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 5.dp, bottomEnd = 5.dp)
                            index == otherUtilities.size - 1 -> RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp, topStart = 5.dp, topEnd = 5.dp)
                            else -> RoundedCornerShape(5.dp)
                        }
                        ActionRowItem(
                            item = item,
                            shape = shape,
                            isSmartReadActive = item.id.substringBefore(":") == "smart_read_aloud" && isSmartReading,
                            isReadAloudActive = item.id.substringBefore(":") == "tts" && isTtsSpeaking,
                            onClick = {
                                when (item.id.substringBefore(":")) {
                                    "summarize" -> { showSummarySheet = true; onSummarizeClick() }
                                    "format_text" -> { showFormatSheet = true; onOpenFormatPanel() }
                                    "smart_read_aloud" -> {
                                        if (isSmartReading) {
                                            stopTts(smartReadTts) { isSmartReading = false }
                                        } else {
                                            showSmartReadSheet = true
                                        }
                                    }
                                    "tts" -> {
                                        readAloud()
                                    }
                                    else -> onActionClick(item)
                                }
                            }
                        )
                        if (index < otherUtilities.size - 1) {
                            HorizontalDivider(modifier = Modifier.fillMaxWidth(), thickness = 1.dp, color = MaterialTheme.colorScheme.background)
                        }
                    }
                }
            }
        }

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = navBarPadding)
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.46f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    titleBlock()
                    inputBlock()
                    toolbarBlock()
                }
                actionsList(Modifier.weight(0.54f).fillMaxHeight())
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = navBarPadding)
            ) {
                titleBlock()
                inputBlock()
                toolbarBlock()
                actionsList(Modifier.fillMaxWidth())
            }
        }
    }


    // ─── Format Text Sheet ───────────────────────────────────────────────────────
    if (showFormatSheet) {
        FormatTextBottomSheet(
            text = text,
            formatUiState = formatUiState,
            onOptionSelected = { option ->
                onFormatOptionSelected(option)
            },
            onApply = {
                onApplyFormat()
                showFormatSheet = false
            },
            onDismissPreview = {
                onDismissFormatPreview()
            },
            onDismiss = {
                showFormatSheet = false
                onCloseFormatPanel()
            }
        )
    }

    if (showSmartReadSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showSmartReadSheet = false
                if (isSmartReading) stopTts(smartReadTts) { isSmartReading = false }
            },
            sheetState = rememberModalBottomSheetState(),
            scrimColor = Color.Transparent,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .padding(bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.smart_read_aloud_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = {
                        showSmartReadSheet = false
                        if (isSmartReading) stopTts(smartReadTts) { isSmartReading = false }
                    }) {
                        Icon(AppIcons.Close, contentDescription = stringResource(R.string.close))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.smart_read_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(16.dp))

                when {
                    isSmartReadPreparing -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(stringResource(R.string.smart_read_filtering), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    !smartReadContent.isNullOrBlank() -> {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 280.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                        ) {
                            Box(
                                modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                            ) {
                                Text(
                                    text = smartReadContent!!,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 24.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (isSmartReading) {
                                        stopTts(smartReadTts) { isSmartReading = false }
                                    } else {
                                        startSmartReadTts(smartReadContent!!)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (isSmartReading) AppIcons.Pause else AppIcons.Play,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isSmartReading) stringResource(R.string.smart_read_pause) else stringResource(R.string.smart_read_play))
                            }
                        }
                    }
                    else -> {
                        Text(
                            text = stringResource(R.string.smart_read_no_content),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showTranslateSheet) {
        // Check if translation models are already downloaded
        var modelsReady by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            try {
                val modelManager = com.google.mlkit.common.model.RemoteModelManager.getInstance()
                val models = modelManager
                    .getDownloadedModels(com.google.mlkit.nl.translate.TranslateRemoteModel::class.java)
                    .await()
                modelsReady = models.size >= 2 // at least source + target downloaded
            } catch (_: Exception) { }
        }

        ModalBottomSheet(
            onDismissRequest = {
                showTranslateSheet = false
                onClearTranslate()
                onClearTranslatedSummary()
                if (autoTranslate) onDismiss()
            },
            sheetState = rememberModalBottomSheetState(),
            windowInsets = WindowInsets(0),
            scrimColor = Color.Transparent,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp)
                    .padding(bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.translate_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = {
                        showTranslateSheet = false
                        onClearTranslate()
                        onClearTranslatedSummary()
                        if (autoTranslate) onDismiss()
                    }) {
                        Icon(AppIcons.Close, contentDescription = stringResource(R.string.close))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                when {
                    isTranslating -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (modelsReady) stringResource(R.string.translating) else stringResource(R.string.downloading_model),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    translation != null -> {
                        TranslationCard(
                            result = translation,
                            translatedSummary = translatedSummary,
                            translatedSummarySource = translatedSummarySource,
                            isSummarizingTranslation = isSummarizingTranslation,
                            selectedTargetLanguage = selectedTargetLanguage,
                            onSummarizeTranslation = onSummarizeTranslation,
                            onCopy = {
                                if (translation is TranslationResult.Success) {
                                    onCopyTranslate(translation.text)
                                }
                            },
                            onClose = {
                                showTranslateSheet = false
                                onClearTranslate()
                                onClearTranslatedSummary()
                                if (autoTranslate) onDismiss()
                            },
                            onLanguageChange = onChangeTargetLanguage,
                            onDownloadModels = onDownloadTranslationModels
                        )
                    }
                }
            }
        }
    }

    if (showAiFallbackSheet && aiFallbackApp != null) {
        ModalBottomSheet(
            onDismissRequest = { showAiFallbackSheet = false },
            sheetState = rememberModalBottomSheetState(),
            scrimColor = Color.Transparent,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .padding(bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.Robot,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.app_not_installed_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.app_not_installed_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.open_in_browser)) },
                    supportingContent = { Text(stringResource(R.string.open_web_version, aiFallbackApp?.label ?: "")) },
                    leadingContent = {
                        Icon(
                            imageVector = AppIcons.Globe,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            aiFallbackApp?.let { openAiWeb(context, it, aiFallbackQuery) }
                            showAiFallbackSheet = false
                        }
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.install_app)) },
                    leadingContent = {
                        Icon(
                            imageVector = AppIcons.Download,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            aiFallbackApp?.let { openAiInstall(context, it) }
                            showAiFallbackSheet = false
                        }
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.choose_another_ai)) },
                    leadingContent = {
                        Icon(
                            imageVector = AppIcons.ArrowsLeftRight,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAiFallbackSheet = false }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            alwaysWebFallback = !alwaysWebFallback
                            settingsManager.setBoolean("ask_ai_fallback_web", alwaysWebFallback)
                        }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = alwaysWebFallback,
                        onCheckedChange = {
                            alwaysWebFallback = it
                            settingsManager.setBoolean("ask_ai_fallback_web", it)
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.always_open_web),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                TextButton(
                    onClick = { showAiFallbackSheet = false },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }

    if (showSummarySheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showSummarySheet = false
                onClearSummary()
                stopTts(tts) { isTtsSpeaking = false }
            },
            sheetState = rememberModalBottomSheetState(),
            scrimColor = Color.Transparent,
            containerColor = appBackground,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.summary_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.summary_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(12.dp))

                when {
                    isSummarizing -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.summarizing), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    summary is SummaryResult.Success -> {
                        val summaryText = summary.text.trim()
                        val showReadAloud = summaryText.isNotBlank() && !talkBackEnabled
                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 22.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (showReadAloud) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onCopySummary(summaryText) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.copy))
                                }
                                OutlinedButton(
                                    onClick = { speakSummary(summaryText) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (isTtsSpeaking) {
                                            AppIcons.Stop
                                        } else {
                                            AppIcons.Volume
                                        },
                                        contentDescription = null,
                                        tint = Color(0xFF6750A4)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (isTtsSpeaking) stringResource(R.string.stop) else stringResource(R.string.read_aloud))
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onCopySummary(summaryText) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.copy))
                            }
                        }
                    }
                    summary is SummaryResult.Error -> {
                        Text(
                            text = summary.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}


private fun isTalkBackEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    return manager?.isEnabled == true && manager.isTouchExplorationEnabled
}

private suspend fun resolveTtsLocale(text: String, fallback: Locale): Locale {
    val languageIdentifier = LanguageIdentification.getClient()
    val languageTag = try {
        languageIdentifier.identifyLanguage(text).await()
    } catch (_: Exception) {
        "und"
    }
    return if (languageTag == "und") fallback else Locale.forLanguageTag(languageTag)
}

private fun configureAndSpeakTts(
    tts: TextToSpeech,
    locale: Locale,
    text: String,
    onStart: () -> Unit,
    onDone: () -> Unit,
    onError: () -> Unit
): Boolean {
    val langResult = tts.setLanguage(locale)
    if (langResult == TextToSpeech.LANG_MISSING_DATA ||
        langResult == TextToSpeech.LANG_NOT_SUPPORTED
    ) {
        onError()
        return false
    }
    tts.setSpeechRate(1.0f)
    tts.setPitch(1.0f)
    val handler = Handler(Looper.getMainLooper())
    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            handler.post { onStart() }
        }

        override fun onDone(utteranceId: String?) {
            handler.post { onDone() }
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onError(utteranceId: String?) {
            handler.post { onError() }
        }
    })
    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "summary_tts")
    return true
}

private fun configureAndSpeakReadAloudTts(
    tts: TextToSpeech,
    locale: Locale,
    text: String,
    onStart: () -> Unit,
    onDone: () -> Unit,
    onError: () -> Unit
): Boolean {
    val langResult = tts.setLanguage(locale)
    if (langResult == TextToSpeech.LANG_MISSING_DATA ||
        langResult == TextToSpeech.LANG_NOT_SUPPORTED
    ) {
        onError()
        return false
    }
    tts.setSpeechRate(0.95f)
    tts.setPitch(1.0f)
    val handler = Handler(Looper.getMainLooper())
    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            handler.post { onStart() }
        }

        override fun onDone(utteranceId: String?) {
            handler.post { onDone() }
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onError(utteranceId: String?) {
            handler.post { onError() }
        }
    })
    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "read_aloud_tts")
    return true
}

private fun stopTts(tts: TextToSpeech?, onStopped: () -> Unit) {
    if (tts == null) return
    tts.stop()
    onStopped()
}

private fun isValidLanguageTag(tag: String): Boolean {
    val cleaned = tag.trim()
    return cleaned.isNotEmpty() && cleaned.lowercase() != "und"
}

private fun configureAndSpeakTranslationTts(
    tts: TextToSpeech,
    primaryLocale: Locale,
    fallbackLocale: Locale,
    text: String,
    onStart: () -> Unit,
    onDone: () -> Unit,
    onError: () -> Unit
): Boolean {
    val primaryResult = tts.setLanguage(primaryLocale)
    if (primaryResult == TextToSpeech.LANG_MISSING_DATA ||
        primaryResult == TextToSpeech.LANG_NOT_SUPPORTED
    ) {
        val fallbackResult = tts.setLanguage(fallbackLocale)
        if (fallbackResult == TextToSpeech.LANG_MISSING_DATA ||
            fallbackResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            onError()
            return false
        }
    }
    tts.setSpeechRate(0.95f)
    tts.setPitch(1.0f)
    val handler = Handler(Looper.getMainLooper())
    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            handler.post { onStart() }
        }

        override fun onDone(utteranceId: String?) {
            handler.post { onDone() }
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onError(utteranceId: String?) {
            handler.post { onError() }
        }
    })
    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "translation_tts")
    return true
}

private fun configureAndSpeakSmartReadTts(
    tts: TextToSpeech,
    primaryLocale: Locale,
    fallbackLocale: Locale,
    text: String,
    onStart: () -> Unit,
    onDone: () -> Unit,
    onError: () -> Unit
): Boolean {
    val primaryResult = tts.setLanguage(primaryLocale)
    if (primaryResult == TextToSpeech.LANG_MISSING_DATA ||
        primaryResult == TextToSpeech.LANG_NOT_SUPPORTED
    ) {
        val fallbackResult = tts.setLanguage(fallbackLocale)
        if (fallbackResult == TextToSpeech.LANG_MISSING_DATA ||
            fallbackResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            onError()
            return false
        }
    }
    tts.setSpeechRate(0.95f)
    tts.setPitch(1.0f)
    val handler = Handler(Looper.getMainLooper())
    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            handler.post { onStart() }
        }

        override fun onDone(utteranceId: String?) {
            handler.post { onDone() }
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onError(utteranceId: String?) {
            handler.post { onError() }
        }
    })
    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "smart_read_tts")
    return true
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun TranslationCard(
    result: TranslationResult,
    translatedSummary: SummaryResult?,
    translatedSummarySource: String?,
    isSummarizingTranslation: Boolean,
    selectedTargetLanguage: String?,
    onSummarizeTranslation: (String) -> Unit,
    onCopy: () -> Unit,
    onClose: () -> Unit,
    onLanguageChange: (String) -> Unit,
    onDownloadModels: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsSpeaking by remember { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }
    var localeSupported by remember { mutableStateOf(false) }
    var showLanguageSelector by remember { mutableStateOf(false) }

    val targetLangTag = selectedTargetLanguage
        ?: (if (result is TranslationResult.Success) result.targetLang else "")
    val targetLocale = if (isValidLanguageTag(targetLangTag)) {
        Locale.forLanguageTag(targetLangTag)
    } else {
        Locale.getDefault()
    }
    val translatedText = if (result is TranslationResult.Success) result.text.trim() else ""
    val summaryMatches = translatedSummarySource == translatedText && translatedSummary != null
    // Ẩn icon Summarize trong bottom sheet Dịch
    val showSummaryAction = false
    val showSummaryOutput = summaryMatches && translatedSummary is SummaryResult.Success

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP ||
                event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE
            ) {
                stopTts(tts) { isTtsSpeaking = false }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stopTts(tts) { isTtsSpeaking = false }
            tts?.shutdown()
            tts = null
        }
    }

    LaunchedEffect(result) {
        if (result !is TranslationResult.Success || result.text.trim().isBlank()) {
            stopTts(tts) { isTtsSpeaking = false }
            localeSupported = false
            ttsReady = false
            return@LaunchedEffect
        }
        if (!isValidLanguageTag(targetLangTag)) {
            localeSupported = false
            ttsReady = false
            return@LaunchedEffect
        }
        val appContext = context.applicationContext
        if (tts == null) {
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true
                    val availability = tts?.isLanguageAvailable(targetLocale)
                        ?: TextToSpeech.LANG_NOT_SUPPORTED
                    localeSupported = availability >= TextToSpeech.LANG_AVAILABLE
                } else {
                    ttsReady = false
                    localeSupported = false
                }
            }
        } else {
            ttsReady = true
            val availability = tts?.isLanguageAvailable(targetLocale)
                ?: TextToSpeech.LANG_NOT_SUPPORTED
            localeSupported = availability >= TextToSpeech.LANG_AVAILABLE
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_translate),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.offline_translation),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                // Action Buttons for card - Speaker first, then Copy
                if (result is TranslationResult.Success &&
                    result.text.trim().isNotBlank() &&
                    localeSupported
                ) {
                    IconButton(
                        onClick = {
                            val text = result.text.trim()
                            if (!ttsReady || !localeSupported) {
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.tts_not_supported_language),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                return@IconButton
                            }
                            if (isTtsSpeaking) {
                                stopTts(tts) { isTtsSpeaking = false }
                                return@IconButton
                            }
                            val engine = tts
                            if (engine == null) {
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.tts_not_supported_language),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                return@IconButton
                            }
                            coroutineScope.launch {
                                val spoke = configureAndSpeakTranslationTts(
                                    tts = engine,
                                    primaryLocale = targetLocale,
                                    fallbackLocale = Locale.getDefault(),
                                    text = text,
                                    onStart = { isTtsSpeaking = true },
                                    onDone = { isTtsSpeaking = false },
                                    onError = { isTtsSpeaking = false }
                                )
                                if (!spoke) {
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(R.string.tts_not_supported_language),
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isTtsSpeaking) {
                                AppIcons.Stop
                            } else {
                                AppIcons.Volume
                            },
                            contentDescription = stringResource(R.string.read_aloud),
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                FilledIconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = AppIcons.Copy,
                        contentDescription = stringResource(R.string.copy),
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (showSummaryAction) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { onSummarizeTranslation(translatedText) },
                        enabled = !isSummarizingTranslation,
                        modifier = Modifier.size(24.dp)
                    ) {
                        if (isSummarizingTranslation) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF6750A4)
                            )
                        } else {
                            Icon(
                                imageVector = AppIcons.Sparkle,
                                contentDescription = stringResource(R.string.btn_summarize),
                                tint = Color(0xFF6750A4),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Language selector row
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLanguageSelector = true },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.translate_to_label, getLanguageName(targetLangTag)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "▼",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            if (!isValidLanguageTag(targetLangTag)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.Translate,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.4f)
                    )
                    Text(
                        text = stringResource(R.string.translate_select_language_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else when (result) {
                is TranslationResult.Success -> {
                    Text(
                        text = translatedText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (showSummaryOutput) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.key_sentences),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = (translatedSummary as SummaryResult.Success).text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            lineHeight = 22.sp
                        )
                    }
                }
                is TranslationResult.ModelsMissing -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.translate_models_needed,
                                result.missingLangs.joinToString(", ") { getLanguageName(it) }
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = stringResource(R.string.translate_models_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                        Button(
                            onClick = onDownloadModels,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.translate_models_download))
                        }
                    }
                }
                is TranslationResult.Error -> {
                    // Check if this is a download/loading error
                    val isDownloading = result.message.contains("download", ignoreCase = true) ||
                                       result.message.contains("waiting", ignoreCase = true) ||
                                       result.message.contains("module", ignoreCase = true)

                    if (isDownloading) {
                        // Show professional loading UI for downloads
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(40.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.downloading_model),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(R.string.downloading_model_wait),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    } else {
                        // Show regular error
                        Text(
                            text = result.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    // Language selector dialog
    if (showLanguageSelector) {
        val allLanguages = getSupportedLanguages()

        // Query downloaded translation models
        var downloadedLangs by remember { mutableStateOf<Set<String>>(emptySet()) }
        LaunchedEffect(Unit) {
            val modelManager = com.google.mlkit.common.model.RemoteModelManager.getInstance()
            try {
                val models = modelManager
                    .getDownloadedModels(com.google.mlkit.nl.translate.TranslateRemoteModel::class.java)
                    .await()
                downloadedLangs = models.map { model -> model.language }.toSet()
            } catch (_: Exception) { }
        }

        val downloadedLanguages = allLanguages.filter { it.code in downloadedLangs }.sortedBy { it.displayName }
        val otherLanguages = allLanguages.filter { it.code !in downloadedLangs }.sortedBy { it.displayName }

        AlertDialog(
            onDismissRequest = { showLanguageSelector = false },
            title = {
                Text(
                    text = stringResource(R.string.translate_to_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                ) {
                    // Downloaded languages section
                    if (downloadedLanguages.isNotEmpty()) {
                        item(key = "header_downloaded") {
                            Text(
                                text = stringResource(R.string.lang_section_downloaded),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                            )
                        }
                        items(downloadedLanguages, key = { it.code }) { language ->
                            LanguageRow(
                                language = language,
                                isSelected = language.code == targetLangTag,
                                isDownloaded = true,
                                onClick = {
                                    onLanguageChange(language.code)
                                    showLanguageSelector = false
                                }
                            )
                        }
                        item(key = "divider") {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    // Other languages section
                    item(key = "header_all") {
                        Text(
                            text = stringResource(R.string.lang_section_all),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                        )
                    }
                    items(otherLanguages, key = { it.code }) { language ->
                        LanguageRow(
                            language = language,
                            isSelected = language.code == targetLangTag,
                            isDownloaded = false,
                            onClick = {
                                onLanguageChange(language.code)
                                showLanguageSelector = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageSelector = false }) {
                    Text(stringResource(R.string.cancel), fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}

// Helper function to get language name
private fun getLanguageName(langTag: String): String {
    // Get from the full list or fallback to Locale
    return getSupportedLanguages()
        .find { it.code == langTag }
        ?.displayName
        ?.substringBefore(" (") // Remove native name for display
        ?: Locale.forLanguageTag(langTag).displayLanguage.ifEmpty { "Unknown" }
}

// Data class for language
private data class Language(val code: String, val displayName: String)

@Composable
private fun LanguageRow(
    language: Language,
    isSelected: Boolean,
    isDownloaded: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = language.displayName,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        if (isDownloaded) {
            Icon(
                imageVector = AppIcons.CheckCircle,
                contentDescription = stringResource(R.string.lang_section_downloaded),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
        }
    }
}

// Frequently used languages
private fun getFrequentLanguages(): List<Language> {
    return listOf(
        Language("en", "English"),
        Language("vi", "Vietnamese (Tiếng Việt)"),
        Language("zh", "Chinese (中文)"),
        Language("ja", "Japanese (日本語)"),
        Language("ko", "Korean (한국어)"),
        Language("es", "Spanish (Español)"),
        Language("fr", "French (Français)"),
        Language("de", "German (Deutsch)"),
        Language("it", "Italian (Italiano)"),
        Language("pt", "Portuguese (Português)"),
        Language("ru", "Russian (Русский)"),
        Language("th", "Thai (ไทย)"),
        Language("id", "Indonesian (Bahasa Indonesia)"),
        Language("ar", "Arabic (العربية)"),
        Language("hi", "Hindi (हिन्दी)")
    )
}

// Other languages (alphabetical)
private fun getOtherLanguages(): List<Language> {
    return listOf(
        Language("af", "Afrikaans"),
        Language("be", "Belarusian (Беларуская)"),
        Language("bn", "Bengali (বাংলা)"),
        Language("bg", "Bulgarian (Български)"),
        Language("ca", "Catalan (Català)"),
        Language("cs", "Czech (Čeština)"),
        Language("da", "Danish (Dansk)"),
        Language("nl", "Dutch (Nederlands)"),
        Language("eo", "Esperanto"),
        Language("et", "Estonian (Eesti)"),
        Language("fi", "Finnish (Suomi)"),
        Language("gl", "Galician (Galego)"),
        Language("ka", "Georgian (ქართული)"),
        Language("el", "Greek (Ελληνικά)"),
        Language("gu", "Gujarati (ગુજરાતી)"),
        Language("ht", "Haitian Creole"),
        Language("he", "Hebrew (עברית)"),
        Language("hu", "Hungarian (Magyar)"),
        Language("is", "Icelandic (Íslenska)"),
        Language("ga", "Irish (Gaeilge)"),
        Language("kn", "Kannada (ಕನ್ನಡ)"),
        Language("lt", "Lithuanian (Lietuvių)"),
        Language("lv", "Latvian (Latviešu)"),
        Language("mk", "Macedonian (Македонски)"),
        Language("mr", "Marathi (मराठी)"),
        Language("ms", "Malay (Bahasa Melayu)"),
        Language("mt", "Maltese (Malti)"),
        Language("no", "Norwegian (Norsk)"),
        Language("fa", "Persian (فارسی)"),
        Language("pl", "Polish (Polski)"),
        Language("ro", "Romanian (Română)"),
        Language("sk", "Slovak (Slovenčina)"),
        Language("sl", "Slovenian (Slovenščina)"),
        Language("sv", "Swedish (Svenska)"),
        Language("sw", "Swahili (Kiswahili)"),
        Language("tl", "Tagalog"),
        Language("ta", "Tamil (தமிழ்)"),
        Language("te", "Telugu (తెలుగు)"),
        Language("tr", "Turkish (Türkçe)"),
        Language("uk", "Ukrainian (Українська)"),
        Language("ur", "Urdu (اردو)"),
        Language("cy", "Welsh (Cymraeg)")
    ).sortedBy { it.displayName }
}

// Get all supported languages (for lookup)
private fun getSupportedLanguages(): List<Language> {
    return getFrequentLanguages() + getOtherLanguages()
}

@Composable
private fun OcrActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimaryAccent: Boolean = false
) {
    val isDark = isSystemInDarkTheme()
    val containerColor = if (isPrimaryAccent) {
        if (isDark) Color(0xFF172924) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        if (isDark) Color(0xFF45574F) else MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = if (isPrimaryAccent) {
        MaterialTheme.colorScheme.primary
    } else {
        if (isDark) Color(0xFFE1EAE6) else MaterialTheme.colorScheme.onSurfaceVariant
    }
    val iconColor = contentColor
    val borderStroke = if (isPrimaryAccent) {
        BorderStroke(1.dp, if (isDark) Color(0xFF2E7463) else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    } else {
        null
    }

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = containerColor,
        border = borderStroke
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = NunitoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                ),
                color = contentColor
            )
        }
    }
}

@Composable
private fun QuickToolbarChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val chipBackground = elevatedCardColor(colorScheme)
    val iconColor = colorScheme.primary

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = chipBackground
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = NunitoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                ),
                color = colorScheme.onSurface
            )
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: Boolean = false) {
    val colors = LocalExpressiveColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        if (icon) {
            Icon(
                painter = painterResource(id = R.drawable.ic_check_circle),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = colors.goldColor
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun ActionIcon(icon: Any, tint: Color, modifier: Modifier = Modifier) {
    when (icon) {
        is Int -> Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            modifier = modifier,
            tint = tint
        )
        is ImageVector -> Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = modifier,
            tint = tint
        )
    }
}

data class ActionColors(
    val iconColor: Color,
    val backgroundColor: Color
)

private data class ChatApp(
    val id: String,
    val label: String,
    val packageName: String,
    val color: Color,
    val phone: String,
    val uriBuilder: (String) -> String,
    val fallbackUriBuilder: ((String) -> String)? = null
)

private enum class SearchIntent {
    KNOWLEDGE,
    MEDIA,
    AMBIGUOUS
}

private data class SearchInsideApp(
    val id: String,
    val label: String,
    val packageName: String,
    val color: Color,
    val group: SearchIntent,
    val intentBuilder: (String) -> Intent,
    val webUrlBuilder: ((String) -> String)? = null
)

private data class SearchInsideResult(
    val aiQuery: String,
    val mediaQuery: String,
    val aiApps: List<SearchInsideApp>,
    val mediaApps: List<SearchInsideApp>,
    val anyAvailable: Boolean
)

fun getActionColors(actionId: String): ActionColors {
    val id = actionId.lowercase().substringBefore(":")
    return when {
        id.contains("reminder") || id.contains("calendar") -> ActionColors(
            iconColor = Color(0xFFD32F2F),
            backgroundColor = Color(0xFFFFCDD2) // Đậm hơn: từ 0xFFFFEBEE
        )
        id.contains("gps") -> ActionColors(
            iconColor = Color(0xFF00897B),
            backgroundColor = Color(0xFFB2DFDB)
        )
        id.contains("map") || id.contains("direction") -> ActionColors(
            iconColor = Color(0xFF2E7D32),
            backgroundColor = Color(0xFFC8E6C9) // Đậm hơn: từ 0xFFE8F5E9
        )
        id.contains("call") || id.contains("sms") || id.contains("zalo") -> ActionColors(
            iconColor = Color(0xFF0288D1),
            backgroundColor = Color(0xFFB3E5FC) // Đậm hơn: từ 0xFFE1F5FE
        )
        id.contains("save_contact") || id.contains("contact") -> ActionColors(
            iconColor = Color(0xFF0097A7),
            backgroundColor = Color(0xFFB2DFDB) // Đậm hơn: từ 0xFFE0F7FA
        )
        id.contains("email") -> ActionColors(
            iconColor = Color(0xFF5C6BC0),
            backgroundColor = Color(0xFFC5CAE9) // Đậm hơn: từ 0xFFE8EAF6
        )
        id.contains("translate") ||
            id.contains("qr") ||
            id.contains("summarize") ||
            id.contains("smart_read_aloud") -> ActionColors(
            iconColor = Color(0xFF6750A4),
            backgroundColor = Color(0xFFE1BEE7) // Đậm hơn: từ 0xFFF3E5F5
        )
        id.contains("shopping") || id.contains("ecommerce") -> ActionColors(
            iconColor = Color(0xFFF57C00),
            backgroundColor = Color(0xFFFFE0B2) // Đậm hơn: từ 0xFFFFF3E0
        )
        else -> ActionColors(
            iconColor = Color(0xFF00897B), // Default Teal
            backgroundColor = Color(0xFFB2DFDB) // Đậm hơn: từ 0xFFE0F7FA
        )
    }
}

@Composable
private fun ChatAppChip(app: ChatApp, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val chipBackground = elevatedCardColor(colorScheme)
    val mutedIconColor = lerp(app.color, chipBackground, 0.5f)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = chipBackground,
        tonalElevation = 0.dp,
        modifier = Modifier
            .height(48.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = mutedIconColor,
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = AppIcons.Chat,
                        contentDescription = app.label,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                ),
                color = colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SearchInsideChip(app: SearchInsideApp, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val chipBackground = elevatedCardColor(colorScheme)
    val mutedIconColor = lerp(app.color, chipBackground, 0.5f)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = chipBackground,
        tonalElevation = 0.dp,
        modifier = Modifier
            .height(48.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = mutedIconColor,
                modifier = Modifier.size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (app.group == SearchIntent.MEDIA) AppIcons.Play else AppIcons.Sparkle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                ),
                color = colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun FormatSuggestionChip(label: String, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val chipBackground = elevatedCardColor(colorScheme)
    val iconColor = lerp(colorScheme.primary, chipBackground, 0.4f)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = chipBackground,
        tonalElevation = 0.dp,
        modifier = Modifier
            .height(48.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = iconColor,
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = AppIcons.MagicWand,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                ),
                color = colorScheme.onSurface
            )
        }
    }
}

private fun getInstalledChatApps(context: android.content.Context, text: String): List<ChatApp> {
    // Extract phone from selected text: nếu text chứa chữ (vd "Call 0987654321") thì lấy số từ TextAnalyzer
    val phoneValue = TextAnalyzer().analyze(text)
        .filter { it.type == TextType.PHONE_NUMBER }
        .firstOrNull()?.value ?: text
    val phoneResult = PhoneClassifier.detectPhone(phoneValue)
    if (!phoneResult.isPhoneNumber || phoneResult.phoneType == PhoneType.VN_LANDLINE) {
        return emptyList()
    }
    val normalized = phoneResult.normalizedNumber ?: return emptyList()

    // Zalo: chỉ VN_MOBILE, URL https://zalo.me/0xxxxxxxxx (bỏ +84, đổi thành 0)
    val zaloPhone = if (phoneResult.phoneType == PhoneType.VN_MOBILE && normalized.startsWith("+84")) {
        "0" + normalized.drop(3)
    } else null
    // WhatsApp / Telegram: dạng quốc tế không dấu + (e.g. 84987654321)
    val waTelegramPhone = normalized.replace("+", "")

    val candidates = buildList {
        if (phoneResult.phoneType == PhoneType.VN_MOBILE && zaloPhone != null) {
            add(ChatApp(
                id = "zalo",
                label = context.getString(R.string.chat_on_zalo),
                packageName = "com.zing.zalo",
                color = Color(0xFF2C6BED),
                phone = zaloPhone,
                uriBuilder = { "https://zalo.me/$zaloPhone" }
            ))
        }
        add(ChatApp(
            id = "whatsapp",
            label = context.getString(R.string.chat_on_whatsapp),
            packageName = "com.whatsapp",
            color = Color(0xFF25D366),
            phone = waTelegramPhone,
            uriBuilder = { "https://wa.me/$waTelegramPhone" }
        ))
        add(ChatApp(
            id = "telegram",
            label = context.getString(R.string.chat_on_telegram),
            packageName = "org.telegram.messenger",
            color = Color(0xFF0088CC),
            phone = waTelegramPhone,
            uriBuilder = { "https://t.me/$waTelegramPhone" }
        ))
    }

    val installed = candidates.filter { isAppInstalled(context, it.packageName) }.toMutableList()

    // Messenger: một số máy dùng com.facebook.orca thay vì com.facebook.messenger
    val messengerPkg = when {
        isAppInstalled(context, "com.facebook.messenger") -> "com.facebook.messenger"
        isAppInstalled(context, "com.facebook.orca") -> "com.facebook.orca"
        else -> null
    }
    if (messengerPkg != null && installed.none { it.id == "messenger" }) {
        installed.add(ChatApp(
            id = "messenger",
            label = context.getString(R.string.chat_on_messenger),
            packageName = messengerPkg,
            color = Color(0xFF0084FF),
            phone = waTelegramPhone,
            uriBuilder = { "https://m.me/$waTelegramPhone" }
        ))
    }

    return installed
}

private fun shouldShowChatSection(text: String, detections: List<Detection>, phone: String): Boolean {
    Log.d("ChatApps", "shouldShowChatSection: text='$text', detections=${detections.size}")
    val cleaned = text.trim()
    if (cleaned.isBlank()) {
        Log.d("ChatApps", "shouldShowChatSection: text is blank")
        return false
    }
    if (cleaned.length > 150) {
        Log.d("ChatApps", "shouldShowChatSection: text too long (${cleaned.length})")
        return false
    }

    Log.d("ChatApps", "shouldShowChatSection: phone=$phone")

    // If a phone number is present, always show chat section even if text includes email/address.
    Log.d("ChatApps", "shouldShowChatSection: returning true (phone present)")
    return true
}

private fun extractPhoneFromText(text: String): String? {
    val digitsOnly = text.replace(Regex("[^0-9]"), "")
    if (digitsOnly.isBlank()) return null
    val normalized = when {
        digitsOnly.startsWith("84") && digitsOnly.length >= 11 -> "0" + digitsOnly.substring(2)
        else -> digitsOnly
    }
    return if (normalized.length in 9..11) normalized else null
}

private fun extractChatPhone(detections: List<Detection>): String? {
    Log.d("ChatApps", "extractChatPhone: detections=${detections.size}")
    val phoneDetections = detections.filter { it.type == TextType.PHONE_NUMBER }
    Log.d("ChatApps", "extractChatPhone: phoneDetections=${phoneDetections.size}")
    
    val candidates = phoneDetections.mapNotNull { detection ->
        Log.d("ChatApps", "extractChatPhone: processing detection value='${detection.value}'")
        // Normalize phone number: remove non-digits
        val digitsOnly = detection.value.replace(Regex("[^0-9]"), "")
        Log.d("ChatApps", "extractChatPhone: digitsOnly='$digitsOnly'")
        if (digitsOnly.isEmpty()) {
            Log.d("ChatApps", "extractChatPhone: digitsOnly is empty")
            return@mapNotNull null
        }
        
        // Remove country code if present (84)
        val normalized = if (digitsOnly.startsWith("84") && digitsOnly.length == 11) {
            val result = "0" + digitsOnly.substring(2)
            Log.d("ChatApps", "extractChatPhone: normalized from 84 prefix: $result")
            result
        } else if (digitsOnly.startsWith("84") && digitsOnly.length == 12) {
            // Handle +84 format
            val result = "0" + digitsOnly.substring(2)
            Log.d("ChatApps", "extractChatPhone: normalized from 84 prefix (12 digits): $result")
            result
        } else {
            Log.d("ChatApps", "extractChatPhone: no normalization needed: $digitsOnly")
            digitsOnly
        }
        
        // Vietnamese phone numbers are 10 digits (after normalization)
        // Accept 9-11 digits for flexibility
        if (normalized.length in 9..11) {
            Log.d("ChatApps", "extractChatPhone: normalized phone accepted: $normalized (length=${normalized.length})")
            normalized
        } else {
            Log.d("ChatApps", "extractChatPhone: normalized phone rejected: $normalized (length=${normalized.length})")
            null
        }
    }
    val result = candidates.maxByOrNull { it.length } ?: candidates.firstOrNull()
    Log.d("ChatApps", "extractChatPhone: final result=$result")
    return result
}

private fun coversMostOfText(text: String, range: IntRange): Boolean {
    val matchLength = range.last - range.first + 1
    return matchLength >= (text.length * 0.8)
}

/** Trả về package name đã cài (từ intent query hoặc isAppInstalled), hoặc null nếu không cài. */
private fun getInstalledPackageForSearchInside(context: android.content.Context, id: String, packageNames: List<String>): String? {
    when (id) {
        "chatgpt", "gemini", "perplexity", "claude", "grok" -> {
            val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain" }
            val list = try {
                context.packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            } catch (_: Exception) { emptyList() }
            val pkg = packageNames.firstOrNull { p -> list.any { it.activityInfo.packageName == p } }
            if (pkg != null) return pkg
        }
        "youtube" -> {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/"))
            val list = try {
                context.packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            } catch (_: Exception) { emptyList() }
            val pkg = packageNames.firstOrNull { p -> list.any { it.activityInfo.packageName == p } }
            if (pkg != null) return pkg
        }
        "spotify" -> {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:"))
            val list = try {
                context.packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            } catch (_: Exception) { emptyList() }
            val pkg = packageNames.firstOrNull { p -> list.any { it.activityInfo.packageName == p } }
            if (pkg != null) return pkg
        }
    }
    return packageNames.firstOrNull { isAppInstalled(context, it) }
}

/** Cùng cách check như Open chat app: getLaunchIntentForPackage rồi fallback getPackageInfo(pkg, 0). */
private fun isAppInstalled(context: android.content.Context, packageName: String): Boolean {
    return try {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launch != null) {
            Log.d("AskAI", "isAppInstalled: $packageName -> true (getLaunchIntentForPackage)")
            true
        } else try {
            context.packageManager.getPackageInfo(packageName, 0)
            Log.d("AskAI", "isAppInstalled: $packageName -> true (getPackageInfo, launch was null)")
            true
        } catch (e: PackageManager.NameNotFoundException) {
            Log.d("AskAI", "isAppInstalled: $packageName -> false (getPackageInfo NameNotFoundException)")
            false
        }
    } catch (e: Exception) {
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            Log.d("AskAI", "isAppInstalled: $packageName -> true (getPackageInfo in catch, launch threw: ${e.message})")
            true
        } catch (e2: PackageManager.NameNotFoundException) {
            Log.d("AskAI", "isAppInstalled: $packageName -> false (getPackageInfo in catch: ${e2.message})")
            false
        }
    }
}

private fun openChatApp(context: android.content.Context, app: ChatApp) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(app.uriBuilder(app.phone)))
    intent.setPackage(app.packageName)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Fail silently: không mở browser, không show error (spec: "One tap. One chat. No surprises.")
    }
}

private fun getSearchInsideResult(context: android.content.Context, text: String): SearchInsideResult? {
    val detections = TextAnalyzer().analyze(text)
    val allowAi = shouldShowAskAi(text)
    val allowMedia = shouldShowSearchInsideAction(text, detections)
    Log.d("AskAI", "getSearchInsideResult: text.length=${text.length}, allowAi=$allowAi, allowMedia=$allowMedia")
    if (!allowAi && !allowMedia) {
        Log.d("AskAI", "getSearchInsideResult: return null (no allowAi and no allowMedia)")
        return null
    }

    val aiQuery = normalizeSearchQuery(text)
    val mediaQuery = limitMediaQuery(text)

    // Chỉ hiển thị app đã cài: dùng intent query trước, fallback isAppInstalled (package đã khai báo trong <queries>).
    val candidateDefs = listOf(
        Triple("chatgpt", "ChatGPT", listOf("com.openai.chatgpt")),
        Triple("gemini", "Gemini", listOf("com.google.android.apps.bard", "com.google.android.apps.genai.gemini", "com.google.android.apps.genai")),
        Triple("perplexity", "Perplexity", listOf("ai.perplexity.app.android", "ai.perplexity.app")),
        Triple("claude", "Claude", listOf("com.anthropic.claude")),
        Triple("grok", "Grok", listOf("ai.x.grok")),
        Triple("youtube", "YouTube", listOf("com.google.android.youtube", "com.google.android.youtube.leanback")),
        Triple("spotify", "Spotify", listOf("com.spotify.music"))
    )
    val installed = mutableListOf<SearchInsideApp>()
    for ((id, label, packageNames) in candidateDefs) {
        val pkg = getInstalledPackageForSearchInside(context, id, packageNames) ?: continue
        val app = when (id) {
            "chatgpt" -> SearchInsideApp(id = id, label = label, packageName = pkg, color = Color(0xFF10A37F), group = SearchIntent.KNOWLEDGE,
                intentBuilder = { Intent(Intent.ACTION_MAIN).apply { setPackage(pkg) } },
                webUrlBuilder = { q -> "https://chatgpt.com/?q=${Uri.encode(q)}" })
            "gemini" -> SearchInsideApp(id = id, label = label, packageName = pkg, color = Color(0xFF4285F4), group = SearchIntent.KNOWLEDGE,
                intentBuilder = { q -> Intent(Intent.ACTION_VIEW, Uri.parse("https://gemini.google.com/?q=${Uri.encode(q)}")).apply { setPackage(pkg) } },
                webUrlBuilder = { q -> "https://gemini.google.com/?q=${Uri.encode(q)}" })
            "perplexity" -> SearchInsideApp(id = id, label = label, packageName = pkg, color = Color(0xFF3B82F6), group = SearchIntent.KNOWLEDGE,
                intentBuilder = { q -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.perplexity.ai/search?q=${Uri.encode(q)}")).apply { setPackage(pkg) } },
                webUrlBuilder = { q -> "https://www.perplexity.ai/search?q=${Uri.encode(q)}" })
            "claude" -> SearchInsideApp(id = id, label = label, packageName = pkg, color = Color(0xFFD97757), group = SearchIntent.KNOWLEDGE,
                intentBuilder = { Intent(Intent.ACTION_MAIN).apply { setPackage(pkg) } },
                webUrlBuilder = { _ -> "https://claude.ai/new" })
            "grok" -> SearchInsideApp(id = id, label = label, packageName = pkg, color = Color(0xFF111111), group = SearchIntent.KNOWLEDGE,
                intentBuilder = { Intent(Intent.ACTION_MAIN).apply { setPackage(pkg) } },
                webUrlBuilder = { _ -> "https://grok.com/" })
            "youtube" -> SearchInsideApp(id = id, label = label, packageName = pkg, color = Color(0xFFFF0000), group = SearchIntent.MEDIA,
                intentBuilder = { q -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(q)}")).apply { setPackage(pkg) } })
            "spotify" -> SearchInsideApp(id = id, label = label, packageName = pkg, color = Color(0xFF1DB954), group = SearchIntent.MEDIA,
                intentBuilder = { q -> Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:${Uri.encode(q)}")).apply { setPackage(pkg) } })
            else -> continue
        }
        installed.add(app)
    }
    val aiCandidates = installed.filter { it.group == SearchIntent.KNOWLEDGE }
    val mediaCandidates = installed.filter { it.group == SearchIntent.MEDIA }

    val aiApps = if (allowAi) aiCandidates else emptyList()
    val mediaApps = if (allowMedia) mediaCandidates else emptyList()
    val anyAvailable = aiApps.isNotEmpty() || mediaApps.isNotEmpty()
    Log.d("AskAI", "getSearchInsideResult: installed=${installed.size}, aiApps=${aiApps.size}, mediaApps=${mediaApps.size}, anyAvailable=$anyAvailable")
    return SearchInsideResult(aiQuery, mediaQuery, aiApps, mediaApps, anyAvailable)
}

private fun shouldShowSearchInsideAction(text: String, detections: List<Detection>): Boolean {
    val cleaned = text.replace(Regex("\\s+"), " ").trim()
    // Open Media: hiển thị khi text 3–150 ký tự, không phải URL/email/phone/địa chỉ
    if (cleaned.length < 3 || cleaned.length > 150) {
        Log.d("AskAI", "shouldShowSearchInsideAction: false (length=${cleaned.length})")
        return false
    }
    if (cleaned.all { it.isDigit() }) {
        Log.d("AskAI", "shouldShowSearchInsideAction: false (all digits)")
        return false
    }

    if (Patterns.WEB_URL.matcher(cleaned).matches() || cleaned.contains("http") || cleaned.contains("www.")) {
        Log.d("AskAI", "shouldShowSearchInsideAction: false (URL)")
        return false
    }
    if (Patterns.EMAIL_ADDRESS.matcher(cleaned).matches()) {
        Log.d("AskAI", "shouldShowSearchInsideAction: false (email)")
        return false
    }

    val directTypes = setOf(TextType.PHONE_NUMBER, TextType.URL, TextType.EMAIL, TextType.ADDRESS)
    val matching = detections.firstOrNull { it.type in directTypes }
    if (matching != null && coversMostOfText(cleaned, matching.range)) {
        Log.d("AskAI", "shouldShowSearchInsideAction: false (directType=${matching.type} covers text)")
        return false
    }


    Log.d("AskAI", "shouldShowSearchInsideAction: true")
    return true
}

private fun shouldShowAskAi(text: String): Boolean {
    // Chỉ cần có text; hiển thị Ask AI nếu có app AI đã cài (không check limit/URL/email/phone)
    return text.replace(Regex("\\s+"), " ").trim().isNotBlank()
}

private fun classifySearchIntent(text: String): SearchIntent {
    val lower = text.lowercase()
    val isQuestion = lower.contains("?") ||
        lower.startsWith("what ") ||
        lower.startsWith("how ") ||
        lower.startsWith("why ") ||
        lower.startsWith("who ") ||
        lower.startsWith("when ") ||
        lower.startsWith("where ") ||
        lower.contains("là gì") ||
        lower.contains("ai là") ||
        lower.contains("tại sao") ||
        lower.contains("như thế nào")

    val mediaKeywords = listOf(
        "song", "music", "mv", "video", "lofi", "playlist", "album", "artist", "podcast", "mix",
        "bài hát", "bai hat", "nghệ sĩ", "nghe si", "ca sĩ", "ca si", "nhạc", "nhac", "xem phim", "phim", "nghe nhạc"
    )
    val isMedia = mediaKeywords.any { lower.contains(it) }

    return when {
        isQuestion && isMedia -> SearchIntent.AMBIGUOUS
        isQuestion -> SearchIntent.KNOWLEDGE
        isMedia -> SearchIntent.MEDIA
        else -> SearchIntent.AMBIGUOUS
    }
}

private fun limitSearchInsideQuery(text: String): String {
    val cleaned = text.replace(Regex("\\s+"), " ").trim()
    return if (cleaned.length > 120) cleaned.substring(0, 120) else cleaned
}

private fun limitMediaQuery(text: String): String {
    val cleaned = text.replace(Regex("\\s+"), " ").trim()
    return if (cleaned.length > 150) cleaned.substring(0, 150) else cleaned
}

private fun normalizeSearchQuery(text: String): String {
    return text.replace(Regex("\\s+"), " ").trim()
}

private fun tryOpenSearchInsideApp(context: android.content.Context, app: SearchInsideApp, query: String): Boolean {
    try {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Search Query", query))
    } catch (_: Exception) {
        // ignore
    }
    try {
        when (app.id) {
            "chatgpt", "gemini", "claude", "grok" -> {
                // Thử ACTION_SEND: mở app và đưa text vào new chat (nếu app hỗ trợ share)
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    setPackage(app.packageName)
                    putExtra(Intent.EXTRA_TEXT, query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(sendIntent)
                    return true
                } catch (_: ActivityNotFoundException) {
                    // Fallback: mở app bằng getLaunchIntentForPackage (user dán từ clipboard)
                }
                val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                    ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                    return true
                }
                return false
            }
            else -> {
                val intent = app.intentBuilder(query).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
                return true
            }
        }
    } catch (e: ActivityNotFoundException) {
        // App chưa cài hoặc không thể mở: fail silently
        return false
    } catch (e: Exception) {
        Log.d("AskAI", "tryOpenSearchInsideApp failed: ${app.label} ${e.message}")
        return false
    }
}

private fun openAiWeb(context: android.content.Context, app: SearchInsideApp, query: String) {
    val url = app.webUrlBuilder?.invoke(query) ?: return
    try {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Search Query", query))
    } catch (_: Exception) {
        // ignore
    }
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        android.widget.Toast.makeText(
            context,
            context.getString(R.string.cannot_open_browser),
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}

private fun openAiInstall(context: android.content.Context, app: SearchInsideApp) {
    val packageName = app.packageName
    val marketUri = Uri.parse("market://details?id=$packageName")
    val webUri = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
    try {
        val intent = Intent(Intent.ACTION_VIEW, marketUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, webUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.cannot_open_play_store),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}

@Composable
fun ActionRowItem(
    item: TextActionItem,
    shape: androidx.compose.ui.graphics.Shape,
    isSmartReadActive: Boolean = false,
    isReadAloudActive: Boolean = false,
    onClick: () -> Unit
) {
    val actionColors = getActionColors(item.id)
    val cardBackground = elevatedCardColor(
        colorScheme = MaterialTheme.colorScheme
    ) // Item dùng surface để tương phản với nền
    val isSmartRead = item.id.substringBefore(":") == "smart_read_aloud"
    val isReadAloud = item.id.substringBefore(":") == "tts"
    
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
                modifier = Modifier.size(44.dp),
            shape = CircleShape,
                color = actionColors.backgroundColor
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isSmartRead) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isSmartReadActive) {
                                AppIcons.Stop
                            } else {
                                AppIcons.Volume
                            },
                            contentDescription = null,
                            tint = actionColors.iconColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Icon(
                            imageVector = AppIcons.Sparkle,
                            contentDescription = null,
                            tint = actionColors.iconColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else if (isReadAloud) {
                    Icon(
                        imageVector = if (isReadAloudActive) {
                            AppIcons.Stop
                        } else {
                            AppIcons.Volume
                        },
                        contentDescription = null,
                        tint = actionColors.iconColor,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                ActionIcon(
                    icon = item.icon,
                        tint = actionColors.iconColor,
                        modifier = Modifier.size(24.dp)
                )
                }
            }
        }
        
            Spacer(modifier = Modifier.width(16.dp))
        
            Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold, // Changed to Bold
                        fontSize = 18.sp // Increased to 18sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
            )
            if (item.subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.subtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Normal,
                            fontSize = 16.sp // Increased to 16sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}


@Composable
fun ActionRowSimple(title: String, subtitle: String, icon: Any, shape: androidx.compose.ui.graphics.Shape, isLoading: Boolean = false, onClick: () -> Unit) {
    // Translate uses purple colors
    val translateColors = ActionColors(
        iconColor = Color(0xFF6750A4),
        backgroundColor = Color(0xFFE1BEE7) // Đậm hơn: từ 0xFFF3E5F5
    )
    val cardBackground = elevatedCardColor(
        colorScheme = MaterialTheme.colorScheme
    ) // Item dùng surface để tương phản với nền
    
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        enabled = !isLoading
    ) {
        Row(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
                modifier = Modifier.size(48.dp),
            shape = CircleShape,
                color = translateColors.backgroundColor
        ) {
            Box(contentAlignment = Alignment.Center) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = translateColors.iconColor
                        )
                    } else {
                ActionIcon(
                    icon = icon,
                            tint = translateColors.iconColor,
                            modifier = Modifier.size(24.dp)
                )
            }
        }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title, 
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                    text = if (isLoading) "Translating..." else subtitle, 
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Normal,
                        fontSize = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Helper function to get action priority for sorting
fun getActionPriority(actionId: String): Int {
    return when {
        actionId.startsWith("call") -> 10
        actionId.startsWith("email") -> 9
        actionId.startsWith("save_contact") || actionId.startsWith("contact") -> 8
        actionId.startsWith("sms") -> 6
        actionId.startsWith("copy_address") -> 6
        actionId.startsWith("map") || actionId.startsWith("direction") || actionId.startsWith("gps") -> 7
        actionId.startsWith("reminder") || actionId.startsWith("calendar") -> 6
        actionId.startsWith("url") || actionId.startsWith("open_link") -> 5
        else -> 0
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Format Text Bottom Sheet
// ─────────────────────────────────────────────────────────────────────────────

private data class FormatGroup(
    val titleRes: Int,
    val items: List<com.freeup.textactions.formatting.FormatOption>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatTextBottomSheet(
    text: String,
    formatUiState: ProcessTextViewModel.FormatUiState,
    onOptionSelected: (com.freeup.textactions.formatting.FormatOption) -> Unit,
    onApply: () -> Unit,
    onDismissPreview: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val preview = formatUiState.preview
    val showPreview = formatUiState.showPreview

    val groups = listOf(
        FormatGroup(
            R.string.format_group_clean,
            listOf(
                com.freeup.textactions.formatting.FormatOption.CLEAN_SPACING,
                com.freeup.textactions.formatting.FormatOption.REMOVE_LINE_BREAKS,
                com.freeup.textactions.formatting.FormatOption.FIX_BROKEN_LINES,
                com.freeup.textactions.formatting.FormatOption.REMOVE_EMOJI
            )
        ),
        FormatGroup(
            R.string.format_group_case,
            listOf(
                com.freeup.textactions.formatting.FormatOption.UPPERCASE,
                com.freeup.textactions.formatting.FormatOption.LOWERCASE,
                com.freeup.textactions.formatting.FormatOption.SENTENCE_CASE,
                com.freeup.textactions.formatting.FormatOption.TITLE_CASE
            )
        ),
        FormatGroup(
            R.string.format_group_structure,
            listOf(
                com.freeup.textactions.formatting.FormatOption.BULLET_LIST,
                com.freeup.textactions.formatting.FormatOption.NUMBERED_LIST,
                com.freeup.textactions.formatting.FormatOption.MARKDOWN
            )
        )
    )

    val optionIcons: Map<com.freeup.textactions.formatting.FormatOption, androidx.compose.ui.graphics.vector.ImageVector> = mapOf(
        com.freeup.textactions.formatting.FormatOption.CLEAN_SPACING to AppIcons.Broom,
        com.freeup.textactions.formatting.FormatOption.REMOVE_LINE_BREAKS to AppIcons.TextWrap,
        com.freeup.textactions.formatting.FormatOption.FIX_BROKEN_LINES to AppIcons.MagicWand,
        com.freeup.textactions.formatting.FormatOption.REMOVE_EMOJI to AppIcons.Smiley,
        com.freeup.textactions.formatting.FormatOption.UPPERCASE to AppIcons.TextAa,
        com.freeup.textactions.formatting.FormatOption.LOWERCASE to AppIcons.TextAa,
        com.freeup.textactions.formatting.FormatOption.SENTENCE_CASE to AppIcons.TextAa,
        com.freeup.textactions.formatting.FormatOption.TITLE_CASE to AppIcons.TextFormat,
        com.freeup.textactions.formatting.FormatOption.BULLET_LIST to AppIcons.ListBullets,
        com.freeup.textactions.formatting.FormatOption.NUMBERED_LIST to AppIcons.ListNumbers,
        com.freeup.textactions.formatting.FormatOption.MARKDOWN to AppIcons.Code
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
        ) {
            // Handle bar + title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.format_panel_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(AppIcons.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Preview section ──────────────────────────────────────────────
            AnimatedVisibility(
                visible = showPreview && preview != null,
                enter = fadeIn(tween(200)) + expandVertically(),
                exit = fadeOut(tween(150)) + shrinkVertically()
            ) {
                if (preview != null) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                stringResource(R.string.format_preview_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            // Before / After
                            Text(
                                text = preview.before.take(120) + if (preview.before.length > 120) "…" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            )
                            Icon(
                                AppIcons.ArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.CenterHorizontally).size(18.dp)
                            )
                            Text(
                                text = preview.after.take(120) + if (preview.after.length > 120) "…" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            )
                            // Action buttons
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = onDismissPreview,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(stringResource(R.string.format_cancel))
                                }
                                // Copy result button
                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("formatted", preview.after))
                                        android.widget.Toast.makeText(context, context.getString(R.string.copied), android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(AppIcons.Copy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.format_copy_result))
                                }
                                Button(
                                    onClick = onApply,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(stringResource(R.string.format_apply), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            // ── Option groups ────────────────────────────────────────────────
            groups.forEach { group ->
                Text(
                    text = stringResource(group.titleRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    group.items.chunked(2).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row.forEach { option ->
                                val isSelected = showPreview && preview?.option == option
                                Surface(
                                    onClick = { onOptionSelected(option) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    tonalElevation = if (isSelected) 0.dp else 0.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        optionIcons[option]?.let { icon ->
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = if (isSelected)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = stringResource(option.labelRes),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isSelected)
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            // If odd number, fill the other half with empty weight
                            if (row.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
