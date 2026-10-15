package com.freeup.textactions

import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.freeup.textactions.ui.theme.AppIcons
import androidx.compose.material3.Card

import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.freeup.textactions.data.local.db.AppDatabase
import com.freeup.textactions.data.repository.HomeRepository
import com.freeup.textactions.ui.theme.TextActionsTheme
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class NotesActivity : BaseActivity() {

    private val viewModel: NotesViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = AppDatabase.getDatabase(applicationContext)
                val repository = HomeRepository(
                    applicationContext,
                    db.actionHistoryDao(),
                    db.noteDao(),
                    com.freeup.textactions.data.local.datastore.AiStatusDataStore(applicationContext)
                )
                return NotesViewModel(repository) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val notes by viewModel.notes.collectAsState()
            val archivedNotes by viewModel.archivedNotes.collectAsState()
            TextActionsTheme(transparentSystemBars = true) {
                NotesScreen(
                    notes = notes,
                    archivedNotes = archivedNotes,
                    onBack = { finish() },
                    onArchiveNote = { viewModel.deleteNote(it) },
                    onUnarchiveNote = { viewModel.restoreNote(it) },
                    onDeleteNotePermanently = { viewModel.deleteNotePermanently(it) },
                    onInsertNote = { viewModel.insertNote(it) },
                    onUpdateNote = { viewModel.updateNote(it) },
                    onPinNote = { viewModel.pinNote(it) },
                    onUnpinNote = { viewModel.unpinNote(it) },
                    onAuthenticate = { success, error -> authenticateUser(success, error) },
                    onMergeNotes = { viewModel.mergeNotes(it) },
                    onLockNote = { note -> viewModel.updateNote(note.copy(isLocked = true)) },
                    onUnlockNote = { note -> viewModel.updateNote(note.copy(isLocked = false)) }
                )
            }
        }
    }
    private fun authenticateUser(onSuccess: () -> Unit, onSecurityError: () -> Unit) {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!keyguardManager.isDeviceSecure) {
            onSecurityError()
            return
        }

        val executor = androidx.core.content.ContextCompat.getMainExecutor(this)
        val biometricPrompt = androidx.biometric.BiometricPrompt(this, executor,
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(applicationContext, getString(R.string.auth_error, errString), Toast.LENGTH_SHORT).show()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(applicationContext, getString(R.string.auth_failed), Toast.LENGTH_SHORT).show()
                }
            })

        val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.unlock_note_title))
            .setSubtitle(getString(R.string.unlock_note_subtitle))
            .setAllowedAuthenticators(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}

// Helper to get string from Context in non-Composable scope or just strictly for logic mapping
fun getString(context: Context, id: Int): String {
    return context.getString(id)
}

fun getCategoryLabel(context: Context, category: String): String {
    return when (category) {
        "All" -> getString(context, R.string.category_all)
        "General" -> getString(context, R.string.category_general)
        "Work" -> getString(context, R.string.category_work)
        "Personal" -> getString(context, R.string.category_personal)
        "Contact" -> getString(context, R.string.category_contact)
        "Shopping" -> getString(context, R.string.category_shopping)
        "Study" -> getString(context, R.string.category_study)
        else -> category
    }
}

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
private fun NotesScreen(
    notes: List<com.freeup.textactions.data.local.db.NoteEntity>,
    archivedNotes: List<com.freeup.textactions.data.local.db.NoteEntity> = emptyList(),
    onBack: () -> Unit,
    onArchiveNote: (String) -> Unit = {},
    onUnarchiveNote: (String) -> Unit = {},
    onDeleteNotePermanently: (String) -> Unit = {},
    onInsertNote: (com.freeup.textactions.data.local.db.NoteEntity) -> Unit = {},
    onUpdateNote: (com.freeup.textactions.data.local.db.NoteEntity) -> Unit,
    onPinNote: (String) -> Unit = {},
    onUnpinNote: (String) -> Unit = {},
    onAuthenticate: (onSuccess: () -> Unit, onError: () -> Unit) -> Unit,
    onMergeNotes: (List<String>) -> Unit = {},
    onLockNote: (com.freeup.textactions.data.local.db.NoteEntity) -> Unit = {},
    onUnlockNote: (com.freeup.textactions.data.local.db.NoteEntity) -> Unit = {}
) {
    var showArchived by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var noteToEditId by remember { mutableStateOf<String?>(null) }
    var selectedNoteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val isSelectMode = selectedNoteIds.isNotEmpty()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    // State for delete confirmation dialog
    var notesToConfirmDelete by remember { mutableStateOf<List<com.freeup.textactions.data.local.db.NoteEntity>>(emptyList()) }

    val currentNotes = if (showArchived) archivedNotes else notes
    val selectedNotes = remember(currentNotes, selectedNoteIds) {
        currentNotes.filter { it.id in selectedNoteIds }
    }
    val allSelectedPinned = selectedNotes.isNotEmpty() && selectedNotes.all { it.isPinned }
    val shouldPinSelection = !allSelectedPinned
    val title = if (isSelectMode) stringResource(R.string.selected_count, selectedNoteIds.size)
        else if (showArchived) stringResource(R.string.archived_notes_title)
        else stringResource(R.string.notes_title)

    val notesInCurrentView = if (showArchived) archivedNotes else notes
    val allIdsInView = notesInCurrentView.map { it.id }.toSet()
    val isAllSelected = selectedNoteIds.containsAll(allIdsInView) && allIdsInView.isNotEmpty()

    val categories = listOf("All") + com.freeup.textactions.data.local.db.NoteCategories.ALL

    val backgroundColor = MaterialTheme.colorScheme.background
    
    BackHandler(enabled = showArchived) {
        showArchived = false
    }

    Scaffold(
        containerColor = backgroundColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().background(backgroundColor)) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor),
                    title = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = if (isSelectMode) 20.sp else 22.sp
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (isSelectMode) selectedNoteIds = emptySet()
                            else if (showArchived) showArchived = false
                            else onBack()
                        }) {
                            Icon(
                                imageVector = if (isSelectMode) AppIcons.Close else AppIcons.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    actions = {
                        if (isSelectMode) {
                            IconButton(onClick = {
                                if (isAllSelected) {
                                    selectedNoteIds = emptySet()
                                } else {
                                    selectedNoteIds = allIdsInView
                                }
                            }) {
                                Icon(
                                    imageVector = if (isAllSelected) AppIcons.Deselect else AppIcons.SelectAll,
                                    contentDescription = if (isAllSelected) stringResource(R.string.action_deselect_all) else stringResource(R.string.action_select_all)
                                )
                            }
                            IconButton(onClick = {
                                if (shouldPinSelection) {
                                    selectedNoteIds.forEach { onPinNote(it) }
                                    Toast.makeText(context, context.getString(R.string.msg_note_pinned), Toast.LENGTH_SHORT).show()
                                } else {
                                    selectedNoteIds.forEach { onUnpinNote(it) }
                                    Toast.makeText(context, context.getString(R.string.msg_note_unpinned), Toast.LENGTH_SHORT).show()
                                }
                                selectedNoteIds = emptySet()
                            }) {
                                Icon(
                                    imageVector = if (shouldPinSelection) AppIcons.Pin else AppIcons.PinFill,
                                    contentDescription = if (shouldPinSelection) stringResource(R.string.pin_note) else stringResource(R.string.unpin_note)
                                )
                            }
                            IconButton(onClick = {
                                if (showArchived) {
                                    selectedNoteIds.forEach { onUnarchiveNote(it) }
                                    Toast.makeText(context, context.getString(R.string.msg_note_unarchived), Toast.LENGTH_SHORT).show()
                                } else {
                                    selectedNoteIds.forEach { onArchiveNote(it) }
                                    Toast.makeText(context, context.getString(R.string.msg_note_archived), Toast.LENGTH_SHORT).show()
                                }
                                selectedNoteIds = emptySet()
                            }) {
                                Icon(
                                    imageVector = if (showArchived) AppIcons.ArchiveFill else AppIcons.Archive,
                                    contentDescription = if (showArchived) stringResource(R.string.action_unarchive) else stringResource(R.string.action_archive)
                                )
                            }
                            IconButton(onClick = {
                                val selectedItems = currentNotes.filter { it.id in selectedNoteIds }
                                val hasLocked = selectedItems.any { it.isLocked }
                                if (hasLocked) {
                                    Toast.makeText(context, context.getString(R.string.unlock_to_delete), Toast.LENGTH_SHORT).show()
                                } else {
                                    notesToConfirmDelete = selectedItems
                                }
                            }) {
                                Icon(
                                    imageVector = AppIcons.Trash,
                                    contentDescription = stringResource(R.string.delete)
                                )
                            }
                            if (selectedNoteIds.size >= 2) {
                                IconButton(onClick = {
                                    onMergeNotes(selectedNoteIds.toList())
                                    Toast.makeText(context, context.getString(R.string.notes_merged), Toast.LENGTH_SHORT).show()
                                    selectedNoteIds = emptySet()
                                }) {
                                    Icon(
                                        imageVector = AppIcons.Merge,
                                        contentDescription = stringResource(R.string.merge_notes)
                                    )
                                }
                            }
                        } else if (!showArchived) {
                            IconButton(onClick = { showArchived = true }) {
                                Icon(
                                    imageVector = AppIcons.Archive,
                                    contentDescription = stringResource(R.string.archived_notes_title)
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SearchBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                placeholderText = if (showArchived) stringResource(R.string.search_in_notes) + " (" + stringResource(R.string.archived_notes_title) + ")" else stringResource(R.string.search_in_notes)
            )

            // Category Chips
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                items(categories) { category ->
                    val isSelected = selectedCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = category },
                        label = { Text(getCategoryLabel(context, category)) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.onSurface,
                            selectedLabelColor = MaterialTheme.colorScheme.surface
                        ),
                        border = null,
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
                }
            }
            
            androidx.compose.animation.AnimatedContent(
                targetState = showArchived,
                transitionSpec = {
                    val direction = if (targetState) 1 else -1
                    androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(180)) +
                        androidx.compose.animation.slideInHorizontally(
                            animationSpec = androidx.compose.animation.core.tween(180)
                        ) { it / 8 * direction } togetherWith
                        androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(180)) +
                        androidx.compose.animation.slideOutHorizontally(
                            animationSpec = androidx.compose.animation.core.tween(180)
                        ) { -it / 8 * direction }
                },
                label = "archiveTransition"
            ) { archived ->
                val notesForTab = if (archived) archivedNotes else notes
                val filtered = remember(notesForTab, searchQuery, selectedCategory) {
                    val query = searchQuery.trim().lowercase()
                    val catFiltered = if (selectedCategory == "All") notesForTab else notesForTab.filter { it.category == selectedCategory }

                    if (query.isEmpty()) catFiltered
                    else catFiltered.filter { note ->
                        note.title.lowercase().contains(query) ||
                            note.content.lowercase().contains(query)
                    }
                }
                if (filtered.isEmpty()) {
                    val emptyTitle = if (searchQuery.isNotBlank())
                        stringResource(R.string.no_results_for, searchQuery)
                    else if (archived)
                        stringResource(R.string.no_archived_notes)
                    else
                        stringResource(R.string.notes_empty_title)

                    EmptyNotesState(
                        title = emptyTitle,
                        subtitle = if (searchQuery.isNotBlank()) "" else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(filtered) { note ->
                            NoteCard(
                                note = note,
                                isArchived = archived,
                                isSelected = note.id in selectedNoteIds,
                                onClick = {
                                    if (isSelectMode) {
                                        selectedNoteIds = if (note.id in selectedNoteIds)
                                            selectedNoteIds - note.id
                                        else
                                            selectedNoteIds + note.id
                                    } else if (note.isLocked) {
                                        onAuthenticate(
                                            { noteToEditId = note.id },
                                            {
                                                coroutineScope.launch {
                                                    val result = snackbarHostState.showSnackbar(
                                                        message = context.getString(R.string.lock_requires_passcode),
                                                        actionLabel = context.getString(R.string.open_settings),
                                                        duration = SnackbarDuration.Long
                                                    )
                                                    if (result == SnackbarResult.ActionPerformed) {
                                                        context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                                                    }
                                                }
                                            }
                                        )
                                    } else {
                                        noteToEditId = note.id
                                    }
                                },
                                onLongClick = {
                                    if (isSelectMode) {
                                        selectedNoteIds = if (note.id in selectedNoteIds)
                                            selectedNoteIds - note.id
                                        else
                                            selectedNoteIds + note.id
                                    } else {
                                        selectedNoteIds = selectedNoteIds + note.id
                                    }
                                },
                                onArchive = { onArchiveNote(note.id) },
                                onUnarchive = { onUnarchiveNote(note.id) },
                                onDeletePermanently = {
                                    if (note.isLocked) {
                                        Toast.makeText(context, context.getString(R.string.unlock_to_delete), Toast.LENGTH_SHORT).show()
                                    } else {
                                        notesToConfirmDelete = listOf(note)
                                    }
                                },
                                onToggleLock = {
                                    if (note.isLocked) onUnlockNote(note)
                                    else onLockNote(note)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (notesToConfirmDelete.isNotEmpty()) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { notesToConfirmDelete = emptyList() },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = {
                Text(
                    if (notesToConfirmDelete.size == 1)
                        stringResource(R.string.confirm_delete_note)
                    else
                        stringResource(R.string.confirm_delete_note) + " (${notesToConfirmDelete.size})"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val deletedNotes = notesToConfirmDelete.toList()
                    deletedNotes.forEach { onDeleteNotePermanently(it.id) }
                    notesToConfirmDelete = emptyList()
                    selectedNoteIds = emptySet()
                    coroutineScope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = context.getString(R.string.msg_note_deleted),
                            actionLabel = context.getString(R.string.undo),
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            deletedNotes.forEach { onInsertNote(it) }
                        }
                    }
                }) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { notesToConfirmDelete = emptyList() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    androidx.compose.runtime.LaunchedEffect(noteToEditId) {
        val targetId = noteToEditId ?: return@LaunchedEffect
        val intent = android.content.Intent(context, EditNoteActivity::class.java).apply {
            putExtra(EditNoteActivity.EXTRA_NOTE_ID, targetId)
        }
        context.startActivity(intent)
        noteToEditId = null
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    note: com.freeup.textactions.data.local.db.NoteEntity,
    isArchived: Boolean,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onDeletePermanently: () -> Unit,
    onToggleLock: () -> Unit = {}
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val dateText = DateFormat.getDateTimeInstance(
        DateFormat.MEDIUM,
        DateFormat.SHORT
    ).format(Date(note.createdAt))
    val textToCopy = "${note.title}\n\n${note.content}".trim()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onLongClick() }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)) else null
    ) {
        Box {
            Column(modifier = Modifier.padding(16.dp)) {
                if (note.isLocked) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = AppIcons.Lock,
                                contentDescription = "Locked",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.locked_note),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    val hasTitle = note.title.isNotBlank()
                    if (hasTitle) {
                        // Case: Has Title -> Title (Bold, 1 line) + Content (Normal, 1 line)
                        Text(
                            text = note.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            text = note.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    } else {
                        // Case: No Title -> Content promoted (SemiBold, 2 lines max)
                        Text(
                            text = note.content,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dateText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = getCategoryLabel(context, note.category),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            if (note.isPinned) {
                Icon(
                    imageVector = AppIcons.Pin,
                    contentDescription = stringResource(R.string.pin_note),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(18.dp)
                )
            }
            
            // Menu dropdown attached to the card
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                offset = androidx.compose.ui.unit.DpOffset(16.dp, 0.dp) 
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.copy_text)) },
                    leadingIcon = { Icon(AppIcons.Copy, null) },
                    onClick = {
                        showMenu = false
                        (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("Note", textToCopy))
                        Toast.makeText(context, context.getString(R.string.copy_text), Toast.LENGTH_SHORT).show()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.edit)) },
                    leadingIcon = { Icon(AppIcons.Edit, null) },
                    onClick = {
                        showMenu = false
                        onClick()
                    }
                )
                if (isArchived) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_unarchive)) },
                        leadingIcon = { Icon(AppIcons.Refresh, null) }, // Changed to Refresh (safe)
                        onClick = {
                            showMenu = false
                            onUnarchive()
                            Toast.makeText(context, context.getString(R.string.msg_note_unarchived), Toast.LENGTH_SHORT).show()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(AppIcons.Trash, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDeletePermanently()
                        }
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_archive)) },
                        leadingIcon = { Icon(AppIcons.Archive, null) },
                        onClick = {
                            showMenu = false
                            onArchive()
                            Toast.makeText(context, context.getString(R.string.msg_note_archived), Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(if (note.isLocked) stringResource(R.string.action_unlock) else stringResource(R.string.lock_this_note)) },
                    leadingIcon = { Icon(if (note.isLocked) AppIcons.LockOpen else AppIcons.Lock, null) },
                    onClick = {
                        showMenu = false
                        onToggleLock()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun EmptyNotesState(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 })
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_empty_snippets),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(144.dp)
                )
                Text(
                    text = title ?: stringResource(R.string.notes_empty_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 26.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val displaySubtitle = subtitle ?: stringResource(R.string.notes_empty_subtitle)
                if (displaySubtitle.isNotEmpty()) {
                    Text(
                        text = displaySubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
