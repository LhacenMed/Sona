package com.lhacenmed.sona.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Round mode, as every shape in the app is cut - Auxio's `roundMode`, taken past covers to the whole UI:
 * every corner as it is designed while it is on, and square while it is off.
 *
 * [SonaTheme] provides it, and hands Material [SquareShapes] while it is off, so every component taking its
 * corners from the theme - a dialog, a sheet, a menu, a snackbar - squares itself. Whatever sets its own
 * corners goes through [roundedRadius], [roundedShape] or [pillShape]. What is a circle by definition - a
 * radio dot, a switch or slider thumb, the fast scroller's thumb, a progress ring and the dial the mini
 * player draws in one - stays one.
 */
val LocalIsRounded = staticCompositionLocalOf { true }

/** [radius] under round mode: as designed, or none while it is off. */
@Composable
@ReadOnlyComposable
fun roundedRadius(radius: Dp): Dp = if (LocalIsRounded.current) radius else 0.dp

/** Corners of [radius] under round mode - see [roundedRadius]. */
@Composable
@ReadOnlyComposable
fun roundedShape(radius: Dp): CornerBasedShape = RoundedCornerShape(roundedRadius(radius))

/** A pill - a circle, on something square - under round mode: what every button rests as. */
val pillShape: CornerBasedShape
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsRounded.current) CircleShape else SquareShape

/** Square corners: the shape of everything while round mode is off. */
val SquareShape: CornerBasedShape = RoundedCornerShape(0.dp)

/** Material's shapes, every one of them square - what [SonaTheme] hands Material while round mode is off. */
internal val SquareShapes = Shapes(
    extraSmall = SquareShape,
    small = SquareShape,
    medium = SquareShape,
    large = SquareShape,
    extraLarge = SquareShape,
    largeIncreased = SquareShape,
    extraLargeIncreased = SquareShape,
    extraExtraLarge = SquareShape,
)
