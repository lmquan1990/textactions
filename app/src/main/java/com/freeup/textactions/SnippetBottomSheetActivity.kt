package com.freeup.textactions

import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.freeup.textactions.ui.theme.AppIcons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.freeup.textactions.data.local.db.AppDatabase
import com.freeup.textactions.ui.theme.TextActionsTheme
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import android.view.WindowManager
import java.text.SimpleDateFormat
import java.util.Locale

class SnippetBottomSheetActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isProcessText = intent.action == Intent.ACTION_PROCESS_TEXT
        val isTile = intent.action == "com.freeup.textactions.ACTION_SHOW_SNIPPETS_TILE"
        
        if (!isProcessText && !isTile) {
            finish()
            return
        }

        @Suppress("DEPRECATION")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // Fetch settings for theme
        val settingsManager = SettingsManager(this)
        val dynamicColorEnabled = settingsManager.isDynamicColorEnabled()
        val themeMode = settingsManager.getThemeMode()
        
        val db = AppDatabase.getDatabase(applicationContext)

        setContent {
            val darkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            var snippets by remember { mutableStateOf<List<com.freeup.textactions.data.local.db.NoteEntity>>(emptyList()) }
            var searchQuery by remember { mutableStateOf("") }
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
            val coroutineScope = rememberCoroutineScope()

            val filteredSnippets = remember(snippets, searchQuery) {
                if (searchQuery.isBlank()) snippets
                else snippets.filter { 
                    it.title.contains(searchQuery, ignoreCase = true) || 
                    it.content.contains(searchQuery, ignoreCase = true) 
                }
            }

            LaunchedEffect(Unit) {
                db.noteDao().getPublicSnippets().collect {
                    snippets = it
                }
            }

            fun formatDateTime(timestamp: Long): String {
                val sdf = SimpleDateFormat("HH:mm dd/MM", Locale.getDefault())
                return sdf.format(java.util.Date(timestamp))
            }

            TextActionsTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColorEnabled,
                transparentSystemBars = true,
                windowBackgroundColor = androidx.compose.ui.graphics.Color.Transparent
            ) {
                ModalBottomSheet(
                    onDismissRequest = { finish() },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.background,
                    scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.32f),
                    windowInsets = WindowInsets(0)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 32.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.notes_title),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            placeholder = { Text(stringResource(R.string.hint_search_snippets)) },
                            leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(AppIcons.Close, contentDescription = "Clear search")
                                    }
                                }
                            },
                            shape = RoundedCornerShape(28.dp),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                                unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        
                        if (filteredSnippets.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) {
                                Text(
                                    text = if (searchQuery.isEmpty()) stringResource(R.string.notes_empty_title) else stringResource(R.string.no_results_found),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(filteredSnippets) { snippet ->
                                    Card(
                                        onClick = {
                                            coroutineScope.launch {
                                                sheetState.hide()
                                                handleSnippetClick(snippet, isProcessText)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                        )
                                    ) {
                                        val hasTitle = snippet.title.isNotBlank()
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = androidx.compose.ui.Alignment.Top
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    if (hasTitle) {
                                                        // Case: Has Title -> Title (Bold) + Content (Normal)
                                                        Text(
                                                            text = snippet.title,
                                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                        Text(
                                                            text = snippet.content,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    } else {
                                                        // Case: No Title -> Content promoted (SemiBold)
                                                        Text(
                                                            text = snippet.content,
                                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = formatDateTime(snippet.createdAt),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    modifier = Modifier.padding(start = 8.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleSnippetClick(snippet: com.freeup.textactions.data.local.db.NoteEntity, isProcessText: Boolean) {
        if (isProcessText) {
            val resultIntent = Intent()
            resultIntent.putExtra(Intent.EXTRA_PROCESS_TEXT, snippet.content)
            setResult(RESULT_OK, resultIntent)
        } else {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val textToCopy = if (snippet.title.isNotBlank()) "${snippet.title}\n\n${snippet.content}".trim() else snippet.content
            clipboard.setPrimaryClip(ClipData.newPlainText("Snippet", textToCopy))
            Toast.makeText(this, getString(R.string.copied), Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
