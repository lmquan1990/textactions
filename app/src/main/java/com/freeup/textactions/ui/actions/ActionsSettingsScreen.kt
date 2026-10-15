package com.freeup.textactions.ui.actions

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.freeup.textactions.ui.theme.AppIcons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import com.freeup.textactions.R
import com.freeup.textactions.ActionIcon
import com.freeup.textactions.SettingsManager
import com.freeup.textactions.AppLocale
import com.freeup.textactions.domain.model.ActionCategory
import com.freeup.textactions.domain.model.ActionSetting
import com.freeup.textactions.ui.theme.LocalExpressiveColors
import com.freeup.textactions.ui.theme.elevatedCardColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionsSettingsScreen(
    onBackClick: () -> Unit,
    viewModel: ActionsViewModel,
    aiStatus: com.freeup.textactions.domain.model.AiStatus,
    formattingStore: com.freeup.textactions.data.local.datastore.TextFormattingDataStore,
    snackbarHostState: SnackbarHostState,
    onRetryAiStatus: () -> Unit,
    onOpenOnboarding: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    onOpenThemeSettings: () -> Unit = {},
    onThemeChanged: (String) -> Unit = {},
    onLanguageChanged: (String) -> Unit = {},
    onOpenAiStatus: () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onDynamicColorChanged: (Boolean) -> Unit = {},
    onExportAllNotes: () -> Unit = {},
    onExportSelectedNote: () -> Unit = {},
    onImportNotesFromFile: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val coroutineScope = rememberCoroutineScope()
    
    val currentLocale = LocalConfiguration.current.locales[0]
    LaunchedEffect(currentLocale) {
        viewModel.reloadLabels()
    }

    var showResetDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showListStyleDialog by remember { mutableStateOf(false) }
    var themeMode by remember { mutableStateOf(settingsManager.getThemeMode()) }
    var appLanguage by remember { mutableStateOf(settingsManager.getAppLanguage()) }
    var screenshotBannerEnabled by remember { mutableStateOf(settingsManager.isScreenshotBannerEnabled()) }

    val formattingSettings by formattingStore.settingsFlow.collectAsState(
        initial = com.freeup.textactions.formatting.FormatSettings()
    )
    
    // State for drag & drop - Recommended Actions
    val recommendedActions = remember { 
        mutableStateListOf<ActionSetting>()
    }
    
    // State for drag & drop - Time (Lịch & nhắc)
    val timeActions = remember { mutableStateListOf<ActionSetting>() }
    
    // State for drag & drop - Utilities
    val utilitiesActions = remember { 
        mutableStateListOf<ActionSetting>()
    }
    
    // Update actions when uiState changes
    LaunchedEffect(uiState.actions) {
        val newRecommended = uiState.actions
            .filter { it.category == ActionCategory.RECOMMENDED }
            .map { it.copy() }
        
        val newTime = uiState.actions
            .filter { it.category == ActionCategory.TIME }
            .map { it.copy() }
        
        val newUtilities = uiState.actions
            .filter { it.category == ActionCategory.UTILITIES }
            .map { it.copy() }
        
        // Update Recommended
        val currentRecommendedIds = recommendedActions.map { it.id }
        val newRecommendedIds = newRecommended.map { it.id }
        
        if (currentRecommendedIds != newRecommendedIds) {
            recommendedActions.clear()
            recommendedActions.addAll(newRecommended)
        } else {
            newRecommended.forEachIndexed { index, newAction ->
                if (index < recommendedActions.size) {
                    val currentAction = recommendedActions[index]
                    if (currentAction.id == newAction.id && 
                        (currentAction.title != newAction.title || currentAction.description != newAction.description)) {
                        recommendedActions[index] = newAction.copy(isEnabled = currentAction.isEnabled)
                    }
                }
            }
        }
        
        // Update Time
        val currentTimeIds = timeActions.map { it.id }
        val newTimeIds = newTime.map { it.id }
        if (currentTimeIds != newTimeIds) {
            timeActions.clear()
            timeActions.addAll(newTime)
        } else {
            newTime.forEachIndexed { index, newAction ->
                if (index < timeActions.size) {
                    val currentAction = timeActions[index]
                    if (currentAction.id == newAction.id && 
                        (currentAction.title != newAction.title || currentAction.description != newAction.description)) {
                        timeActions[index] = newAction.copy(isEnabled = currentAction.isEnabled)
                    }
                }
            }
        }
        
        // Update Utilities
        val currentUtilitiesIds = utilitiesActions.map { it.id }
        val newUtilitiesIds = newUtilities.map { it.id }
        
        if (currentUtilitiesIds != newUtilitiesIds) {
            utilitiesActions.clear()
            utilitiesActions.addAll(newUtilities)
        } else {
            newUtilities.forEachIndexed { index, newAction ->
                if (index < utilitiesActions.size) {
                    val currentAction = utilitiesActions[index]
                    if (currentAction.id == newAction.id && 
                        (currentAction.title != newAction.title || currentAction.description != newAction.description)) {
                        utilitiesActions[index] = newAction.copy(isEnabled = currentAction.isEnabled)
                    }
                }
            }
        }
    }
    
    // Drag state for Recommended
    var draggedRecommendedIndex by remember { mutableStateOf<Int?>(null) }
    var dragRecommendedOffset by remember { mutableStateOf(0.dp) }
    var targetRecommendedIndex by remember { mutableStateOf<Int?>(null) }
    
    // Drag state for Time
    var draggedTimeIndex by remember { mutableStateOf<Int?>(null) }
    var dragTimeOffset by remember { mutableStateOf(0.dp) }
    var targetTimeIndex by remember { mutableStateOf<Int?>(null) }
    
    // Drag state for Utilities
    var draggedUtilitiesIndex by remember { mutableStateOf<Int?>(null) }
    var dragUtilitiesOffset by remember { mutableStateOf(0.dp) }
    var targetUtilitiesIndex by remember { mutableStateOf<Int?>(null) }
    
    val backgroundColor = MaterialTheme.colorScheme.background

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(AppIcons.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor)
            )
        },
        containerColor = backgroundColor,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // General section
            item { SettingHeader(title = stringResource(R.string.settings_section_general)) }
            item {
                SettingsLinkRow(
                    title = stringResource(R.string.settings_help),
                    subtitle = stringResource(R.string.settings_help_subtitle),
                    icon = AppIcons.Question,
                    onClick = onOpenHelp
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsLinkRow(
                    title = stringResource(R.string.settings_theme),
                    subtitle = when (themeMode) {
                        "light" -> stringResource(R.string.theme_light)
                        "dark" -> stringResource(R.string.theme_dark)
                        else -> stringResource(R.string.theme_system)
                    },
                    icon = AppIcons.Palette,
                    onClick = { showThemeDialog = true }
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsLinkRow(
                    title = stringResource(R.string.settings_language),
                    subtitle = when (appLanguage) {
                        AppLocale.LANGUAGE_ENGLISH -> stringResource(R.string.language_english)
                        AppLocale.LANGUAGE_VIETNAMESE -> stringResource(R.string.language_vietnamese)
                        else -> stringResource(R.string.language_system)
                    },
                    icon = AppIcons.Globe,
                    onClick = { showLanguageDialog = true }
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsToggleRow(
                    title = stringResource(R.string.settings_screenshot_banner),
                    subtitle = stringResource(R.string.settings_screenshot_banner_desc),
                    icon = AppIcons.Camera,
                    checked = screenshotBannerEnabled,
                    onCheckedChange = { enabled ->
                        screenshotBannerEnabled = enabled
                        settingsManager.setScreenshotBannerEnabled(enabled)
                    }
                )
            }

            // Recommended Actions with drag & drop
            item { SettingHeader(title = stringResource(R.string.settings_section_recommended)) }
            
            item {
                Column {
                    recommendedActions.forEachIndexed { index, action ->
                        val isDragging = draggedRecommendedIndex == index
                        val itemHeight = 80.dp
                        
                        // Calculate offset: items move aside when dragging, and snap to position when dropped
                        val offset by animateDpAsState(
                            targetValue = when {
                                isDragging -> dragRecommendedOffset
                                draggedRecommendedIndex != null && targetRecommendedIndex != null -> {
                                    val draggedIdx = draggedRecommendedIndex!!
                                    val targetIdx = targetRecommendedIndex!!
                                    when {
                                        // Item is being pushed down
                                        index in (draggedIdx + 1)..targetIdx && targetIdx > draggedIdx -> -itemHeight
                                        // Item is being pushed up
                                        index in targetIdx until draggedIdx && targetIdx < draggedIdx -> itemHeight
                                        else -> 0.dp
                                    }
                                }
                                else -> 0.dp
                            },
                            // Use spring animation while dragging (for smooth avoidance), 
                            // but snap instantly when dropped (draggedIndex is null) to avoid "sliding" effect
                            animationSpec = if (draggedRecommendedIndex != null) spring(stiffness = Spring.StiffnessMedium) 
                                          else snap(),
                            label = "itemOffset"
                        )
                        
                        ReorderableActionSettingRow(
                            action = action,
                            isDragging = isDragging,
                            dragOffset = offset,
                            onToggle = { enabled ->
                                val actionIndex = recommendedActions.indexOfFirst { it.id == action.id }
                                if (actionIndex >= 0) {
                                    recommendedActions[actionIndex] = recommendedActions[actionIndex].copy(isEnabled = enabled)
                                }
                                viewModel.toggleAction(action.id, enabled)
                            },
                            onDragStart = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                draggedRecommendedIndex = index
                                targetRecommendedIndex = index
                                dragRecommendedOffset = 0.dp
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragRecommendedOffset += with(density) { dragAmount.y.toDp() }
                                
                                val offsetInItems = (dragRecommendedOffset / itemHeight).toInt()
                                val newTargetIndex = (draggedRecommendedIndex!! + offsetInItems).coerceIn(0, recommendedActions.size - 1)
                                
                                if (newTargetIndex != targetRecommendedIndex) {
                                    targetRecommendedIndex = newTargetIndex
                                }
                            },
                            onDragEnd = {
                                if (targetRecommendedIndex != null && draggedRecommendedIndex != null && 
                                    targetRecommendedIndex != draggedRecommendedIndex) {
                                    val fromIndex = draggedRecommendedIndex!!
                                    val toIndex = targetRecommendedIndex!!
                                    if (fromIndex >= 0 && fromIndex < recommendedActions.size &&
                                        toIndex >= 0 && toIndex < recommendedActions.size) {
                                        val item = recommendedActions[fromIndex]
                                        recommendedActions.removeAt(fromIndex)
                                        recommendedActions.add(toIndex, item)
                                    }
                                }
                                
                                viewModel.saveRecommendedOrder(recommendedActions.map { it.id })
                                draggedRecommendedIndex = null
                                targetRecommendedIndex = null
                                dragRecommendedOffset = 0.dp
                            },
                            onDragCancel = {
                                draggedRecommendedIndex = null
                                targetRecommendedIndex = null
                                dragRecommendedOffset = 0.dp
                            }
                        )
                    }
                }
            }

            item { SettingHeader(title = stringResource(R.string.settings_section_utilities)) }
            item {
                Column {
                    utilitiesActions.forEachIndexed { index, action ->
                        val isDragging = draggedUtilitiesIndex == index
                        val itemHeight = 80.dp
                        
                        val offset by animateDpAsState(
                            targetValue = when {
                                isDragging -> dragUtilitiesOffset
                                draggedUtilitiesIndex != null && targetUtilitiesIndex != null -> {
                                    val draggedIdx = draggedUtilitiesIndex!!
                                    val targetIdx = targetUtilitiesIndex!!
                                    when {
                                        index in (draggedIdx + 1)..targetIdx && targetIdx > draggedIdx -> -itemHeight
                                        index in targetIdx until draggedIdx && targetIdx < draggedIdx -> itemHeight
                                        else -> 0.dp
                                    }
                                }
                                else -> 0.dp
                            },
                            animationSpec = if (draggedUtilitiesIndex != null) spring(stiffness = Spring.StiffnessMedium) 
                                          else snap(),
                            label = "itemOffset"
                        )
                        
                        ReorderableActionSettingRow(
                            action = action,
                            isDragging = isDragging,
                            dragOffset = offset,
                            onToggle = { enabled ->
                                val actionIndex = utilitiesActions.indexOfFirst { it.id == action.id }
                                if (actionIndex >= 0) {
                                    utilitiesActions[actionIndex] = utilitiesActions[actionIndex].copy(isEnabled = enabled)
                                }
                                viewModel.toggleAction(action.id, enabled)
                            },
                            onDragStart = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                draggedUtilitiesIndex = index
                                targetUtilitiesIndex = index
                                dragUtilitiesOffset = 0.dp
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragUtilitiesOffset += with(density) { dragAmount.y.toDp() }
                                
                                val offsetInItems = (dragUtilitiesOffset / itemHeight).toInt()
                                val newTargetIndex = (draggedUtilitiesIndex!! + offsetInItems).coerceIn(0, utilitiesActions.size - 1)
                                
                                if (newTargetIndex != targetUtilitiesIndex) {
                                    targetUtilitiesIndex = newTargetIndex
                                }
                            },
                            onDragEnd = {
                                if (targetUtilitiesIndex != null && draggedUtilitiesIndex != null && 
                                    targetUtilitiesIndex != draggedUtilitiesIndex) {
                                    val fromIndex = draggedUtilitiesIndex!!
                                    val toIndex = targetUtilitiesIndex!!
                                    if (fromIndex >= 0 && fromIndex < utilitiesActions.size &&
                                        toIndex >= 0 && toIndex < utilitiesActions.size) {
                                        val item = utilitiesActions[fromIndex]
                                        utilitiesActions.removeAt(fromIndex)
                                        utilitiesActions.add(toIndex, item)
                                    }
                                }
                                
                                viewModel.saveUtilityOrder(utilitiesActions.map { it.id })
                                draggedUtilitiesIndex = null
                                targetUtilitiesIndex = null
                                dragUtilitiesOffset = 0.dp
                            },
                            onDragCancel = {
                                draggedUtilitiesIndex = null
                                targetUtilitiesIndex = null
                                dragUtilitiesOffset = 0.dp
                            }
                        )
                    }
                }
            }

            item { SettingHeader(title = stringResource(R.string.settings_section_time)) }
            item {
                Column {
                    timeActions.forEachIndexed { index, action ->
                        val isDragging = draggedTimeIndex == index
                        val itemHeight = 80.dp
                        val offset by animateDpAsState(
                            targetValue = when {
                                isDragging -> dragTimeOffset
                                draggedTimeIndex != null && targetTimeIndex != null -> {
                                    val draggedIdx = draggedTimeIndex!!
                                    val targetIdx = targetTimeIndex!!
                                    when {
                                        index in (draggedIdx + 1)..targetIdx && targetIdx > draggedIdx -> -itemHeight
                                        index in targetIdx until draggedIdx && targetIdx < draggedIdx -> itemHeight
                                        else -> 0.dp
                                    }
                                }
                                else -> 0.dp
                            },
                            animationSpec = if (draggedTimeIndex != null) spring(stiffness = Spring.StiffnessMedium) else snap(),
                            label = "itemOffset"
                        )
                        ReorderableActionSettingRow(
                            action = action,
                            isDragging = isDragging,
                            dragOffset = offset,
                            onToggle = { enabled ->
                                val actionIndex = timeActions.indexOfFirst { it.id == action.id }
                                if (actionIndex >= 0) timeActions[actionIndex] = timeActions[actionIndex].copy(isEnabled = enabled)
                                viewModel.toggleAction(action.id, enabled)
                            },
                            onDragStart = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                draggedTimeIndex = index
                                targetTimeIndex = index
                                dragTimeOffset = 0.dp
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragTimeOffset += with(density) { dragAmount.y.toDp() }
                                val offsetInItems = (dragTimeOffset / itemHeight).toInt()
                                val newTargetIndex = (draggedTimeIndex!! + offsetInItems).coerceIn(0, timeActions.size - 1)
                                if (newTargetIndex != targetTimeIndex) targetTimeIndex = newTargetIndex
                            },
                            onDragEnd = {
                                if (targetTimeIndex != null && draggedTimeIndex != null && targetTimeIndex != draggedTimeIndex) {
                                    val fromIndex = draggedTimeIndex!!
                                    val toIndex = targetTimeIndex!!
                                    if (fromIndex in timeActions.indices && toIndex in timeActions.indices) {
                                        val item = timeActions[fromIndex]
                                        timeActions.removeAt(fromIndex)
                                        timeActions.add(toIndex, item)
                                    }
                                }
                                viewModel.saveTimeOrder(timeActions.map { it.id })
                                draggedTimeIndex = null
                                targetTimeIndex = null
                                dragTimeOffset = 0.dp
                            },
                            onDragCancel = {
                                draggedTimeIndex = null
                                targetTimeIndex = null
                                dragTimeOffset = 0.dp
                            }
                        )
                    }
                }
            }

            item { SettingHeader(title = stringResource(R.string.settings_section_text_formatting)) }
            item {
                SettingsToggleRow(
                    title = stringResource(R.string.format_setting_auto_suggest),
                    subtitle = stringResource(R.string.format_setting_auto_suggest_desc),
                    icon = AppIcons.Sparkle,
                    checked = formattingSettings.autoSuggest,
                    onCheckedChange = { enabled ->
                        coroutineScope.launch { formattingStore.setAutoSuggest(enabled) }
                    }
                )
            }
            item {
                SettingsToggleRow(
                    title = stringResource(R.string.format_setting_ocr_fix),
                    subtitle = stringResource(R.string.format_setting_ocr_fix_desc),
                    icon = AppIcons.ImageBroken,
                    checked = formattingSettings.enableOcrFix,
                    onCheckedChange = { enabled ->
                        coroutineScope.launch { formattingStore.setOcrFixEnabled(enabled) }
                    }
                )
            }
            // "Default list style" and "Preserve original text" removed:
            // users now pick list type directly in the Format Text sheet,
            // and the before/after preview replaces the preserve-original toggle.

            item { SettingHeader(title = stringResource(R.string.settings_section_backup_restore)) }
            item {
                SettingsLinkRow(
                    title = stringResource(R.string.settings_export_all_notes),
                    subtitle = stringResource(R.string.settings_export_all_notes_subtitle),
                    icon = AppIcons.FileArrowUp,
                    onClick = onExportAllNotes
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsLinkRow(
                    title = stringResource(R.string.settings_export_selected_note),
                    subtitle = stringResource(R.string.settings_export_selected_note_subtitle),
                    icon = AppIcons.FileText,
                    onClick = onExportSelectedNote
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingsLinkRow(
                    title = stringResource(R.string.settings_import_file),
                    subtitle = stringResource(R.string.settings_import_file_subtitle),
                    icon = AppIcons.FolderOpen,
                    onClick = onImportNotesFromFile
                )
            }

            item { SettingHeader(title = stringResource(R.string.settings_section_privacy)) }
            item {
                SettingsLinkRow(
                    title = stringResource(R.string.settings_privacy_title),
                    subtitle = stringResource(R.string.settings_privacy_subtitle),
                    icon = AppIcons.ShieldCheck,
                    onClick = onOpenPrivacyPolicy
                )
            }

            item { SettingHeader(title = stringResource(R.string.settings_section_about)) }
            item {
                SettingsLinkRow(
                    title = stringResource(R.string.settings_about_title),
                    subtitle = stringResource(R.string.settings_about_subtitle),
                    icon = AppIcons.Info,
                    onClick = onOpenAbout
                )
            }

            item {
                TextButton(
                    onClick = { showResetDialog = true },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error // Use dynamic color
                    )
                ) {
                    Text(
                        stringResource(R.string.settings_reset_actions),
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 18.sp, // Set to 18sp
                        fontWeight = FontWeight.Bold // Changed to Bold
                    )
                }
            }
            
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { 
                Text(
                    stringResource(R.string.settings_reset_dialog_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = { 
                Text(
                    stringResource(R.string.settings_reset_dialog_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetDefaults()
                    showResetDialog = false
                }) {
                    Text(
                        stringResource(R.string.reset),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.Red
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(
                        stringResource(R.string.cancel),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringResource(R.string.settings_choose_theme)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    listOf(
                        stringResource(R.string.theme_light) to "light",
                        stringResource(R.string.theme_dark) to "dark",
                        stringResource(R.string.theme_system) to "system"
                    ).forEach { (label, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    settingsManager.setThemeMode(value)
                                    themeMode = value
                                    onThemeChanged(value)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = themeMode == value,
                                onClick = {
                                    settingsManager.setThemeMode(value)
                                    themeMode = value
                                    onThemeChanged(value)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.settings_choose_language)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    listOf(
                        stringResource(R.string.language_system) to AppLocale.LANGUAGE_SYSTEM,
                        stringResource(R.string.language_vietnamese) to AppLocale.LANGUAGE_VIETNAMESE,
                        stringResource(R.string.language_english) to AppLocale.LANGUAGE_ENGLISH
                    ).forEach { (label, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    settingsManager.setAppLanguage(value)
                                    appLanguage = value
                                    onLanguageChanged(value)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = appLanguage == value,
                                onClick = {
                                    settingsManager.setAppLanguage(value)
                                    appLanguage = value
                                    onLanguageChanged(value)
                                    showLanguageDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    if (showListStyleDialog) {
        AlertDialog(
            onDismissRequest = { showListStyleDialog = false },
            title = { Text(stringResource(R.string.format_setting_default_list)) },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                coroutineScope.launch {
                                    formattingStore.setDefaultListStyle(com.freeup.textactions.formatting.DefaultListStyle.BULLET)
                                }
                                showListStyleDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = formattingSettings.defaultListStyle == com.freeup.textactions.formatting.DefaultListStyle.BULLET,
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.format_list_bullet))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                coroutineScope.launch {
                                    formattingStore.setDefaultListStyle(com.freeup.textactions.formatting.DefaultListStyle.NUMBERED)
                                }
                                showListStyleDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = formattingSettings.defaultListStyle == com.freeup.textactions.formatting.DefaultListStyle.NUMBERED,
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.format_list_numbered))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showListStyleDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

@Composable
fun SettingsLinkRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    trailingContent: @Composable (() -> Unit)? = null
) {
    val cardBackground = elevatedCardColor(
        colorScheme = MaterialTheme.colorScheme
    ) // Item dùng surface để tương phản với nền
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground), // Item background
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp) // Icon size 28dp
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 18.sp, // Increased by 2sp
                    fontWeight = FontWeight.Bold, // Changed to Bold
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 17.sp, // Set to 17sp
                    color = Color.Gray
                )
            }
            
            if (trailingContent != null) {
                trailingContent()
                Spacer(modifier = Modifier.width(12.dp))
            }
            
            Icon(
                imageVector = AppIcons.CaretRight,
                contentDescription = "Open",
                tint = Color.Gray,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    val colors = LocalExpressiveColors.current
    val cardBackground = elevatedCardColor(
        colorScheme = MaterialTheme.colorScheme
    ) // Item dùng surface để tương phản với nền
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable { onCheckedChange(!checked) } else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .then(if (!enabled) Modifier.alpha(0.5f) else Modifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp) // Icon size 28dp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 18.sp, // Increased by 2sp
                    fontWeight = FontWeight.Bold, // Changed to Bold
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 17.sp, // Set to 17sp
                    color = Color.Gray
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = colors.titleColor,
                    checkedThumbColor = Color.White
                )
            )
        }
    }
}

@Composable
fun SettingHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun ActionSettingRow(
    action: ActionSetting,
    onToggle: (Boolean) -> Unit
) {
    val colors = LocalExpressiveColors.current
    val cardBackground = elevatedCardColor(
        colorScheme = MaterialTheme.colorScheme
    ) // Item dùng surface để tương phản với nền
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBackground // Item background
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                ActionIcon(
                    icon = action.icon,
                    tint = if (action.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp) // Icon size 28dp
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = action.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 18.sp, // Increased by 2sp
                    fontWeight = FontWeight.Bold, // Changed to Bold
                    color = if (action.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = action.description,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 17.sp, // Set to 17sp
                    color = Color.Gray
                )
            }
            
            Switch(
                checked = action.isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = colors.titleColor,
                    checkedThumbColor = Color.White
                )
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Icon(
                imageVector = AppIcons.DragHandle,
                contentDescription = "Reorder",
                tint = Color.LightGray,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun ReorderableActionSettingRow(
    action: ActionSetting,
    isDragging: Boolean,
    dragOffset: androidx.compose.ui.unit.Dp,
    onToggle: (Boolean) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (androidx.compose.ui.input.pointer.PointerInputChange, androidx.compose.ui.geometry.Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val colors = LocalExpressiveColors.current
    val cardBackground = elevatedCardColor(
        colorScheme = MaterialTheme.colorScheme
    )
    
    var isDragHandlePressed by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .offset(y = dragOffset)
            .alpha(if (isDragging) 0.9f else 1f)
            .shadow(if (isDragging) 8.dp else 0.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBackground
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Draggable area: icon + text
            Row(
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(action.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart() },
                            onDrag = onDrag,
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragCancel() }
                        )
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ActionIcon(
                        icon = action.icon,
                        tint = if (action.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = action.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (action.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = action.description,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 17.sp,
                        color = Color.Gray
                    )
                }
            }
            
            // Switch - separate from draggable area
            Box(
                modifier = Modifier.pointerInput(action.id) {
                    detectTapGestures(
                        onPress = { /* Consume to prevent drag */ }
                    )
                }
            ) {
                Switch(
                    checked = action.isEnabled,
                    onCheckedChange = { newValue ->
                        onToggle(newValue)
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = colors.titleColor,
                        checkedThumbColor = Color.White
                    )
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Drag handle - also draggable on click
            Icon(
                imageVector = AppIcons.DragHandle,
                contentDescription = "Reorder",
                tint = if (isDragHandlePressed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .pointerInput(action.id + "_handle") {
                        detectDragGestures(
                            onDragStart = {
                                isDragHandlePressed = true
                                onDragStart()
                            },
                            onDrag = { change, dragAmount ->
                                onDrag(change, dragAmount)
                            },
                            onDragEnd = {
                                isDragHandlePressed = false
                                onDragEnd()
                            },
                            onDragCancel = {
                                isDragHandlePressed = false
                                onDragCancel()
                            }
                        )
                    }
            )
        }
    }
}
