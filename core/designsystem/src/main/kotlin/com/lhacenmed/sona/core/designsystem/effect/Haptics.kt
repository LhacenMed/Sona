package com.lhacenmed.sona.core.designsystem.effect

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
