package com.lhacenmed.sona.feature.playback

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import androidx.core.graphics.PathParser
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.session.CacheBitmapLoader
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.lhacenmed.sona.core.common.cover.DefaultCover
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
 * given none, which the session still limits in size.
 */
@UnstableApi
internal class DefaultCoverBitmapLoader(
    private val context: Context,
    private val imageSettings: ImageSettings,
) : BitmapLoader {

    private val artworkLoader =
        CacheBitmapLoader(DataSourceBitmapLoader.Builder(context).setMakeShared(true).build())

    private val glyph = PathParser.createPathFromPathData(DefaultCover.GLYPH_PATH_DATA)
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
        return drawDefaultCover(isNight).also {
            defaultCover = it
            defaultCoverIsNight = isNight
        }
    }

    private fun drawDefaultCover(isNight: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(DEFAULT_COVER_SIZE_PX, DEFAULT_COVER_SIZE_PX, Bitmap.Config.ARGB_8888)
        val glyphSizePx = DEFAULT_COVER_SIZE_PX * DefaultCover.GLYPH_SIZE_FRACTION
        val glyphInsetPx = (DEFAULT_COVER_SIZE_PX - glyphSizePx) / 2f
        val glyphScale = glyphSizePx / DefaultCover.GLYPH_VIEWPORT_SIZE
        Canvas(bitmap).apply {
            drawColor(groundColor(isNight))
            translate(glyphInsetPx, glyphInsetPx)
            scale(glyphScale, glyphScale)
            drawPath(glyph, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = glyphColor(isNight) })
        }
        return bitmap
    }

    /**
     * The app's `surfaceContainer`. On Android 12 and later the app's default theme is the system's own
     * colours, so they are taken from the system - exactly on Android 14, by the nearest tone before it.
     * Older systems have none to offer, so a neutral grey of the same tone stands in.
     */
    private fun groundColor(isNight: Boolean): Int = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> context.getColor(
            if (isNight) android.R.color.system_surface_container_dark else android.R.color.system_surface_container_light,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> context.getColor(
            if (isNight) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_50,
        )
        else -> if (isNight) 0xFF201F1F.toInt() else 0xFFEFEDEC.toInt()
    }

    /** The app's `onSurface`, taken the same way as [groundColor]. */
    private fun glyphColor(isNight: Boolean): Int = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> context.getColor(
            if (isNight) android.R.color.system_on_surface_dark else android.R.color.system_on_surface_light,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> context.getColor(
            if (isNight) android.R.color.system_neutral1_100 else android.R.color.system_neutral1_900,
        )
        else -> if (isNight) 0xFFE6E1E0.toInt() else 0xFF1C1B1B.toInt()
    }
}
