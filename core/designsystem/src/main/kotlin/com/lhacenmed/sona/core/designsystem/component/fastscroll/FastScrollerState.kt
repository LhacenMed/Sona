package com.lhacenmed.sona.core.designsystem.component.fastscroll

import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long the thumb stays out once the list stops. */
private const val AUTO_HIDE_DELAY_MILLIS = 500L

/**
 * Whether the thumb is out and whether it is being dragged - Auxio's `showingThumb` and `dragging` - and
 * the [metrics] the thumb and the list are placed by, one reckoning for both.
 */
@Stable
internal class FastScrollerState(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
) {
    val metrics = ListScrollMetrics()

    var isThumbShown by mutableStateOf(false)
        private set

    var isDragging by mutableStateOf(false)
        private set

    private var hideJob: Job? = null

    fun onScrolled() {
        isThumbShown = true
        hideAfterDelay()
    }

    fun startDragging() {
        isDragging = true
        isThumbShown = true
        hideJob?.cancel()
        // A fling still running would fight the thumb for the list.
        scope.launch { listState.stopScroll() }
    }

    fun stopDragging() {
        isDragging = false
        hideAfterDelay()
    }

    fun hide() {
        hideJob?.cancel()
        isDragging = false
        isThumbShown = false
    }

    /** Puts the list where a thumb [thumbTopPx] down its [thumbRangePx] of travel stands for. */
    fun scrollToThumbTop(thumbTopPx: Float, thumbRangePx: Float) {
        if (thumbRangePx <= 0f) return
        listState.scrollToFraction(thumbTopPx / thumbRangePx, metrics)
    }

    private fun hideAfterDelay() {
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(AUTO_HIDE_DELAY_MILLIS)
            if (!isDragging) isThumbShown = false
        }
    }
}
