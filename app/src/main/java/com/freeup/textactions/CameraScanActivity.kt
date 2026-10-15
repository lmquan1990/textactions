package com.freeup.textactions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.annotation.OptIn
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import android.os.Build
import android.content.ContentUris
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Rect
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.freeup.textactions.ui.theme.NunitoFontFamily
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.freeup.textactions.ui.theme.AppIcons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.max
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import android.graphics.Color as AndroidColor
import com.freeup.textactions.ui.theme.TextActionsTheme
import com.freeup.textactions.SettingsManager
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

/** One detected QR code: full content + short label */
private data class DetectedQr(val rawValue: String, val displayText: String)

/** Detected QR with bounding box in image coordinates */
private data class DetectedQrWithBox(
    val rawValue: String,
    val displayText: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

/** Result from analyzer: list + image dimensions for coordinate transform */
private data class QrAnalysisResult(
    val detected: List<DetectedQrWithBox>,
    val imageWidth: Int,
    val imageHeight: Int,
    val rotationDegrees: Int
)

/** Pick the QR whose center is closest to image center (the one user is pointing at) */
private fun QrAnalysisResult.focusedQr(): DetectedQrWithBox? {
    if (detected.isEmpty()) return null
    val cx = imageWidth / 2f
    val cy = imageHeight / 2f
    return detected.minByOrNull { qr ->
        val qcx = (qr.left + qr.right) / 2f
        val qcy = (qr.top + qr.bottom) / 2f
        (qcx - cx) * (qcx - cx) + (qcy - cy) * (qcy - cy)
    }
}

/** Transform image rect to view rect (fit-center); rotation 0,90,180,270 */
private fun imageRectToView(
    left: Int, top: Int, right: Int, bottom: Int,
    imageWidth: Int, imageHeight: Int, rotationDegrees: Int,
    viewWidth: Int, viewHeight: Int
): ComposeRect? {
    if (viewWidth <= 0 || viewHeight <= 0) return null
    val (dispW, dispH) = when (rotationDegrees) {
        90, 270 -> imageHeight to imageWidth
        else -> imageWidth to imageHeight
    }
    val scale = minOf(viewWidth.toFloat() / dispW, viewHeight.toFloat() / dispH)
    val drawW = dispW * scale
    val drawH = dispH * scale
    val offsetX = (viewWidth - drawW) / 2f
    val offsetY = (viewHeight - drawH) / 2f
    fun mapPoint(ix: Float, iy: Float): Offset {
        return when (rotationDegrees) {
            0 -> Offset(offsetX + ix * scale, offsetY + iy * scale)
            90 -> Offset(offsetX + (imageHeight - iy) * scale, offsetY + ix * scale)
            180 -> Offset(offsetX + (imageWidth - ix) * scale, offsetY + (imageHeight - iy) * scale)
            270 -> Offset(offsetX + iy * scale, offsetY + (imageWidth - ix) * scale)
            else -> Offset(offsetX + ix * scale, offsetY + iy * scale)
        }
    }
    val tl = mapPoint(left.toFloat(), top.toFloat())
    val tr = mapPoint(right.toFloat(), top.toFloat())
    val br = mapPoint(right.toFloat(), bottom.toFloat())
    val bl = mapPoint(left.toFloat(), bottom.toFloat())
    val minX = min(min(tl.x, tr.x), min(br.x, bl.x))
    val maxX = max(max(tl.x, tr.x), max(br.x, bl.x))
    val minY = min(min(tl.y, tr.y), min(br.y, bl.y))
    val maxY = max(max(tl.y, tr.y), max(br.y, bl.y))
    return ComposeRect(Offset(minX, minY), Size(maxX - minX, maxY - minY))
}

private fun getRotationFromFile(file: File): Int {
    return try {
        ExifInterface(file.absolutePath).let { exif ->
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }
    } catch (_: Exception) { 0 }
}

internal fun getRotationFromUri(context: android.content.Context, uri: Uri): Int {
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).let { exif ->
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            }
        } ?: 0
    } catch (_: Exception) { 0 }
}

class CameraScanActivity : BaseActivity() {
    data class CachedSharedImage(val uri: Uri, val path: String)

    private var scanType: String = "ocr" // "ocr", "qr", or "gallery"
    /** URI passed to TakePicture; kept so callback can read image after config change */
    var photoUriForCapture: Uri? = null
        private set
    var photoPathForCapture: String? = null
        private set
    var lastPickedImageUri: Uri? = null
        private set

    fun setPhotoUriForCapture(uri: Uri?, path: String? = null) {
        photoUriForCapture = uri
        photoPathForCapture = path
    }
    fun setLastPickedImageUri(uri: Uri?) { lastPickedImageUri = uri }

    // When opened for a result (rescan from the OCR sheet), the crop screen's text is handed back to the caller
    private val cropLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Scan succeeded: hand the text back (rescan) or just release the camera (the result sheet is already open)
            if (result.data != null) setResult(RESULT_OK, result.data)
            finish()
        }
    }

    private fun openCrop(cropIntent: Intent) {
        if (intent.getBooleanExtra(EXTRA_RETURN_RESULT, false)) {
            cropIntent.putExtra(EXTRA_RETURN_RESULT, true)
        }
        cropIntent.removeFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        cropLauncher.launch(cropIntent)
    }

    private fun handleScanResult(text: String, imageUri: Uri?, inputSource: InputSource) {
        val returnResult = intent.getBooleanExtra(EXTRA_RETURN_RESULT, false)
        if (returnResult) {
            val data = Intent().apply {
                putExtra(Intent.EXTRA_PROCESS_TEXT, text)
                putExtra(EXTRA_INPUT_SOURCE, inputSource.name)
                putExtra(EXTRA_ORIGINAL_IMAGE_URI, imageUri?.toString())
            }
            setResult(RESULT_OK, data)
            finish()
        } else {
            val intent = Intent(this@CameraScanActivity, ProcessTextActivity::class.java).apply {
                putExtra(Intent.EXTRA_PROCESS_TEXT, text)
                putExtra(EXTRA_INPUT_SOURCE, inputSource.name)
                putExtra(EXTRA_ORIGINAL_IMAGE_URI, imageUri?.toString())
            }
            startActivity(intent)
            finish()
        }
    }

    companion object {
        const val EXTRA_RETURN_RESULT = "EXTRA_RETURN_RESULT"
        const val EXTRA_INPUT_SOURCE = "EXTRA_INPUT_SOURCE"
        const val EXTRA_ORIGINAL_IMAGE_URI = "EXTRA_ORIGINAL_IMAGE_URI"

        private var openInstance: java.lang.ref.WeakReference<CameraScanActivity>? = null

        /**
         * Closes the camera screen once a scan has succeeded. A result callback can't do this: the
         * translucent result sheet covers the camera, so the camera never resumes to receive it.
         */
        fun finishIfOpen() {
            openInstance?.get()?.finish()
            openInstance = null
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newScanType = intent.getStringExtra("scan_type") ?: "ocr"
        if (newScanType != scanType) {
            scanType = newScanType
            recreate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openInstance = java.lang.ref.WeakReference(this)

        // Android shares screenshot previews and Gallery images as ACTION_SEND with a
        // temporary content URI. The grant belongs to this receiving activity; merely
        // forwarding the URI in EXTRA_STREAM does not reliably forward that grant to
        // CropOcrActivity (notably with Chrome on MIUI). Copy it into our cache while
        // the grant is valid, then crop the app-owned copy.
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val sharedImage = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (sharedImage == null) {
                Toast.makeText(this, "No image received", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            val localImage = copySharedImageToCache(sharedImage)
            if (localImage == null) {
                Toast.makeText(this, "Could not open shared image", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            startActivity(Intent(this, CropOcrActivity::class.java).apply {
                putExtra(CropOcrActivity.EXTRA_IMAGE_URI, localImage.uri.toString())
                putExtra(CropOcrActivity.EXTRA_LOCAL_IMAGE_PATH, localImage.path)
                putExtra(CropOcrActivity.EXTRA_INPUT_SOURCE, InputSource.SHARE.name)
                clipData = ClipData.newRawUri("shared_image", localImage.uri)
                // A second Share can arrive while a previous crop screen is still in
                // this task. Clear it so that the new URI cannot reuse its bitmap.
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
            finish()
            return
        }
        
        scanType = intent.getStringExtra("scan_type") ?: "ocr"
        
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
                if (scanType == "qr") {
                    LiveQrScanScreen(
                        onResult = { text ->
                            if (text.isNotEmpty()) {
                                handleScanResult(
                                    text = text,
                                    imageUri = null,
                                    inputSource = InputSource.SHARE
                                )
                            }
                        },
                        onCancel = { finish() }
                    )
                } else if (scanType == "gallery") {
                    GalleryOnlyScreen(
                        onImagePicked = { uri ->
                            val localImagePath = copySharedImageToCache(uri)?.path
                            openCrop(Intent(this@CameraScanActivity, CropOcrActivity::class.java).apply {
                                putExtra(CropOcrActivity.EXTRA_IMAGE_URI, uri.toString())
                                if (localImagePath != null) {
                                    putExtra(CropOcrActivity.EXTRA_LOCAL_IMAGE_PATH, localImagePath)
                                }
                                putExtra(CropOcrActivity.EXTRA_INPUT_SOURCE, InputSource.GALLERY.name)
                                clipData = ClipData.newRawUri("image", uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            })
                            if (!intent.getBooleanExtra(EXTRA_RETURN_RESULT, false)) finish()
                        },
                        onCancel = { finish() }
                    )
                } else {
                    LiveCameraOcrScreen(
                        onImageCaptured = { uri, path ->
                            openCrop(Intent(this@CameraScanActivity, CropOcrActivity::class.java).apply {
                                putExtra(CropOcrActivity.EXTRA_IMAGE_URI, uri.toString())
                                putExtra(CropOcrActivity.EXTRA_LOCAL_IMAGE_PATH, path)
                                putExtra(CropOcrActivity.EXTRA_INPUT_SOURCE, InputSource.CAMERA.name)
                                clipData = ClipData.newRawUri("image", uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            })
                        },
                        onGalleryPick = { uri ->
                            val localImagePath = copySharedImageToCache(uri)?.path
                            openCrop(Intent(this@CameraScanActivity, CropOcrActivity::class.java).apply {
                                putExtra(CropOcrActivity.EXTRA_IMAGE_URI, uri.toString())
                                if (localImagePath != null) {
                                    putExtra(CropOcrActivity.EXTRA_LOCAL_IMAGE_PATH, localImagePath)
                                }
                                putExtra(CropOcrActivity.EXTRA_INPUT_SOURCE, InputSource.GALLERY.name)
                                clipData = ClipData.newRawUri("image", uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            })
                        },
                        onCancel = { finish() }
                    )
                }
            }
        }
    }

    /** Persist an externally shared URI before its one-shot read permission expires. */
    internal fun copySharedImageToCache(source: Uri): CachedSharedImage? {
        return try {
            val directory = File(cacheDir, "shared-images").apply { mkdirs() }
            val destination = File(directory, "shared-${System.currentTimeMillis()}.img")
            contentResolver.openInputStream(source)?.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            CachedSharedImage(
                uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", destination),
                path = destination.absolutePath
            )
        } catch (error: Exception) {
            Log.e("CameraScan", "Unable to copy shared image: $source", error)
            null
        }
    }
}

/** Holds camera reference so we can toggle torch from Composable */
private class CameraHolder { var camera: Camera? = null }

/**
 * Calculates the viewfinder guide box rectangle for OCR scanning.
 * Proportioned to provide comfortable breathing room away from the top bar and shutter button.
 */
private fun calculateOcrFrameRect(width: Float, height: Float, topShiftPx: Float = 0f): ComposeRect {
    val boxWidth = width * 0.85f
    val boxHeight = height * 0.60f
    val left = (width - boxWidth) / 2f
    val top = (height - boxHeight) / 2f - topShiftPx
    return ComposeRect(Offset(left, top), Size(boxWidth, boxHeight))
}

/**
 * Checks whether the app has permission to read user's media images.
 */
private fun hasMediaPermission(context: android.content.Context): Boolean {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        }
        else -> {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
}

/**
 * Loads the latest photo thumbnail from the user's gallery / MediaStore, like Google Lens.
 * Returns null if no images are present or if permission is not granted.
 */
private suspend fun loadLatestGalleryThumbnail(context: android.content.Context): Bitmap? = withContext(Dispatchers.IO) {
    try {
        if (!hasMediaPermission(context)) return@withContext null

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val queryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        context.contentResolver.query(
            queryUri,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val id = cursor.getLong(idColumn)
                val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        return@withContext context.contentResolver.loadThumbnail(contentUri, android.util.Size(160, 160), null)
                    } catch (_: Exception) { }
                }

                context.contentResolver.openInputStream(contentUri)?.use { inputStream ->
                    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(inputStream, null, boundsOpts)

                    val maxDim = max(boundsOpts.outWidth, boundsOpts.outHeight)
                    var sample = 1
                    while (maxDim / sample > 200) {
                        sample *= 2
                    }

                    context.contentResolver.openInputStream(contentUri)?.use { stream2 ->
                        val decodeOpts = BitmapFactory.Options().apply {
                            inSampleSize = sample
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        return@withContext BitmapFactory.decodeStream(stream2, null, decodeOpts)
                    }
                }
            }
        }
        null
    } catch (e: Exception) {
        Log.w("LiveCameraOcr", "Could not load latest gallery thumbnail", e)
        null
    }
}

@Composable
fun LiveCameraOcrScreen(
    onImageCaptured: (Uri, String) -> Unit,
    onGalleryPick: (Uri) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    var hasPermission by remember { mutableStateOf(false) }
    var permissionRequested by remember { mutableStateOf(false) }
    var flashOn by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    val cameraHolder = remember { CameraHolder() }
    val imageCaptureHolder = remember { mutableStateOf<ImageCapture?>(null) }
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current

    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var frameRect by remember { mutableStateOf<ComposeRect?>(null) }
    val topShiftPx = with(density) { 24.dp.toPx() }

    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        @Suppress("DEPRECATION")
        window.statusBarColor = AndroidColor.TRANSPARENT
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, context.getString(R.string.camera_permission_required), Toast.LENGTH_SHORT).show()
            onCancel()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { onGalleryPick(it) }
    }

    var latestThumbnail by remember { mutableStateOf<Bitmap?>(null) }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            coroutineScope.launch {
                latestThumbnail = loadLatestGalleryThumbnail(context)
            }
        }
    }

    LaunchedEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission && !permissionRequested) {
            permissionRequested = true
        }
    }
    LaunchedEffect(hasPermission, permissionRequested) {
        if (permissionRequested && !hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Load latest gallery thumbnail when camera permission is active
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            if (!hasMediaPermission(context)) {
                val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_IMAGES
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
                mediaPermissionLauncher.launch(perm)
            } else {
                latestThumbnail = loadLatestGalleryThumbnail(context)
            }
        }
    }

    // Refresh gallery thumbnail whenever returning to the camera
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (hasMediaPermission(context)) {
                    coroutineScope.launch {
                        latestThumbnail = loadLatestGalleryThumbnail(context)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(flashOn) {
        try {
            cameraHolder.camera?.cameraControl?.enableTorch(flashOn)
        } catch (_: Exception) { }
    }

    if (!hasPermission) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { size ->
                viewSize = size
                if (size.width > 0 && size.height > 0) {
                    frameRect = calculateOcrFrameRect(size.width.toFloat(), size.height.toFloat(), topShiftPx)
                }
            }
    ) {
        // 1. CameraX PreviewView
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build()
                    val imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    imageCaptureHolder.value = imageCapture

                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture
                        )
                        cameraHolder.camera = camera
                    } catch (e: Exception) {
                        Log.e("LiveCameraOcr", "Camera bind failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize(),
            update = {
                try {
                    cameraHolder.camera?.cameraControl?.enableTorch(flashOn)
                } catch (_: Exception) { }
            }
        )

        // 2. Viewfinder Overlay (Google Lens style: 4 scanning corners + dimmed scrim)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val rect = frameRect ?: if (size.width > 0 && size.height > 0) {
                calculateOcrFrameRect(size.width, size.height, topShiftPx)
            } else null

            if (rect != null && size.width > 0 && size.height > 0) {
                val cornerRadiusPx = 22.dp.toPx()
                val cornerLengthPx = 38.dp.toPx().coerceAtMost(min(rect.width, rect.height) / 2f)

                // Semi-transparent scrim outside guide box (darkened to 0.65f)
                val fullPath = Path().apply {
                    addRect(ComposeRect(Offset.Zero, Size(size.width, size.height)))
                }
                val docPath = Path().apply {
                    addRoundRect(RoundRect(rect, CornerRadius(cornerRadiusPx)))
                }
                val scrimPath = Path()
                scrimPath.op(fullPath, docPath, PathOperation.ReverseDifference)
                drawPath(scrimPath, Color.Black.copy(alpha = 0.65f))

                // Google Lens 4 corner brackets
                val cornerPath = Path().apply {
                    // Top-Left corner
                    moveTo(rect.left, rect.top + cornerLengthPx)
                    lineTo(rect.left, rect.top + cornerRadiusPx)
                    arcTo(
                        rect = ComposeRect(rect.left, rect.top, rect.left + 2 * cornerRadiusPx, rect.top + 2 * cornerRadiusPx),
                        startAngleDegrees = 180f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    lineTo(rect.left + cornerLengthPx, rect.top)

                    // Top-Right corner
                    moveTo(rect.right - cornerLengthPx, rect.top)
                    lineTo(rect.right - cornerRadiusPx, rect.top)
                    arcTo(
                        rect = ComposeRect(rect.right - 2 * cornerRadiusPx, rect.top, rect.right, rect.top + 2 * cornerRadiusPx),
                        startAngleDegrees = 270f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    lineTo(rect.right, rect.top + cornerLengthPx)

                    // Bottom-Right corner
                    moveTo(rect.right, rect.bottom - cornerLengthPx)
                    lineTo(rect.right, rect.bottom - cornerRadiusPx)
                    arcTo(
                        rect = ComposeRect(rect.right - 2 * cornerRadiusPx, rect.bottom - 2 * cornerRadiusPx, rect.right, rect.bottom),
                        startAngleDegrees = 0f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    lineTo(rect.right - cornerLengthPx, rect.bottom)

                    // Bottom-Left corner
                    moveTo(rect.left + cornerLengthPx, rect.bottom)
                    lineTo(rect.left + cornerRadiusPx, rect.bottom)
                    arcTo(
                        rect = ComposeRect(rect.left, rect.bottom - 2 * cornerRadiusPx, rect.left + 2 * cornerRadiusPx, rect.bottom),
                        startAngleDegrees = 90f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    lineTo(rect.left, rect.bottom - cornerLengthPx)
                }

                drawPath(
                    path = cornerPath,
                    color = Color.White.copy(alpha = 0.8f),
                    style = Stroke(
                        width = 3.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }

        // 3. Top Action Bar: [✕] Back/Close and [⚡] Torch toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onCancel,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f)
            ) {
                Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = AppIcons.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Surface(
                onClick = { flashOn = !flashOn },
                shape = CircleShape,
                color = if (flashOn) Color(0xFFFFD54F).copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.45f)
            ) {
                Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = AppIcons.Flash,
                        contentDescription = "Flash",
                        tint = if (flashOn) Color(0xFFFFD54F) else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // 4. Bottom Controls: [Gallery] [Shutter Button] [Spacer]
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp, start = 32.dp, end = 32.dp)
        ) {
            // Gallery Picker Button (Left) - Google Lens style with latest image thumbnail
            Surface(
                onClick = {
                    galleryLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                },
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.5f),
                border = BorderStroke(
                    width = if (latestThumbnail != null) 2.dp else 1.dp,
                    color = if (latestThumbnail != null) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .size(57.dp)
                    .align(Alignment.CenterStart)
            ) {
                if (latestThumbnail != null) {
                    Image(
                        bitmap = latestThumbnail!!.asImageBitmap(),
                        contentDescription = "Gallery",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.Image,
                            contentDescription = "Gallery",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            // Big Shutter Button (Center)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(80.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !isCapturing) {
                        val imageCapture = imageCaptureHolder.value
                        if (imageCapture == null) {
                            Toast.makeText(context, "Camera chưa sẵn sàng", Toast.LENGTH_SHORT).show()
                            return@clickable
                        }
                        isCapturing = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                        val photoFile = File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                        val captureFrame = frameRect ?: if (viewSize.width > 0 && viewSize.height > 0) {
                            calculateOcrFrameRect(viewSize.width.toFloat(), viewSize.height.toFloat(), topShiftPx)
                        } else null
                        val captureSize = viewSize

                        imageCapture.takePicture(
                            outputOptions,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            cropImageToViewfinder(
                                                photoFile = photoFile,
                                                frameRect = captureFrame,
                                                viewWidth = captureSize.width,
                                                viewHeight = captureSize.height
                                            )
                                        } catch (e: Exception) {
                                            Log.e("LiveCameraOcr", "Crop to viewfinder failed", e)
                                        }
                                        withContext(Dispatchers.Main) {
                                            isCapturing = false
                                            val photoUri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                photoFile
                                            )
                                            onImageCaptured(photoUri, photoFile.absolutePath)
                                        }
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    isCapturing = false
                                    Log.e("LiveCameraOcr", "Capture failed", exception)
                                    Toast.makeText(context, "Lỗi chụp: ${exception.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // Outer ring
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(4.dp, Color.White, CircleShape)
                )
                // Inner solid circle
                if (isCapturing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color.White, CircleShape)
                    )
                }
            }

            // Right spacer
            Spacer(modifier = Modifier.size(50.dp).align(Alignment.CenterEnd))
        }
    }
}

/**
 * Crops captured JPEG to match the on-screen viewfinder guide box.
 */
private fun cropImageToViewfinder(
    photoFile: File,
    frameRect: ComposeRect?,
    viewWidth: Int,
    viewHeight: Int
) {
    if (viewWidth <= 0 || viewHeight <= 0 || !photoFile.exists()) return

    val targetFrame = frameRect ?: calculateOcrFrameRect(viewWidth.toFloat(), viewHeight.toFloat(), 24f)

    val rotationDegrees = getRotationFromFile(photoFile)

    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(photoFile.absolutePath, boundsOpts)
    val rawW = boundsOpts.outWidth
    val rawH = boundsOpts.outHeight
    if (rawW <= 0 || rawH <= 0) return

    var sample = 1
    while (max(rawW / sample, rawH / sample) > 4096) {
        sample *= 2
    }

    val decodeOpts = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val rawBitmap = BitmapFactory.decodeFile(photoFile.absolutePath, decodeOpts) ?: return

    val uprightBitmap = if (rotationDegrees != 0) {
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
        if (rotated != rawBitmap) {
            rawBitmap.recycle()
        }
        rotated
    } else {
        rawBitmap
    }

    val bmpWidth = uprightBitmap.width
    val bmpHeight = uprightBitmap.height

    val screenW = viewWidth.toFloat()
    val screenH = viewHeight.toFloat()
    val scale = max(screenW / bmpWidth, screenH / bmpHeight)
    val scaledW = bmpWidth * scale
    val scaledH = bmpHeight * scale
    val offsetX = (scaledW - screenW) / 2f
    val offsetY = (scaledH - screenH) / 2f

    val cropX = ((targetFrame.left + offsetX) / scale).toInt().coerceIn(0, bmpWidth - 1)
    val cropY = ((targetFrame.top + offsetY) / scale).toInt().coerceIn(0, bmpHeight - 1)
    val cropW = (targetFrame.width / scale).toInt().coerceIn(1, bmpWidth - cropX)
    val cropH = (targetFrame.height / scale).toInt().coerceIn(1, bmpHeight - cropY)

    val croppedBitmap = Bitmap.createBitmap(uprightBitmap, cropX, cropY, cropW, cropH)

    photoFile.outputStream().use { out ->
        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
    }

    if (croppedBitmap != uprightBitmap) {
        croppedBitmap.recycle()
    }
    uprightBitmap.recycle()

    try {
        val exif = ExifInterface(photoFile.absolutePath)
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        exif.saveAttributes()
    } catch (_: Exception) { }
}

@Composable
private fun GalleryOnlyScreen(
    onImagePicked: (Uri) -> Unit,
    onCancel: () -> Unit
) {
    var launched by remember { mutableStateOf(false) }
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onImagePicked(uri)
        } else {
            onCancel()
        }
    }

    LaunchedEffect(Unit) {
        if (!launched) {
            launched = true
            galleryLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = Color.White)
    }
}

@Composable
fun LiveQrScanScreen(
    onResult: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    var hasPermission by remember { mutableStateOf(false) }
    var permissionRequested by remember { mutableStateOf(false) }
    var analysisResult by remember { mutableStateOf<QrAnalysisResult?>(null) }
    val focusedQr = remember(analysisResult) { analysisResult?.focusedQr() }
    var selectedQr by remember { mutableStateOf<DetectedQr?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var didVibrateForQr by remember { mutableStateOf(false) }
    var flashOn by remember { mutableStateOf(false) }
    val view = LocalView.current
    val clipboardManager = remember { context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager }
    val cameraHolder = remember { CameraHolder() }
    var galleryPickerLaunched by remember { mutableStateOf(false) }

    var latestThumbnail by remember { mutableStateOf<Bitmap?>(null) }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            coroutineScope.launch {
                latestThumbnail = loadLatestGalleryThumbnail(context)
            }
        }
    }

    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        @Suppress("DEPRECATION")
        window.statusBarColor = AndroidColor.TRANSPARENT
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Cần quyền camera để quét mã QR", Toast.LENGTH_SHORT).show()
            onCancel()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { processImageFromUri(context, it, "qr", onResult) } ?: Unit
    }

    LaunchedEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission && !permissionRequested) {
            permissionRequested = true
        }
    }
    LaunchedEffect(hasPermission, permissionRequested) {
        if (permissionRequested && !hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Load latest gallery thumbnail when camera permission is active
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            if (!hasMediaPermission(context)) {
                val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_IMAGES
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
                mediaPermissionLauncher.launch(perm)
            } else {
                latestThumbnail = loadLatestGalleryThumbnail(context)
            }
        }
    }

    // Refresh gallery thumbnail whenever returning to the QR scanner
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (hasMediaPermission(context)) {
                    coroutineScope.launch {
                        latestThumbnail = loadLatestGalleryThumbnail(context)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(focusedQr) {
        if (focusedQr != null && !didVibrateForQr) {
            didVibrateForQr = true
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    LaunchedEffect(flashOn) {
        try {
            cameraHolder.camera?.cameraControl?.enableTorch(flashOn)
        } catch (_: Exception) { }
    }

    val onQrAnalysisResult: (QrAnalysisResult) -> Unit = remember {
        { result ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                analysisResult = result
            }
        }
    }

    fun openGalleryForQr() {
        galleryPickerLaunched = true
        galleryLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
    }

    if (selectedQr != null && showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showBottomSheet = false
                selectedQr = null
            },
            scrimColor = Color.Transparent
        ) {
            val qr = selectedQr!!
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Nội dung mã QR",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = qr.rawValue,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        onClick = {
                            clipboardManager.setPrimaryClip(ClipData.newPlainText("QR", qr.rawValue))
                            Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                AppIcons.Copy,
                                contentDescription = null,
                                Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.copy_text),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Button(
                        onClick = {
                            showBottomSheet = false
                            selectedQr = null
                            onResult(qr.rawValue)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(AppIcons.OpenInNew, contentDescription = null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.app_name))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        showBottomSheet = false
                        selectedQr = null
                        val intent = Intent(context, ProcessTextActivity::class.java).apply {
                            putExtra(Intent.EXTRA_PROCESS_TEXT, qr.rawValue)
                            putExtra("EXTRA_AUTO_ACTION", "save_note")
                        }
                        context.startActivity(intent)
                        (context as? android.app.Activity)?.finish()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(AppIcons.Note, contentDescription = null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save as Note")
                }
            }
        }
    }

    if (!hasPermission) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build()
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(ContextCompat.getMainExecutor(ctx), QrAnalyzer(onQrAnalysisResult))
                        }
                    previewView.surfaceProvider.let { provider ->
                        preview.setSurfaceProvider(provider)
                        try {
                            cameraProvider.unbindAll()
                            val camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                            cameraHolder.camera = camera
                        } catch (e: Exception) {
                            Log.e("LiveQrScan", "bind failed", e)
                        }
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize(),
            update = {
                try {
                    cameraHolder.camera?.cameraControl?.enableTorch(flashOn)
                } catch (_: Exception) { }
            }
        )
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(300)
            try {
                cameraHolder.camera?.cameraControl?.enableTorch(flashOn)
            } catch (_: Exception) { }
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (size.width > 0 && size.height > 0) {
                val centerSizePx = min(size.width, size.height) * 0.72f
                val centerLeft = (size.width - centerSizePx) / 2f
                val centerTop = (size.height - centerSizePx) / 2f
                val cornerRadiusPx = 24.dp.toPx()
                val cornerLengthPx = 38.dp.toPx().coerceAtMost(centerSizePx / 2f)
                val centerRect = ComposeRect(
                    offset = Offset(centerLeft, centerTop),
                    size = Size(centerSizePx, centerSizePx)
                )
                val fullPath = Path().apply {
                    addRect(ComposeRect(Offset.Zero, Size(size.width, size.height)))
                }
                val centerPath = Path().apply {
                    addRoundRect(RoundRect(centerRect, CornerRadius(cornerRadiusPx)))
                }
                val scrimPath = Path()
                scrimPath.op(fullPath, centerPath, PathOperation.ReverseDifference)
                drawPath(scrimPath, Color.Black.copy(alpha = 0.65f))

                // Google Lens 4 corner brackets
                val cornerPath = Path().apply {
                    // Top-Left corner
                    moveTo(centerRect.left, centerRect.top + cornerLengthPx)
                    lineTo(centerRect.left, centerRect.top + cornerRadiusPx)
                    arcTo(
                        rect = ComposeRect(centerRect.left, centerRect.top, centerRect.left + 2 * cornerRadiusPx, centerRect.top + 2 * cornerRadiusPx),
                        startAngleDegrees = 180f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    lineTo(centerRect.left + cornerLengthPx, centerRect.top)

                    // Top-Right corner
                    moveTo(centerRect.right - cornerLengthPx, centerRect.top)
                    lineTo(centerRect.right - cornerRadiusPx, centerRect.top)
                    arcTo(
                        rect = ComposeRect(centerRect.right - 2 * cornerRadiusPx, centerRect.top, centerRect.right, centerRect.top + 2 * cornerRadiusPx),
                        startAngleDegrees = 270f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    lineTo(centerRect.right, centerRect.top + cornerLengthPx)

                    // Bottom-Right corner
                    moveTo(centerRect.right, centerRect.bottom - cornerLengthPx)
                    lineTo(centerRect.right, centerRect.bottom - cornerRadiusPx)
                    arcTo(
                        rect = ComposeRect(centerRect.right - 2 * cornerRadiusPx, centerRect.bottom - 2 * cornerRadiusPx, centerRect.right, centerRect.bottom),
                        startAngleDegrees = 0f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    lineTo(centerRect.right - cornerLengthPx, centerRect.bottom)

                    // Bottom-Left corner
                    moveTo(centerRect.left + cornerLengthPx, centerRect.bottom)
                    lineTo(centerRect.left + cornerRadiusPx, centerRect.bottom)
                    arcTo(
                        rect = ComposeRect(centerRect.left, centerRect.bottom - 2 * cornerRadiusPx, centerRect.left + 2 * cornerRadiusPx, centerRect.bottom),
                        startAngleDegrees = 90f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    lineTo(centerRect.left, centerRect.bottom - cornerLengthPx)
                }

                drawPath(
                    path = cornerPath,
                    color = Color.White.copy(alpha = 0.8f),
                    style = Stroke(
                        width = 3.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onCancel,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f)
            ) {
                Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = AppIcons.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Surface(
                onClick = { flashOn = !flashOn },
                shape = CircleShape,
                color = if (flashOn) Color(0xFFFFD54F).copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.45f)
            ) {
                Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = AppIcons.Flash,
                        contentDescription = "Flash",
                        tint = if (flashOn) Color(0xFFFFD54F) else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val centerSize = minOf(maxWidth, maxHeight) * 0.72f
            val centerTop = (maxHeight - centerSize) / 2
            Text(
                text = context.getString(R.string.scan_qr_code),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = centerTop - 48.dp),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (focusedQr != null) {
                Card(
                    onClick = {
                        selectedQr = DetectedQr(rawValue = focusedQr.rawValue, displayText = focusedQr.displayText)
                        showBottomSheet = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            AppIcons.QrCode,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = focusedQr.displayText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            AppIcons.CaretRight,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Gallery Picker Button - aligned to left corner and scaled to 1.1x (57dp)
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                Surface(
                    onClick = { openGalleryForQr() },
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.5f),
                    border = BorderStroke(
                        width = if (latestThumbnail != null) 2.dp else 1.dp,
                        color = if (latestThumbnail != null) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.size(57.dp)
                ) {
                    if (latestThumbnail != null) {
                        Image(
                            bitmap = latestThumbnail!!.asImageBitmap(),
                            contentDescription = "Gallery",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = AppIcons.Image,
                                contentDescription = "Gallery",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val QR_DISPLAY_MAX_LEN = 42

@OptIn(ExperimentalGetImage::class)
private class QrAnalyzer(
    private val onQrAnalysisResult: (QrAnalysisResult) -> Unit
) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient(
        com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }
        val rotation = imageProxy.imageInfo.rotationDegrees
        val imageWidth = imageProxy.width
        val imageHeight = imageProxy.height
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val list = barcodes.mapNotNull { barcode ->
                    val raw = barcode.rawValue?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val box: Rect? = barcode.boundingBox
                    if (box == null) return@mapNotNull null
                    val display = if (raw.length <= QR_DISPLAY_MAX_LEN) raw
                    else raw.take(QR_DISPLAY_MAX_LEN - 3) + "..."
                    DetectedQrWithBox(
                        rawValue = raw,
                        displayText = display,
                        left = box.left,
                        top = box.top,
                        right = box.right,
                        bottom = box.bottom
                    )
                }.distinctBy { it.rawValue }
                if (list.isNotEmpty()) {
                    onQrAnalysisResult(
                        QrAnalysisResult(
                            detected = list,
                            imageWidth = imageWidth,
                            imageHeight = imageHeight,
                            rotationDegrees = rotation
                        )
                    )
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}

fun processImage(context: android.content.Context, scanType: String, onResult: (String) -> Unit) {
    val photoFile = context.cacheDir.listFiles()
        ?.filter { it.name.startsWith("photo_") && it.name.endsWith(".jpg") }
        ?.maxByOrNull { it.lastModified() }
    photoFile?.let { file ->
        try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                ?: run {
                    Log.e("CameraScan", "processImage: decodeFile returned null for ${file.absolutePath}")
                    onResult("")
                    return
                }
            val rotation = getRotationFromFile(file)
            val image = InputImage.fromBitmap(bitmap, rotation)
            when (scanType) {
                "qr" -> scanBarcode(image, onResult)
                else -> scanText(image, onResult)
            }
        } catch (e: Exception) {
            Log.e("CameraScan", "processImage error", e)
            onResult("")
        }
    } ?: run {
        Log.e("CameraScan", "processImage: no photo_*.jpg file found in cache")
        onResult("")
    }
}

fun processImageFromUri(context: android.content.Context, uri: Uri, scanType: String, onResult: (String) -> Unit) {
    try {
        val rotation = getRotationFromUri(context, uri)
        // Luôn load qua InputStream + Bitmap để tránh lỗi với content URI (FileProvider/gallery)
        val image = context.contentResolver.openInputStream(uri)?.use { stream ->
            val opts = BitmapFactory.Options().apply {
                inSampleSize = 1
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeStream(stream, null, opts) ?: run {
                Log.e("CameraScan", "processImageFromUri: decodeStream returned null for $uri")
                onResult("")
                return
            }
            InputImage.fromBitmap(bitmap, rotation)
        }
        if (image == null) {
            onResult("")
            return
        }
        when (scanType) {
            "qr" -> scanBarcode(image, onResult)
            else -> scanText(image, onResult)
        }
    } catch (e: Exception) {
        Log.e("CameraScan", "processImageFromUri error", e)
        onResult("")
    }
}

fun scanText(image: InputImage, onResult: (String) -> Unit) {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    recognizer.process(image)
        .addOnSuccessListener { visionText ->
            val extractedText = OcrLayoutFormatter.format(visionText)
            Log.d("CameraScan", "scanText success: length=${extractedText.length}")
            onResult(extractedText)
        }
        .addOnFailureListener { e ->
            Log.e("CameraScan", "scanText failure", e)
            onResult("")
        }
}

fun scanBarcode(image: InputImage, onResult: (String) -> Unit) {
    val options = com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()
    val scanner = BarcodeScanning.getClient(options)
    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            val result = barcodes.firstOrNull()?.rawValue ?: ""
            Log.d("CameraScan", "scanBarcode success: ${result.take(50)}...")
            onResult(result)
        }
        .addOnFailureListener { e ->
            Log.e("CameraScan", "scanBarcode failure", e)
            onResult("")
        }
}
