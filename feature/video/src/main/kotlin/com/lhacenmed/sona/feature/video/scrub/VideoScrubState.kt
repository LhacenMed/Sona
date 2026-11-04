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
 * that shows it: the bar, its times, the preview above it and the dimming around it.
 *
 * Each read of [targetMs] where it is drawn or laid out follows the finger without composing anything for it.
 */
@Stable
internal class VideoScrubState(
    private val onSeek: (Long) -> Unit,
    private val onScrubbingChange: (Boolean) -> Unit,
    private val now: () -> Long = SystemClock::uptimeMillis,
) {
    var isActive by mutableStateOf(false)
        private set

    /** Where the scrub began, which the bar marks. */
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

    fun begin(fromMs: Long) {
        if (isActive) return
        originMs = fromMs
        targetMs = fromMs
        settlingMs = null
        isActive = true
        onScrubbingChange(true)
    }

    /** Takes the video to [positionMs], the player scrubbing meanwhile so each frame shows at once. */
    fun moveTo(positionMs: Long) {
        targetMs = positionMs
        onSeek(positionMs)
    }

    /** Lets go. Nothing more is sought: the last move was already to where it let go. */
    fun end() {
        if (!isActive) return
        settlingMs = targetMs
        settlingUntilMs = now() + SettleTimeoutMs
        isActive = false
        onScrubbingChange(false)
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
