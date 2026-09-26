package com.lhacenmed.sona.feature.settings.appearance.palette

import android.graphics.Picture
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.caverock.androidsvg.SVG
import com.lhacenmed.sona.core.designsystem.theme.palette.ThemeSeedPalette
import com.lhacenmed.sona.core.designsystem.theme.roundedShape
import com.materialkolor.hct.Hct
import com.materialkolor.ktx.toColor
import com.materialkolor.ktx.toHct

/** The tones each palette is drawn in - Material's tonal palette steps. */
private val TonalSteps = listOf(0, 10, 20, 25, 30, 40, 50, 60, 70, 80, 90, 95, 98, 99, 100)

/** Material's error seed, which no palette chooses. */
private val ErrorSeed = Color(0xFFB3261E)

/** A fill naming a tonal role: a palette's letters, then a tone - `p40`, `nv90`. */
private val RoleFill = Regex("""fill="(p|s|t|n|nv|e)(\d+)"""")

/**
 * ArchiveTune's palette preview: its illustration drawn in [palette]'s tones, flipped to their dark
 * counterparts while [isDarkTheme]. Rendered to a picture once per size and palette, then drawn as is.
 */
@Composable
internal fun PalettePreview(
    palette: ThemeSeedPalette,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val tonalPalettes = remember(palette) { TonalPalettes.from(palette) }
    val picture: Picture? = remember(tonalPalettes, isDarkTheme, size) {
        if (size.width == 0 || size.height == 0) {
            null
        } else {
            SVG.getFromString(tonalPalettes.colorize(PaletteIllustrationSvg, isDarkTheme))
                .renderToPicture(size.width, size.height)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.38f)
            .clip(roundedShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Canvas(modifier = Modifier.fillMaxSize().onSizeChanged { size = it }) {
            picture?.let { drawIntoCanvas { canvas -> canvas.nativeCanvas.drawPicture(it) } }
        }
    }
}

/** ArchiveTune's `TonalPalettes`: each seed's tones, and neutrals with the neutral seed's chroma held low. */
private class TonalPalettes(private val roles: Map<String, Map<Int, Color>>) {

    /** [svg] with every role fill replaced by its colour; any other fill is left as it is. */
    fun colorize(svg: String, isDarkTheme: Boolean): String =
        RoleFill.replace(svg) { match ->
            val (role, tone) = match.destructured
            val color = roles.getValue(role)[tone.toInt().forTheme(isDarkTheme)] ?: return@replace match.value
            "fill=\"${String.format("#%06X", 0xFFFFFF and color.toArgb())}\""
        }

    companion object {
        fun from(palette: ThemeSeedPalette): TonalPalettes {
            val neutral = palette.neutral.toHct()
            return TonalPalettes(
                mapOf(
                    "p" to palette.primary.tones(),
                    "s" to palette.secondary.tones(),
                    "t" to palette.tertiary.tones(),
                    "n" to neutral.tonesWithChroma(4.0),
                    "nv" to neutral.tonesWithChroma(8.0),
                    "e" to ErrorSeed.tones(),
                ),
            )
        }

        private fun Color.tones(): Map<Int, Color> {
            val hct = toHct()
            return TonalSteps.associateWith { hct.withTone(it.toDouble()).toColor() }
        }

        private fun Hct.tonesWithChroma(maxChroma: Double): Map<Int, Color> =
            TonalSteps.associateWith { Hct.from(hue, minOf(chroma, maxChroma), it.toDouble()).toColor() }
    }
}

/** A tone as the illustration draws it in a dark theme: light and dark swapped, as ArchiveTune swaps them. */
private fun Int.forTheme(isDarkTheme: Boolean): Int {
    if (!isDarkTheme) return this
    return when (this) {
        10 -> 99
        20 -> 95
        25, 30 -> 90
        40 -> 80
        50 -> 60
        60 -> 50
        70, 80 -> 40
        90 -> 30
        95 -> 20
        98, 99 -> 10
        100 -> 20
        else -> this
    }
}
