package com.lhacenmed.sona.feature.player

import android.view.HapticFeedbackConstants
import android.view.View
import com.lhacenmed.sona.core.designsystem.effect.performSonaHaptic

/** The light tick ArchiveTune's player controls answer a press with. */
@Suppress("DEPRECATION")
internal fun View.performContextClick() {
    performSonaHaptic(HapticFeedbackConstants.CONTEXT_CLICK, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
}
