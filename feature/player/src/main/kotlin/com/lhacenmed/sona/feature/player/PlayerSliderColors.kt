package com.lhacenmed.sona.feature.player

import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Alpha of a slider's inactive track - ArchiveTune's `PlayerSliderColors.Config.INACTIVE_TRACK_ALPHA`. */
private const val InactiveTrackAlpha = 0.22f

/** The seek bar in the player's button colour - ArchiveTune's standard and circular slider colours, which are the same. */
@Composable
internal fun playerSliderColors(buttonColor: Color): SliderColors =
    SliderDefaults.colors(
        activeTrackColor = buttonColor,
        activeTickColor = buttonColor,
        thumbColor = buttonColor,
        inactiveTrackColor = buttonColor.copy(alpha = InactiveTrackAlpha),
    )
