package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember

/**
 * Material's slider state, over [valueRange], standing wherever [value] - held elsewhere: a track's or a video's position, a
 * scrub - stands, as the value-based slider did: a slider with a thumb or a track of its own is drawn only from a state now.
 * Its drags reach the holder through the slider's own `onValueChange`, which moves [value], and so this.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberFollowingSliderState(value: Float, valueRange: ClosedFloatingPointRange<Float> = 0f..1f): SliderState {
    val state = remember(valueRange) { SliderState(value, 0, valueRange) }
    SideEffect { state.value = value }
    return state
}
