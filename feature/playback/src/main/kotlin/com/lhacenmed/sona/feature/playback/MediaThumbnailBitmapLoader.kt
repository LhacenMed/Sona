package com.lhacenmed.sona.feature.playback

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.lhacenmed.sona.core.common.cover.isMediaFile
import com.lhacenmed.sona.core.common.cover.mediaThumbnailOf
import java.util.concurrent.Executors

/** The side a file's thumbnail is asked for at - what the session scales artwork down to anyway. */
private const val THUMBNAIL_SIZE_PX = 512

/**
 * Artwork as [images] loads it - except a track's or a video's own, which is Android's thumbnail of its file,
 * as the app's own covers show it. Read as a picture, the file would be read whole into memory before failing
 * to decode.
 */
@UnstableApi
internal class MediaThumbnailBitmapLoader(
    private val context: Context,
    private val images: BitmapLoader,
) : BitmapLoader {

    private val executor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun supportsMimeType(mimeType: String): Boolean = images.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = images.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        if (uri.isMediaFile) {
            executor.submit<Bitmap> { checkNotNull(mediaThumbnailOf(context, uri, THUMBNAIL_SIZE_PX)) { "$uri has no thumbnail" } }
        } else {
            images.loadBitmap(uri)
        }
}
