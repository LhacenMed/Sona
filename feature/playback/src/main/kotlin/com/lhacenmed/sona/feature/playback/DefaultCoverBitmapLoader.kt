package com.lhacenmed.sona.feature.playback

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.session.CacheBitmapLoader
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.lhacenmed.sona.core.common.cover.DefaultCover
import com.lhacenmed.sona.core.common.cover.drawDefaultCover
import com.lhacenmed.sona.core.datastore.CoverMode
import com.lhacenmed.sona.core.datastore.ImageSettings

/** The size the default cover is drawn at; the session scales artwork down to what it needs from there. */
private const val DEFAULT_COVER_SIZE_PX = 512

/**
 * The artwork the session hands the notification, the lock screen and connected devices: the playing
 * track's cover, or [DefaultCover] wherever the app itself would show it - covers turned off, a track
 * without artwork, or artwork that fails to load.
 *
 * Artwork loads exactly as it would without this: through the loader a session builds for itself when
 * given none, which the session still limits in size - a track's or a video's own being Android's thumbnail
 * of its file ([MediaThumbnailBitmapLoader]).
 */
@UnstableApi
internal class DefaultCoverBitmapLoader(
    private val context: Context,
    private val imageSettings: ImageSettings,
) : BitmapLoader {

    private val artworkLoader = CacheBitmapLoader(
        MediaThumbnailBitmapLoader(context, DataSourceBitmapLoader.Builder(context).setMakeShared(true).build()),
    )

    private var defaultCover: Bitmap? = null
    private var defaultCoverIsNight = false

    override fun supportsMimeType(mimeType: String): Boolean = artworkLoader.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        orDefaultCover { artworkLoader.decodeBitmap(data) }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        orDefaultCover { artworkLoader.loadBitmap(uri) }

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap> =
        orDefaultCover { artworkLoader.loadBitmapFromMetadata(metadata) }

    /** The artwork [load] produces, or the default cover when there is none to show. */
    private fun orDefaultCover(load: () -> ListenableFuture<Bitmap>?): ListenableFuture<Bitmap> {
        if (imageSettings.coverMode.value == CoverMode.OFF) return Futures.immediateFuture(defaultCover())
        val artwork = load() ?: return Futures.immediateFuture(defaultCover())
        return Futures.catching(artwork, Exception::class.java, { defaultCover() }, MoreExecutors.directExecutor())
    }

    /** Drawn once for each of light and dark, since the ground and glyph follow the system's mode. */
    @Synchronized
    private fun defaultCover(): Bitmap {
        val isNight = context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        defaultCover?.takeIf { defaultCoverIsNight == isNight }?.let { return it }
        return drawDefaultCover(context, isNight, DEFAULT_COVER_SIZE_PX).also {
            defaultCover = it
            defaultCoverIsNight = isNight
        }
    }
}
