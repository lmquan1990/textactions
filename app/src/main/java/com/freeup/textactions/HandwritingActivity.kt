package com.freeup.textactions

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import com.freeup.textactions.ui.theme.AppIcons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.freeup.textactions.ui.theme.TextActionsTheme
import com.freeup.textactions.SettingsManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

class HandwritingActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settingsManager = SettingsManager(this)
        val dynamicColorEnabled = settingsManager.isDynamicColorEnabled()
        val themeMode = settingsManager.getThemeMode()

        setContent {
            val darkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            TextActionsTheme(darkTheme = darkTheme, dynamicColor = dynamicColorEnabled) {
                HandwritingScreen(
                    onResult = { text ->
                        if (text.isNotEmpty()) {
                            val intent = Intent(this@HandwritingActivity, ProcessTextActivity::class.java).apply {
                                putExtra(Intent.EXTRA_PROCESS_TEXT, text)
                            }
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(this@HandwritingActivity, "No text recognized", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onCancel = {
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandwritingScreen(
    onResult: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val paths = remember { mutableStateListOf<List<Offset>>() }
    var isProcessing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.White)
    ) {
        // Top bar
        TopAppBar(
            title = { 
                Text(
                    "Digital Ink",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            navigationIcon = {
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = AppIcons.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        paths.clear()
                    },
                    enabled = paths.isNotEmpty()
                ) {
                    Icon(
                        imageVector = AppIcons.Trash,
                        contentDescription = "Clear"
                    )
                }
                IconButton(
                    onClick = {
                        if (paths.isNotEmpty()) {
                            isProcessing = true
                            coroutineScope.launch {
                                val text = recognizeHandwriting(context, paths)
                                isProcessing = false
                                onResult(text)
                            }
                        }
                    },
                    enabled = paths.isNotEmpty() && !isProcessing
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = AppIcons.Check,
                            contentDescription = "Recognize"
                        )
                    }
                }
            }
        )
        
        // Drawing canvas
        val currentStroke = remember { mutableStateListOf<Offset>() }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(androidx.compose.ui.graphics.Color.White)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentStroke.clear()
                            currentStroke.add(offset)
                        },
                        onDrag = { change, _ ->
                            currentStroke.add(change.position)
                        },
                        onDragEnd = {
                            if (currentStroke.isNotEmpty()) {
                                paths.add(currentStroke.toList())
                                currentStroke.clear()
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Draw completed strokes
                paths.forEach { stroke ->
                    if (stroke.size > 1) {
                        for (i in 0 until stroke.size - 1) {
                            drawLine(
                                color = androidx.compose.ui.graphics.Color.Black,
                                start = stroke[i],
                                end = stroke[i + 1],
                                strokeWidth = 8f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
                // Draw current stroke
                if (currentStroke.size > 1) {
                    for (i in 0 until currentStroke.size - 1) {
                        drawLine(
                            color = Color.Black,
                            start = currentStroke[i],
                            end = currentStroke[i + 1],
                            strokeWidth = 8f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
        
        // Hint text
        Text(
            text = "Write on the screen, then tap ✓ to recognize",
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = androidx.compose.ui.graphics.Color.Gray
        )
    }
}

@Suppress("UNUSED_PARAMETER")
suspend fun recognizeHandwriting(
    _context: android.content.Context,
    paths: List<List<Offset>>
): String = withContext(Dispatchers.IO) {
    try {
        // Convert paths to bitmap
        val bitmap = createBitmapFromPaths(paths, 800, 1200)
        
        // Convert bitmap to InputImage
        val image = InputImage.fromBitmap(bitmap, 0)
        
        // Recognize text
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val result = recognizer.process(image).await()
        
        OcrLayoutFormatter.format(result)
    } catch (e: Exception) {
        e.printStackTrace()
        ""
    }
}

fun createBitmapFromPaths(paths: List<List<Offset>>, width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(AndroidColor.WHITE)
    
    val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = AndroidColor.BLACK
    }
    
    // Draw all strokes
    paths.forEach { stroke ->
        if (stroke.size > 1) {
            val androidPath = AndroidPath()
            androidPath.moveTo(stroke[0].x, stroke[0].y)
            for (i in 1 until stroke.size) {
                androidPath.lineTo(stroke[i].x, stroke[i].y)
            }
            canvas.drawPath(androidPath, paint)
        }
    }
    
    return bitmap
}
