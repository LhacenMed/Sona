package com.lhacenmed.sona.core.designsystem.motion

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.compose.animation.core.Easing
import kotlin.math.abs
import kotlin.math.sign

/**
 * Where the band holds what is dragged, for a [pull] of finger travel either side of rest - Khatmah's
 * wird wall curve:
 *
 *     offset(pull) = arm + limit·over / (over + limit),   over = |pull| − arm
 *
 * Up to [arm] this is the identity: what is dragged sits exactly under the finger. Past it the term is
 * a hyperbola whose slope at `over = 0` is 1, so resistance arrives gradually instead of switching on;
 * it has given half of [limit] by `over = limit`, and only approaches `arm + limit`, so no pull however
 * long takes it further. An [arm] of 0 resists from the first pixel.
 */
fun rubberBandOffset(pull: Float, arm: Float, limit: Float): Float {
    val distance = abs(pull)
    if (distance <= arm) return pull
    val over = distance - arm
    return pull.sign * (arm + limit * over / (over + limit))
}

/**
 * The pull that holds the band at [offset] - the inverse of [rubberBandOffset], so a drag can be taken
 * up wherever a settle has left it. An offset the band cannot reach (left by a settle begun under a
 * longer [arm]) is taken as the band at full stretch.
 */
fun rubberBandPull(offset: Float, arm: Float, limit: Float): Float {
    val distance = abs(offset)
    if (distance <= arm) return offset
    val stretch = (distance - arm).coerceAtMost(limit * FullStretch)
    return offset.sign * (arm + limit * stretch / (limit - stretch))
}

private const val FullStretch = 0.99f

/**
 * How a released band falls back to rest: this long, decelerating with this tension - Khatmah's page
 * falling back over an unfinished wall.
 */
const val RubberBandSettleDurationMillis = 240
const val RubberBandSettleTension = 1.6f

/** [RubberBandSettleTension] as a Compose easing, for a band settled by a Compose animation. */
val RubberBandSettleEasing = Easing(DecelerateInterpolator(RubberBandSettleTension)::getInterpolation)

/**
 * Where a sideways swipe - the mini player's, a list row's - stops following the finger and releasing it
 * acts, and the ceiling it can never pass, both as a share of the width swiped: Khatmah's wird wall, as
 * it is of its page.
 */
const val SwipeArmFraction = 0.15f
const val SwipeStretchMaxFraction = 0.27f

/**
 * Felt as a swipe crosses its arm, either way - [isArmed] as it reaches it, so letting go now acts, and
 * not as it falls back behind it, so letting go now will not - so the finger feels the range the action
 * holds without the eye having to check. The platform's own threshold haptics where there are some, a
 * distinct feel each way; Khatmah's CLOCK_TICK, felt where lighter ones are not, before them.
 */
fun View.performSwipeArmHaptic(isArmed: Boolean) {
    val feedback = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> HapticFeedbackConstants.CLOCK_TICK
        isArmed -> HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
        else -> HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE
    }
    performHapticFeedback(feedback)
}
