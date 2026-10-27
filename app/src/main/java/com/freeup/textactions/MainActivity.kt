package com.freeup.textactions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freeup.textactions.data.local.db.AppDatabase
import com.freeup.textactions.data.local.datastore.AiStatusDataStore
import com.freeup.textactions.data.repository.HomeRepository
import com.freeup.textactions.ui.home.HomeViewModel
import com.freeup.textactions.history.ActionReRunner
import com.freeup.textactions.history.HistoryActionSheet
import com.freeup.textactions.history.formatHistoryContextText
import com.freeup.textactions.history.HistoryRepository
import com.freeup.textactions.history.HistoryUiEvent
import com.freeup.textactions.history.HistoryViewModel
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import kotlinx.coroutines.delay
import com.freeup.textactions.ui.theme.AppIcons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontSynthesis
import com.freeup.textactions.ui.theme.NunitoFontFamily
import com.freeup.textactions.ui.theme.elevatedCardColor
import com.freeup.textactions.ui.theme.DarkBackground
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freeup.textactions.domain.model.AiState
import com.freeup.textactions.domain.CreateReminderUseCase
import com.freeup.textactions.domain.ExtractEntitiesUseCase
import com.freeup.textactions.ui.theme.LocalExpressiveColors
import com.freeup.textactions.ui.theme.TextActionsTheme
import java.util.concurrent.TimeUnit
import java.text.DateFormat
import java.util.Date
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

// Animation constants
private const val PRESS_ANIMATION_DURATION = 150
private const val STATE_TRANSITION_DURATION = 200
private const val FADE_IN_DURATION = 150
private const val ICON_PRESS_SCALE = 0.96f
private const val PRESS_COLOR_DARKEN_FACTOR = 0.95f

// Animation specs
private val pressAnimationSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessLow
)

private val stateTransitionSpec = tween<Float>(
    durationMillis = STATE_TRANSITION_DURATION,
    easing = FastOutSlowInEasing
)

private val fadeInSpec = tween<Float>(
    durationMillis = FADE_IN_DURATION,
    easing = FastOutSlowInEasing
)


// Color constants
private val TealPrimary = Color(0xFF00897B)
private val CardBackground = Color(0xFFF2FCF7)
private val GreenSuccess = Color(0xFF2E7D32)
private val GreenBackground = Color(0xFFC8E6C9)
private val AmberError = Color(0xFFFFB300)
private val AmberBackground = Color(0xFFFFECB3)

class MainActivity : BaseActivity() {
    private var openEditorTrigger by mutableStateOf(false)

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("extra_open_editor", false) == true) {
            openEditorTrigger = true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }
    
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
        val settingsManager = com.freeup.textactions.SettingsManager(this)
        handleIntent(intent)

        setContent {
            var themeMode by remember { mutableStateOf<String>(settingsManager.getThemeMode()) }
            var dynamicColorEnabled by remember { mutableStateOf<Boolean>(settingsManager.isDynamicColorEnabled()) }
            val lifecycleOwner = LocalLifecycleOwner.current
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
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        themeMode = settingsManager.getThemeMode()
                        dynamicColorEnabled = settingsManager.isDynamicColorEnabled()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            val darkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            val uiState by viewModel.uiState.collectAsState()
            val currentView by viewModel.currentView.collectAsState()
            var entitySheetItems by remember { mutableStateOf<List<com.freeup.textactions.TextActionItem>>(emptyList()) }
            val snackbarHostState = remember { SnackbarHostState() }
            val coroutineScope = rememberCoroutineScope()
            LaunchedEffect(Unit) {
                historyViewModel.events.collect { event ->
                    when (event) {
                        is HistoryUiEvent.ShowToast -> Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_SHORT).show()
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
            var showCameraPermissionDialog by remember { mutableStateOf(false) }
            var pendingScanType by remember { mutableStateOf<String?>(null) }
            val cameraPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted && pendingScanType != null) {
                    startActivity(Intent(this@MainActivity, CameraScanActivity::class.java).apply {
                        putExtra("scan_type", pendingScanType)
                    })
                }
                pendingScanType = null
            }
            fun launchScanIfHasCameraPermission(toolId: String) {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    startActivity(Intent(this@MainActivity, CameraScanActivity::class.java).apply {
                        putExtra("scan_type", toolId)
                    })
                } else {
                    pendingScanType = toolId
                    showCameraPermissionDialog = true
                }
            }
            fun launchScanImageIfHasGalleryPermission() {
                startActivity(Intent(this@MainActivity, CameraScanActivity::class.java).apply {
                    putExtra("scan_type", "gallery")
                })
            }

            fun copyImageUriToCache(source: android.net.Uri): String? {
                return try {
                    val directory = java.io.File(cacheDir, "shared-images").apply { mkdirs() }
                    val destination = java.io.File(directory, "img-${System.currentTimeMillis()}.jpg")
                    contentResolver.openInputStream(source)?.use { input ->
                        destination.outputStream().use { output -> input.copyTo(output) }
                    } ?: return null
                    destination.absolutePath
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Failed to cache image from $source", e)
                    null
                }
            }

            val cropPickerLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.PickVisualMedia()
            ) { uri ->
                if (uri != null) {
                    val localPath = copyImageUriToCache(uri)
                    startActivity(Intent(this@MainActivity, CropOcrActivity::class.java).apply {
                        putExtra(CropOcrActivity.EXTRA_IMAGE_URI, uri.toString())
                        if (localPath != null) {
                            putExtra(CropOcrActivity.EXTRA_LOCAL_IMAGE_PATH, localPath)
                        }
                        putExtra(CropOcrActivity.EXTRA_INPUT_SOURCE, InputSource.GALLERY.name)
                        clipData = android.content.ClipData.newRawUri("image", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
                }
            }

            fun launchCropScan() {
                cropPickerLauncher.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            }

            fun launchCropForUri(uri: android.net.Uri) {
                val localPath = copyImageUriToCache(uri)
                startActivity(Intent(this@MainActivity, CropOcrActivity::class.java).apply {
                    putExtra(CropOcrActivity.EXTRA_IMAGE_URI, uri.toString())
                    if (localPath != null) {
                        putExtra(CropOcrActivity.EXTRA_LOCAL_IMAGE_PATH, localPath)
                    }
                    putExtra(CropOcrActivity.EXTRA_INPUT_SOURCE, InputSource.GALLERY.name)
                    clipData = android.content.ClipData.newRawUri("image", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            }

            fun onSnippetCopy(snippet: com.freeup.textactions.data.local.db.NoteEntity) {
                val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val textToCopy = if (snippet.title.isNotBlank()) "${snippet.title}\n\n${snippet.content}".trim() else snippet.content
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Snippet", textToCopy))
                Toast.makeText(this@MainActivity, getString(R.string.copied), Toast.LENGTH_SHORT).show()
            }

            // History records that belong to a note, removed together with it and restored by Undo
            val deletedNoteHistory = remember { mutableMapOf<String, List<com.freeup.textactions.domain.model.ActionHistory>>() }

            fun historyLinkedTo(snippet: com.freeup.textactions.data.local.db.NoteEntity) =
                uiState.recentActions.filter { a ->
                    (a.actionType.equals("SAVE_NOTE", ignoreCase = true) || a.actionType.equals("SAVE_TO_NOTE", ignoreCase = true)) &&
                        a.id > 0 && (
                            a.resultText == snippet.id ||
                            a.inputText.trim() == snippet.content.trim() ||
                            a.inputText.trim() == "${snippet.title}\n\n${snippet.content}".trim()
                        )
                }

            fun deleteNotesWithHistory(snippets: List<com.freeup.textactions.data.local.db.NoteEntity>) {
                snippets.forEach { note ->
                    val linked = historyLinkedTo(note)
                    deletedNoteHistory[note.id] = linked
                    linked.forEach { viewModel.deleteAction(it.id) }
                    viewModel.deleteSnippet(note.id)
                }
            }

            fun restoreNotesWithHistory(snippets: List<com.freeup.textactions.data.local.db.NoteEntity>) {
                snippets.forEach { note ->
                    viewModel.createSnippet(
                        title = note.title,
                        content = note.content,
                        category = note.category,
                        isLocked = note.isLocked,
                        isPinned = note.isPinned,
                        isArchived = note.isArchived
                    )
                    deletedNoteHistory.remove(note.id)?.forEach { viewModel.addAction(it) }
                }
            }

            fun onSnippetDelete(snippet: com.freeup.textactions.data.local.db.NoteEntity) {
                deleteNotesWithHistory(listOf(snippet))
                coroutineScope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = getString(R.string.msg_note_deleted),
                        actionLabel = getString(R.string.undo),
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) restoreNotesWithHistory(listOf(snippet))
                }
            }

            // Quiet variants: no snackbar of their own, so a bulk delete can show a single Undo for everything
            fun deleteSnippetsQuiet(snippets: List<com.freeup.textactions.data.local.db.NoteEntity>) {
                snippets.forEach { viewModel.deleteSnippet(it.id) }
            }

            fun restoreSnippets(snippets: List<com.freeup.textactions.data.local.db.NoteEntity>) {
                snippets.forEach {
                    viewModel.createSnippet(
                        title = it.title,
                        content = it.content,
                        category = it.category,
                        isLocked = it.isLocked,
                        isPinned = it.isPinned,
                        isArchived = it.isArchived
                    )
                }
            }

            fun onSaveSnippet(id: String?, title: String, content: String, category: String, isLocked: Boolean, isPinned: Boolean, isArchived: Boolean) {
                if (id == null) {
                    viewModel.createSnippet(title, content, category, isLocked, isPinned, isArchived)
                    val text = if (title.isNotBlank()) "$title\n\n$content" else content
                    viewModel.addAction(
                        com.freeup.textactions.domain.model.ActionHistory(
                            id = 0,
                            actionType = "SAVE_NOTE",
                            inputText = text.trim(),
                            resultText = null,
                            metadata = null,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                } else {
                    viewModel.updateSnippet(id, title, content, category, isLocked, isPinned, isArchived)
                }
            }

            fun onArchiveSnippet(snippet: com.freeup.textactions.data.local.db.NoteEntity) {
                viewModel.archiveSnippet(snippet.id)
            }

            TextActionsTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColorEnabled,
                transparentSystemBars = true
            ) {
                if (showCameraPermissionDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            showCameraPermissionDialog = false
                            pendingScanType = null
                        },
                        title = { Text("Quyền camera") },
                        text = { Text("Ứng dụng cần quyền camera để quét văn bản và mã QR.") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showCameraPermissionDialog = false
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            ) { Text("Cho phép") }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showCameraPermissionDialog = false
                                    pendingScanType = null
                                }
                            ) { Text("Hủy") }
                        }
                    )
                }
                when (currentView) {
                    com.freeup.textactions.ui.home.HomeView.LOCKED -> {
                        VaultScreen(
                            uiState = uiState,
                            onBackClick = { viewModel.setView(com.freeup.textactions.ui.home.HomeView.PUBLIC) },
                            onSnippetCopy = { onSnippetCopy(it) },
                            onSnippetDelete = { onSnippetDelete(it) },
                            onSaveSnippet = { id, title, content, cat, locked, pinned, archived ->
                                onSaveSnippet(id, title, content, cat, locked, pinned, archived)
                            },
                            onToggleNoteLock = { snippet ->
                                if (snippet.isLocked) viewModel.unlockSnippet(snippet)
                                else viewModel.lockSnippet(snippet)
                            },
                            onArchiveSnippet = { onArchiveSnippet(it) },
                            snackbarHostState = snackbarHostState
                        )
                    }
                    else -> {
                        MainDashboardScreen(
                            openEditorOnStart = openEditorTrigger,
                            onEditorOpened = { openEditorTrigger = false },
                            uiState = uiState,
                            currentView = currentView,
                            onToggleLockClick = {
                                android.util.Log.d("LockDebug", "onToggleLockClick lambda called")
                                val view = viewModel.currentView.value
                                if (view != com.freeup.textactions.ui.home.HomeView.LOCKED) {
                                    android.util.Log.d("LockDebug", "View is not LOCKED, triggering authentication")
                                    authenticateUser(
                                        onSuccess = {
                                            android.util.Log.d("LockDebug", "Authentication success, setting view to LOCKED")
                                            viewModel.setView(com.freeup.textactions.ui.home.HomeView.LOCKED)
                                        },
                                        onSecurityError = {
                                            coroutineScope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = getString(R.string.lock_requires_passcode),
                                                    actionLabel = getString(R.string.open_settings),
                                                    duration = SnackbarDuration.Long
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                                                }
                                            }
                                        }
                                    )
                                } else {
                                    android.util.Log.d("LockDebug", "View is LOCKED, switching back to PUBLIC")
                                    viewModel.setView(com.freeup.textactions.ui.home.HomeView.PUBLIC)
                                }
                            },
                            onToggleArchiveClick = {
                                if (currentView != com.freeup.textactions.ui.home.HomeView.ARCHIVED) {
                                    viewModel.setView(com.freeup.textactions.ui.home.HomeView.ARCHIVED)
                                } else {
                                    viewModel.setView(com.freeup.textactions.ui.home.HomeView.PUBLIC)
                                }
                            },
                            onToggleNoteLock = { snippet ->
                                if (snippet.isLocked) viewModel.unlockSnippet(snippet)
                                else viewModel.lockSnippet(snippet)
                            },
                            onSettingsClick = {
                                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                            },
                            onToolClick = { toolId ->
                                when (toolId) {
                                    "ocr", "qr" -> launchScanIfHasCameraPermission(toolId)
                                    "gallery" -> launchScanImageIfHasGalleryPermission()
                                    "crop" -> launchCropScan()
                                    "recent" -> startActivity(Intent(this@MainActivity, RecentActionsActivity::class.java))
                                }
                            },
                            onScanScreenshot = { uri -> launchCropForUri(uri) },
                            onRetryAiStatus = { viewModel.updateAiStatus(com.freeup.textactions.domain.model.AiStatus(com.freeup.textactions.domain.model.AiState.READY)) },
                            onSnippetCopy = { snippet -> onSnippetCopy(snippet) },
                            onSnippetDelete = { snippet -> onSnippetDelete(snippet) },
                            onSnippetsDeleteQuiet = { deleteSnippetsQuiet(it) },
                            onSnippetsRestore = { restoreSnippets(it) },
                            onNotesDelete = { deleteNotesWithHistory(it) },
                            onNotesRestore = { restoreNotesWithHistory(it) },
                            onSaveSnippet = { id, title, content, category, isLocked, isPinned, isArchived ->
                                onSaveSnippet(id, title, content, category, isLocked, isPinned, isArchived)
                            },
                            onArchiveSnippet = { snippet -> onArchiveSnippet(snippet) },
                            onHistoryItemClick = { action ->
                                historyViewModel.rerun(action)
                            },
                            onHistoryRerun = { action -> historyViewModel.rerun(action) },
                            onHistoryDelete = { action -> viewModel.deleteAction(action.id) },
                            onHistoryRestore = { action -> viewModel.addAction(action) },
                            onHistoryTogglePin = { action -> viewModel.setActionPinned(action.id, !action.isPinned) },
                            snackbarHostState = snackbarHostState
                        )
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

    private fun authenticateUser(onSuccess: () -> Unit, onSecurityError: () -> Unit) {
        android.util.Log.d("LockDebug", "authenticateUser called")
        val keyguardManager = getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        android.util.Log.d("LockDebug", "isDeviceSecure: ${keyguardManager.isDeviceSecure}")
        if (!keyguardManager.isDeviceSecure) {
            android.util.Log.w("LockDebug", "Device is not secure, triggering error callback")
            onSecurityError()
            return
        }

        val biometricManager = androidx.biometric.BiometricManager.from(this)
        val authenticators = androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
        
        val canAuth = biometricManager.canAuthenticate(authenticators)
        android.util.Log.d("LockDebug", "canAuthenticate result: $canAuth")
        
        when (canAuth) {
            androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS -> {
                android.util.Log.d("LockDebug", "Starting BiometricPrompt")
                val executor = androidx.core.content.ContextCompat.getMainExecutor(this)
                val biometricPrompt = androidx.biometric.BiometricPrompt(this, executor,
                    object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            android.util.Log.d("LockDebug", "onAuthenticationSucceeded")
                            onSuccess()
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            android.util.Log.e("LockDebug", "onAuthenticationError: $errorCode - $errString")
                            
                            val message = if (errorCode == androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED || 
                                errorCode == androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                                getString(R.string.auth_canceled)
                            } else {
                                getString(R.string.auth_error, errString)
                            }
                            
                            android.widget.Toast.makeText(applicationContext, message, android.widget.Toast.LENGTH_SHORT).show()
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            android.util.Log.w("LockDebug", "onAuthenticationFailed")
                            android.widget.Toast.makeText(applicationContext, getString(R.string.auth_failed), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    })

                val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                    .setTitle(getString(R.string.unlock_note_title))
                    .setSubtitle(getString(R.string.unlock_note_subtitle))
                    .setAllowedAuthenticators(authenticators)
                    .build()
                biometricPrompt.authenticate(promptInfo)
            }
            else -> {
                android.util.Log.w("LockDebug", "canAuthenticate failed with code $canAuth, triggering error callback")
                onSecurityError()
            }
        }
    }
}

/**
 * Localized and user-friendly labels for action type filter chips on Home screen.
 */
private fun getHistoryChipLabel(context: android.content.Context, type: String): String {
    return when (type.uppercase()) {
        "ALL" -> context.getString(R.string.all)
        "SAVE_NOTE", "SAVE_TO_NOTE", "NOTE" -> context.getString(R.string.notes_title)
        "OCR", "OCR_SCAN" -> context.getString(R.string.tool_scan_text)
        "QR", "QR_SCAN" -> context.getString(R.string.tool_scan_qr)
        else -> ActionLabels.getLabelForHistory(context, type)
    }
}

/**
 * Material 3 Speed Dial Floating Action Button for Home screen.
 * Expands to show:
 * 1. Create Snippet
 * 2. Scan QR
 * 3. Scan Text (Camera OCR + Gallery pick)
 */
@Composable
fun SpeedDialFab(
    isExpanded: Boolean,
    onExpandChanged: (Boolean) -> Unit,
    onScanTextClick: () -> Unit,
    onScanQrClick: () -> Unit,
    onCreateSnippetClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 45f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "fabRotation"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(19.dp)
    ) {
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(animationSpec = tween(150)) + slideInVertically(initialOffsetY = { it / 3 }),
            exit = fadeOut(animationSpec = tween(150)) + slideOutVertically(targetOffsetY = { it / 3 })
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Create Snippet Option
                SpeedDialItem(
                    label = stringResource(R.string.add_snippet_title),
                    icon = AppIcons.NotePencil,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onExpandChanged(false)
                        onCreateSnippetClick()
                    }
                )

                // 2. Scan QR Option
                SpeedDialItem(
                    label = stringResource(R.string.tool_scan_qr),
                    icon = AppIcons.QrCode,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onExpandChanged(false)
                        onScanQrClick()
                    }
                )

                // 3. Scan Text (OCR) Option
                SpeedDialItem(
                    label = stringResource(R.string.tool_scan_text),
                    icon = AppIcons.Camera,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onExpandChanged(false)
                        onScanTextClick()
                    }
                )
            }
        }

        // Main FAB (scaled 0.9x from 68dp ≈ 61dp, icon 31dp * 0.9 ≈ 28dp)
        FloatingActionButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onExpandChanged(!isExpanded)
            },
            modifier = Modifier.size(61.dp),
            containerColor = if (isExpanded) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primary,
            contentColor = if (isExpanded) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
        ) {
            Icon(
                imageVector = AppIcons.Plus,
                contentDescription = if (isExpanded) "Close" else "Add or Scan",
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer { rotationZ = rotation }
            )
        }
    }
}

@Composable
private fun SpeedDialItem(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 3.dp,
            onClick = onClick
        ) {
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = NunitoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }

        // Sub FAB (scaled 1.2x: 40dp * 1.2 = 48dp, icon 20dp * 1.2 = 24dp)
        SmallFloatingActionButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
            containerColor = containerColor,
            contentColor = contentColor,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
        ) {
            Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Suppress("UNUSED_PARAMETER")
@Composable
fun MainDashboardScreen(
    openEditorOnStart: Boolean = false,
    onEditorOpened: () -> Unit = {},
    uiState: com.freeup.textactions.ui.home.HomeUiState,
    currentView: com.freeup.textactions.ui.home.HomeView,
    onToggleLockClick: () -> Unit,
    onToggleArchiveClick: () -> Unit,
    onToggleNoteLock: (com.freeup.textactions.data.local.db.NoteEntity) -> Unit,
    onSettingsClick: () -> Unit,
    onToolClick: (String) -> Unit,
    onRetryAiStatus: () -> Unit,
    onSnippetCopy: (com.freeup.textactions.data.local.db.NoteEntity) -> Unit,
    onSnippetDelete: (com.freeup.textactions.data.local.db.NoteEntity) -> Unit,
    onSnippetsDeleteQuiet: (List<com.freeup.textactions.data.local.db.NoteEntity>) -> Unit = {},
    onSnippetsRestore: (List<com.freeup.textactions.data.local.db.NoteEntity>) -> Unit = {},
    onNotesDelete: (List<com.freeup.textactions.data.local.db.NoteEntity>) -> Unit = {},
    onNotesRestore: (List<com.freeup.textactions.data.local.db.NoteEntity>) -> Unit = {},
    onSaveSnippet: (id: String?, title: String, content: String, category: String, isLocked: Boolean, isPinned: Boolean, isArchived: Boolean) -> Unit,
    onArchiveSnippet: (com.freeup.textactions.data.local.db.NoteEntity) -> Unit,
    onScanScreenshot: (android.net.Uri) -> Unit = {},
    onHistoryItemClick: (com.freeup.textactions.domain.model.ActionHistory) -> Unit = {},
    onHistoryRerun: (com.freeup.textactions.domain.model.ActionHistory) -> Unit = {},
    onHistoryDelete: (com.freeup.textactions.domain.model.ActionHistory) -> Unit = {},
    onHistoryRestore: (com.freeup.textactions.domain.model.ActionHistory) -> Unit = {},
    onHistoryTogglePin: (com.freeup.textactions.domain.model.ActionHistory) -> Unit = {},
    snackbarHostState: SnackbarHostState
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var selectedSnippetIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedHistoryIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var isFabExpanded by remember { mutableStateOf(false) }

    val isSelectMode = selectedSnippetIds.isNotEmpty()
    val isHistorySelectMode = selectedHistoryIds.isNotEmpty()

    var detectedScreenshot by remember { mutableStateOf<ScreenshotInfo?>(null) }
    val bannerSettings = remember { SettingsManager(context) }
    var dismissedScreenshotUri by remember {
        mutableStateOf(bannerSettings.getDismissedScreenshotUri()?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() })
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Setting can change while we're away (Settings screen)
                if (!bannerSettings.isScreenshotBannerEnabled()) {
                    detectedScreenshot = null
                    return@LifecycleEventObserver
                }
                val latest = ScreenshotDetector.getLatestScreenshot(context)
                if (latest != null && latest.uri != dismissedScreenshotUri) {
                    detectedScreenshot = latest
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // ── Archived view snippets computation ────────────────────────────────
    val filteredSnippets = remember(uiState.snippets, searchQuery) {
        val query = searchQuery.trim().lowercase()
        val baseSnippets = uiState.snippets
        if (query.isEmpty()) baseSnippets
        else baseSnippets.filter { snippet ->
            snippet.title.lowercase().contains(query) || snippet.content.lowercase().contains(query)
        }
    }
    val allIdsInView = filteredSnippets.map { it.id }.toSet()

    // ── Unified History actions (ActionHistory + Snippets) ───────────────
    val combinedActions = remember(uiState.recentActions, uiState.snippets) {
        val existingActionTexts = uiState.recentActions
            .filter { it.actionType.equals("SAVE_NOTE", ignoreCase = true) || it.actionType.equals("SAVE_TO_NOTE", ignoreCase = true) }
            .map { it.inputText.trim() }
            .toSet()

        fun matchSnippet(a: com.freeup.textactions.domain.model.ActionHistory) =
            uiState.snippets.find {
                it.id == a.resultText ||
                it.content.trim() == a.inputText.trim() ||
                "${it.title}\n\n${it.content}".trim() == a.inputText.trim()
            }
        // Saved-note entries mirror the pin state of their snippet
        val recentWithPin = uiState.recentActions.map { a ->
            if (a.actionType.equals("SAVE_NOTE", true) || a.actionType.equals("SAVE_TO_NOTE", true)) {
                matchSnippet(a)?.let { a.copy(isPinned = it.isPinned, pinnedAt = it.pinnedAt) } ?: a
            } else a
        }

        val syntheticFromSnippets = uiState.snippets
            .filter { !it.isArchived && !it.isLocked }
            .filter { snippet ->
                val text = if (snippet.title.isNotBlank()) "${snippet.title}\n\n${snippet.content}".trim() else snippet.content.trim()
                text !in existingActionTexts
            }
            .map { snippet ->
                com.freeup.textactions.domain.model.ActionHistory(
                    id = -kotlin.math.abs(snippet.id.hashCode().toLong()).coerceAtLeast(1L),
                    actionType = "SAVE_NOTE",
                    inputText = if (snippet.title.isNotBlank()) "${snippet.title}\n\n${snippet.content}".trim() else snippet.content.trim(),
                    resultText = snippet.id,
                    metadata = null,
                    createdAt = snippet.createdAt,
                    isPinned = snippet.isPinned,
                    pinnedAt = snippet.pinnedAt
                )
            }

        (recentWithPin + syntheticFromSnippets)
            .sortedWith(
                compareByDescending<com.freeup.textactions.domain.model.ActionHistory> { it.isPinned }
                    .thenByDescending { it.pinnedAt ?: 0L }
                    .thenByDescending { it.createdAt }
            )
    }

    var selectedActionType by remember { mutableStateOf("All") }

    val actionTypes = remember(combinedActions) {
        val types: MutableList<String> = combinedActions.map { it.actionType }.distinct().toMutableList()
        types.add(0, "All")
        types
    }

    val finalFilteredActions = remember(combinedActions, searchQuery, selectedActionType) {
        val query = searchQuery.trim().lowercase()
        combinedActions.filter { action ->
            val matchesType = selectedActionType == "All" || action.actionType.equals(selectedActionType, ignoreCase = true)
            val matchesQuery = query.isEmpty() ||
                action.inputText.lowercase().contains(query) ||
                getHistoryChipLabel(context, action.actionType).lowercase().contains(query) ||
                (action.resultText?.lowercase()?.contains(query) == true)
            matchesType && matchesQuery
        }
    }

    val titleText = if (currentView == com.freeup.textactions.ui.home.HomeView.ARCHIVED) {
        if (isSelectMode) stringResource(R.string.selected_count, selectedSnippetIds.size)
        else stringResource(R.string.archived_notes_title)
    } else {
        if (isHistorySelectMode) stringResource(R.string.selected_count, selectedHistoryIds.size)
        else stringResource(R.string.dashboard_title)
    }
    val backgroundColor = MaterialTheme.colorScheme.background

    BackHandler(enabled = currentView == com.freeup.textactions.ui.home.HomeView.ARCHIVED) {
        onToggleArchiveClick()
    }

    // ── Editor Sheet state (Create = editorSnippet null, Edit = non-null) ────
    var editorSnippet by remember { mutableStateOf<com.freeup.textactions.data.local.db.NoteEntity?>(null) }
    var showEditorSheet by remember { mutableStateOf(false) }
    val editorSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Handle deep link from widget "+" button
    LaunchedEffect(openEditorOnStart) {
        if (openEditorOnStart) {
            showEditorSheet = true
            onEditorOpened()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (currentView == com.freeup.textactions.ui.home.HomeView.ARCHIVED) {
                        if (isSelectMode) {
                            IconButton(onClick = { selectedSnippetIds = emptySet() }) {
                                Icon(AppIcons.Close, contentDescription = "Close")
                            }
                        } else {
                            IconButton(onClick = onToggleArchiveClick) {
                                Icon(
                                    imageVector = AppIcons.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    } else {
                        if (isHistorySelectMode) {
                            IconButton(onClick = { selectedHistoryIds = emptySet() }) {
                                Icon(AppIcons.Close, contentDescription = "Close")
                            }
                        }
                    }
                },
                title = {
                    Text(
                        text = titleText,
                        style = TextStyle(
                            fontFamily = NunitoFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = if (isSelectMode || isHistorySelectMode) 22.sp else 28.sp,
                            letterSpacing = MaterialTheme.typography.headlineSmall.letterSpacing,
                            fontSynthesis = FontSynthesis.Weight
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                },
                actions = {
                    if (currentView == com.freeup.textactions.ui.home.HomeView.ARCHIVED) {
                        if (isSelectMode) {
                            val allSelected = selectedSnippetIds.containsAll(allIdsInView) && allIdsInView.isNotEmpty()
                            // Once everything is selected the close (X) on the left already clears the selection
                            if (!allSelected) IconButton(onClick = { selectedSnippetIds = allIdsInView }) {
                                Icon(
                                    imageVector = AppIcons.SelectAll,
                                    contentDescription = stringResource(R.string.action_select_all)
                                )
                            }
                            IconButton(onClick = {
                                selectedSnippetIds.forEach { id ->
                                    val s = uiState.snippets.find { it.id == id }
                                    if (s != null) onArchiveSnippet(s)
                                }
                                selectedSnippetIds = emptySet()
                            }) {
                                Icon(AppIcons.Archive, contentDescription = null)
                            }
                            IconButton(onClick = {
                                showBulkDeleteConfirm = true
                            }) {
                                Icon(AppIcons.Trash, contentDescription = null)
                            }
                        }
                    } else if (isHistorySelectMode) {
                        val allSelected = finalFilteredActions.isNotEmpty() && selectedHistoryIds.containsAll(finalFilteredActions.map { it.id }.toSet())
                        if (!allSelected) IconButton(onClick = {
                            selectedHistoryIds = finalFilteredActions.map { it.id }.toSet()
                        }) {
                            Icon(
                                imageVector = AppIcons.SelectAll,
                                contentDescription = stringResource(R.string.action_select_all)
                            )
                        }
                        IconButton(onClick = {
                            val selectedActions = finalFilteredActions.filter { selectedHistoryIds.contains(it.id) }
                            val linkedSnippets = selectedActions
                                .filter { it.actionType.equals("SAVE_NOTE", ignoreCase = true) || it.actionType.equals("SAVE_TO_NOTE", ignoreCase = true) }
                                .mapNotNull { action ->
                                    uiState.snippets.find {
                                        it.id == action.resultText ||
                                        it.content.trim() == action.inputText.trim() ||
                                        "${it.title}\n\n${it.content}".trim() == action.inputText.trim()
                                    }
                                }
                                .distinctBy { it.id }
                            selectedActions.forEach { onHistoryDelete(it) }
                            onSnippetsDeleteQuiet(linkedSnippets)
                            selectedHistoryIds = emptySet()
                            coroutineScope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = historyDeletedMessage(context, selectedActions),
                                    actionLabel = context.getString(R.string.undo),
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    selectedActions.forEach { onHistoryRestore(it) }
                                    onSnippetsRestore(linkedSnippets)
                                }
                            }
                        }) {
                            Icon(
                                imageVector = AppIcons.Trash,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        IconButton(onClick = onToggleArchiveClick) {
                            Icon(
                                imageVector = AppIcons.Archive,
                                contentDescription = stringResource(R.string.action_toggle_archived),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onToggleLockClick) {
                            Icon(
                                imageVector = AppIcons.Lock,
                                contentDescription = "Toggle Locked Snippets",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(
                                imageVector = AppIcons.Gear,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor)
            )
        },
        floatingActionButton = {
            if (currentView == com.freeup.textactions.ui.home.HomeView.PUBLIC && !isHistorySelectMode) {
                SpeedDialFab(
                    isExpanded = isFabExpanded,
                    onExpandChanged = { isFabExpanded = it },
                    onScanTextClick = {
                        focusManager.clearFocus()
                        onToolClick("ocr")
                    },
                    onScanQrClick = {
                        focusManager.clearFocus()
                        onToolClick("qr")
                    },
                    onCreateSnippetClick = {
                        focusManager.clearFocus()
                        editorSnippet = null
                        showEditorSheet = true
                    }
                )
            }
        },
        containerColor = backgroundColor,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (currentView == com.freeup.textactions.ui.home.HomeView.ARCHIVED) {
                // Archived notes list view
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 24.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { focusManager.clearFocus() })
                        },
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    if (filteredSnippets.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 80.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.notes_empty_title),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 18.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    } else {
                        itemsIndexed(
                            items = filteredSnippets,
                            key = { _, snippet -> snippet.id }
                        ) { _, snippet ->
                            SnippetCard(
                                snippet = snippet,
                                isSelected = snippet.id in selectedSnippetIds,
                                onActionClick = { 
                                    val intent = Intent(context, ProcessTextActivity::class.java).apply {
                                        action = Intent.ACTION_PROCESS_TEXT
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_PROCESS_TEXT, snippet.content)
                                        putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
                                        putExtra("EXTRA_HIDE_SAVE_ACTION", true)
                                        putExtra("EXTRA_SUPPRESS_HISTORY", true)
                                    }
                                    context.startActivity(intent)
                                },
                                onDeleteClick = { onSnippetDelete(snippet) },
                                onLongClick = {
                                    if (isSelectMode) {
                                        selectedSnippetIds = if (snippet.id in selectedSnippetIds)
                                            selectedSnippetIds - snippet.id
                                        else
                                            selectedSnippetIds + snippet.id
                                    } else {
                                        selectedSnippetIds = selectedSnippetIds + snippet.id
                                    }
                                },
                                onClick = {
                                    if (isSelectMode) {
                                        selectedSnippetIds = if (snippet.id in selectedSnippetIds)
                                            selectedSnippetIds - snippet.id
                                        else
                                            selectedSnippetIds + snippet.id
                                    } else {
                                        editorSnippet = snippet
                                        showEditorSheet = true
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                        }
                    }
                }
            } else {
                // Main History Feed (PUBLIC view)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                focusManager.clearFocus()
                                if (isFabExpanded) isFabExpanded = false
                            })
                        }
                ) {
                    // 1-2. Search bar + filter chips. In landscape (short screen) they share one row
                    // so the list keeps most of the height.
                    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
                        android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    if (isLandscape) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SearchBar(
                                modifier = Modifier.weight(0.8f),
                                searchQuery = searchQuery,
                                onSearchQueryChange = { searchQuery = it },
                                placeholderText = stringResource(R.string.search_history)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(actionTypes) { type ->
                                    val isSelected = selectedActionType == type
                                    val label = getHistoryChipLabel(context, type)
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
                                        shape = CircleShape
                                    )
                                }
                            }
                        }
                    } else {
                    SearchBar(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        placeholderText = stringResource(R.string.search_history)
                    )

                    // 2. Action Type Filter Chips
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        items(actionTypes) { type ->
                            val isSelected = selectedActionType == type
                            val label = getHistoryChipLabel(context, type)
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
                                shape = CircleShape
                            )
                        }
                    }

                    }

                    // 3. Screenshot Detection Banner
                    if (!isLandscape && detectedScreenshot != null && detectedScreenshot?.uri != dismissedScreenshotUri) {
                        ScreenshotDetectionBanner(
                            screenshot = detectedScreenshot!!,
                            onScanClick = {
                                focusManager.clearFocus()
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onScanScreenshot(detectedScreenshot!!.uri)
                            },
                            onDismiss = {
                                dismissedScreenshotUri = detectedScreenshot?.uri
                                bannerSettings.setDismissedScreenshotUri(dismissedScreenshotUri?.toString())
                            },
                            onNeverShowAgain = {
                                bannerSettings.setScreenshotBannerEnabled(false)
                                detectedScreenshot = null
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.screenshot_banner_turned_off))
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // 4. History List Feed
                    if (uiState.isLoading) {
                        HistoryListSkeleton()
                    } else if (searchQuery.isNotBlank() && finalFilteredActions.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.no_results_for, searchQuery),
                                    style = TextStyle(
                                        fontFamily = NunitoFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            FullRecentActionsList(
                                recentActions = finalFilteredActions,
                                selectionMode = isHistorySelectMode,
                                selectedIds = selectedHistoryIds,
                                onItemClick = { action ->
                                    if (action.actionType.equals("SAVE_NOTE", ignoreCase = true) || action.actionType.equals("SAVE_TO_NOTE", ignoreCase = true)) {
                                        val matching = uiState.snippets.find {
                                            it.id == action.resultText ||
                                            it.content.trim() == action.inputText.trim() ||
                                            "${it.title}\n\n${it.content}".trim() == action.inputText.trim()
                                        }
                                        if (matching != null) {
                                            editorSnippet = matching
                                        } else {
                                            editorSnippet = com.freeup.textactions.data.local.db.NoteEntity(
                                                id = java.util.UUID.randomUUID().toString(),
                                                title = "",
                                                content = action.inputText,
                                                createdAt = action.createdAt
                                            )
                                        }
                                        showEditorSheet = true
                                    } else {
                                        onHistoryItemClick(action)
                                    }
                                },
                                onRerunClick = { action -> onHistoryRerun(action) },
                                onTogglePin = { action ->
                                    val isNote = action.actionType.equals("SAVE_NOTE", ignoreCase = true) || action.actionType.equals("SAVE_TO_NOTE", ignoreCase = true)
                                    val snippet = if (isNote) uiState.snippets.find {
                                        it.id == action.resultText ||
                                        it.content.trim() == action.inputText.trim() ||
                                        "${it.title}\n\n${it.content}".trim() == action.inputText.trim()
                                    } else null
                                    if (snippet != null) {
                                        onSaveSnippet(snippet.id, snippet.title, snippet.content, snippet.category, snippet.isLocked, !snippet.isPinned, snippet.isArchived)
                                    } else {
                                        onHistoryTogglePin(action)
                                    }
                                },
                                onCopyClick = { action ->
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("History", action.inputText))
                                    Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
                                },
                                onShareClick = { action ->
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, action.inputText)
                                    }
                                    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_title)))
                                },
                                onToggleSelect = { id ->
                                    selectedHistoryIds = if (selectedHistoryIds.contains(id)) selectedHistoryIds - id else selectedHistoryIds + id
                                },
                                onLongPressSelect = { id ->
                                    selectedHistoryIds = if (selectedHistoryIds.contains(id)) selectedHistoryIds - id else selectedHistoryIds + id
                                },
                                onDeleteAction = { action ->
                                    onHistoryDelete(action)
                                    val linkedSnippets = if (action.actionType.equals("SAVE_NOTE", ignoreCase = true) || action.actionType.equals("SAVE_TO_NOTE", ignoreCase = true)) {
                                        uiState.snippets.find {
                                            it.id == action.resultText ||
                                            it.content.trim() == action.inputText.trim() ||
                                            "${it.title}\n\n${it.content}".trim() == action.inputText.trim()
                                        }?.let { listOf(it) } ?: emptyList()
                                    } else emptyList()
                                    onSnippetsDeleteQuiet(linkedSnippets)
                                    coroutineScope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = historyDeletedMessage(context, listOf(action)),
                                            actionLabel = context.getString(R.string.undo),
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            onHistoryRestore(action)
                                            onSnippetsRestore(linkedSnippets)
                                        }
                                    }
                                },
                                onScanTextClick = {
                                    focusManager.clearFocus()
                                    onToolClick("ocr")
                                },
                                onScanQrClick = {
                                    focusManager.clearFocus()
                                    onToolClick("qr")
                                },
                                onCreateSnippetClick = {
                                    focusManager.clearFocus()
                                    editorSnippet = null
                                    showEditorSheet = true
                                }
                            )
                        }
                    }
                }
            }

            // Full-screen dim scrim for Speed Dial FAB
            AnimatedVisibility(
                visible = isFabExpanded,
                enter = fadeIn(animationSpec = tween(150)),
                exit = fadeOut(animationSpec = tween(150))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            isFabExpanded = false
                        }
                )
            }
        }

        if (showBulkDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showBulkDeleteConfirm = false },
                title = { Text(stringResource(R.string.confirm_delete_title)) },
                text = { Text(stringResource(R.string.confirm_delete_note)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val toDelete = uiState.snippets.filter { it.id in selectedSnippetIds }
                            onNotesDelete(toDelete)
                            selectedSnippetIds = emptySet()
                            showBulkDeleteConfirm = false
                            coroutineScope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = if (toDelete.size > 1) context.getString(R.string.msg_notes_deleted_count, toDelete.size)
                                    else context.getString(R.string.msg_note_deleted),
                                    actionLabel = context.getString(R.string.undo),
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) onNotesRestore(toDelete)
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBulkDeleteConfirm = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        // ── Unified Snippet Editor Sheet ────────────────────────────────────
        if (showEditorSheet) {
            SnippetEditorSheet(
                snippet = editorSnippet,
                sheetState = editorSheetState,
                onDismiss = {
                    showEditorSheet = false
                    editorSnippet = null
                },
                onSave = { title, content, category, isLocked, isPinned, isArchived ->
                    onSaveSnippet(editorSnippet?.id, title, content, category, isLocked, isPinned, isArchived)
                },
                onDelete = { snippetId ->
                    editorSnippet?.let { onSnippetDelete(it) }
                },
                onArchive = {
                    editorSnippet?.let { onArchiveSnippet(it) }
                },
                snackbarHostState = snackbarHostState
            )
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VaultScreen(
    uiState: com.freeup.textactions.ui.home.HomeUiState,
    onBackClick: () -> Unit,
    onSnippetCopy: (com.freeup.textactions.data.local.db.NoteEntity) -> Unit,
    onSnippetDelete: (com.freeup.textactions.data.local.db.NoteEntity) -> Unit,
    onSaveSnippet: (id: String?, title: String, content: String, category: String, isLocked: Boolean, isPinned: Boolean, isArchived: Boolean) -> Unit,
    onToggleNoteLock: (com.freeup.textactions.data.local.db.NoteEntity) -> Unit,
    onArchiveSnippet: (com.freeup.textactions.data.local.db.NoteEntity) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedSnippetIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    val isSelectMode = selectedSnippetIds.isNotEmpty()

    val filteredSnippets = remember(uiState.snippets, searchQuery) {
        val query = searchQuery.trim().lowercase()
        val baseSnippets = uiState.snippets
        if (query.isEmpty()) baseSnippets
        else baseSnippets.filter { snippet ->
            snippet.title.lowercase().contains(query) || snippet.content.lowercase().contains(query)
        }
    }

    val selectedSnippets = remember(filteredSnippets, selectedSnippetIds) {
        filteredSnippets.filter { it.id in selectedSnippetIds }
    }
    val allIdsInView = filteredSnippets.map { it.id }.toSet()
    val isAllSelected = selectedSnippetIds.containsAll(allIdsInView) && allIdsInView.isNotEmpty()

    val titleText = if (isSelectMode) stringResource(R.string.selected_count, selectedSnippetIds.size) else stringResource(R.string.vault_title)
    val backgroundColor = MaterialTheme.colorScheme.background

    BackHandler {
        onBackClick()
    }

    var editorSnippet by remember { mutableStateOf<com.freeup.textactions.data.local.db.NoteEntity?>(null) }
    var showEditorSheet by remember { mutableStateOf(false) }
    val editorSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (isSelectMode) {
                        IconButton(onClick = { selectedSnippetIds = emptySet() }) {
                            Icon(AppIcons.Close, contentDescription = "Close")
                        }
                    } else {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = AppIcons.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                title = {
                    Text(
                        text = titleText,
                        style = TextStyle(
                            fontFamily = NunitoFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = if (isSelectMode) 22.sp else 28.sp,
                            letterSpacing = MaterialTheme.typography.headlineSmall.letterSpacing,
                            fontSynthesis = FontSynthesis.Weight
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                actions = {
                    if (isSelectMode) {
                        if (!isAllSelected) IconButton(onClick = { selectedSnippetIds = allIdsInView }) {
                            Icon(
                                imageVector = AppIcons.SelectAll,
                                contentDescription = stringResource(R.string.action_select_all)
                            )
                        }
                        IconButton(onClick = {
                            selectedSnippetIds.forEach { id ->
                                val s = uiState.snippets.find { it.id == id }
                                if (s != null) onToggleNoteLock(s)
                            }
                            selectedSnippetIds = emptySet()
                        }) {
                            Icon(AppIcons.LockOpen, contentDescription = "Unlock")
                        }
                        IconButton(onClick = {
                            showBulkDeleteConfirm = true
                        }) {
                            Icon(AppIcons.Trash, contentDescription = null)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor)
            )
        },
        containerColor = backgroundColor
    ) { padding ->
        val context = LocalContext.current
        var snippetToLock by remember { mutableStateOf<com.freeup.textactions.data.local.db.NoteEntity?>(null) }
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                },
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            stickyHeader(key = "search_bar_vault") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(backgroundColor)
                        .padding(top = 8.dp, bottom = 12.dp)
                ) {
                    SearchBar(
                        modifier = Modifier.fillMaxWidth(),
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        placeholderText = stringResource(R.string.hint_search_vault)
                    )
                }
            }

            if (filteredSnippets.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_empty_snippets),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(144.dp)
                            )
                            Text(
                                text = if (searchQuery.isNotEmpty()) stringResource(R.string.no_results_found) else stringResource(R.string.vault_empty_title),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 22.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = filteredSnippets,
                    key = { _, snippet -> snippet.id }
                ) { _, snippet ->
                    SnippetCard(
                        snippet = snippet,
                        isSelected = snippet.id in selectedSnippetIds,
                        onActionClick = {
                            val intent = Intent(context, ProcessTextActivity::class.java).apply {
                                action = Intent.ACTION_PROCESS_TEXT
                                type = "text/plain"
                                putExtra(Intent.EXTRA_PROCESS_TEXT, snippet.content)
                                putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
                                putExtra("EXTRA_HIDE_SAVE_ACTION", true)
                                putExtra("EXTRA_SUPPRESS_HISTORY", true)
                            }
                            context.startActivity(intent)
                        },
                        onLongClick = {
                            if (isSelectMode) {
                                selectedSnippetIds = if (snippet.id in selectedSnippetIds)
                                    selectedSnippetIds - snippet.id
                                else
                                    selectedSnippetIds + snippet.id
                            } else {
                                selectedSnippetIds = selectedSnippetIds + snippet.id
                            }
                        },
                        onDeleteClick = { onSnippetDelete(snippet) },
                        onClick = {
                            if (isSelectMode) {
                                selectedSnippetIds = if (snippet.id in selectedSnippetIds)
                                    selectedSnippetIds - snippet.id
                                else
                                    selectedSnippetIds + snippet.id
                            } else {
                                editorSnippet = snippet
                                showEditorSheet = true
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                }
            }
        }
        if (showBulkDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showBulkDeleteConfirm = false },
                title = { Text(stringResource(R.string.confirm_delete_title)) },
                text = { Text(stringResource(R.string.confirm_delete_note)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            selectedSnippets.forEach { onSnippetDelete(it) }
                            selectedSnippetIds = emptySet()
                            showBulkDeleteConfirm = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBulkDeleteConfirm = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (snippetToLock != null) {
            AlertDialog(
                onDismissRequest = { snippetToLock = null },
                title = { Text(stringResource(R.string.dialog_unlock_snippet_title)) },
                text = { Text(stringResource(R.string.dialog_unlock_snippet_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        snippetToLock?.let { onToggleNoteLock(it) }
                        snippetToLock = null
                    }) { Text(stringResource(R.string.action_unlock)) }
                },
                dismissButton = {
                    TextButton(onClick = { snippetToLock = null }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }

        if (showEditorSheet) {
            SnippetEditorSheet(
                snippet = editorSnippet,
                sheetState = editorSheetState,
                onDismiss = {
                    showEditorSheet = false
                    editorSnippet = null
                },
                onSave = { title, content, category, isLocked, isPinned, isArchived ->
                    onSaveSnippet(editorSnippet?.id, title, content, category, isLocked, isPinned, isArchived)
                },
                onDelete = { snippetId ->
                    editorSnippet?.let { onSnippetDelete(it) }
                },
                onArchive = {
                    // Lock screen probably doesn't need direct archive, but keeping it for consistency
                    editorSnippet?.let { 
                        // Implementation for onArchive in Vault
                    }
                    showEditorSheet = false
                    editorSnippet = null
                },
                snackbarHostState = snackbarHostState
            )
        }
    }
}


@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SnippetCard(
    snippet: com.freeup.textactions.data.local.db.NoteEntity,
    isSelected: Boolean = false,
    onActionClick: () -> Unit,
    onLongClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val dateText = remember(snippet.createdAt) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(snippet.createdAt))
    }

    var isDismissed by remember { mutableStateOf(false) }
    var isRowVisible by remember { mutableStateOf(true) }
    var itemWidth by remember { mutableFloatStateOf(1f) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                isDismissed = true
                true
            } else false
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.67f }
    )

    LaunchedEffect(isDismissed) {
        if (isDismissed) {
            kotlinx.coroutines.delay(350L)
            isRowVisible = false
            onDeleteClick()
        }
    }

    AnimatedVisibility(
        visible = isRowVisible,
        exit = shrinkVertically(animationSpec = tween(280, easing = FastOutSlowInEasing)) +
               fadeOut(animationSpec = tween(200))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { itemWidth = it.width.toFloat().coerceAtLeast(1f) }
        ) {
            SwipeToDismissBox(
                state = dismissState,
                enableDismissFromStartToEnd = false,
                enableDismissFromEndToStart = true,
                backgroundContent = {
                    val offsetX = try { dismissState.requireOffset() } catch (_: Exception) { 0f }
                    val swipeFraction = ((-offsetX) / itemWidth).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = swipeFraction)
                            ),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Icon(
                            imageVector = AppIcons.Trash,
                            contentDescription = "Xóa",
                            tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = swipeFraction),
                            modifier = Modifier
                                .padding(end = 20.dp)
                                .scale((0.5f + 0.5f * swipeFraction).coerceIn(0.5f, 1f))
                        )
                    }
                }
            ) {
                Column {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = onClick,
                                onLongClick = onLongClick
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)) else null
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val hasTitle = snippet.title.isNotBlank()
                            
                            // ── Row 1: Text Content (Title & Body) + Copy Action ──────────
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                ) {
                                    if (hasTitle) {
                                        // Case: Has Title -> Title (Bold) + Content (Normal, 1 line)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (snippet.isPinned) {
                                                Icon(
                                                    imageVector = AppIcons.PinFill,
                                                    contentDescription = "Pinned",
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                            }
                                            Text(
                                                text = snippet.title,
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = snippet.content,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    } else {
                                        // Case: No Title -> Content becomes primary (SemiBold, 2 lines max)
                                        Row(verticalAlignment = Alignment.Top) {
                                            if (snippet.isPinned) {
                                                Icon(
                                                    imageVector = AppIcons.PinFill,
                                                    contentDescription = "Pinned",
                                                    modifier = Modifier.size(14.dp).padding(top = 4.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                            }
                                            Text(
                                                text = snippet.content,
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 18.sp
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 2,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                
                                IconButton(
                                    onClick = onActionClick,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Sparkle,
                                        contentDescription = stringResource(R.string.section_smart_suggestions),
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(14.dp))
                            
                            // ── Row 3: Chip Tag (trái) + DateTime (phải) ─────────
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = getCategoryLabel(context, snippet.category),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        maxLines = 1
                                    )
                                }
                                
                                Text(
                                    text = dateText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                }
            }
        }
    }
}

@Composable
fun DashboardSection(
    title: String,
    content: @Composable () -> Unit,
    onSeeAllClick: (() -> Unit)? = null,
    seeAllText: String? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = TextStyle(
                    fontFamily = NunitoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    letterSpacing = MaterialTheme.typography.titleLarge.letterSpacing,
                    fontSynthesis = FontSynthesis.Weight
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (seeAllText != null && onSeeAllClick != null) {
                Text(
                    text = seeAllText,
                    style = TextStyle(
                        fontFamily = NunitoFontFamily(500),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
                        letterSpacing = MaterialTheme.typography.bodyLarge.letterSpacing,
                        fontSynthesis = FontSynthesis.Weight
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onSeeAllClick)
                )
            }
        }
            Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
fun ScreenshotDetectionBanner(
    screenshot: ScreenshotInfo,
    onScanClick: () -> Unit,
    onDismiss: () -> Unit,
    onNeverShowAgain: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDarkTheme = MaterialTheme.colorScheme.background == DarkBackground

    val baseBackground = if (isDarkTheme) Color(0xFF282A2F) else Color(0xFFF0F4F8)
    val contentColor = if (isDarkTheme) Color(0xFFE2E2E2) else Color(0xFF1E1E1E)
    val iconColor = if (isDarkTheme) Color(0xFFA6C8FF) else Color(0xFF0057B7)

    val backgroundColor by animateColorAsState(
        targetValue = if (isPressed) baseBackground.copy(alpha = 0.8f) else baseBackground,
        label = "bannerBackground"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(),
                onClick = onScanClick
            ),
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = AppIcons.Camera,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.screenshot_detected_title),
                    style = TextStyle(
                        fontFamily = NunitoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        letterSpacing = 0.sp
                    ),
                    color = contentColor,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.screenshot_detected_subtitle),
                    style = TextStyle(
                        fontFamily = NunitoFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        letterSpacing = 0.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.screenshot_banner_never_show),
                    style = TextStyle(
                        fontFamily = NunitoFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        letterSpacing = 0.sp
                    ),
                    color = iconColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onNeverShowAgain)
                        .padding(vertical = 4.dp)
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = AppIcons.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun QuickToolButton(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    lightContainerColor: Color,
    darkContainerColor: Color,
    lightContentColor: Color,
    darkContentColor: Color,
    lightIconColor: Color = lightContentColor,
    darkIconColor: Color = darkContentColor,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDarkTheme = MaterialTheme.colorScheme.background == DarkBackground

    val baseBackground = if (isDarkTheme) darkContainerColor else lightContainerColor
    val contentColor = if (isDarkTheme) darkContentColor else lightContentColor
    val iconColor = if (isDarkTheme) darkIconColor else lightIconColor

    val backgroundColor by animateColorAsState(
        targetValue = if (isPressed) baseBackground.copy(alpha = 0.8f) else baseBackground,
        label = "backgroundColor"
    )

    Surface(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(),
                onClick = onClick
            ),
        color = backgroundColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = title,
                style = TextStyle(
                    fontFamily = NunitoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 0.sp
                ),
                color = contentColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CompactToolCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    accentColor: Color,
    darkAccentColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDarkTheme = MaterialTheme.colorScheme.background == DarkBackground

    val containerColor = if (isDarkTheme) Color(0xFF282A2F) else Color(0xFFF0F4F8)
    val contentColor = if (isDarkTheme) Color(0xFFE2E2E2) else Color(0xFF1E1E1E)
    val iconTint = if (isDarkTheme) darkAccentColor else accentColor

    val backgroundColor by animateColorAsState(
        targetValue = if (isPressed) containerColor.copy(alpha = 0.8f) else containerColor,
        label = "toolCardBg"
    )

    Surface(
        modifier = modifier
            .height(68.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(),
                onClick = onClick
            ),
        color = backgroundColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = TextStyle(
                    fontFamily = NunitoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 0.sp
                ),
                color = contentColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    placeholderText: String? = null,
    containerColor: Color? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val baseBackground = containerColor ?: elevatedCardColor(
        colorScheme = colorScheme
    )
    val resolvedPlaceholder = placeholderText ?: stringResource(R.string.search_in_history)
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        placeholder = {
            Text(
                text = resolvedPlaceholder,
                style = TextStyle(
                    fontFamily = NunitoFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = baseBackground,
            unfocusedContainerColor = baseBackground,
            cursorColor = colorScheme.primary,
            focusedTextColor = colorScheme.onSurface,
            unfocusedTextColor = colorScheme.onSurface
        ),
        textStyle = TextStyle(
            fontFamily = NunitoFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp
        ),
        leadingIcon = {
            Icon(
                imageVector = AppIcons.Search,
                contentDescription = "Search",
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchQueryChange("") }) {
                    Icon(
                        imageVector = AppIcons.Close,
                        contentDescription = "Clear",
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    )
}

@Composable
fun AIStatusCard(
    aiStatus: com.freeup.textactions.domain.model.AiStatus,
    onRetry: () -> Unit
) {
    val cardBackground = elevatedCardColor(
        colorScheme = MaterialTheme.colorScheme
    ) // Item dùng surface để tương phản với nền

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (aiStatus.state == AiState.ERROR) {
                    Modifier.clickable(onClick = onRetry)
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedContent(
                targetState = aiStatus.state,
                transitionSpec = {
                    fadeIn(animationSpec = stateTransitionSpec) togetherWith
                            fadeOut(animationSpec = stateTransitionSpec)
                },
                label = "aiStatusIcon"
            ) { state ->
                when (state) {
                    AiState.READY -> {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            color = GreenBackground
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = AppIcons.Cpu,
                                    contentDescription = null,
                                    tint = GreenSuccess,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                    AiState.INITIALIZING -> {
                        PulsingIcon(
                            icon = AppIcons.Cpu,
                            tint = TealPrimary
                        )
                    }
                    AiState.DOWNLOADING -> {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            color = GreenBackground
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = GreenSuccess
                                )
                            }
                        }
                    }
                    AiState.ERROR -> {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            color = AmberBackground
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = AppIcons.Warning,
                                    contentDescription = null,
                                    tint = AmberError,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            AnimatedContent(
                targetState = aiStatus.state,
                transitionSpec = {
                    fadeIn(animationSpec = stateTransitionSpec) togetherWith
                            fadeOut(animationSpec = stateTransitionSpec)
                },
                label = "aiStatusText"
            ) { state ->
                Column {
                    Text(
                        text = stringResource(R.string.ai_engine_title),
                        style = TextStyle(
                            fontFamily = NunitoFontFamily, // Sử dụng base FontFamily
                            fontWeight = FontWeight.Bold, // Changed to Bold
                            fontSize = 18.sp, // Increased size
                            lineHeight = 28.sp,
                            letterSpacing = MaterialTheme.typography.titleMedium.letterSpacing,
                            fontSynthesis = FontSynthesis.Weight
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    when (state) {
                        AiState.READY -> {
                            Text(
                                text = stringResource(R.string.ai_engine_ready),
                                style = TextStyle(
                                    fontFamily = NunitoFontFamily(500), // Sử dụng helper function với weight 500 (Medium) để đậm hơn
                                    fontSize = (MaterialTheme.typography.bodyMedium.fontSize.value + 2).sp,
                                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                                    letterSpacing = MaterialTheme.typography.bodyMedium.letterSpacing,
                                    fontSynthesis = FontSynthesis.Weight
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.ai_engine_offline),
                                style = TextStyle(
                                    fontFamily = NunitoFontFamily(500), // Sử dụng helper function với weight 500 (Medium) để đậm hơn
                                    fontSize = (MaterialTheme.typography.bodySmall.fontSize.value + 2).sp,
                                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                                    letterSpacing = MaterialTheme.typography.bodySmall.letterSpacing,
                                    fontSynthesis = FontSynthesis.Weight
                                ),
                                color = Color.Gray
                            )
                        }
                        AiState.INITIALIZING -> {
                            Text(
                                text = stringResource(R.string.ai_engine_initializing),
                                style = TextStyle(
                                    fontFamily = NunitoFontFamily(500), // Sử dụng helper function với weight 500 (Medium) để đậm hơn
                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                                    letterSpacing = MaterialTheme.typography.bodyMedium.letterSpacing,
                                    fontSynthesis = FontSynthesis.Weight
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        AiState.DOWNLOADING -> {
                            Text(
                                text = stringResource(R.string.ai_engine_initializing),
                                style = TextStyle(
                                    fontFamily = NunitoFontFamily(500), // Sử dụng helper function với weight 500 (Medium) để đậm hơn
                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                                    letterSpacing = MaterialTheme.typography.bodyMedium.letterSpacing,
                                    fontSynthesis = FontSynthesis.Weight
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        AiState.ERROR -> {
                            Text(
                                text = stringResource(R.string.ai_engine_unavailable),
                                style = TextStyle(
                                    fontFamily = NunitoFontFamily(500), // Sử dụng helper function với weight 500 (Medium) để đậm hơn
                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                                    letterSpacing = MaterialTheme.typography.bodyMedium.letterSpacing,
                                    fontSynthesis = FontSynthesis.Weight
                                ),
                                color = AmberError
                            )
                            Text(
                                text = stringResource(R.string.tap_to_retry),
                                style = TextStyle(
                                    fontFamily = NunitoFontFamily(500), // Sử dụng helper function với weight 500 (Medium) để đậm hơn
                                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                                    letterSpacing = MaterialTheme.typography.bodySmall.letterSpacing,
                                    fontSynthesis = FontSynthesis.Weight
                                ),
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PulsingIcon(
    icon: ImageVector,
    tint: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    
    Surface(
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.background
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint.copy(alpha = alpha),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun RecentActionsList(
    recentActions: List<com.freeup.textactions.domain.model.ActionHistory>,
    onItemClick: (com.freeup.textactions.domain.model.ActionHistory) -> Unit = {},
    onRerunClick: (com.freeup.textactions.domain.model.ActionHistory) -> Unit = {},
    onCopyClick: (com.freeup.textactions.domain.model.ActionHistory) -> Unit = {},
    onShareClick: (com.freeup.textactions.domain.model.ActionHistory) -> Unit = {},
    selectionMode: Boolean = false,
    selectedIds: Set<Long> = emptySet(),
    onToggleSelect: (Long) -> Unit = {},
    onLongPressSelect: (Long) -> Unit = {},
    onDeleteAction: (com.freeup.textactions.domain.model.ActionHistory) -> Unit = {},
    onTogglePin: (com.freeup.textactions.domain.model.ActionHistory) -> Unit = {}
) {
    if (recentActions.isEmpty()) {
        RecentActionsEmptyState()
    } else {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            recentActions.forEachIndexed { index, action ->
                key(action.id) {
                    HistoryItemCard(
                        action = action,
                        isFirst = index == 0,
                        isLast = index == recentActions.size - 1,
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
}

@Composable
fun RecentActionsEmptyState() {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = fadeInSpec) + slideInVertically(initialOffsetY = { it / 4 }),
            label = "emptyStateFadeIn"
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = AppIcons.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = stringResource(R.string.no_actions_yet),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.history_empty_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun getHistoryActionIcon(actionType: String): ImageVector = when (actionType.uppercase()) {
    "CALL" -> AppIcons.Phone
    "SMS" -> AppIcons.Sms
    "EMAIL" -> AppIcons.Envelope
    "MAP", "GPS_MAP", "DIRECTION" -> AppIcons.Map
    "SAVE_CONTACT" -> AppIcons.UserPlus
    "TRANSLATE" -> AppIcons.Translate
    "SEARCH" -> AppIcons.Search
    "COPY" -> AppIcons.Copy
    "QUICK_SHARE", "SHARE" -> AppIcons.Share
    "REMINDER" -> AppIcons.Bell
    "CALENDAR" -> AppIcons.Calendar
    "SAVE_NOTE", "SAVE_TO_NOTE" -> AppIcons.Note
    "SUMMARIZE" -> AppIcons.Summarize
    "CREATE_QR", "QR", "QR_SCAN" -> AppIcons.QrCode
    "OCR", "OCR_SCAN" -> AppIcons.Camera
    "TTS", "READ_ALOUD" -> AppIcons.Volume
    "SMART_READ_ALOUD" -> AppIcons.Voice
    "OPEN_LINK", "URL" -> AppIcons.OpenInBrowser
    else -> AppIcons.Sparkle
}

private fun getHistoryActionLabel(context: android.content.Context, actionType: String): String =
    ActionLabels.getLabelForHistory(context, actionType)

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryItemCard(
    action: com.freeup.textactions.domain.model.ActionHistory,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
    onItemClick: () -> Unit = {},
    onRerunClick: () -> Unit = {},
    onCopyClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    onToggleSelect: () -> Unit = {},
    onLongPressSelect: () -> Unit = {},
    onDeleteAction: () -> Unit = {},
    onTogglePin: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cardBackground = MaterialTheme.colorScheme.surfaceContainerLow
    // Start visible: animating each card in left the section headers on screen with no cards under them
    val isVisible = true
    val icon = getHistoryActionIcon(action.actionType)
    val actionIdForColors = action.actionType.lowercase()
    val actionColors = getActionColors(actionIdForColors)
    var isDismissed by remember { mutableStateOf(false) }
    var isRowVisible by remember { mutableStateOf(true) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                isDismissed = true
                true
            } else {
                false
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.67f }
    )

    // After swipe completes, delay 500ms then collapse row and trigger delete
    LaunchedEffect(isDismissed) {
        if (isDismissed) {
            kotlinx.coroutines.delay(500L)
            isRowVisible = false
            onDeleteAction()
        }
    }

    AnimatedVisibility(
        visible = isVisible && isRowVisible,
        enter = fadeIn(animationSpec = fadeInSpec) + slideInVertically(initialOffsetY = { it / 4 }),
        exit = shrinkVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(200)),
        label = "recentActionFadeIn"
    ) {
        var itemWidth by remember { mutableFloatStateOf(1f) }
        Box(modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { itemWidth = it.width.toFloat().coerceAtLeast(1f) }
        ) {
            SwipeToDismissBox(
                state = dismissState,
                enableDismissFromStartToEnd = false,
                enableDismissFromEndToStart = !selectionMode,
                backgroundContent = {
                    val offsetX = try { dismissState.requireOffset() } catch (_: Exception) { 0f }
                    val swipeFraction = ((-offsetX) / itemWidth).coerceIn(0f, 1f)
                    val bgAlpha = swipeFraction
                    val iconScale = (0.5f + 0.5f * swipeFraction).coerceIn(0.5f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = bgAlpha)),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Icon(
                            imageVector = AppIcons.Trash,
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = bgAlpha),
                            modifier = Modifier
                                .padding(end = 20.dp)
                                .scale(iconScale)
                        )
                    }
                }
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(3.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.08f))
                        .combinedClickable(
                            onClick = {
                                if (selectionMode) onToggleSelect() else onItemClick()
                            },
                            onLongClick = {
                                if (selectionMode) onToggleSelect() else onLongPressSelect()
                            }
                        ),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else cardBackground
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .fillMaxWidth()
                            // Same height for 1- and 2-line titles so the list looks even
                            .heightIn(min = 72.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = actionColors.backgroundColor.copy(alpha = 0.9f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(icon, contentDescription = null, tint = actionColors.iconColor, modifier = Modifier.size(22.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = formatHistoryContextText(action.inputText),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${getHistoryActionLabel(context, action.actionType)} · ${formatRelativeTime(context, action.createdAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        if (!selectionMode) {
                            IconButton(
                                onClick = {
                                    val isNote = action.actionType.equals("SAVE_NOTE", ignoreCase = true) ||
                                        action.actionType.equals("SAVE_TO_NOTE", ignoreCase = true)
                                    val intent = Intent(context, ProcessTextActivity::class.java).apply {
                                        this.action = Intent.ACTION_PROCESS_TEXT
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_PROCESS_TEXT, action.inputText)
                                        putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
                                        if (isNote) putExtra("EXTRA_HIDE_SAVE_ACTION", true)
                                        // Opened from inside the app: picking an action here must not add another history record
                                        putExtra("EXTRA_SUPPRESS_HISTORY", true)
                                    }
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = AppIcons.Sparkle,
                                    contentDescription = stringResource(R.string.btn_open_actions),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(onClick = onTogglePin, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = if (action.isPinned) AppIcons.PinFill else AppIcons.Pin,
                                    contentDescription = stringResource(if (action.isPinned) R.string.unpin_note else R.string.pin_note),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryOverflowMenu(
    enabled: Boolean,
    onRerun: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            enabled = enabled
        ) {
            Icon(
                imageVector = AppIcons.MoreVert,
                contentDescription = stringResource(R.string.more),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.4f)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rerun_action)) },
                onClick = { expanded = false; onRerun() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.copy_text)) },
                onClick = { expanded = false; onCopy() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.share_text)) },
                onClick = { expanded = false; onShare() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete_history), color = MaterialTheme.colorScheme.error) },
                onClick = { expanded = false; onDelete() }
            )
        }
    }
}

fun formatRelativeTime(context: android.content.Context, timestampMillis: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestampMillis
    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> context.getString(R.string.time_just_now)
        diff < TimeUnit.HOURS.toMillis(1) -> {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
            if (minutes == 1L) context.getString(R.string.time_one_minute_ago)
            else context.getString(R.string.time_minutes_ago, minutes.toInt())
        }
        diff < TimeUnit.DAYS.toMillis(1) -> {
            val hours = TimeUnit.MILLISECONDS.toHours(diff)
            if (hours == 1L) context.getString(R.string.time_one_hour_ago)
            else context.getString(R.string.time_hours_ago, hours.toInt())
        }
        diff < TimeUnit.DAYS.toMillis(2) -> context.getString(R.string.time_group_yesterday)
        diff < TimeUnit.DAYS.toMillis(7) -> {
            val days = TimeUnit.MILLISECONDS.toDays(diff)
            if (days == 1L) context.getString(R.string.time_one_day_ago)
            else context.getString(R.string.time_days_ago, days.toInt())
        }
        else -> {
            val days = TimeUnit.MILLISECONDS.toDays(diff)
            context.getString(R.string.time_days_ago, days.toInt())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryEntitySheet(
    items: List<com.freeup.textactions.TextActionItem>,
    onDismiss: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val sheetColor = if (isDark) com.freeup.textactions.ui.theme.DarkSurface else com.freeup.textactions.ui.theme.LightSurface
    val rowColor = if (isDark) com.freeup.textactions.ui.theme.DarkSurfaceContainerHigh else com.freeup.textactions.ui.theme.LightBackground
    val contentColor = if (isDark) com.freeup.textactions.ui.theme.DarkOnSurface else com.freeup.textactions.ui.theme.LightOnSurface
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = sheetColor,
        scrimColor = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.history_entities_title),
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )
            items.forEach { item ->
                Surface(
                    onClick = {
                        item.action.invoke()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = rowColor,
                    tonalElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (val icon = item.icon) {
                            is ImageVector -> Icon(icon, contentDescription = null, tint = contentColor)
                            is Int -> Icon(
                                painter = androidx.compose.ui.res.painterResource(id = icon),
                                contentDescription = null,
                                tint = contentColor
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = item.title, style = MaterialTheme.typography.bodyLarge, color = contentColor)
                            if (!item.subtitle.isNullOrBlank()) {
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = contentColor.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/** Group key for section headers: Today, Yesterday, Earlier. */
fun historyGroupKey(timestampMillis: Long): String {
    val todayStart = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis
    val yesterdayStart = todayStart - TimeUnit.DAYS.toMillis(1)
    return when {
        timestampMillis >= todayStart -> "TODAY"
        timestampMillis >= yesterdayStart -> "YESTERDAY"
        else -> "EARLIER"
    }
}

/** Context text for history item: 1 line, 30–50 chars, ellipsis. */

private fun isNoteHistoryType(type: String) =
    type.equals("SAVE_NOTE", ignoreCase = true) || type.equals("SAVE_TO_NOTE", ignoreCase = true)

/** Snackbar text that says what was deleted: "Note deleted", "Translate deleted", "3 items deleted". */
fun historyDeletedMessage(
    context: android.content.Context,
    actions: List<com.freeup.textactions.domain.model.ActionHistory>
): String {
    if (actions.size > 1) {
        return if (actions.all { isNoteHistoryType(it.actionType) }) {
            context.getString(R.string.msg_notes_deleted_count, actions.size)
        } else {
            context.getString(R.string.msg_items_deleted_count, actions.size)
        }
    }
    val type = actions.firstOrNull()?.actionType ?: return context.getString(R.string.msg_history_deleted)
    return if (isNoteHistoryType(type)) context.getString(R.string.msg_note_deleted)
    else context.getString(R.string.msg_item_deleted, getHistoryActionLabel(context, type))
}

/** Placeholder rows with a light band sweeping across them while the history is loading. */
@Composable
fun HistoryListSkeleton(rows: Int = 6) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "skeletonShimmer"
    )
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    // Band travels from off-screen left to off-screen right, then repeats
    val travel = 1600f
    val start = -400f + progress * travel
    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(start, 0f),
        end = Offset(start + 400f, 120f)
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(rows) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(brush))
                    Spacer(Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.fillMaxWidth(0.7f).height(16.dp).clip(RoundedCornerShape(6.dp)).background(brush))
                        Box(Modifier.fillMaxWidth(0.4f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(brush))
                    }
                }
            }
        }
    }
}
