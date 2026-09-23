package com.lhacenmed.sona.feature.player

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
internal fun rubberBandOffset(pull: Float, arm: Float, limit: Float): Float {
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
internal fun rubberBandPull(offset: Float, arm: Float, limit: Float): Float {
    val distance = abs(offset)
    if (distance <= arm) return offset
    val stretch = (distance - arm).coerceAtMost(limit * FullStretch)
    return offset.sign * (arm + limit * stretch / (limit - stretch))
}

private const val FullStretch = 0.99f
