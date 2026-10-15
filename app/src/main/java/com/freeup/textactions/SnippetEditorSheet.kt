package com.freeup.textactions

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.freeup.textactions.ui.theme.AppIcons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.freeup.textactions.data.local.db.NoteEntity
import com.freeup.textactions.ui.theme.DarkBackground
import com.freeup.textactions.ui.theme.NunitoFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import com.freeup.textactions.data.local.db.NoteCategories

/**
 * Unified Snippet Editor Sheet.
 *
 * @param snippet null → Create mode, non-null → Edit mode (pre-fills all fields).
 * @param onSave  Called with (title, content, category, isLocked). Caller handles DB.
 * @param onDelete Called with snippetId. Only available in Edit mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnippetEditorSheet(
    snippet: NoteEntity? = null,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, category: String, isLocked: Boolean, isPinned: Boolean, isArchived: Boolean) -> Unit,
    onDelete: ((snippetId: String) -> Unit)? = null,
    onArchive: (() -> Unit)? = null,
    snackbarHostState: SnackbarHostState? = null
) {
    val isEditMode = snippet != null
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // ── State (keyed by snippet.id so resetting when editing different items) ──
    var title by remember(snippet?.id) { mutableStateOf(snippet?.title ?: "") }
    var content by remember(snippet?.id) { mutableStateOf(snippet?.content ?: "") }
    var selectedCategory by remember(snippet?.id) {
        mutableStateOf(snippet?.category ?: "General")
    }
    var isLocked by remember(snippet?.id) { mutableStateOf(snippet?.isLocked ?: false) }
    var isPinned by remember(snippet?.id) { mutableStateOf(snippet?.isPinned ?: false) }
    var isArchived by remember(snippet?.id) { mutableStateOf(snippet?.isArchived ?: false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var showSecurityDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val initialIsLocked = remember(snippet?.id) { snippet?.isLocked ?: false }

    val isSaveEnabled = content.isNotBlank()

    val contentFocusRequester = remember { FocusRequester() }

    val isDark = MaterialTheme.colorScheme.background == DarkBackground
    val textFieldContainerColor =
        if (isDark) Color(0xFF2A2A2A) else MaterialTheme.colorScheme.surfaceContainerLow

    fun dismiss() {
        keyboardController?.hide()
        coroutineScope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    fun saveAndDismiss() {
        if (isSaveEnabled) {
            if (isLocked && !initialIsLocked) {
                Toast.makeText(context, context.getString(R.string.msg_moved_to_private), Toast.LENGTH_SHORT).show()
            }
            onSave(title, content, selectedCategory, isLocked, isPinned, isArchived)
            dismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = ::dismiss,
        sheetState = sheetState,
        windowInsets = WindowInsets(0),
        dragHandle = { 
            BottomSheetDefaults.DragHandle(
                modifier = Modifier.padding(top = 4.dp, bottom = 0.dp)
            ) 
        },
        containerColor = if (isDark) Color(0xFF1C1C1E) else MaterialTheme.colorScheme.surface,
        scrimColor = Color.Transparent,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                // ── Header ─────────────────────────────────────────────────────
                // ── Header ─────────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-8).dp)
                        .padding(bottom = 0.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Close button
                    IconButton(onClick = ::dismiss) {
                        Icon(
                            imageVector = AppIcons.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Right: Pin, Lock, Archive actions
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            val nextPinned = !isPinned
                            isPinned = nextPinned
                            if (isEditMode && isSaveEnabled) {
                                onSave(title, content, selectedCategory, isLocked, nextPinned, isArchived)
                            }
                        }) {
                            Icon(
                                imageVector = if (isPinned) AppIcons.PinFill else AppIcons.Pin,
                                contentDescription = stringResource(if (isPinned) R.string.unpin_note else R.string.pin_note),
                                tint = if (isPinned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!isArchived) {
                            IconButton(onClick = {
                                val keyguardManager =
                                    context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                                if (!keyguardManager.isDeviceSecure) {
                                    showSecurityDialog = true
                                    return@IconButton
                                }

                                val nextLocked = !isLocked
                                isLocked = nextLocked

                                // Mutual Exclusivity: Locking automatically un-archives
                                if (nextLocked) {
                                    isArchived = false
                                }

                                if (isEditMode && isSaveEnabled) {
                                    onSave(title, content, selectedCategory, nextLocked, isPinned, isArchived)
                                    dismiss()
                                }
                            }) {
                                Icon(
                                    imageVector = if (isLocked) AppIcons.LockFill else AppIcons.LockOpen,
                                    contentDescription = stringResource(if (isLocked) R.string.lock_this_note else R.string.unlock_note_title),
                                    tint = if (isLocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (onArchive != null && !isLocked) {
                            IconButton(onClick = {
                                val nextArchived = !isArchived
                                isArchived = nextArchived
                                
                                // Mutual Exclusivity: Archiving automatically un-locks
                                if (nextArchived) {
                                    isLocked = false
                                }

                                if (isEditMode && isSaveEnabled) {
                                    onSave(title, content, selectedCategory, isLocked, isPinned, nextArchived)
                                    dismiss()
                                }
                            }) {
                                Icon(
                                    imageVector = if (isArchived) AppIcons.ArchiveFill else AppIcons.Archive,
                                    contentDescription = if (isArchived) stringResource(R.string.action_unarchive) else stringResource(R.string.action_archive),
                                    tint = if (isArchived) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }


                // ── TextField 1: Tiêu đề (optional) ───────────────────────────
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.hint_snippet_title),
                            style = TextStyle(
                                fontFamily = NunitoFontFamily,
                                fontWeight = FontWeight.Normal,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        )
                    },
                    textStyle = TextStyle(
                        fontFamily = NunitoFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { contentFocusRequester.requestFocus() }
                    ),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = textFieldContainerColor,
                        unfocusedContainerColor = textFieldContainerColor,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ── TextField 2: Nội dung (*) multiline ──────────────────────
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 240.dp)
                        .focusRequester(contentFocusRequester),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.hint_snippet_content),
                            style = TextStyle(
                                fontFamily = NunitoFontFamily,
                                fontWeight = FontWeight.Normal,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        )
                    },
                    textStyle = TextStyle(
                        fontFamily = NunitoFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 15.sp
                    ),
                    maxLines = 8,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Default
                    ),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = textFieldContainerColor,
                        unfocusedContainerColor = textFieldContainerColor,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                // ── Category Group Tag Chips ──────────────────────────────────────
                Text(
                    text = stringResource(R.string.label_category_group),
                    style = TextStyle(
                        fontFamily = NunitoFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                )

                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    items(NoteCategories.ALL.size) { index ->
                        val category = NoteCategories.ALL[index]
                        val isSelected = selectedCategory == category
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else textFieldContainerColor,
                            modifier = Modifier.clickable { selectedCategory = category }
                        ) {
                            Text(
                                text = getCategoryLabel(context, category),
                                style = TextStyle(
                                    fontFamily = NunitoFontFamily,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    fontSize = 14.sp
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                // ── Bottom Action Buttons ───────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Delete (only when editing)
                    if (isEditMode && onDelete != null) {
                        TextButton(
                            onClick = { showDeleteConfirm = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(AppIcons.Trash, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.delete),
                                style = TextStyle(fontFamily = NunitoFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // Right: Save
                    Button(
                        onClick = ::saveAndDismiss,
                        enabled = isSaveEnabled,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.action_save_snippet),
                            style = TextStyle(
                                fontFamily = NunitoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        )
                    }
                }
            }
        }

        if (showSecurityDialog) {
            AlertDialog(
                onDismissRequest = { showSecurityDialog = false },
                title = { Text(text = stringResource(R.string.dialog_security_setup_title), style = TextStyle(fontFamily = NunitoFontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp)) },
                text = { Text(text = stringResource(R.string.dialog_security_setup_message), style = TextStyle(fontFamily = NunitoFontFamily, fontSize = 15.sp)) },
                confirmButton = {
                    Button(
                        onClick = {
                            showSecurityDialog = false
                            context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.open_settings), style = TextStyle(fontFamily = NunitoFontFamily, fontWeight = FontWeight.Bold))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSecurityDialog = false }) {
                        Text(stringResource(R.string.close), style = TextStyle(fontFamily = NunitoFontFamily))
                    }
                },
                containerColor = if (isDark) Color(0xFF2C2C2E) else Color.White,
                shape = RoundedCornerShape(16.dp)
            )
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text(text = stringResource(R.string.confirm_delete_title), style = TextStyle(fontFamily = NunitoFontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp)) },
                text = { Text(text = stringResource(R.string.confirm_delete_note), style = TextStyle(fontFamily = NunitoFontFamily, fontSize = 15.sp)) },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteConfirm = false
                            onDelete?.invoke(snippet!!.id)
                            dismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.delete), style = TextStyle(fontFamily = NunitoFontFamily, fontWeight = FontWeight.Bold))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text(stringResource(R.string.cancel), style = TextStyle(fontFamily = NunitoFontFamily))
                    }
                },
                containerColor = if (isDark) Color(0xFF2C2C2E) else Color.White,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // ── Focus logic ────────────────────────────────────────────────────────
        // Create mode: auto-focus content field + show keyboard
        // Edit mode:   no auto-focus (user reads first, taps to edit)
        if (!isEditMode) {
            LaunchedEffect(Unit) {
                delay(150) // wait for sheet open animation
                contentFocusRequester.requestFocus()
            }
        }
    }
}
