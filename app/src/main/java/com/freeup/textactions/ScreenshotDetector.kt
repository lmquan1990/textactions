package com.freeup.textactions

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log

data class ScreenshotInfo(
    val uri: Uri,
    val displayName: String,
    val timestampMillis: Long
)

object ScreenshotDetector {
    private const val TAG = "ScreenshotDetector"

    /**
     * Checks if a screenshot was taken recently (default within 30 minutes).
     * Returns the newest screenshot info or null if none found or permissions not granted.
     */
    fun getLatestScreenshot(context: Context, maxAgeMinutes: Long = 30): ScreenshotInfo? {
        return try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED
            )
            val minDateSeconds = (System.currentTimeMillis() / 1000) - (maxAgeMinutes * 60)
            val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ?"
            val selectionArgs = arrayOf(minDateSeconds.toString())
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            val queryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            context.contentResolver.query(
                queryUri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: ""
                    val dateAdded = cursor.getLong(dateCol)

                    val lower = name.lowercase()
                    if (lower.contains("screenshot") || lower.contains("screen_shot") || lower.contains("chup_man_hinh")) {
                        val contentUri = ContentUris.withAppendedId(queryUri, id)
                        val isReadable = try {
                            context.contentResolver.openInputStream(contentUri)?.use { true } ?: false
                        } catch (_: Exception) {
                            false
                        }
                        if (isReadable) {
                            return ScreenshotInfo(
                                uri = contentUri,
                                displayName = name,
                                timestampMillis = dateAdded * 1000
                            )
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.d(TAG, "Cannot query screenshots (permission or MediaStore unavailable): ${e.message}")
            null
        }
    }
}
