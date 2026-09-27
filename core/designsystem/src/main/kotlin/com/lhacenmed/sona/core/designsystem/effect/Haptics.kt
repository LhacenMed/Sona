package com.lhacenmed.sona.core.designsystem.effect

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

// Every haptic the app gives answers to [SonaEffects.areHapticsEnabled], checked where each haptic is
// performed rather than set on the views that perform them: a view's own haptic setting is not honoured
// everywhere - MIUI and HyperOS perform haptics past it - and a window opened later would start without it.

/** [View.performHapticFeedback] while haptics are on - how the app performs every haptic of its own. */
fun View.performSonaHaptic(feedbackConstant: Int, flags: Int = 0) {
    if (SonaEffects.areHapticsEnabled) performHapticFeedback(feedbackConstant, flags)
}

/**
 * A confirming haptic, from wherever the app holds only a [Context] - what every toast is given.
 *
 * Performed by the window of the activity this context belongs to, as a touch on the screen is, so
 * Android's own touch-feedback setting applies to it too. With no activity - a service, the app closed -
 * the vibrator performs it instead, as a touch where Android can say what kind it is (13 and on).
 */
fun Context.performSonaHaptic() {
    if (!SonaEffects.areHapticsEnabled) return
    val window = findActivity()?.window
    if (window != null) {
        val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        window.decorView.performSonaHaptic(confirm)
        return
    }
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        getSystemService(Vibrator::class.java)
    }
    if (vibrator?.hasVibrator() != true) return
    val click = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
    } else {
        VibrationEffect.createOneShot(CLICK_FALLBACK_MS, VibrationEffect.DEFAULT_AMPLITUDE)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        vibrator.vibrate(click, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
    } else {
        vibrator.vibrate(click)
    }
}

/** How long a click lasts where Android has no click of its own to play (below 10). */
private const val CLICK_FALLBACK_MS = 20L

/**
 * Compose's own haptics - a long press, a fast scroller's thumb - answering to the same switch, for the
 * window [content] is the root of. Every window sets its own `LocalHapticFeedback`, so each window's root
 * provides this: the activities', and the dialogs' and sheets' opened over them.
 */
@Composable
fun ProvideSonaHaptics(content: @Composable () -> Unit) {
    val platformHaptics = LocalHapticFeedback.current
    val haptics = remember(platformHaptics) {
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                if (SonaEffects.areHapticsEnabled) platformHaptics.performHapticFeedback(hapticFeedbackType)
            }
        }
    }
    CompositionLocalProvider(LocalHapticFeedback provides haptics, content = content)
}
