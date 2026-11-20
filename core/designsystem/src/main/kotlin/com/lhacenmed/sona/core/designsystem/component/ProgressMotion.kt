package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

/**
 * How every determinate progress indicator in the app moves to a new [progress]: on Material's own spring for one - no bounce,
 * gliding between figures that come in steps - never jumping to it. Read where it is drawn - `progress = { shown }` - so a frame
 * of it redraws, not recomposes.
 */
@Composable
fun animateProgressAsState(progress: Float): State<Float> =
    animateFloatAsState(progress.coerceIn(0f, 1f), ProgressIndicatorDefaults.ProgressAnimationSpec, label = "progress")
