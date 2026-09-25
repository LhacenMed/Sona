package com.lhacenmed.sona.core.designsystem.effect

import android.animation.ValueAnimator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale

/**
 * How the app moves and feels, as the user set it - the one place every window, animation, refresh rate
 * and haptic in the app reads it from.
 *
 * Held as Compose snapshot state: whatever Compose draws from it follows a change the moment it is made,
 * and View code reads it at the moment it animates or buzzes. The app keeps it in step with the stored
 * settings, from before the first frame.
 */
object SonaEffects {
    var areAnimationsDisabled by mutableStateOf(false)
    var isHighRefreshRateForced by mutableStateOf(false)
    var areHapticsEnabled by mutableStateOf(true)

    /**
     * Whether anything should move on its own - not while the user has disabled animations here, nor
     * while Android's own "Remove animations" is on. What a looping animation checks, which a shorter
     * pace would only make jitter, and a View animation, which Compose's pace never reaches.
     */
    val shouldAnimate: Boolean
        get() = !areAnimationsDisabled && ValueAnimator.areAnimatorsEnabled()

    /**
     * [millis] for an Android View animation while things animate, and 0 - its end, at once, its end
     * listeners called as ever - while they do not.
     */
    fun animationDuration(millis: Long): Long = if (shouldAnimate) millis else 0L

    /**
     * The pace every Compose animation in a window runs at: none - each ends on its first frame - while
     * animations are disabled, and Android's own animator scale otherwise, which the window would follow
     * without it. Read on every frame an animation runs, so it is read from memory.
     */
    internal val motionDurationScale = object : MotionDurationScale {
        override val scaleFactor: Float
            get() = if (areAnimationsDisabled) 0f else ValueAnimator.getDurationScale()
    }
}
