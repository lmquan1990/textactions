package com.freeup.textactions

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import android.widget.Toast
import com.freeup.textactions.data.local.datastore.AiStatusDataStore
import com.freeup.textactions.data.local.db.AppDatabase
import com.freeup.textactions.data.local.db.NoteEntity
import com.freeup.textactions.data.repository.HomeRepository
import com.freeup.textactions.ui.theme.TextActionsTheme
import com.freeup.textactions.util.NoteCategorizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class QuickNoteActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        if (text.isNullOrBlank()) {
            finish()
            return
        }

        val db = AppDatabase.getDatabase(applicationContext)
        val repository = HomeRepository(
            applicationContext,
            db.actionHistoryDao(),
            db.noteDao(),
            AiStatusDataStore(applicationContext)
        )

        val sourceApp = referrer?.authority
        val sourcePkg = callingPackage

        setContent {
            TextActionsTheme(
                transparentSystemBars = true,
                windowBackgroundColor = androidx.compose.ui.graphics.Color.Transparent
            ) {
                var savedNoteId by remember { mutableStateOf<String?>(null) }

                val savedMsg = stringResource(R.string.saved_to_snippet)

                LaunchedEffect(Unit) {
                    // Save note in background
                    val noteId = withContext(Dispatchers.IO) {
                        val type = NoteCategorizer.detectType(text)
                        val url = NoteCategorizer.extractSourceUrl(text)

                        val id = UUID.randomUUID().toString()
                        val now = System.currentTimeMillis()
                        val note = NoteEntity(
                            id = id,
                            title = "",
                            content = text,
                            createdAt = now,
                            category = "General",
                            isLocked = false,
                            sourceAppName = sourceApp,
                            sourcePackageName = sourcePkg,
                            sourceUrl = url,
                            detectedType = type,
                            isArchived = false,
                            isPinned = false,
                            pinnedAt = null
                        )
                        repository.insertNote(note)

                        repository.insertAction(
                            com.freeup.textactions.domain.model.ActionHistory(
                                id = 0,
                                actionType = "SAVE_NOTE",
                                inputText = text.take(100).trim(),
                                resultText = null,
                                metadata = null,
                                createdAt = now
                            )
                        )
                        id
                    }

                    savedNoteId = noteId
                    Toast.makeText(this@QuickNoteActivity, savedMsg, Toast.LENGTH_SHORT).show()

                    finish()
                }

                Box(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
