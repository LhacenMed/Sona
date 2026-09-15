package com.lhacenmed.sona.core.designsystem.theme

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext

private const val ThemeTransitionDurationMillis = 350

/**
 * Sona's Material3 theme.
 *
 * By default, this behaves like a plain Material3 theme following the system light/dark setting.
 * Passing a non-default [themeColor] (e.g. extracted from the currently playing track's artwork)
 * switches to a materialkolor-generated dynamic scheme seeded from that color; leaving it as
 * [DefaultThemeColor] on Android 12+ instead uses the system's wallpaper-based dynamic color.
 *
 * This composable only builds the [androidx.compose.material3.ColorScheme] - it does not know
 * anything about playback, artwork loading, or color extraction. That pipeline lives in the app
 * layer, which observes the current track and calls [extractThemeColor] on its artwork before
 * passing the result down here.
 *
 * Every change of scheme - a new cover, the wallpaper colours returning, light and dark - is animated
 * by [animateColorSchemeAsState], so the whole app moves between schemes as one.
 *
 * [coverStyle] is provided alongside the colours, so every cover in the app is drawn the same way.
 */
@Composable
fun SonaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeColor: Color = DefaultThemeColor,
    coverStyle: CoverStyle,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    // Generated once per change rather than once per frame: an HCT scheme is dozens of tone solves,
    // and the transition below only ever needs its two ends.
    val targetColorScheme = remember(themeColor, darkTheme) {
        val useSystemDynamicColor =
            themeColor == DefaultThemeColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val scheme = if (useSystemDynamicColor) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            materialKolorDynamicColorScheme(seedColor = themeColor, isDark = darkTheme)
        }
        // Every bar, row and sheet is `surface`, while a Scaffold - and any screen that paints nothing
        // itself - shows `background`. The two match on most devices but not all, and where they differ
        // content sits in bands of another shade. One colour for both, here, keeps every screen whole.
        scheme.copy(background = scheme.surface, onBackground = scheme.onSurface)
    }

    MaterialTheme(colorScheme = animateColorSchemeAsState(targetColorScheme)) {
        CompositionLocalProvider(LocalCoverStyle provides coverStyle, content = content)
    }
}

/** The two schemes a transition runs between, and how far along it is. */
private class ColorSchemeTransition(initial: ColorScheme) {
    var from by mutableStateOf(initial)
    var to by mutableStateOf(initial)
    val progress = Animatable(1f)

    val current: ColorScheme
        get() = if (progress.value == 1f) to else from.lerp(to, progress.value)
}

/**
 * [target], reached by a transition from the scheme on screen rather than applied at once.
 *
 * One progress value drives every role, so no colour can lead or trail another: whatever reads the
 * theme - a bar, a card, a row - is on the same frame of the same transition. A target that changes
 * mid-transition starts the next one from the colours on screen at that moment, so nothing jumps. The
 * first scheme is applied as it is: there is nothing on screen yet to move from.
 */
@Composable
private fun animateColorSchemeAsState(target: ColorScheme): ColorScheme {
    val transition = remember { ColorSchemeTransition(target) }
    LaunchedEffect(target) {
        if (target === transition.to) return@LaunchedEffect
        transition.from = transition.current
        transition.to = target
        transition.progress.snapTo(0f)
        transition.progress.animateTo(1f, tween(durationMillis = ThemeTransitionDurationMillis))
    }
    return transition.current
}

private fun ColorScheme.lerp(to: ColorScheme, fraction: Float) = ColorScheme(
    primary = lerp(primary, to.primary, fraction),
    onPrimary = lerp(onPrimary, to.onPrimary, fraction),
    primaryContainer = lerp(primaryContainer, to.primaryContainer, fraction),
    onPrimaryContainer = lerp(onPrimaryContainer, to.onPrimaryContainer, fraction),
    inversePrimary = lerp(inversePrimary, to.inversePrimary, fraction),
    secondary = lerp(secondary, to.secondary, fraction),
    onSecondary = lerp(onSecondary, to.onSecondary, fraction),
    secondaryContainer = lerp(secondaryContainer, to.secondaryContainer, fraction),
    onSecondaryContainer = lerp(onSecondaryContainer, to.onSecondaryContainer, fraction),
    tertiary = lerp(tertiary, to.tertiary, fraction),
    onTertiary = lerp(onTertiary, to.onTertiary, fraction),
    tertiaryContainer = lerp(tertiaryContainer, to.tertiaryContainer, fraction),
    onTertiaryContainer = lerp(onTertiaryContainer, to.onTertiaryContainer, fraction),
    background = lerp(background, to.background, fraction),
    onBackground = lerp(onBackground, to.onBackground, fraction),
    surface = lerp(surface, to.surface, fraction),
    onSurface = lerp(onSurface, to.onSurface, fraction),
    surfaceVariant = lerp(surfaceVariant, to.surfaceVariant, fraction),
    onSurfaceVariant = lerp(onSurfaceVariant, to.onSurfaceVariant, fraction),
    surfaceTint = lerp(surfaceTint, to.surfaceTint, fraction),
    inverseSurface = lerp(inverseSurface, to.inverseSurface, fraction),
    inverseOnSurface = lerp(inverseOnSurface, to.inverseOnSurface, fraction),
    error = lerp(error, to.error, fraction),
    onError = lerp(onError, to.onError, fraction),
    errorContainer = lerp(errorContainer, to.errorContainer, fraction),
    onErrorContainer = lerp(onErrorContainer, to.onErrorContainer, fraction),
    outline = lerp(outline, to.outline, fraction),
    outlineVariant = lerp(outlineVariant, to.outlineVariant, fraction),
    scrim = lerp(scrim, to.scrim, fraction),
    surfaceBright = lerp(surfaceBright, to.surfaceBright, fraction),
    surfaceContainer = lerp(surfaceContainer, to.surfaceContainer, fraction),
    surfaceContainerHigh = lerp(surfaceContainerHigh, to.surfaceContainerHigh, fraction),
    surfaceContainerHighest = lerp(surfaceContainerHighest, to.surfaceContainerHighest, fraction),
    surfaceContainerLow = lerp(surfaceContainerLow, to.surfaceContainerLow, fraction),
    surfaceContainerLowest = lerp(surfaceContainerLowest, to.surfaceContainerLowest, fraction),
    surfaceDim = lerp(surfaceDim, to.surfaceDim, fraction),
    primaryFixed = lerp(primaryFixed, to.primaryFixed, fraction),
    primaryFixedDim = lerp(primaryFixedDim, to.primaryFixedDim, fraction),
    onPrimaryFixed = lerp(onPrimaryFixed, to.onPrimaryFixed, fraction),
    onPrimaryFixedVariant = lerp(onPrimaryFixedVariant, to.onPrimaryFixedVariant, fraction),
    secondaryFixed = lerp(secondaryFixed, to.secondaryFixed, fraction),
    secondaryFixedDim = lerp(secondaryFixedDim, to.secondaryFixedDim, fraction),
    onSecondaryFixed = lerp(onSecondaryFixed, to.onSecondaryFixed, fraction),
    onSecondaryFixedVariant = lerp(onSecondaryFixedVariant, to.onSecondaryFixedVariant, fraction),
    tertiaryFixed = lerp(tertiaryFixed, to.tertiaryFixed, fraction),
    tertiaryFixedDim = lerp(tertiaryFixedDim, to.tertiaryFixedDim, fraction),
    onTertiaryFixed = lerp(onTertiaryFixed, to.onTertiaryFixed, fraction),
    onTertiaryFixedVariant = lerp(onTertiaryFixedVariant, to.onTertiaryFixedVariant, fraction),
)
