package com.lhacenmed.sona.core.common.cover

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import android.webkit.MimeTypeMap
import java.io.File

/**
 * Whether [this] is a track's or a video's own address - whose cover is the file's own: MediaStore's entry for
 * it, or the file itself. Told by the address alone, so asking costs nothing for any other cover.
 */
val Uri.isMediaFile: Boolean
    get() = when (scheme) {
        ContentResolver.SCHEME_CONTENT -> authority == MediaStore.AUTHORITY &&
            ("video" in pathSegments || "audio" in pathSegments && "media" in pathSegments)
        ContentResolver.SCHEME_FILE -> mimeType?.let { it.startsWith("video/") || it.startsWith("audio/") } == true
        else -> false
    }

/** Whether [this] is a video's own address - see [isMediaFile]. */
val Uri.isVideo: Boolean
    get() = when (scheme) {
        ContentResolver.SCHEME_CONTENT -> authority == MediaStore.AUTHORITY && "video" in pathSegments
        ContentResolver.SCHEME_FILE -> mimeType?.startsWith("video/") == true
        else -> false
    }

private val Uri.mimeType: String?
    get() = MimeTypeMap.getSingleton().getMimeTypeFromExtension(path?.substringAfterLast('.', "")?.lowercase())

/**
 * The cover of the track or video at [uri], no larger than [sizePx]. A track's is Android's own thumbnail of its file - its
 * embedded picture, or failing that its folder's - which Android keeps once made, so showing it again costs a read rather
 * than a decode; a file MediaStore has not indexed has one made from it, and before Android 10 a track has none.
 *
 * A video's is always made here, by [videoPictureOf]: Android's kept thumbnail of a video is whichever frame it took when
 * the thumbnail was first made - on many devices its first, so often black - and it never takes another.
 *
 * Blocks, and is called off the main thread.
 */
fun mediaThumbnailOf(context: Context, uri: Uri, sizePx: Int): Bitmap? = runCatching {
    val size = Size(sizePx, sizePx)
    when {
        uri.isVideo -> videoPictureOf(context, uri, sizePx)
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> null
        uri.scheme == ContentResolver.SCHEME_FILE -> ThumbnailUtils.createAudioThumbnail(File(checkNotNull(uri.path)), size, null)
        else -> context.contentResolver.loadThumbnail(uri, size, null)
    }
}.getOrNull()

/** Where in a video, as parts of its length, a frame is looked for to stand for it - in turn, until one is not dark. */
private val VideoCoverPositions = listOf(0.5, 0.25, 0.75, 0.1, 0.9)

/** How bright, of 255, a frame has to be on average not to be taken for a dark one - a fade, a black screen. */
private const val DARK_FRAME_LUMA = 40

/** How many points across and down a frame is sampled at to tell how bright it is. */
private const val BRIGHTNESS_SAMPLES = 12

/**
 * The picture that stands for the video at [uri], no larger than [sizePx]: the picture embedded in it, or failing that the
 * first frame at [VideoCoverPositions] that is not dark - the brightest of them where all are.
 */
private fun videoPictureOf(context: Context, uri: Uri, sizePx: Int): Bitmap? {
    val retriever = MediaMetadataRetriever()
    try {
        if (uri.scheme == ContentResolver.SCHEME_FILE) retriever.setDataSource(checkNotNull(uri.path)) else retriever.setDataSource(context, uri)
        retriever.embeddedPicture?.let { picture ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(picture, 0, picture.size, bounds)
            val sampled = BitmapFactory.Options().apply { inSampleSize = (minOf(bounds.outWidth, bounds.outHeight) / sizePx).coerceAtLeast(1) }
            return BitmapFactory.decodeByteArray(picture, 0, picture.size, sampled)
        }
        val durationUs = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000
        var brightest: Pair<Bitmap, Int>? = null
        for (position in VideoCoverPositions) {
            val frame = retriever.frameAt((durationUs * position).toLong(), sizePx) ?: continue
            val luma = frame.averageLuma()
            if (luma >= DARK_FRAME_LUMA) return frame
            if (brightest == null || luma > brightest.second) brightest = frame to luma
        }
        return brightest?.first
    } finally {
        retriever.release()
    }
}

private fun MediaMetadataRetriever.frameAt(timeUs: Long, sizePx: Int): Bitmap? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, sizePx, sizePx)
    } else {
        getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    }

/** How bright [this] is on average, of 255, from a grid of points across it. */
private fun Bitmap.averageLuma(): Int {
    var total = 0
    for (row in 0 until BRIGHTNESS_SAMPLES) {
        for (column in 0 until BRIGHTNESS_SAMPLES) {
            val pixel = getPixel(width * (2 * column + 1) / (2 * BRIGHTNESS_SAMPLES), height * (2 * row + 1) / (2 * BRIGHTNESS_SAMPLES))
            total += (299 * (pixel shr 16 and 0xFF) + 587 * (pixel shr 8 and 0xFF) + 114 * (pixel and 0xFF)) / 1000
        }
    }
    return total / (BRIGHTNESS_SAMPLES * BRIGHTNESS_SAMPLES)
}

/**
 * [this] address stamped with [version] - the file's latest change. A cover written into a file in place is
 * then a new address, which every list, the player and the notification load afresh, while every cover left
 * as it was keeps its cache. The stamp is the uri's fragment, which neither a content provider nor a file
 * path reads.
 */
fun Uri.withCoverVersion(version: Long?): String = buildUpon().fragment(version?.toString()).build().toString()
