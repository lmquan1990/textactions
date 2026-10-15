package com.freeup.textactions.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ColorFilter
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.layout.Alignment
import androidx.glance.layout.Spacer
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.freeup.textactions.MainActivity
import com.freeup.textactions.R
import com.freeup.textactions.data.local.db.AppDatabase
import com.freeup.textactions.data.local.db.NoteEntity

class TextActionsWidget : GlanceAppWidget() {
    
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Fetch data from Room (Filtered: isLocked == 0 AND isArchived == 0)
        // We fetch synchronously here in the provideGlance bridge
        val db = AppDatabase.getDatabase(context)
        val snippets = db.noteDao().getPublicSnippetsList() // Correct DAO method is getPublicSnippetsList

        provideContent {
            GlanceTheme {
                WidgetContent(context, snippets)
            }
        }
    }

    @Composable
    private fun WidgetContent(context: Context, snippets: List<NoteEntity>) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(ColorProvider(R.color.widget_background))
                .cornerRadius(28.dp)
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp), // Reduced from 11.dp to 6.dp
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = context.getString(R.string.widget_title),
                    style = TextStyle(
                        color = ColorProvider(R.color.widget_text_primary),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.padding(start = 4.dp)
                )

                Spacer(modifier = GlanceModifier.defaultWeight())

                // Add Button (+)
                Image(
                    provider = ImageProvider(R.drawable.ic_add),
                    contentDescription = "Add",
                    colorFilter = ColorFilter.tint(ColorProvider(R.color.widget_icon_tint)),
                    modifier = GlanceModifier
                        .size(25.dp)
                        .clickable(actionStartActivity<MainActivity>(
                            actionParametersOf(EXTRA_OPEN_EDITOR to true)
                        ))
                )
            }

            if (snippets.isEmpty()) {
                Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = context.getString(R.string.widget_empty_text),
                        style = TextStyle(
                            color = ColorProvider(R.color.widget_text_secondary),
                            fontSize = 14.sp
                        )
                    )
                }
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(snippets) { snippet ->
                        SnippetItem(context, snippet)
                    }
                }
            }
        }
    }

    @Composable
    private fun SnippetItem(context: Context, snippet: NoteEntity) {
        val hasTitle = !snippet.title.isNullOrBlank()

        // Outer wrapper Column handles the gap BETWEEN items (transparent space)
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(bottom = 5.dp) // Reduced from 10.dp to 5.dp to separation items more tightly
        ) {
            // Inner Column handles the card BACKGROUND, shape, and click action
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(ColorProvider(R.color.widget_card_background))
                    .cornerRadius(8.dp) // Increased from 5.dp to 8.dp
                    .padding(horizontal = 12.dp, vertical = 12.dp) // Restored to 12.dp
                    .clickable(actionRunCallback<CopyActionCallback>(
                        actionParametersOf(
                            PARAM_TITLE to (snippet.title),
                            PARAM_CONTENT to (snippet.content)
                        )
                    ))
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        if (hasTitle) {
                            Text(
                                text = snippet.title,
                                maxLines = 1,
                                style = TextStyle(
                                    color = ColorProvider(R.color.widget_text_primary),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = snippet.content.replace("\n", " "),
                                maxLines = 1,
                                style = TextStyle(
                                    color = ColorProvider(R.color.widget_text_secondary),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            )
                        } else {
                            Text(
                                text = snippet.content,
                                maxLines = 2,
                                style = TextStyle(
                                    color = ColorProvider(R.color.widget_text_primary),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }
        }
    }



    companion object {
        val EXTRA_OPEN_EDITOR = ActionParameters.Key<Boolean>("extra_open_editor")
        val PARAM_TITLE = ActionParameters.Key<String>("param_title")
        val PARAM_CONTENT = ActionParameters.Key<String>("param_content")
    }
}

/**
 * Handle copy action directly via Glance ActionCallback to avoid extra broadcast latency where possible,
 * but specifically to fulfill the "Don't open app" requirement.
 */
class CopyActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val title = parameters[TextActionsWidget.PARAM_TITLE] ?: ""
        val content = parameters[TextActionsWidget.PARAM_CONTENT] ?: ""
        
        if (content.isBlank()) return

        // 1. Copy to clipboard
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Snippet", content)
        clipboard.setPrimaryClip(clip)

        // 2. Trigger Haptic Feedback
        triggerHapticFeedback(context)

        // 3. Smart Visual Feedback (Android 13+ has native clipboard overlay)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val toastMessage = if (title.isNotBlank()) {
                context.getString(R.string.toast_copied_with_title, title)
            } else {
                context.getString(R.string.copied)
            }

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, toastMessage, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun triggerHapticFeedback(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(50)
                }
            }
        } catch (e: Exception) {
            // Silently fail if vibration is not supported or accessible
        }
    }
}
