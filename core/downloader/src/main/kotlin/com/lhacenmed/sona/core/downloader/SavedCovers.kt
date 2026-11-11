package com.lhacenmed.sona.core.downloader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import com.kyant.taglib.TagLib
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request

/** How much of the JPEG's detail a saved cover keeps. */
private const val CoverJpegQuality = 92

/**
 * A download's cover kept as a picture of its own, beside its file - what Save cover art adds to the cover every song has in
 * its file: the same picture, for the gallery and for players that read a folder's picture rather than a file's.
 */
object SavedCovers {
    private val client = OkHttpClient()

    /**
     * Saves [file]'s cover beside it as a JPEG named as it is: the one in it - its song's, or its video's picture - else, for a
     * file with none - a video - the picture at [pictureUrl]. The picture saved; null where there was none to save, or it
     * could not be written.
     */
    fun saveBeside(file: File, pictureUrl: String?): File? = runCatching {
        val bytes = embeddedCover(file) ?: pictureUrl?.let(::fetch) ?: return null
        val picture = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        File(file.parentFile, "${file.nameWithoutExtension}.jpg").apply {
            outputStream().use { picture.compress(Bitmap.CompressFormat.JPEG, CoverJpegQuality, it) }
        }
    }.getOrNull()

    private fun embeddedCover(file: File): ByteArray? = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        // TagLib takes the descriptor it is handed as its own and closes it: it is given a duplicate.
        TagLib.getPictures(descriptor.dup().detachFd()).firstOrNull()?.data
    }

    private fun fetch(url: String): ByteArray? = client.newCall(Request.Builder().url(url).build()).execute().use { response ->
        response.body?.bytes()?.takeIf { response.isSuccessful }
    }
}
