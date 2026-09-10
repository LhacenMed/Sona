package com.lhacenmed.sona.core.designsystem.theme

import android.graphics.Bitmap
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.ktx.toHct

/**
 * Fallback seed color used whenever no artwork-derived color is available (no track playing,
 * extraction failed, or dynamic theming disabled with no manual override set).
 */
val DefaultThemeColor = Color(0xFFED5564)

private fun Int.toComposeColor(): Color = Color(this.toLong() and 0xFFFFFFFFL)

/**
 * Extracts a single representative seed color from this bitmap using Android's Palette API.
 *
 * Swatch priority: vibrant -> dominant -> muted -> light-vibrant -> dark-vibrant -> light-muted
 * -> dark-muted, falling back to [DefaultThemeColor] if every swatch is null.
 *
 * The bitmap must be a software (non-hardware) bitmap so Palette can read raw pixels.
 */
fun Bitmap.extractThemeColor(): Color {
    val palette = Palette.from(this).maximumColorCount(16).generate()

    val swatch =
        palette.vibrantSwatch
            ?: palette.dominantSwatch
            ?: palette.mutedSwatch
            ?: palette.lightVibrantSwatch
            ?: palette.darkVibrantSwatch
            ?: palette.lightMutedSwatch
            ?: palette.darkMutedSwatch

    return swatch?.rgb?.toComposeColor() ?: DefaultThemeColor
}

/**
 * Picks a [PaletteStyle] based on the seed color's HCT chroma, matching the reference
 * implementation: low-chroma (near-gray) seeds get a calmer style so the resulting scheme
 * doesn't look artificially saturated.
 */
private fun paletteStyleFor(seedColor: Color): PaletteStyle {
    val chroma = seedColor.toHct().chroma
    return when {
        chroma < 4.0 -> PaletteStyle.Monochrome
        chroma < 12.0 -> PaletteStyle.Neutral
        else -> PaletteStyle.TonalSpot
    }
}

/**
 * Builds a full Material3 [ColorScheme] from a single seed color using materialkolor's HCT-based
 * dynamic color scheme generation, with the [PaletteStyle] chosen from the seed's chroma.
 */
fun materialKolorDynamicColorScheme(seedColor: Color, isDark: Boolean): ColorScheme =
    dynamicColorScheme(
        seedColor = seedColor,
        isDark = isDark,
        contrastLevel = 0.0,
        style = paletteStyleFor(seedColor),
    )
