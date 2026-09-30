package com.lhacenmed.sona.core.common.cover

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
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
 * The cover of the track or video at [uri], no larger than [sizePx]: Android's own thumbnail of the file - a
 * track's embedded picture, or failing that its folder's; a video's embedded picture, or failing that a frame
 * of it - which it keeps once made, so showing it again costs a read rather than a decode. A file MediaStore
 * has not indexed has none kept, and has one made from it. Null where the file has no picture to give, and
 * for a track before Android 10, which has no thumbnail of its own.
 *
 * Blocks, and is called off the main thread.
 */
fun mediaThumbnailOf(context: Context, uri: Uri, sizePx: Int): Bitmap? = runCatching {
    val size = Size(sizePx, sizePx)
    val isVideo = uri.isVideo
    when {
        uri.scheme == ContentResolver.SCHEME_FILE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
            val file = File(checkNotNull(uri.path))
            if (isVideo) ThumbnailUtils.createVideoThumbnail(file, size, null) else ThumbnailUtils.createAudioThumbnail(file, size, null)
        }
        uri.scheme == ContentResolver.SCHEME_FILE ->
            @Suppress("DEPRECATION")
            if (isVideo) ThumbnailUtils.createVideoThumbnail(checkNotNull(uri.path), MediaStore.Video.Thumbnails.MINI_KIND) else null
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> context.contentResolver.loadThumbnail(uri, size, null)
        isVideo ->
            @Suppress("DEPRECATION")
            MediaStore.Video.Thumbnails.getThumbnail(
                context.contentResolver,
                ContentUris.parseId(uri),
                MediaStore.Video.Thumbnails.MINI_KIND,
                null,
            )
        else -> null
    }
}.getOrNull()

/**
 * [this] address stamped with [version] - the file's latest change. A cover written into a file in place is
 * then a new address, which every list, the player and the notification load afresh, while every cover left
 * as it was keeps its cache. The stamp is the uri's fragment, which neither a content provider nor a file
 * path reads.
 */
fun Uri.withCoverVersion(version: Long?): String = buildUpon().fragment(version?.toString()).build().toString()
