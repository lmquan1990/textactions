package com.freeup.textactions

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.offset
import com.freeup.textactions.ui.theme.AppIcons
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freeup.textactions.ui.theme.NunitoFontFamily
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freeup.textactions.data.local.db.AppDatabase
import com.freeup.textactions.data.repository.HomeRepository
import com.freeup.textactions.ui.home.HomeViewModel
import com.freeup.textactions.ui.theme.TextActionsTheme
import com.freeup.textactions.SettingsManager
import com.freeup.textactions.data.local.datastore.AiStatusDataStore
import com.freeup.textactions.domain.CreateReminderUseCase
import com.freeup.textactions.domain.ExtractEntitiesUseCase
import com.freeup.textactions.history.ActionReRunner
import com.freeup.textactions.history.HistoryRepository
import com.freeup.textactions.history.HistoryUiEvent
import com.freeup.textactions.history.HistoryViewModel
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
class RecentActionsActivity : BaseActivity() {
    
    private val viewModel: HomeViewModel by viewModels {
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
                return HomeViewModel(repository) as T
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settingsManager = SettingsManager(this)
        val dynamicColorEnabled = settingsManager.isDynamicColorEnabled()
        val themeMode = settingsManager.getThemeMode()

        setContent {
            val darkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            val context = androidx.compose.ui.platform.LocalContext.current
            val db = remember { AppDatabase.getDatabase(applicationContext) }
            val historyViewModel: HistoryViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        val historyRepo = HistoryRepository(db.actionHistoryDao())
                        val homeRepo = HomeRepository(
                            applicationContext,
                            db.actionHistoryDao(),
                            db.noteDao(),
                            AiStatusDataStore(applicationContext)
                        )
                        val reRunner = ActionReRunner(
                            context = context.applicationContext,
                            historyRepository = historyRepo,
                            homeRepository = homeRepo,
                            reminderUseCase = CreateReminderUseCase(),
                            extractEntitiesUseCase = ExtractEntitiesUseCase(context.applicationContext)
                        )
                        return HistoryViewModel(reRunner) as T
                    }
                }
            )
            var entitySheetItems by remember { mutableStateOf<List<com.freeup.textactions.TextActionItem>>(emptyList()) }
            val snackbarHostState = remember { SnackbarHostState() }
            val coroutineScope = rememberCoroutineScope()
            LaunchedEffect(Unit) {
                historyViewModel.events.collect { event ->
                    when (event) {
                        is HistoryUiEvent.ShowToast -> Toast.makeText(this@RecentActionsActivity, event.message, Toast.LENGTH_SHORT).show()
                        is HistoryUiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                        is HistoryUiEvent.ShowEntities -> entitySheetItems = event.items
                    }
                }
            }
            fun isSafeHistoryAction(actionType: String): Boolean {
                return when (actionType.uppercase()) {
                    "COPY", "SAVE_NOTE", "SAVE_TO_NOTE", "SHARE", "QUICK_SHARE" -> true
                    else -> false
                }
            }
            TextActionsTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColorEnabled,
                transparentSystemBars = true
            ) {
                val uiState by viewModel.uiState.collectAsState()

                val backgroundColor = MaterialTheme.colorScheme.background
                var selectedHistoryIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
                val isHistorySelectMode = selectedHistoryIds.isNotEmpty()

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                if (isHistorySelectMode) {
                                    Text(
                                        text = stringResource(R.string.selected_count, selectedHistoryIds.size),
                                        style = TextStyle(
                                            fontFamily = NunitoFontFamily,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 22.sp,
                                            letterSpacing = MaterialTheme.typography.headlineSmall.letterSpacing,
                                            fontSynthesis = FontSynthesis.Weight
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                } else {
                                    Text(
                                        text = stringResource(R.string.history),
                                        style = TextStyle(
                                            fontFamily = NunitoFontFamily,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 28.sp,
                                            letterSpacing = MaterialTheme.typography.headlineSmall.letterSpacing,
                                            fontSynthesis = FontSynthesis.Weight
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = {
                                    if (isHistorySelectMode) selectedHistoryIds = emptySet()
                                    else finish()
                                }) {
                                    Icon(
                                        imageVector = if (isHistorySelectMode) AppIcons.Close else AppIcons.ArrowBack,
                                        contentDescription = "Back",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            actions = {
                                if (isHistorySelectMode) {
                                    val allSelected = uiState.recentActions.isNotEmpty() && selectedHistoryIds.containsAll(uiState.recentActions.map { it.id }.toSet())
                                    if (!allSelected) IconButton(onClick = {
                                        selectedHistoryIds = uiState.recentActions.map { it.id }.toSet()
                                    }) {
                                        Icon(
                                            imageVector = AppIcons.SelectAll,
                                            contentDescription = stringResource(R.string.select_all)
                                        )
                                    }
                                    IconButton(onClick = {
                                        val selectedActions = uiState.recentActions.filter { selectedHistoryIds.contains(it.id) }
                                        selectedActions.forEach { viewModel.deleteAction(it.id) }
                                        selectedHistoryIds = emptySet()
                                        coroutineScope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = historyDeletedMessage(this@RecentActionsActivity, selectedActions),
                                                actionLabel = getString(R.string.undo),
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                selectedActions.forEach { viewModel.addAction(it) }
                                            }
                                        }
                                    }) {
                                        Icon(
                                            imageVector = AppIcons.Trash,
                                            contentDescription = stringResource(R.string.clear_all),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor)
                        )
                    },
                    containerColor = backgroundColor,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                ) { padding ->
                    val activityContext = this@RecentActionsActivity
                    var searchQuery by remember { mutableStateOf("") }
                    val filteredActions = remember(uiState.recentActions, searchQuery) {
                        val query = searchQuery.trim().lowercase()
                        if (query.isEmpty()) uiState.recentActions
                        else uiState.recentActions.filter { action ->
                            action.inputText.lowercase().contains(query) ||
                                ActionLabels.getLabelForHistory(activityContext, action.actionType).lowercase().contains(query) ||
                                (action.resultText?.lowercase()?.contains(query) == true)
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 16.dp)
                            .padding(top = 8.dp)
                    ) {
                        SearchBar(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            onSearchQueryChange = { searchQuery = it },
                            placeholderText = stringResource(R.string.search_history)
                        )

                        // Action Type Chips
                        // Derive unique action types from the visible history
                        val actionTypes = remember(uiState.recentActions) {
                            val types: MutableList<String> = uiState.recentActions.map { it.actionType }.distinct().toMutableList()
                            types.add(0, "All")
                            types
                        }
                        var selectedActionType by remember { mutableStateOf("All") }

                        androidx.compose.foundation.lazy.LazyRow(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp), // Adjust padding as needed
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            items(actionTypes) { type ->
                                val isSelected = selectedActionType == type
                                val label = if (type == "All") stringResource(R.string.all) else ActionLabels.getLabelForHistory(activityContext, type)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedActionType = type },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        selectedContainerColor = MaterialTheme.colorScheme.onSurface,
                                        selectedLabelColor = MaterialTheme.colorScheme.surface
                                    ),
                                    border = null,
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                            }
                        }

                        // Update filtering logic to include selectedActionType
                        val finalFilteredActions = remember(filteredActions, selectedActionType) {
                            if (selectedActionType == "All") filteredActions
                            else filteredActions.filter { it.actionType == selectedActionType }
                        }
                        
                        if (searchQuery.isNotBlank() && finalFilteredActions.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.no_results_for, searchQuery),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            FullRecentActionsList(
                                recentActions = finalFilteredActions,
                                selectionMode = isHistorySelectMode,
                                selectedIds = selectedHistoryIds,
                                onItemClick = { action ->
                                    historyViewModel.rerun(action)
                                },
                                onRerunClick = { action -> historyViewModel.rerun(action) },
                                onCopyClick = { action ->
                                    val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("History", action.inputText))
                                    Toast.makeText(this@RecentActionsActivity, getString(R.string.copied), Toast.LENGTH_SHORT).show()
                                },
                                onShareClick = { action ->
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, action.inputText)
                                    }
                                    startActivity(android.content.Intent.createChooser(intent, getString(R.string.share_title)))
                                },
                                onToggleSelect = { id ->
                                    selectedHistoryIds = if (selectedHistoryIds.contains(id)) {
                                        selectedHistoryIds - id
                                    } else {
                                        selectedHistoryIds + id
                                    }
                                },
                                onLongPressSelect = { id ->
                                    selectedHistoryIds = if (selectedHistoryIds.contains(id)) {
                                        selectedHistoryIds - id
                                    } else {
                                        selectedHistoryIds + id
                                    }
                                },
                                onTogglePin = { action -> viewModel.setActionPinned(action.id, !action.isPinned) },
                                onDeleteAction = { action ->
                                    viewModel.deleteAction(action.id)
                                    coroutineScope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = historyDeletedMessage(this@RecentActionsActivity, listOf(action)),
                                            actionLabel = getString(R.string.undo),
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.addAction(action)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (entitySheetItems.isNotEmpty()) {
                HistoryEntitySheet(
                    items = entitySheetItems,
                    onDismiss = { entitySheetItems = emptyList() }
                )
            }
        }
    }
}

/**
 * Modern visual hero badge for empty history state.
 * Layered with soft ambient glow circle, scanner icon, and accent spark badge.
 */
@Composable
fun EmptyHistoryHero(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(108.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer ambient glow ring
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    shape = CircleShape
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                    shape = CircleShape
                )
        )
        // Middle circular badge
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.Camera,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        }
        // Floating sparkles badge top-right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-2).dp, y = 2.dp)
                .size(30.dp)
                .background(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = CircleShape
                )
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.surface,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.Sparkle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(16.dp)
            )
        }
        // Floating note icon bottom-left
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 2.dp, y = (-2).dp)
                .size(26.dp)
                .background(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = CircleShape
                )
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.surface,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.Note,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * Modern, expressive empty state for History feed.
 * Features a glowing hero badge and clear typography.
 */
@Composable
fun HistoryEmptyState(
    modifier: Modifier = Modifier,
    onScanTextClick: (() -> Unit)? = null,
    onScanQrClick: (() -> Unit)? = null,
    onCreateSnippetClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(bottom = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            EmptyHistoryHero()

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.no_actions_yet),
                style = TextStyle(
                    fontFamily = NunitoFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.scan_text_or_qr),
                style = TextStyle(
                    fontFamily = NunitoFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

@Composable
fun FullRecentActionsList(
    recentActions: List<com.freeup.textactions.domain.model.ActionHistory>,
    selectionMode: Boolean = false,
    selectedIds: Set<Long> = emptySet(),
    onItemClick: (com.freeup.textactions.domain.model.ActionHistory) -> Unit = {},
    onRerunClick: (com.freeup.textactions.domain.model.ActionHistory) -> Unit = {},
    onCopyClick: (com.freeup.textactions.domain.model.ActionHistory) -> Unit = {},
    onShareClick: (com.freeup.textactions.domain.model.ActionHistory) -> Unit = {},
    onToggleSelect: (Long) -> Unit = {},
    onLongPressSelect: (Long) -> Unit = {},
    onDeleteAction: (com.freeup.textactions.domain.model.ActionHistory) -> Unit = {},
    onTogglePin: (com.freeup.textactions.domain.model.ActionHistory) -> Unit = {},
    onScanTextClick: (() -> Unit)? = null,
    onScanQrClick: (() -> Unit)? = null,
    onCreateSnippetClick: (() -> Unit)? = null
) {
    if (recentActions.isEmpty()) {
        HistoryEmptyState(
            onScanTextClick = onScanTextClick,
            onScanQrClick = onScanQrClick,
            onCreateSnippetClick = onCreateSnippetClick
        )
    } else {
        val grouped = recentActions.groupBy { if (it.isPinned) "PINNED" else historyGroupKey(it.createdAt) }
        val order = listOf("PINNED", "TODAY", "YESTERDAY", "EARLIER")
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            order.forEach { key ->
                grouped[key]?.let { items ->
                    if (key != order.first()) {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    Text(
                        text = when (key) {
                            "PINNED" -> stringResource(R.string.time_group_pinned)
                            "TODAY" -> stringResource(R.string.time_group_today)
                            "YESTERDAY" -> stringResource(R.string.time_group_yesterday)
                            else -> stringResource(R.string.time_group_earlier)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    items.forEach { action ->
                        key(action.id) {
                            HistoryItemCard(
                                action = action,
                                isSelected = selectedIds.contains(action.id),
                                selectionMode = selectionMode,
                                onItemClick = { onItemClick(action) },
                                onRerunClick = { onRerunClick(action) },
                                onCopyClick = { onCopyClick(action) },
                                onShareClick = { onShareClick(action) },
                                onToggleSelect = { onToggleSelect(action.id) },
                                onLongPressSelect = { onLongPressSelect(action.id) },
                                onDeleteAction = { onDeleteAction(action) },
                                onTogglePin = { onTogglePin(action) }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(88.dp))
        }
    }
}
