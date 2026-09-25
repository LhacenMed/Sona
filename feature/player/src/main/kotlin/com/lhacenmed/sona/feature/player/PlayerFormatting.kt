package com.lhacenmed.sona.feature.player

import android.view.HapticFeedbackConstants
import android.view.View
import com.lhacenmed.sona.core.designsystem.effect.performSonaHaptic
import java.util.Locale

/** "m:ss", or "h:mm:ss" from the hour on - ArchiveTune's `makeTimeString`. */
internal fun makeTimeString(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1000
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

/** The light tick ArchiveTune's player controls answer a press with. */
@Suppress("DEPRECATION")
internal fun View.performContextClick() {
    performSonaHaptic(HapticFeedbackConstants.CONTEXT_CLICK, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
}
