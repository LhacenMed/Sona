package com.lhacenmed.sona.feature.playback

import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the sleep timer is waiting for. */
data class SleepTimerState(
    /** When playback pauses, in wall-clock milliseconds, or null while not counting down. */
    val pausesAtMs: Long? = null,
    /** Whether playback pauses when the current track ends. */
    val pausesAtEndOfTrack: Boolean = false,
) {
    val isActive: Boolean get() = pausesAtMs != null || pausesAtEndOfTrack
}

/** Pauses playback after a number of minutes, or at the end of the current track. Ported from ArchiveTune's `SleepTimer`. */
internal class SleepTimer(
    private val scope: CoroutineScope,
    private val pause: () -> Unit,
) {

    private var countdownJob: Job? = null

    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    fun start(minutes: Int) {
        countdownJob?.cancel()
        _state.value = SleepTimerState(pausesAtMs = System.currentTimeMillis() + minutes.minutes.inWholeMilliseconds)
        countdownJob = scope.launch {
            delay(minutes.minutes)
            pauseAndClear()
        }
    }

    fun startAtEndOfTrack() {
        countdownJob?.cancel()
        countdownJob = null
        _state.value = SleepTimerState(pausesAtEndOfTrack = true)
    }

    fun clear() {
        countdownJob?.cancel()
        countdownJob = null
        _state.value = SleepTimerState()
    }

    /** Playback has left the current track - it ended, or moved on - which is when an end-of-track timer pauses. */
    fun onTrackEnd() {
        if (_state.value.pausesAtEndOfTrack) pauseAndClear()
    }

    private fun pauseAndClear() {
        clear()
        pause()
    }
}
