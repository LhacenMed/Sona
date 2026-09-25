package com.lhacenmed.sona.core.designsystem.effect

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

// ArchiveTune's refresh rate request (`AppearanceSettings.ApplyRefreshRate`), copied as is - save that it
// is applied for as long as each activity shows, where ArchiveTune applies it only while its appearance
// settings screen is open.

/**
 * Asks the display for [targetFps] while [isEnabled], and hands the choice back to the system otherwise
 * and once this leaves the composition.
 */
@Composable
internal fun ApplyRefreshRate(
    isEnabled: Boolean,
    targetFps: Float,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() }
    val requestedFps = if (isEnabled) targetFps else DEFAULT_REFRESH_RATE_REQUEST

    DisposableEffect(view, activity, requestedFps) {
        applyRefreshRate(
            view = view,
            activity = activity,
            requestedFps = requestedFps,
        )

        onDispose {
            applyRefreshRate(
                view = view,
                activity = activity,
                requestedFps = DEFAULT_REFRESH_RATE_REQUEST,
            )
        }
    }
}

/** The fastest refresh rate this display offers, in frames per second. */
@Composable
fun rememberSupportedHighestFps(): Float {
    val view = LocalView.current

    return remember(view) {
        val display = view.display
        display
            ?.supportedModes
            ?.maxOfOrNull { mode -> mode.refreshRate }
            ?: display?.refreshRate
            ?: DEFAULT_STANDARD_REFRESH_RATE_FPS
    }
}

/** Whether [fps] is faster than a standard display - whether there is anything to force. */
fun isHighRefreshRate(fps: Float): Boolean = fps > HIGH_REFRESH_RATE_THRESHOLD_FPS

private fun applyRefreshRate(
    view: View,
    activity: Activity?,
    requestedFps: Float,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        view.setRequestedFrameRate(requestedFps)
        return
    }

    activity?.window?.let { window ->
        val attributes = window.attributes
        if (attributes.preferredRefreshRate != requestedFps) {
            attributes.preferredRefreshRate = requestedFps
            window.attributes = attributes
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private const val HIGH_REFRESH_RATE_THRESHOLD_FPS = 60.5f
private const val DEFAULT_STANDARD_REFRESH_RATE_FPS = 60f
private const val DEFAULT_REFRESH_RATE_REQUEST = 0f
