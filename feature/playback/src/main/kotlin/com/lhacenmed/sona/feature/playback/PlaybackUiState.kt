package com.lhacenmed.sona.feature.playback

import com.lhacenmed.sona.core.model.PlaybackParent
import com.lhacenmed.sona.core.model.RepeatMode

/**
 * Snapshot of playback state exposed to the UI layer.
 */
data class PlaybackUiState(
    /** Whether playback is on - including while the current track buffers - which is what play/pause shows. */
    val isPlaying: Boolean = false,
    /** Whether the current track is waiting for data, which the player shows as a spinner. */
    val isBuffering: Boolean = false,
    /** Whether playback has run to its end, which turns play/pause into replay. */
    val hasEnded: Boolean = false,
    val currentTrackId: Long? = null,
    /** The collection the queue was built from, or null when it was the whole library. */
    val parent: PlaybackParent? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    /** Whether previous and next can act - previous includes rewinding the current track. */
    val canSkipPrevious: Boolean = false,
    val canSkipNext: Boolean = false,
    /** Whether there is a track before or after the current one to move to. */
    val hasPreviousTrack: Boolean = false,
    val hasNextTrack: Boolean = false,
    /** The queue in the order it plays, shuffle included. */
    val queue: List<QueueEntry> = emptyList(),
    /** Where the current track sits in [queue], or -1 while nothing is loaded. */
    val currentQueueIndex: Int = -1,
)
