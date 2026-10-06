package com.lhacenmed.sona.feature.video.capture

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.SurfaceView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** The folder under Pictures every screenshot of a video is saved in. */
private const val ScreenshotFolder = "Sona"

private const val JpegQuality = 95

/** The longest a video's name runs in a screenshot's file name. */
private const val MaxNameLength = 60

/** What a file name cannot hold, on any storage the device may save to. */
private val UnsafeNameCharacters = Regex("""[\\/:*?"<>|\x00-\x1F]""")

/**
 * The frame on screen now - playing or paused - at the video's own [width] by [height], not the screen's: the
 * decoded picture itself, with nothing drawn over it.
 *
 * Copied straight off [surface], which is instant; where the device cannot copy from it, the frame at
 * [positionMs] is decoded from the video's file at [uri] instead. Null only if neither gives a frame.
 */
internal suspend fun Context.captureVideoFrame(
    surface: SurfaceView?,
    width: Int,
    height: Int,
    uri: Uri,
    positionMs: Long,
): Bitmap? = surface?.copyFrame(width, height) ?: decodeFrame(uri, positionMs)

private suspend fun SurfaceView.copyFrame(width: Int, height: Int): Bitmap? {
    if (width <= 0 || height <= 0 || !holder.surface.isValid) return null
    return suspendCancellableCoroutine { continuation ->
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(
            this,
            bitmap,
            { result -> continuation.resume(bitmap.takeIf { result == PixelCopy.SUCCESS }) },
            Handler(Looper.getMainLooper()),
        )
    }
}

/** The frame at [positionMs] exactly, decoded from the file at [uri]. */
private suspend fun Context.decodeFrame(uri: Uri, positionMs: Long): Bitmap? = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(this@decodeFrame, uri)
        retriever.getFrameAtTime(positionMs * 1_000, MediaMetadataRetriever.OPTION_CLOSEST)
    } catch (_: RuntimeException) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

/**
 * Saves [frame] among the device's pictures, in Pictures/Sona, named after [videoName] and the moment it was
 * taken, and returns whether it was saved. Before Android 10 that takes the storage permission, which the caller
 * has asked for.
 *
 * Written while marked pending, then published: the gallery never shows it half-written, and one that fails is
 * removed rather than left behind empty.
 */
internal suspend fun Context.saveScreenshot(frame: Bitmap, videoName: String): Boolean = withContext(Dispatchers.IO) {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val name = "${videoName.replace(UnsafeNameCharacters, "_").trim().take(MaxNameLength).ifEmpty { "Sona" }}_$stamp.jpg"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ScreenshotFolder")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        } else {
            val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ScreenshotFolder)
            folder.mkdirs()
            @Suppress("DEPRECATION")
            put(MediaStore.Images.Media.DATA, File(folder, name).path)
        }
    }
    val uri = runCatching { contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) }.getOrNull()
        ?: return@withContext false
    val isWritten = runCatching {
        checkNotNull(contentResolver.openOutputStream(uri)).use { check(frame.compress(Bitmap.CompressFormat.JPEG, JpegQuality, it)) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        }
    }.isSuccess
    if (!isWritten) runCatching { contentResolver.delete(uri, null, null) }
    isWritten
}
