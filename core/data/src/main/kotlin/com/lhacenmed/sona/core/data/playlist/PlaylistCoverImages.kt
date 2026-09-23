package com.lhacenmed.sona.core.data.playlist

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.lhacenmed.sona.core.common.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** The longest side a playlist cover is kept at - larger than the largest cover drawn, a detail header's. */
private const val MAX_SIDE_PX = 1024

private const val JPEG_QUALITY = 90

private const val DIRECTORY_NAME = "playlist_covers"

/**
 * The images playlists are given as covers - the app's own copies, so a cover never depends on a grant
 * to read someone else's file outliving the picker that gave it, nor on that file staying where it was.
 *
 * Each is decoded no larger than [MAX_SIDE_PX] a side and saved under a name of its own: a new cover is
 * a new address, so no image cache can go on showing the one it replaced.
 */
@Singleton
class PlaylistCoverImages @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val directory = File(context.filesDir, DIRECTORY_NAME)

    /** Whether [uri] is one of these copies, rather than an image still to be copied. */
    fun isOwned(uri: String): Boolean = ownedFile(uri) != null

    /** Copies the image at [sourceUri] in, and returns the copy's address. */
    suspend fun import(sourceUri: String): String = withContext(ioDispatcher) {
        val bitmap = decode(Uri.parse(sourceUri)) ?: error("Could not read $sourceUri")
        try {
            // Transparency would turn black as a JPEG, so only an image that has none is compressed as one.
            val format = if (bitmap.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            directory.mkdirs()
            val file = File(directory, UUID.randomUUID().toString())
            val isWritten = file.outputStream().use { bitmap.compress(format, JPEG_QUALITY, it) }
            if (!isWritten) {
                file.delete()
                error("Could not write $file")
            }
            Uri.fromFile(file).toString()
        } finally {
            bitmap.recycle()
        }
    }

    /** Deletes [uri]'s copy. An address that is not one of these copies is left alone. */
    suspend fun delete(uri: String) {
        val file = ownedFile(uri) ?: return
        withContext(ioDispatcher) { file.delete() }
    }

    private fun ownedFile(uri: String): File? {
        val parsed = Uri.parse(uri)
        if (parsed.scheme != "file") return null
        return parsed.path?.let(::File)?.takeIf { it.parentFile == directory }
    }

    /**
     * [uri]'s image, sampled down towards [MAX_SIDE_PX]. Upright on Android 9 and later, where the
     * platform decoder applies a photo's orientation; before that, as its pixels are stored.
     */
    private fun decode(uri: Uri): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val scale = MAX_SIDE_PX.toFloat() / maxOf(info.size.width, info.size.height)
                if (scale < 1f) {
                    decoder.setTargetSize(
                        (info.size.width * scale).roundToInt().coerceAtLeast(1),
                        (info.size.height * scale).roundToInt().coerceAtLeast(1),
                    )
                }
                // Compressed again once decoded, which reads the pixels back - a hardware bitmap cannot be.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_SIDE_PX) sampleSize *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
}
