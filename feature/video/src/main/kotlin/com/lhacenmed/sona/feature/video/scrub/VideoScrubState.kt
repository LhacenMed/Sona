package com.lhacenmed.sona.feature.video.scrub

import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs

/** How close the player's position has to come to where a scrub let go for the seek bar to follow it again. */
private const val SettleToleranceMs = 1_000L

/** How long the seek bar waits for the player to catch up before following it wherever it is. */
private const val SettleTimeoutMs = 1_500L

/**
 * A scrub through the video - by the seek bar's thumb or by a swipe across the picture, one and the same to everything
 * that shows it. The video plays on meanwhile: only letting go moves it, and letting go where the scrub began leaves it be.
 *
 * Each read of [targetMs] where it is drawn or laid out follows the finger without composing anything for it.
 */
@Stable
internal class VideoScrubState(
    private val onSeek: (Long) -> Unit,
    private val onSnappedToOrigin: () -> Unit,
    private val now: () -> Long = SystemClock::uptimeMillis,
) {
    var isActive by mutableStateOf(false)
        private set

    /** Where the scrub began. */
    var originMs by mutableLongStateOf(0L)
        private set

    /** Where the finger has taken it to. */
    var targetMs by mutableLongStateOf(0L)
        private set

    /** The seek track's place on the screen, which the preview stands over. */
    var trackBounds by mutableStateOf(Rect.Zero)

    /** Where the last scrub let go, until the player has caught up with it - and how long that may take. */
    private var settlingMs: Long? = null
    private var settlingUntilMs = 0L

    /** Whether the thumb has gone far enough from where it began to snap back to it, and whether it stands snapped there now. */
    private var hasLeftOrigin = false
    private var isSnapped = false

    fun begin(fromMs: Long) {
        if (isActive) return
        originMs = fromMs
        targetMs = fromMs
        settlingMs = null
        hasLeftOrigin = false
        isSnapped = false
        isActive = true
    }

    /** Takes the scrub to [positionMs]. */
    fun moveTo(positionMs: Long) {
        targetMs = positionMs
    }

    /**
     * Takes the scrub to [positionMs] under the thumb, which, once it has gone further than [releaseRadiusMs] from where it
     * began, snaps back there on coming within [snapRadiusMs], and stays until pulled out past [releaseRadiusMs] again.
     */
    fun dragTo(positionMs: Long, snapRadiusMs: Long, releaseRadiusMs: Long) {
        val distanceMs = abs(positionMs - originMs)
        if (isSnapped) {
            if (distanceMs > releaseRadiusMs) isSnapped = false
        } else if (hasLeftOrigin && distanceMs <= snapRadiusMs) {
            isSnapped = true
            onSnappedToOrigin()
        }
        if (distanceMs > releaseRadiusMs) hasLeftOrigin = true
        targetMs = if (isSnapped) originMs else positionMs
    }

    /** Lets go, the video going where the scrub reached - or left playing on, back where it began. */
    fun end() {
        if (!isActive) return
        isActive = false
        if (targetMs == originMs) return
        settlingMs = targetMs
        settlingUntilMs = now() + SettleTimeoutMs
        onSeek(targetMs)
    }

    /** Where the bar stands, the player being at [playerMs]: never jumping back for a moment after letting go. */
    fun shownPositionMs(playerMs: Long): Long {
        if (isActive) return targetMs
        val settling = settlingMs ?: return playerMs
        if (abs(playerMs - settling) <= SettleToleranceMs || now() >= settlingUntilMs) {
            settlingMs = null
            return playerMs
        }
        return settling
    }
}

/**
 * Where a preview [previewWidth] wide stands, centred over [thumbX] where it can be and kept [edgeMargin] inside a
 * screen [screenWidth] wide.
 */
internal fun previewLeft(thumbX: Float, previewWidth: Float, screenWidth: Float, edgeMargin: Float): Float {
    val furthest = (screenWidth - previewWidth - edgeMargin).coerceAtLeast(edgeMargin)
    return (thumbX - previewWidth / 2f).coerceIn(edgeMargin, furthest)
}
