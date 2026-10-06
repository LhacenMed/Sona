package com.lhacenmed.sona.feature.video

import android.app.Activity
import android.content.pm.ActivityInfo
import android.provider.Settings
import android.view.Window
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.lhacenmed.sona.core.datastore.VideoOrientation

/** The brightest a screen setting stored as a number goes - Android's own scale, before any device's. */
private const val SystemBrightnessMax = 255f

/**
 * Puts the player's window as the screen says: standing as [orientation] holds it, kept awake while
 * [keepScreenOn], and with the system bars only while [showSystemBars] - hidden, a swipe from the edge brings
 * them back for a moment.
 */
@Composable
internal fun VideoWindowEffects(orientation: VideoOrientation, keepScreenOn: Boolean, showSystemBars: Boolean) {
    val activity = checkNotNull(LocalActivity.current)
    val view = LocalView.current
    SideEffect {
        activity.requestedOrientation = orientation.activityOrientation
        view.keepScreenOn = keepScreenOn
        WindowCompat.getInsetsController(activity.window, view).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (showSystemBars) show(WindowInsetsCompat.Type.systemBars()) else hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(view) { onDispose { view.keepScreenOn = false } }
}

/** The way Android is asked to stand the player's window for each [VideoOrientation]. */
internal val VideoOrientation.activityOrientation: Int
    get() = when (this) {
        VideoOrientation.AUTO -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
        VideoOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        VideoOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

/**
 * The player window's own brightness - what the brightness gesture moves - starting from the system's, and
 * gone with the window: the rest of the device stays as bright as it was.
 */
internal class WindowBrightness(private val window: Window) {
    var level: Float
        get() = window.attributes.screenBrightness.takeIf { it >= 0f } ?: systemLevel()
        set(value) {
            window.attributes = window.attributes.apply { screenBrightness = value.coerceIn(MinLevel, 1f) }
        }

    private fun systemLevel(): Float =
        runCatching { Settings.System.getInt(window.context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / SystemBrightnessMax }
            .getOrDefault(0.5f)
            .coerceIn(0f, 1f)

    private companion object {
        /** Never quite black, which some devices read as turning the screen off. */
        const val MinLevel = 0.01f
    }
}

@Composable
internal fun rememberWindowBrightness(): WindowBrightness {
    val activity: Activity = checkNotNull(LocalActivity.current)
    return remember(activity) { WindowBrightness(activity.window) }
}
