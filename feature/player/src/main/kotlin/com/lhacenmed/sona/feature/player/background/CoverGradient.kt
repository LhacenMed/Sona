package com.lhacenmed.sona.feature.player.background

import android.content.Context
import android.util.LruCache
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.lhacenmed.sona.feature.player.PlayerColorExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.Color as AndroidColor

/** How many covers' colours are kept: a queue's worth of skipping back and forth. */
private const val CachedCoverCount = 24

/**
 * The colours the player, the mini player and the lyrics sheet are drawn in from a cover - ArchiveTune's
 * gradient colours, which it extracts separately in each of the three. Here a cover's are extracted once
 * and shared, so opening the player or the lyrics over the mini player finds them already there.
 */
private object CoverGradients {
    private val cache = LruCache<String, List<Color>>(CachedCoverCount)

    fun cached(coverArtUri: String): List<Color>? = cache.get(coverArtUri)

    /** Empty when the cover cannot be read - and then not kept, so it is tried again next time. */
    suspend fun load(context: Context, coverArtUri: String): List<Color> {
        cache.get(coverArtUri)?.let { return it }
        val request = ImageRequest.Builder(context)
            .data(coverArtUri)
            .size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE)
            .allowHardware(false) // Palette needs to read raw pixels off a software bitmap.
            .build()
        val colors = try {
            val bitmap = withContext(Dispatchers.IO) { context.imageLoader.execute(request) }.image?.toBitmap()
                ?: return emptyList()
            val palette = withContext(Dispatchers.Default) {
                Palette.from(bitmap)
                    .maximumColorCount(PlayerColorExtractor.Config.MAX_COLOR_COUNT)
                    .resizeBitmapArea(PlayerColorExtractor.Config.BITMAP_AREA)
                    .generate()
            }
            PlayerColorExtractor.extractGradientColors(palette = palette, fallbackColor = Color.Black.toArgb())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return emptyList()
        }
        cache.put(coverArtUri, colors)
        return colors
    }
}

/**
 * The colours drawn from [coverArtUri], toned for the app's theme - see [tonedFor] - and empty while there are
 * none: no cover, one that cannot be read, or while not [enabled], when nothing is loaded at all. A new cover's
 * colours replace the last ones once they are ready, so what they colour moves from one cover to the next rather
 * than through nothing; a change of theme tones them anew.
 */
@Composable
internal fun rememberCoverGradientColors(coverArtUri: String?, enabled: Boolean): List<Color> {
    val context = LocalContext.current
    val colors by produceState(
        initialValue = coverArtUri?.takeIf { enabled }?.let(CoverGradients::cached).orEmpty(),
        coverArtUri,
        enabled,
    ) {
        value = if (enabled && coverArtUri != null) CoverGradients.load(context.applicationContext, coverArtUri) else emptyList()
    }
    // Whether the theme is dark rather than its colours, which move through every frame of a change of theme.
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return remember(colors, isDarkTheme) { colors.map { it.tonedFor(isDarkTheme) } }
}

/**
 * This colour at a tone the theme's own text reads over - ArchiveTune's comfortable colours, for either theme:
 * deep and vivid under a dark one, pale under a light one.
 */
private fun Color.tonedFor(isDarkTheme: Boolean): Color {
    val hsv = FloatArray(3).also { AndroidColor.colorToHSV(toArgb(), it) }
    if (isDarkTheme) {
        hsv[1] = hsv[1].coerceAtLeast(0.32f)
        hsv[2] = hsv[2].coerceIn(0.15f, 0.58f)
    } else {
        hsv[1] = hsv[1].coerceAtMost(0.45f)
        hsv[2] = hsv[2].coerceIn(0.85f, 0.97f)
    }
    return Color(AndroidColor.HSVToColor(hsv))
}
