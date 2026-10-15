package com.freeup.textactions

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.freeup.textactions.ui.theme.AppIcons
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.text.font.FontWeight
import com.freeup.textactions.ui.theme.NunitoFontFamily
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.freeup.textactions.ui.theme.TextActionsTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/** Script-specific recognizers available in ML Kit Text Recognition v2. */
enum class OcrScript(val labelRes: Int) {
    AUTO(R.string.ocr_script_auto),
    LATIN(R.string.ocr_script_latin),
    CHINESE(R.string.ocr_script_chinese),
    JAPANESE(R.string.ocr_script_japanese),
    KOREAN(R.string.ocr_script_korean),
    DEVANAGARI(R.string.ocr_script_hindi)
}

private enum class CropDragMode { MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

class CropOcrActivity : BaseActivity() {
    private data class CropRequest(val uri: Uri, val localImagePath: String?, val source: InputSource)

    companion object {
        const val EXTRA_IMAGE_URI = "extra_image_uri"
        const val EXTRA_LOCAL_IMAGE_PATH = "extra_local_image_path"
        const val EXTRA_INPUT_SOURCE = "extra_input_source"
    }

    private var currentRequest by mutableStateOf<CropRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentRequest = requestFrom(intent)
        if (currentRequest == null) {
            finish()
            return
        }

        setContent {
            val settings = SettingsManager(this)
            val dark = when (settings.getThemeMode()) {
                "dark" -> true
                "light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            TextActionsTheme(darkTheme = dark, dynamicColor = settings.isDynamicColorEnabled()) {
                currentRequest?.let { request ->
                    CropOcrScreen(
                        imageUri = request.uri,
                        localImagePath = request.localImagePath,
                        source = request.source,
                        onCancel = { finish() },
                        onRecognize = { bitmap, script, onFinished ->
                            recognizeAndOpen(request.uri, request.source, bitmap, script, onFinished)
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // singleTask routes later shares here. Updating Compose state reloads the
        // newest cached image instead of retaining the previous bitmap/card.
        currentRequest = requestFrom(intent)
    }

    private fun requestFrom(intent: Intent): CropRequest? {
        val uri = intent.getStringExtra(EXTRA_IMAGE_URI)?.let(Uri::parse)
            ?: intent.data
            ?: return null
        val source = intent.getStringExtra(EXTRA_INPUT_SOURCE)
            ?.let { runCatching { InputSource.valueOf(it) }.getOrNull() }
            ?: InputSource.GALLERY
        var localPath = intent.getStringExtra(EXTRA_LOCAL_IMAGE_PATH)
        if (localPath == null || !File(localPath).exists()) {
            localPath = resolveLocalFileFromUri(this, uri)
        }
        return CropRequest(uri, localPath, source)
    }

    private fun recognizeAndOpen(
        uri: Uri,
        source: InputSource,
        bitmap: Bitmap,
        script: OcrScript,
        onFinished: () -> Unit
    ) {
        lifecycleScope.launch {
            val text = withContext(Dispatchers.Default) {
                runCatching { recognizeText(InputImage.fromBitmap(bitmap, 0), script) }
                    .onFailure { Log.e("CropOcr", "OCR failed", it) }
                    .getOrDefault("")
            }
            if (text.isBlank()) {
                Toast.makeText(this@CropOcrActivity, getString(R.string.ocr_no_text_found), Toast.LENGTH_SHORT).show()
                onFinished()
                return@launch
            }
            if (intent.getBooleanExtra(CameraScanActivity.EXTRA_RETURN_RESULT, false)) {
                // Rescan from an open OCR sheet: give the text back instead of opening a second sheet
                setResult(RESULT_OK, android.content.Intent().apply {
                    putExtra(android.content.Intent.EXTRA_PROCESS_TEXT, text)
                    putExtra(CameraScanActivity.EXTRA_INPUT_SOURCE, source.name)
                    putExtra(CameraScanActivity.EXTRA_ORIGINAL_IMAGE_URI, uri.toString())
                })
                finish()
                return@launch
            }
            startActivity(android.content.Intent(this@CropOcrActivity, ProcessTextActivity::class.java).apply {
                putExtra(android.content.Intent.EXTRA_PROCESS_TEXT, text)
                putExtra(CameraScanActivity.EXTRA_INPUT_SOURCE, source.name)
                putExtra(CameraScanActivity.EXTRA_ORIGINAL_IMAGE_URI, uri.toString())
            })
            setResult(RESULT_OK)
            CameraScanActivity.finishIfOpen()
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CropOcrScreen(
    imageUri: Uri,
    localImagePath: String?,
    source: InputSource = InputSource.GALLERY,
    onCancel: () -> Unit,
    onRecognize: (Bitmap, OcrScript, onFinished: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    var script by remember { mutableStateOf(OcrScript.AUTO) }
    var selection by remember(imageUri) { mutableStateOf<Rect?>(null) }
    var imageBounds by remember(imageUri) { mutableStateOf<Rect?>(null) }
    var dragMode by remember(imageUri) { mutableStateOf(CropDragMode.MOVE) }
    var loading by remember(imageUri) { mutableStateOf(true) }
    var recognizing by remember(imageUri) { mutableStateOf(false) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val handleRadius = with(density) { 12.dp.toPx() }
    val handleTouchRadius = with(density) { 32.dp.toPx() }

    androidx.compose.runtime.LaunchedEffect(imageUri, localImagePath) {
        loading = true
        bitmap = null
        bitmap = withContext(Dispatchers.IO) { loadUprightBitmap(context, imageUri, localImagePath) }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.ocr_select_text_area),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = NunitoFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(AppIcons.Close, stringResource(R.string.ocr_close))
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Language selector button (Same height: 52dp)
                var menuExpanded by remember { mutableStateOf(false) }
                Box {
                    Surface(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.Translate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(script.labelRes),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = NunitoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Icon(
                                imageVector = AppIcons.CaretDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        for (entry in OcrScript.entries) {
                            val isSelected = script == entry
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(entry.labelRes),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = NunitoFontFamily,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = AppIcons.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                } else null,
                                onClick = {
                                    script = entry
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                }

                // 2. Recognize text button (Same height: 52dp, flex weight)
                Button(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = bitmap != null && !loading && !recognizing,
                    onClick = {
                        val source = bitmap ?: return@Button
                        val crop = selection?.let { rect -> imageBounds?.let { bounds -> cropBitmap(source, rect, bounds) } }
                        recognizing = true
                        onRecognize(crop ?: source, script) { recognizing = false }
                    }
                ) {
                    if (recognizing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.ocr_recognizing),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = NunitoFontFamily,
                                fontWeight = FontWeight.SemiBold
                            ),
                            maxLines = 1
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.ocr_recognize_text),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = NunitoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (bitmap == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text(stringResource(R.string.ocr_could_not_open_image)) }
        } else {
            val image = bitmap!!
            // Calculate bounds only when the image or viewport changes. The old code
            // mutated Compose state during every Canvas draw, which made dragging
            // continuously restart composition and the pointer gesture.
            androidx.compose.runtime.LaunchedEffect(image, viewportSize) {
                if (viewportSize == IntSize.Zero) return@LaunchedEffect
                val scale = min(
                    viewportSize.width.toFloat() / image.width,
                    viewportSize.height.toFloat() / image.height
                )
                val width = image.width * scale
                val height = image.height * scale
                val left = (viewportSize.width - width) / 2f
                val top = (viewportSize.height - height) / 2f
                val bounds = Rect(left, top, left + width, top + height)
                imageBounds = bounds
                if (selection == null) {
                    selection = if (source == InputSource.CAMERA) bounds else defaultSelection(bounds)
                }
            }
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color.Black)
                    .onSizeChanged { viewportSize = it }
                    // Keep this gesture alive while selection changes. Selection was
                    // previously a pointerInput key, so every drag event restarted it.
                    .pointerInput(imageBounds) {
                        detectDragGestures(
                            onDragStart = { point ->
                                val bounds = imageBounds ?: return@detectDragGestures
                                val current = selection ?: if (source == InputSource.CAMERA) bounds else defaultSelection(bounds)
                                selection = current.coerceInside(bounds)
                                dragMode = current.dragModeAt(point, handleTouchRadius)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val bounds = imageBounds ?: return@detectDragGestures
                                val current = selection ?: if (source == InputSource.CAMERA) bounds else defaultSelection(bounds)
                                selection = when (dragMode) {
                                    CropDragMode.MOVE -> current.translate(dragAmount).coerceInside(bounds)
                                    else -> current.resize(dragMode, dragAmount, bounds)
                                }
                            }
                        )
                    }
            ) {
                val bounds = imageBounds ?: return@Canvas
                drawImage(
                    image = image.asImageBitmap(),
                    dstOffset = androidx.compose.ui.unit.IntOffset(bounds.left.toInt(), bounds.top.toInt()),
                    dstSize = IntSize(bounds.width.toInt(), bounds.height.toInt())
                )
                selection?.let { selected ->
                    drawRect(Color(0x99000000), topLeft = Offset(bounds.left, bounds.top), size = androidx.compose.ui.geometry.Size(bounds.width, selected.top - bounds.top))
                    drawRect(Color(0x99000000), topLeft = Offset(bounds.left, selected.bottom), size = androidx.compose.ui.geometry.Size(bounds.width, bounds.bottom - selected.bottom))
                    drawRect(Color(0x99000000), topLeft = Offset(bounds.left, selected.top), size = androidx.compose.ui.geometry.Size(selected.left - bounds.left, selected.height))
                    drawRect(Color(0x99000000), topLeft = Offset(selected.right, selected.top), size = androidx.compose.ui.geometry.Size(bounds.right - selected.right, selected.height))
                    drawRect(Color.White, topLeft = selected.topLeft, size = selected.size, style = Stroke(width = 3.dp.toPx()))
                    listOf(selected.topLeft, selected.topRight, selected.bottomLeft, selected.bottomRight).forEach { corner ->
                        drawCircle(Color(0xFF78D7C2), radius = handleRadius, center = corner)
                        drawCircle(Color.White, radius = handleRadius, center = corner, style = Stroke(width = 2.dp.toPx()))
                    }
                }
            }
        }
    }
}

private fun defaultSelection(bounds: Rect): Rect = Rect(
    bounds.left + bounds.width * .08f,
    bounds.top + bounds.height * .08f,
    bounds.right - bounds.width * .08f,
    bounds.bottom - bounds.height * .08f
)

private fun Rect.translate(delta: Offset) = Rect(left + delta.x, top + delta.y, right + delta.x, bottom + delta.y)

private fun Rect.coerceInside(bounds: Rect): Rect {
    val dx = when {
        left < bounds.left -> bounds.left - left
        right > bounds.right -> bounds.right - right
        else -> 0f
    }
    val dy = when {
        top < bounds.top -> bounds.top - top
        bottom > bounds.bottom -> bounds.bottom - bottom
        else -> 0f
    }
    return translate(Offset(dx, dy))
}

private fun Rect.dragModeAt(point: Offset, threshold: Float): CropDragMode {
    fun near(x: Float, y: Float) = (point.x - x) * (point.x - x) + (point.y - y) * (point.y - y) < threshold * threshold
    return when {
        near(left, top) -> CropDragMode.TOP_LEFT
        near(right, top) -> CropDragMode.TOP_RIGHT
        near(left, bottom) -> CropDragMode.BOTTOM_LEFT
        near(right, bottom) -> CropDragMode.BOTTOM_RIGHT
        else -> CropDragMode.MOVE
    }
}

private fun Rect.resize(mode: CropDragMode, delta: Offset, bounds: Rect): Rect {
    val minSide = 64f
    var l = left
    var t = top
    var r = right
    var b = bottom
    when (mode) {
        CropDragMode.TOP_LEFT -> { l += delta.x; t += delta.y }
        CropDragMode.TOP_RIGHT -> { r += delta.x; t += delta.y }
        CropDragMode.BOTTOM_LEFT -> { l += delta.x; b += delta.y }
        CropDragMode.BOTTOM_RIGHT -> { r += delta.x; b += delta.y }
        CropDragMode.MOVE -> Unit
    }
    l = l.coerceIn(bounds.left, r - minSide)
    r = r.coerceIn(l + minSide, bounds.right)
    t = t.coerceIn(bounds.top, b - minSide)
    b = b.coerceIn(t + minSide, bounds.bottom)
    return Rect(l, t, r, b)
}

private fun cropBitmap(bitmap: Bitmap, selected: Rect, bounds: Rect): Bitmap {
    val sx = bitmap.width / bounds.width
    val sy = bitmap.height / bounds.height
    val x = ((selected.left - bounds.left) * sx).toInt().coerceIn(0, bitmap.width - 1)
    val y = ((selected.top - bounds.top) * sy).toInt().coerceIn(0, bitmap.height - 1)
    val width = max(1, (selected.width * sx).toInt()).coerceAtMost(bitmap.width - x)
    val height = max(1, (selected.height * sy).toInt()).coerceAtMost(bitmap.height - y)
    return Bitmap.createBitmap(bitmap, x, y, width, height)
}

internal fun resolveLocalFileFromUri(context: android.content.Context, uri: Uri): String? {
    return try {
        if (uri.scheme == "file") {
            val path = uri.path ?: return null
            if (File(path).exists()) return path
        }
        if (uri.authority == "${context.packageName}.fileprovider") {
            val path = uri.path ?: return null
            val relCache = path.removePrefix("/cache/").removePrefix("/")
            val cacheFile = File(context.cacheDir, relCache)
            if (cacheFile.exists()) return cacheFile.absolutePath

            uri.lastPathSegment?.let { segment ->
                val directCache = File(context.cacheDir, segment)
                if (directCache.exists()) return directCache.absolutePath
            }

            val relFiles = path.removePrefix("/files/").removePrefix("/")
            val filesFile = File(context.filesDir, relFiles)
            if (filesFile.exists()) return filesFile.absolutePath
        }
        null
    } catch (e: Exception) {
        Log.e("CropOcr", "Error resolving local file from URI: $uri", e)
        null
    }
}

private fun loadUprightBitmap(context: android.content.Context, uri: Uri, localImagePath: String? = null): Bitmap? {
    // 1. Direct local file path if available or resolvable from FileProvider/file URI
    val resolvedPath = localImagePath ?: resolveLocalFileFromUri(context, uri)
    if (resolvedPath != null && File(resolvedPath).exists()) {
        val bitmap = loadUprightBitmapFromFile(resolvedPath)
        if (bitmap != null) return bitmap
    }

    // 2. Try copying stream to a temp file in cache (reliable across activity hops and permission bounds)
    try {
        val tempDir = File(context.cacheDir, "crop-temp").apply { mkdirs() }
        val tempFile = File(tempDir, "crop-${System.currentTimeMillis()}.img")
        val copied = context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
            true
        } ?: false
        if (copied && tempFile.length() > 0) {
            val bitmap = loadUprightBitmapFromFile(tempFile.absolutePath)
            if (bitmap != null) return bitmap
        }
    } catch (e: Exception) {
        Log.w("CropOcr", "Could not copy stream to temp file for $uri: ${e.message}")
    }

    // 3. Fallback: direct stream decode with downsampling
    try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth > 0 && options.outHeight > 0) {
            var sample = 1
            while (max(options.outWidth / sample, options.outHeight / sample) > 2048) sample *= 2
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                })
            }
            if (decoded != null) {
                val rotation = getRotationFromUri(context, uri)
                return if (rotation == 0) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
            }
        }
    } catch (e: Exception) {
        Log.w("CropOcr", "Direct stream decode failed for $uri: ${e.message}")
    }

    // 4. Fallback: ImageDecoder (HEIC/AVIF/WebP)
    return decodeWithImageDecoder(context, uri)
}

private fun loadUprightBitmapFromFile(path: String): Bitmap? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, options)
    if (options.outWidth <= 0 || options.outHeight <= 0) {
        Log.e("CropOcr", "Shared file is not a decodable image: $path")
        return null
    }
    var sample = 1
    while (max(options.outWidth / sample, options.outHeight / sample) > 2048) sample *= 2
    val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }) ?: return null
    val rotation = try {
        when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (_: Exception) { 0 }
    if (rotation == 0) return decoded
    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
}

private fun decodeWithImageDecoder(context: android.content.Context, uri: Uri): Bitmap? = try {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
} catch (error: Exception) {
    Log.e("CropOcr", "Unable to decode shared image: $uri", error)
    null
}

private suspend fun recognizeText(image: InputImage, script: OcrScript): String {
    if (script == OcrScript.AUTO) {
        return coroutineScope {
            listOf(
                OcrScript.LATIN,
                OcrScript.CHINESE,
                OcrScript.JAPANESE,
                OcrScript.KOREAN,
                OcrScript.DEVANAGARI
            ).map { candidate ->
                async { candidate to runCatching { recognizeWithScript(image, candidate) }.getOrDefault("") }
            }.awaitAll()
                .maxByOrNull { (candidate, text) -> scoreRecognition(candidate, text) }
                ?.second
                .orEmpty()
        }
    }
    return recognizeWithScript(image, script)
}

private suspend fun recognizeWithScript(image: InputImage, script: OcrScript): String {
    val recognizer = when (script) {
        OcrScript.LATIN -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        OcrScript.CHINESE -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        OcrScript.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        OcrScript.KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        OcrScript.DEVANAGARI -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        OcrScript.AUTO -> error("Auto must choose a recognizer first")
    }
    return try {
        OcrLayoutFormatter.format(recognizer.process(image).await())
    } finally {
        recognizer.close()
    }
}

private fun scoreRecognition(script: OcrScript, text: String): Int {
    val glyphs = text.count { it.isLetterOrDigit() }
    if (glyphs == 0) return 0
    val supported = text.count { char ->
        val unicodeScript = Character.UnicodeScript.of(char.code)
        when (script) {
            OcrScript.LATIN -> unicodeScript == Character.UnicodeScript.LATIN
            OcrScript.CHINESE -> unicodeScript == Character.UnicodeScript.LATIN || unicodeScript == Character.UnicodeScript.HAN
            OcrScript.JAPANESE -> unicodeScript == Character.UnicodeScript.LATIN || unicodeScript == Character.UnicodeScript.HAN || unicodeScript == Character.UnicodeScript.HIRAGANA || unicodeScript == Character.UnicodeScript.KATAKANA
            OcrScript.KOREAN -> unicodeScript == Character.UnicodeScript.LATIN || unicodeScript == Character.UnicodeScript.HANGUL
            OcrScript.DEVANAGARI -> unicodeScript == Character.UnicodeScript.LATIN || unicodeScript == Character.UnicodeScript.DEVANAGARI
            OcrScript.AUTO -> false
        }
    }
    // Prefer the output whose letters fit the script model, while still favouring
    // complete text over a short but perfectly matching fragment.
    return glyphs + supported * 3
}
