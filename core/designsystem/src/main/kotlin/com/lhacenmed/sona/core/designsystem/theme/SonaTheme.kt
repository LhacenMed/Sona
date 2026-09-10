package com.lhacenmed.sona.core.designsystem.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
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
 */
@Composable
fun SonaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeColor: Color = DefaultThemeColor,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    val useSystemDynamicColor =
        themeColor == DefaultThemeColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // A single animated seed, rather than animating each of the scheme's ~46 roles separately:
    // every role is derived from the same instantaneous seed, so they can never drift out of step
    // with each other mid-transition, and one animation drives the whole theme instead of dozens.
    val animatedSeed by animateColorAsState(
        targetValue = themeColor,
        animationSpec = tween(durationMillis = ThemeTransitionDurationMillis),
        label = "themeSeed",
    )

    val colorScheme = remember(animatedSeed, darkTheme, useSystemDynamicColor) {
        if (useSystemDynamicColor) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            materialKolorDynamicColorScheme(seedColor = animatedSeed, isDark = darkTheme)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
