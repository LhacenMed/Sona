package com.lhacenmed.sona.core.designsystem.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * Material's emphasized decelerate: anything arriving or moving - an entrance, a shape turning into another, a step sliding
 * in. Things arrive quickly and settle.
 */
val EmphasizedDecelerate: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** A small overshoot past the end and back: a shape turning, an icon growing in. */
val OvershootEasing: Easing = CubicBezierEasing(0.34f, 1.4f, 0.64f, 1f)

/** How long something arriving takes to fade in. */
const val FadeInMillis = 200

/** How long something leaving takes to fade out. */
const val FadeOutMillis = 90

/** The gap between things that arrive one after another. */
const val StaggerMillis = 30
