package com.lhacenmed.sona.feature.video.controls

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.lhacenmed.sona.feature.video.VideoPlayerTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Whether the controls are up: a tap on the video shows or hides them, and left untouched they hide by
 * themselves - see [HideAfterTimeout].
 */
@Stable
internal class VideoControlsState {
    var isVisible by mutableStateOf(false)
        private set

    /**
     * When the screen was last touched. A plain field, not state: it changes with every movement of a finger,
     * and only the timeout reads it - nothing is composed again for it.
     */
    private var lastTouchMs = 0L

    fun show() {
        isVisible = true
        onTouch()
    }

    fun hide() {
        isVisible = false
    }

    fun toggle() = if (isVisible) hide() else show()

    /** A touch anywhere on the screen - a control's included, a drag's every movement - keeping the controls up. */
    fun onTouch() {
        lastTouchMs = SystemClock.uptimeMillis()
    }

    /**
     * Hides the controls once they have been left alone for [timeoutMs] - never while it is null. The time is
     * counted afresh whenever it starts over - the video paused and played again, a panel closed.
     */
    @Composable
    fun HideAfterTimeout(timeoutMs: Long?) {
        LaunchedEffect(isVisible, timeoutMs) {
            if (!isVisible || timeoutMs == null) return@LaunchedEffect
            onTouch()
            while (true) {
                val remainingMs = lastTouchMs + timeoutMs - SystemClock.uptimeMillis()
                if (remainingMs <= 0L) break
                delay(remainingMs)
            }
            isVisible = false
        }
    }
}

/**
 * How far the video is, followed from the player. State read only by what shows it - the seek bar and its times, the
 * ambient line - so each tick redraws those and composes nothing around them.
 */
@Stable
internal class VideoPosition(positionMs: Long) {
    var positionMs by mutableLongStateOf(positionMs)

    /** How far ahead of [positionMs] the video has been read. */
    var bufferedMs by mutableLongStateOf(positionMs)
}

/** The player's [VideoPosition], read for as long as [isFollowing] and the screen is started - not while nothing shows it. */
@Composable
internal fun rememberVideoPosition(currentPositionMs: () -> Long, bufferedPositionMs: () -> Long, isFollowing: Boolean): VideoPosition {
    val position = remember { VideoPosition(currentPositionMs()) }
    val latestCurrentPositionMs by rememberUpdatedState(currentPositionMs)
    val latestBufferedPositionMs by rememberUpdatedState(bufferedPositionMs)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(isFollowing, lifecycleOwner) {
        if (!isFollowing) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                position.positionMs = latestCurrentPositionMs()
                position.bufferedMs = latestBufferedPositionMs()
                delay(VideoPlayerTokens.PositionTickMs)
            }
        }
    }
    return position
}
