package com.lhacenmed.sona.core.designsystem.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp

/**
 * The keylines, corners, gaps and press response every component in Sona shares.
 *
 * Covers, cards, tabs and buttons all read their look from here, so tuning a value moves every one of
 * them together and no component can drift from the rest.
 */
object SonaComponentStyle {

    /**
     * The keyline every section of a screen starts and ends on - bar, cards, tabs and rows alike.
     * An icon aligns by its glyph rather than its touch target, which is what the eye lines up.
     */
    val ContentHorizontalPadding = 16.dp

    /** The corners everything rests with: Material's medium corner. */
    val CornerRadius = 12.dp

    /** Tighter while held, the way an expressive button's corners answer a press: Material's small corner. */
    val PressedCornerRadius = 8.dp

    val Shape: Shape = RoundedCornerShape(CornerRadius)

    /** The gap between neighbouring items in a row. */
    val ItemSpacing = 8.dp

    /** How much wider a held item grows within its row, as a share of its width. Its neighbours give it up. */
    const val PressedExpandedRatio = 0.08f
}

/** The corners of something [pressFraction] of the way from resting to fully held. */
fun pressedCornerRadius(pressFraction: Float): Dp =
    lerp(SonaComponentStyle.CornerRadius, SonaComponentStyle.PressedCornerRadius, pressFraction)

/**
 * How held [interactionSource] is, from 0 at rest to 1 fully pressed, on the same motion Material's own
 * buttons morph with - so a custom component and a Material one answer a press identically.
 */
@Composable
fun rememberPressFraction(interactionSource: InteractionSource): State<Float> {
    val isPressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "pressFraction",
    )
}
