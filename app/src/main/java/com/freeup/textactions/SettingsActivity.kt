package com.freeup.textactions

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freeup.textactions.backup.BackupJsonSerializer
import com.freeup.textactions.backup.BackupViewModel
import com.freeup.textactions.backup.NotesExporter
import com.freeup.textactions.backup.NotesImporter
import com.freeup.textactions.data.local.db.AppDatabase
import com.freeup.textactions.data.repository.HomeRepository
import com.freeup.textactions.SettingsManager
import com.freeup.textactions.data.local.datastore.ActionSettingsDataStore
import com.freeup.textactions.data.local.datastore.AiStatusDataStore
import com.freeup.textactions.data.local.datastore.TextFormattingDataStore
import com.freeup.textactions.ui.actions.ActionsSettingsScreen
import com.freeup.textactions.ui.actions.ActionsViewModel
import com.freeup.textactions.ui.theme.TextActionsTheme
import com.freeup.textactions.domain.model.AiState
import com.freeup.textactions.domain.model.AiStatus
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            val context = LocalContext.current
            val settingsManager = remember { SettingsManager(context) }
            var dynamicColorEnabled by remember { mutableStateOf(settingsManager.isDynamicColorEnabled()) }
            var themeMode by remember { mutableStateOf(settingsManager.getThemeMode()) }
            val darkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            var isChangingTheme by remember { mutableStateOf(false) }

            TextActionsTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColorEnabled,
                transparentSystemBars = true
            ) {
                val dataStore = ActionSettingsDataStore(applicationContext)
                val aiStatusStore = AiStatusDataStore(applicationContext)
                val formattingStore = TextFormattingDataStore(applicationContext)
                val db = remember { AppDatabase.getDatabase(applicationContext) }
                val repository = remember {
                    HomeRepository(applicationContext, db.actionHistoryDao(), db.noteDao(), aiStatusStore)
                }
                val serializer = remember { BackupJsonSerializer() }
                val exporter = remember { NotesExporter(repository, serializer) }
                val importer = remember { NotesImporter(repository, serializer) }

                val backupViewModel: BackupViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return BackupViewModel(repository, exporter, importer) as T
                        }
                    }
                )
                val viewModel: ActionsViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return ActionsViewModel(applicationContext, dataStore) as T
                        }
                    }
                )

                val coroutineScope = rememberCoroutineScope()
                val snackbarHostState = remember { SnackbarHostState() }
                val aiStatus by aiStatusStore.aiStatusFlow.collectAsState(
                    initial = AiStatus(AiState.READY)
                )

                var showAboutDialog by remember { mutableStateOf(false) }
                var showNotePicker by remember { mutableStateOf(false) }
                var notePickerNotes by remember { mutableStateOf(listOf<com.freeup.textactions.data.local.db.NoteEntity>()) }

                var pendingExport by remember { mutableStateOf<ExportRequest?>(null) }

                val createDocumentLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/json")
                ) { uri: Uri? ->
                    val request = pendingExport
                    pendingExport = null
                    if (uri == null || request == null) return@rememberLauncherForActivityResult
                    coroutineScope.launch {
                        try {
                            when (request) {
                                is ExportRequest.All -> backupViewModel.exportAllToUri(contentResolver, uri)
                                is ExportRequest.Selected -> backupViewModel.exportSelectedToUri(contentResolver, uri, request.noteIds)
                            }
                            snackbarHostState.showSnackbar(getString(R.string.export_successful))
                        } catch (e: IllegalStateException) {
                            snackbarHostState.showSnackbar(getString(R.string.no_notes_to_export))
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar(getString(R.string.export_failed))
                        }
                    }
                }

                val openDocumentLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    coroutineScope.launch {
                        try {
                            val count = backupViewModel.importFromUri(contentResolver, uri)
                            snackbarHostState.showSnackbar(getString(R.string.imported_count, count))
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar(getString(R.string.import_failed))
                        }
                    }
                }

                ActionsSettingsScreen(
                    onBackClick = { finish() },
                    viewModel = viewModel,
                    aiStatus = aiStatus,
                    formattingStore = formattingStore,
                    snackbarHostState = snackbarHostState,
                    onRetryAiStatus = {
                        coroutineScope.launch {
                            aiStatusStore.updateAiStatus(AiStatus(AiState.READY))
                        }
                    },
                    onOpenOnboarding = {
                        startActivity(Intent(this@SettingsActivity, OnboardingActivity::class.java))
                    },
                    onOpenHelp = {
                        startActivity(Intent(this@SettingsActivity, HelpActivity::class.java))
                    },
                    onOpenThemeSettings = { },
                    onThemeChanged = { mode ->
                        themeMode = mode
                        val delegateMode = when (mode) {
                            "light" -> AppCompatDelegate.MODE_NIGHT_NO
                            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        }
                        AppCompatDelegate.setDefaultNightMode(delegateMode)
                        isChangingTheme = true
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            recreate()
                        }, 300)
                    },
                    onLanguageChanged = { lang ->
                        isChangingTheme = true
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            val locales = if (lang == AppLocale.LANGUAGE_SYSTEM || lang.isBlank()) {
                                LocaleListCompat.getEmptyLocaleList()
                            } else {
                                LocaleListCompat.forLanguageTags(lang)
                            }
                            AppCompatDelegate.setApplicationLocales(locales)
                        }, 300)
                    },
                    onOpenAiStatus = {
                        // Về Main, tránh tạo Main trùng
                        val intent = Intent(this@SettingsActivity, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        startActivity(intent)
                        finish()
                    },
                    onOpenPrivacyPolicy = {
                        // Open privacy policy in browser
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://tinyurl.com/text-actions-privacy"))
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                "Privacy policy: Your data stays on your device. No data is sent to external servers.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    onOpenAbout = {
                        showAboutDialog = true
                    },
                    onDynamicColorChanged = { enabled ->
                        dynamicColorEnabled = enabled
                    },
                    onExportAllNotes = {
                        pendingExport = ExportRequest.All
                        createDocumentLauncher.launch(createBackupFileName())
                    },
                    onExportSelectedNote = {
                        coroutineScope.launch {
                            val notes = backupViewModel.loadAllNotes()
                            if (notes.isEmpty()) {
                                snackbarHostState.showSnackbar(getString(R.string.no_notes_to_export))
                            } else {
                                notePickerNotes = notes
                                showNotePicker = true
                            }
                        }
                    },
                    onImportNotesFromFile = {
                        openDocumentLauncher.launch(arrayOf("application/json"))
                    }
                )
                
                if (showAboutDialog) {
                    AboutDialog(onDismiss = { showAboutDialog = false })
                }

                if (showNotePicker) {
                    NotePickerSheet(
                        notes = notePickerNotes,
                        onDismiss = { showNotePicker = false },
                        onExport = { noteIds ->
                            showNotePicker = false
                            pendingExport = ExportRequest.Selected(noteIds)
                            createDocumentLauncher.launch(createBackupFileName())
                        }
                    )
                }

                // Loading overlay when switching theme
                androidx.compose.animation.AnimatedVisibility(
                    visible = isChangingTheme,
                    enter = androidx.compose.animation.fadeIn(
                        animationSpec = androidx.compose.animation.core.tween(200)
                    ),
                    exit = androidx.compose.animation.fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

            }
        }
    }
}

sealed class ExportRequest {
    data object All : ExportRequest()
    data class Selected(val noteIds: List<String>) : ExportRequest()
}

private fun createBackupFileName(): String {
    val formatter = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
    val ts = formatter.format(Date())
    return "ImageToText_backup_${ts}.json"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotePickerSheet(
    notes: List<com.freeup.textactions.data.local.db.NoteEntity>,
    onDismiss: () -> Unit,
    onExport: (List<String>) -> Unit
) {
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.select_note_to_export),
                    style = MaterialTheme.typography.titleMedium
                )
                if (selectedIds.isNotEmpty()) {
                    Button(onClick = { onExport(selectedIds.toList()) }) {
                        Text(stringResource(R.string.export_count, selectedIds.size))
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    selectedIds = if (selectedIds.size == notes.size) emptySet()
                    else notes.map { it.id }.toSet()
                }.padding(vertical = 8.dp)
            ) {
                Checkbox(
                    checked = selectedIds.size == notes.size && notes.isNotEmpty(),
                    onCheckedChange = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.select_all),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(notes) { note ->
                    val isSelected = note.id in selectedIds
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(),
                        onClick = {
                            selectedIds = if (isSelected) selectedIds - note.id
                            else selectedIds + note.id
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = note.title.ifBlank { note.content.take(40) },
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", note.createdAt).toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0.0"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.settings_about_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.about_version, versionName),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    text = stringResource(R.string.about_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.about_built_with),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.about_feedback_title),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.about_feedback_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(4.dp))
                val uriHandler = LocalUriHandler.current
                val email = stringResource(R.string.about_email)
                Text(
                    text = stringResource(R.string.about_send_feedback),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        uriHandler.openUri("mailto:$email")
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(R.string.ok),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    )
}
