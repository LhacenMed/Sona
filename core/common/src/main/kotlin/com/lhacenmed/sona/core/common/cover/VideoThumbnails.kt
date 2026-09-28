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
 * Whether [this] is a video's own address - which is a video's cover: MediaStore's entry in its video
 * collection, or a video file. Told by the address alone, so asking costs nothing for any other cover.
 */
val Uri.isVideo: Boolean
    get() = when (scheme) {
        ContentResolver.SCHEME_CONTENT -> authority == MediaStore.AUTHORITY && "video" in pathSegments
        ContentResolver.SCHEME_FILE -> MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(path?.substringAfterLast('.', "")?.lowercase())
            ?.startsWith("video/") == true
        else -> false
    }

/**
 * The thumbnail of the video at [uri], no larger than [sizePx] - Android's own, which it keeps once made, so
 * showing it again costs a read rather than a decode. A file MediaStore has not indexed has none kept, and
 * has a frame taken from it instead. Null where the video has no picture to give.
 *
 * Blocks, and is called off the main thread.
 */
fun videoThumbnailOf(context: Context, uri: Uri, sizePx: Int): Bitmap? = runCatching {
    val size = Size(sizePx, sizePx)
    when {
        uri.scheme == ContentResolver.SCHEME_FILE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
            ThumbnailUtils.createVideoThumbnail(File(checkNotNull(uri.path)), size, null)
        uri.scheme == ContentResolver.SCHEME_FILE ->
            @Suppress("DEPRECATION")
            ThumbnailUtils.createVideoThumbnail(checkNotNull(uri.path), MediaStore.Video.Thumbnails.MINI_KIND)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> context.contentResolver.loadThumbnail(uri, size, null)
        else ->
            @Suppress("DEPRECATION")
            MediaStore.Video.Thumbnails.getThumbnail(
                context.contentResolver,
                ContentUris.parseId(uri),
                MediaStore.Video.Thumbnails.MINI_KIND,
                null,
            )
    }
}.getOrNull()
