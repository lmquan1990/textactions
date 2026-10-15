package com.freeup.textactions

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.freeup.textactions.ui.theme.AppIcons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freeup.textactions.ui.theme.TextActionsTheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class CreateQRActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val content = intent.getStringExtra("qr_content") ?: ""
        val qrType = intent.getStringExtra("qr_type") ?: "text"
        
        setContent {
            val settingsManager = remember { SettingsManager(this) }
            val dynamicColorEnabled = settingsManager.isDynamicColorEnabled()
            val themeMode = settingsManager.getThemeMode()
            val darkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            TextActionsTheme(darkTheme = darkTheme, dynamicColor = dynamicColorEnabled) {
                CreateQRScreen(
                    content = content,
                    qrType = qrType,
                    onClose = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateQRScreen(
    content: String,
    qrType: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Generate QR code on launch
    LaunchedEffect(content) {
        scope.launch {
            qrBitmap = withContext(Dispatchers.Default) {
                generateQRCode(content, qrType)
            }
            isLoading = false
        }
    }
    
    val qrTypeLabel = when (qrType) {
        "url" -> "QR for Website"
        "phone" -> "QR for Phone Number"
        "email" -> "QR for Email"
        "address" -> "QR for Address"
        else -> "QR Code"
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(qrTypeLabel) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(AppIcons.Close, "Close")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.weight(0.5f))
            
            // QR Code Display
            AnimatedVisibility(
                visible = !isLoading && qrBitmap != null,
                enter = fadeIn(animationSpec = tween(300)) + scaleIn(
                    initialScale = 0.8f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            ) {
                Card(
                    modifier = Modifier
                        .size(280.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(androidx.compose.ui.graphics.Color.White)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        qrBitmap?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "QR Code",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
            
            // Loading indicator
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp)
                )
            }
            
            // Content preview
            if (!isLoading) {
                Text(
                    text = if (content.length > 60) 
                        content.take(60) + "..." 
                    else content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Action buttons
            if (!isLoading && qrBitmap != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                qrBitmap?.let { bitmap ->
                                    saveQRToGallery(context, bitmap, content)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(AppIcons.Save, null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save QR", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                qrBitmap?.let { bitmap ->
                                    shareQR(context, bitmap)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(AppIcons.Share, null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share QR", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) 
                                as android.content.ClipboardManager
                            clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText("QR Content", content)
                            )
                            Toast.makeText(context, "Content copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(AppIcons.Copy, null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy Content", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun generateQRCode(content: String, qrType: String): Bitmap {
    // Convert content to appropriate QR format
    val qrContent = when (qrType) {
        "url" -> {
            if (!content.startsWith("http://") && !content.startsWith("https://")) {
                "https://$content"
            } else content
        }
        "phone" -> {
            val normalized = content.replace(Regex("[^0-9+]"), "")
            "tel:$normalized"
        }
        "email" -> "mailto:$content"
        "address" -> {
            // Create Google Maps link for address
            "https://www.google.com/maps/search/?api=1&query=${android.net.Uri.encode(content)}"
        }
        else -> content
    }
    
    val hints = hashMapOf<EncodeHintType, Any>()
    hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.H
    hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
    hints[EncodeHintType.MARGIN] = 1
    
    val writer = QRCodeWriter()
    val bitMatrix = writer.encode(qrContent, BarcodeFormat.QR_CODE, 512, 512, hints)
    
    val width = bitMatrix.width
    val height = bitMatrix.height
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    
    for (x in 0 until width) {
        for (y in 0 until height) {
            bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
        }
    }
    
    return bitmap
}

@Suppress("UNUSED_PARAMETER")
private suspend fun saveQRToGallery(
    context: android.content.Context,
    bitmap: Bitmap,
    _content: String
): Boolean = withContext(Dispatchers.IO) {
    try {
        val filename = "QR_${System.currentTimeMillis()}.png"
        val fos: OutputStream?
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SmartActions")
            }
            val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            fos = imageUri?.let { resolver.openOutputStream(it) }
        } else {
            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val smartActionsDir = File(imagesDir, "SmartActions")
            if (!smartActionsDir.exists()) {
                smartActionsDir.mkdirs()
            }
            val image = File(smartActionsDir, filename)
            fos = FileOutputStream(image)
        }
        
        fos?.use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "QR saved to gallery", Toast.LENGTH_SHORT).show()
        }
        true
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Failed to save QR: ${e.message}", Toast.LENGTH_SHORT).show()
        }
        false
    }
}

private suspend fun shareQR(
    context: android.content.Context,
    bitmap: Bitmap
) = withContext(Dispatchers.IO) {
    try {
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = File(cachePath, "qr_code.png")
        val fos = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        fos.close()
        
        val contentUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        withContext(Dispatchers.Main) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share QR Code"))
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
