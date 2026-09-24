package com.lhacenmed.sona.core.designsystem.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ToggleButtonShapes
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

    /**
     * The corners everything tightens to while held, the way an expressive button's corners answer a
     * press: Material's extra-small corner, what its connected button groups press to. The one value
     * every press in the app morphs to - buttons, icon buttons, the FAB, tabs, shortcuts and connected
     * groups alike - so tuning it retunes all of them.
     */
    val PressedCornerRadius = 8.dp

    val PressedShape: Shape = RoundedCornerShape(PressedCornerRadius)

    val Shape: Shape = RoundedCornerShape(CornerRadius)

    /** The gap between neighbouring items in a row. */
    val ItemSpacing = 8.dp

    /** How much wider a held item grows within its row, as a share of its width. Its neighbours give it up. */
    const val PressedExpandedRatio = 0.12f
}

/** The shapes every labelled button presses with: round at rest, tightening to [SonaComponentStyle.PressedShape] while held. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun buttonPressShapes(): ButtonShapes =
    ButtonDefaults.shapes(shape = CircleShape, pressedShape = SonaComponentStyle.PressedShape)

/** The shapes every icon button presses with: round at rest, tightening to [SonaComponentStyle.PressedShape] while held. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun iconButtonPressShapes(): IconButtonShapes =
    IconButtonDefaults.shapes(shape = CircleShape, pressedShape = SonaComponentStyle.PressedShape)

/**
 * The shapes the first button of a connected group presses with: Material's own, its inner corners
 * tightening to [SonaComponentStyle.PressedCornerRadius] while held. Its outer corners stay round.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun connectedLeadingButtonPressShapes(): ToggleButtonShapes =
    ButtonGroupDefaults.connectedLeadingButtonShapes(
        pressedShape = RoundedCornerShape(
            topStart = FullCorner,
            bottomStart = FullCorner,
            topEnd = PressedCorner,
            bottomEnd = PressedCorner,
        ),
    )

/** The last button of a connected group's shapes - see [connectedLeadingButtonPressShapes]. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun connectedTrailingButtonPressShapes(): ToggleButtonShapes =
    ButtonGroupDefaults.connectedTrailingButtonShapes(
        pressedShape = RoundedCornerShape(
            topStart = PressedCorner,
            bottomStart = PressedCorner,
            topEnd = FullCorner,
            bottomEnd = FullCorner,
        ),
    )

private val FullCorner = CornerSize(50)
private val PressedCorner = CornerSize(SonaComponentStyle.PressedCornerRadius)

/**
 * The corners of something [pressFraction] of the way from [restingRadius] to fully held - for a
 * component that draws its own shape rather than taking Material's pressed one.
 */
fun pressedCornerRadius(pressFraction: Float, restingRadius: Dp = SonaComponentStyle.CornerRadius): Dp =
    lerp(restingRadius, SonaComponentStyle.PressedCornerRadius, pressFraction)

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
