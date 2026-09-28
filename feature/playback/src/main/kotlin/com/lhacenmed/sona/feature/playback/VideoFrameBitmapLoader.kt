package com.lhacenmed.sona.feature.playback

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Executors

/** The longest side a video's frame is taken at - what the session scales artwork down to anyway. */
private const val FRAME_MAX_SIDE_PX = 512

/**
 * Artwork as [images] loads it - except a video's, which is a frame of the video itself, as the app's own
 * covers show it. Read as a picture, a video would be read whole into memory before failing to decode.
 */
@UnstableApi
internal class VideoFrameBitmapLoader(
    private val context: Context,
    private val images: BitmapLoader,
) : BitmapLoader {

    private val executor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun supportsMimeType(mimeType: String): Boolean = images.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = images.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        Futures.submitAsync(
            { if (uri.isVideo()) Futures.immediateFuture(frameOf(uri)) else images.loadBitmap(uri) },
            executor,
        )

    private fun Uri.isVideo(): Boolean {
        val mimeType = context.contentResolver.getType(this)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(lastPathSegment?.substringAfterLast('.', "")?.lowercase())
        return mimeType?.startsWith("video/") == true
    }

    private fun frameOf(uri: Uri): Bitmap {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, FRAME_MAX_SIDE_PX, FRAME_MAX_SIDE_PX)
            } else {
                retriever.frameAtTime
            }
            return checkNotNull(frame) { "$uri has no frame to show" }
        } finally {
            retriever.release()
        }
    }
}
