package com.lhacenmed.sona.feature.player.background

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lhacenmed.sona.core.datastore.CustomBackground
import com.lhacenmed.sona.core.datastore.PlayerBackgroundStyle
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin
import android.graphics.Color as AndroidColor

/** How far the cover is blurred behind the player: ArchiveTune's default blur intensity. */
private val CoverBlurRadius = 48.dp

private const val CrossfadeMillis = 1000
private const val GlowCrossfadeMillis = 1200
private const val GlowCycleMillis = 20_000

/** The near-black the glows are drawn over. */
private val GlowBase = Color(0xFF050505)

/**
 * What the expanded player - and the lyrics sheet, for its coloring and custom styles - is drawn over:
 * ArchiveTune's `PlayerBackground`. [PlayerBackgroundStyle.DEFAULT] draws nothing, leaving the theme's
 * own surface the sheet is.
 *
 * [gradientColors] are the cover's - see [rememberCoverGradientColors]. Every style fades from one cover
 * to the next.
 */
@Composable
internal fun PlayerBackground(
    style: PlayerBackgroundStyle,
    coverArtUri: String?,
    gradientColors: List<Color>,
    customBackground: CustomBackground,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (style) {
            PlayerBackgroundStyle.DEFAULT -> Unit

            PlayerBackgroundStyle.BLUR ->
                BlurredCover(coverArtUri) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(colorStops = blurOverlayStops(gradientColors))),
                    )
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.08f)))
                }

            PlayerBackgroundStyle.BLUR_GRADIENT ->
                BlurredCover(coverArtUri) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(colorStops = blurGradientStops(gradientColors))),
                    )
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.05f)))
                }

            PlayerBackgroundStyle.GRADIENT -> GradientBackground(gradientColors)

            PlayerBackgroundStyle.COLORING -> ColoringBackground(gradientColors)

            PlayerBackgroundStyle.CUSTOM -> CustomImageBackground(customBackground)

            PlayerBackgroundStyle.GLOW -> GlowBackground(gradientColors)

            PlayerBackgroundStyle.GLOW_ANIMATED -> AnimatedGlowBackground(gradientColors)
        }
    }
}

/** The cover, blurred to fill the player, with [overlay] over it. */
@Composable
private fun BlurredCover(coverArtUri: String?, overlay: @Composable () -> Unit) {
    AnimatedContent(
        targetState = coverArtUri,
        transitionSpec = { fadeIn(tween(CrossfadeMillis)) togetherWith fadeOut(tween(CrossfadeMillis)) },
        label = "blurred-cover-background",
    ) { artworkUri ->
        if (artworkUri != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = artworkUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(CoverBlurRadius),
                )
                overlay()
            }
        }
    }
}

@Composable
private fun GradientBackground(gradientColors: List<Color>) {
    AnimatedContent(
        targetState = gradientColors,
        transitionSpec = { fadeIn(tween(CrossfadeMillis)) togetherWith fadeOut(tween(CrossfadeMillis)) },
        label = "gradient-background",
    ) { colors ->
        if (colors.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxSize()) {
                val colorStops =
                    if (colors.size >= 3) {
                        arrayOf(
                            0.0f to colors[0].copy(alpha = 0.92f),
                            0.5f to colors[1].copy(alpha = 0.75f),
                            1.0f to colors[2].copy(alpha = 0.65f),
                        )
                    } else {
                        arrayOf(
                            0.0f to colors[0].copy(alpha = 0.9f),
                            0.6f to colors[0].copy(alpha = 0.55f),
                            1.0f to Color.Black.copy(alpha = 0.7f),
                        )
                    }
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colorStops = colorStops)))
                // A gentle dark overlay keeps the text readable over bright artwork.
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))
            }
        }
    }
}

/** The cover's dominant colour, darkened down the player. */
@Composable
internal fun ColoringBackground(gradientColors: List<Color>) {
    AnimatedContent(
        targetState = gradientColors,
        transitionSpec = { fadeIn(tween(CrossfadeMillis)) togetherWith fadeOut(tween(CrossfadeMillis)) },
        label = "coloring-background",
    ) { colors ->
        if (colors.isNotEmpty()) {
            val baseColor = ensureComfortableColor(colors.first())
            Box(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize().background(baseColor))
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(colorStops = coloringStops(baseColor))),
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
            }
        }
    }
}

/** The user's own image, adjusted as they set it - see [CustomBackground]. */
@Composable
internal fun CustomImageBackground(customBackground: CustomBackground) {
    AnimatedContent(
        targetState = customBackground.imageUri,
        transitionSpec = { fadeIn(tween(CrossfadeMillis)) togetherWith fadeOut(tween(CrossfadeMillis)) },
        label = "custom-background",
    ) { imageUri ->
        if (imageUri != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                val colorFilter = remember(customBackground.contrast, customBackground.brightness) {
                    customBackgroundColorFilter(customBackground.contrast, customBackground.brightness)
                }
                AsyncImage(
                    model = Uri.parse(imageUri),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = colorFilter,
                    modifier = Modifier.fillMaxSize().blur(customBackground.blur.dp),
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = CustomBackgroundScrimAlpha)))
            }
        }
    }
}

/** The dimming over the custom image, so the player's white text stays readable on any picture. */
const val CustomBackgroundScrimAlpha = 0.4f

/** Six soft lights in the cover's colours, one in each corner and edge, over near-black. */
@Composable
private fun GlowBackground(gradientColors: List<Color>) {
    AnimatedContent(
        targetState = gradientColors,
        transitionSpec = { fadeIn(tween(GlowCrossfadeMillis)) togetherWith fadeOut(tween(GlowCrossfadeMillis)) },
        label = "glow-background",
    ) { colors ->
        if (colors.isNotEmpty()) {
            val color1 = colors.getOrElse(0) { Color.DarkGray }
            val color2 = colors.getOrElse(1) { color1 }
            val color3 = colors.getOrElse(2) { color2 }
            val color4 = colors.getOrElse(3) { color1 }
            val color5 = colors.getOrElse(4) { color2 }
            val color6 = colors.getOrElse(5) { color3 }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val width = size.width
                        val height = size.height
                        val brushes = listOf(
                            glow(color1, 0.8f, 0.5f, Offset(width * 0.2f, height * 0.25f), width * 1.2f),
                            glow(color2, 0.75f, 0.45f, Offset(width * 0.85f, height * 0.8f), width * 1.1f),
                            glow(color3, 0.7f, 0.4f, Offset(width * 0.9f, height * 0.15f), width * 1.0f),
                            glow(color4, 0.65f, 0.35f, Offset(width * 0.1f, height * 0.9f), width * 1.0f),
                            glow(color5, 0.6f, 0.3f, Offset(width * 0.5f, height * 0.1f), width * 0.9f),
                            glow(color6, 0.6f, 0.3f, Offset(width * 0.5f, height * 0.95f), width * 0.9f),
                        )
                        onDrawBehind {
                            drawRect(color = GlowBase)
                            brushes.forEach { drawRect(brush = it) }
                        }
                    },
            )
        }
    }
}

/**
 * [GlowBackground]'s lights, drifting and swapping colours over a twenty-second loop. Worked out where
 * they are drawn, so each frame of the loop redraws the lights and recomposes nothing.
 */
@Composable
private fun AnimatedGlowBackground(gradientColors: List<Color>) {
    AnimatedContent(
        targetState = gradientColors,
        transitionSpec = { fadeIn(tween(GlowCrossfadeMillis)) togetherWith fadeOut(tween(GlowCrossfadeMillis)) },
        label = "animated-glow-background",
    ) { colors ->
        if (colors.isNotEmpty()) {
            val progress = rememberInfiniteTransition(label = "glow-animation").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(GlowCycleMillis, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "glow-progress",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val brushes = animatedGlowBrushes(colors, progress.value, size.width, size.height)
                        onDrawBehind {
                            drawRect(color = GlowBase)
                            brushes.forEach { drawRect(brush = it) }
                        }
                    },
            )
        }
    }
}

/** The six lights at [progress] through the loop, each colour sliding into the next as they drift. */
private fun animatedGlowBrushes(colors: List<Color>, progress: Float, width: Float, height: Float): List<Brush> {
    fun rotatedColorAt(index: Int): Color {
        val position = index.toFloat() + progress * colors.size
        val from = floor(position).toInt() % colors.size
        val to = (from + 1) % colors.size
        return lerp(colors[from], colors[to], position - floor(position))
    }

    // Whole cycles only, so the loop is seamless where progress wraps from 1 back to 0.
    fun oscillate(min: Float, max: Float, phase: Float): Float {
        val wave = sin(2f * PI.toFloat() * (progress + phase))
        return min + (max - min) * ((wave + 1f) * 0.5f)
    }

    fun light(index: Int, centerAlpha: Float, midAlpha: Float, x: Float, y: Float, radius: Float) =
        glow(rotatedColorAt(index), centerAlpha, midAlpha, Offset(width * x, height * y), width * radius)

    return listOf(
        light(0, 0.85f, 0.5f, oscillate(0.0f, 1.0f, 0.00f), oscillate(0.0f, 0.5f, 0.07f), oscillate(0.8f, 1.6f, 0.12f)),
        light(1, 0.8f, 0.45f, oscillate(1.0f, 0.0f, 0.2f), oscillate(0.5f, 1.0f, 0.25f), oscillate(0.7f, 1.5f, 0.18f)),
        light(2, 0.75f, 0.4f, oscillate(0.2f, 0.8f, 0.33f), oscillate(0.8f, 0.2f, 0.36f), oscillate(0.6f, 1.4f, 0.29f)),
        light(3, 0.7f, 0.35f, oscillate(0.3f, 0.7f, 0.44f), oscillate(0.2f, 0.8f, 0.41f), oscillate(0.9f, 1.7f, 0.47f)),
        light(4, 0.65f, 0.3f, oscillate(0.4f, 0.6f, 0.55f), oscillate(0.0f, 1.0f, 0.51f), oscillate(0.7f, 1.5f, 0.58f)),
        light(5, 0.6f, 0.25f, oscillate(0.0f, 1.0f, 0.66f), oscillate(0.5f, 0.7f, 0.62f), oscillate(0.8f, 1.8f, 0.69f)),
    )
}

private fun glow(color: Color, centerAlpha: Float, midAlpha: Float, center: Offset, radius: Float) =
    Brush.radialGradient(
        colors = listOf(color.copy(alpha = centerAlpha), color.copy(alpha = midAlpha), Color.Transparent),
        center = center,
        radius = radius,
    )

/** [CustomBackground]'s contrast and brightness, as the colour matrix ArchiveTune applies them with. */
fun customBackgroundColorFilter(contrast: Float, brightness: Float): ColorFilter {
    val translation = (1f - contrast) * 128f + (brightness - 1f) * 255f
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translation,
                0f, contrast, 0f, 0f, translation,
                0f, 0f, contrast, 0f, translation,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
}

// ArchiveTune's `PlayerBackgroundColorUtils`: the cover's colours, kept within what is comfortable to
// read white text over.

private fun ensureComfortableColor(
    color: Color,
    minBrightness: Float = 0.15f,
    maxBrightness: Float = 0.58f,
    minSaturation: Float = 0.32f,
): Color {
    val hsv = color.toHsv()
    hsv[1] = hsv[1].coerceAtLeast(minSaturation)
    hsv[2] = hsv[2].coerceIn(minBrightness, maxBrightness)
    return hsv.toColor()
}

private fun darkenColor(color: Color, factor: Float): Color {
    val hsv = color.toHsv()
    hsv[2] = (hsv[2] * factor).coerceAtLeast(0f)
    return hsv.toColor()
}

private fun coloringStops(baseColor: Color): Array<Pair<Float, Color>> {
    val comfortable = ensureComfortableColor(baseColor, minBrightness = 0.18f, maxBrightness = 0.5f)
    return arrayOf(
        0f to comfortable.copy(alpha = 0.97f),
        0.4f to darkenColor(comfortable, 0.82f).copy(alpha = 0.94f),
        0.75f to darkenColor(comfortable, 0.6f).copy(alpha = 0.92f),
        1f to Color.Black.copy(alpha = 0.88f),
    )
}

private fun blurOverlayStops(colors: List<Color>): Array<Pair<Float, Color>> {
    if (colors.isEmpty()) return arrayOf(0f to Color.Black.copy(alpha = 0.35f), 1f to Color.Black.copy(alpha = 0.45f))
    val comfortable = colors.map { ensureComfortableColor(it) }
    val first = comfortable[0]
    val second = comfortable.getOrNull(1) ?: first
    val third = comfortable.getOrNull(2) ?: second
    return arrayOf(
        0f to first.copy(alpha = 0.45f),
        0.4f to lerp(first, second, 0.5f).copy(alpha = 0.38f),
        0.75f to lerp(second, third, 0.55f).copy(alpha = 0.35f),
        1f to third.copy(alpha = 0.50f),
    )
}

private fun blurGradientStops(colors: List<Color>): Array<Pair<Float, Color>> {
    if (colors.isEmpty()) return arrayOf(0f to Color.Transparent, 1f to Color.Transparent)
    val comfortable = colors.map { ensureComfortableColor(it) }
    val first = comfortable[0]
    val second = comfortable.getOrNull(1) ?: first
    val third = comfortable.getOrNull(2) ?: second
    return arrayOf(
        0f to first.copy(alpha = 0.55f),
        0.2f to lerp(first, second, 0.3f).copy(alpha = 0.48f),
        0.5f to second.copy(alpha = 0.42f),
        0.8f to lerp(second, third, 0.6f).copy(alpha = 0.38f),
        1f to third.copy(alpha = 0.35f),
    )
}

private fun Color.toHsv(): FloatArray = FloatArray(3).also { AndroidColor.colorToHSV(toArgb(), it) }

private fun FloatArray.toColor(): Color = Color(AndroidColor.HSVToColor(this))
