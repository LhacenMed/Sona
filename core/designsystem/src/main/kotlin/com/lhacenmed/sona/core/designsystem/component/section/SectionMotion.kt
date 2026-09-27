package com.lhacenmed.sona.core.designsystem.component.section

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * How a section folds and unfolds, lazy or not - one motion for both, so a settings screen's sections move
 * as a detail screen's do.
 *
 * What moves - rows sliding up into the room a fold left, a column closing - does so on a spring that never
 * overshoots: the expressive scheme's spatial springs bounce, and a bouncing list reads as unsteady. What
 * fades does so on the scheme's own fast effects.
 */
internal object SectionMotion {
    val placement: FiniteAnimationSpec<IntOffset> =
        spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium, IntOffset.VisibilityThreshold)

    val size: FiniteAnimationSpec<IntSize> =
        spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium, IntSize.VisibilityThreshold)

    @Composable
    fun fade(): FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.fastEffectsSpec()
}
