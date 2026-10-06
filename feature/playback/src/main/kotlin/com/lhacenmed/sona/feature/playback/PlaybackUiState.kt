package com.lhacenmed.sona.feature.playback

import com.lhacenmed.sona.core.model.PlaybackParent
import com.lhacenmed.sona.core.model.RepeatMode

/**
 * Snapshot of playback state exposed to the UI layer.
 */
data class PlaybackUiState(
    /**
     * Whether the session has connected and any saved queue has been put back, so a null
     * [currentTrackId] means nothing is loaded rather than that it is not known yet.
     */
    val isReady: Boolean = false,
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
    /** How fast it plays, 1 being as recorded. */
    val playbackSpeed: Float = 1f,
    /** Whether the player itself is silenced - the device's volume left as it is. */
    val isMuted: Boolean = false,
    /** The current video's picture as it is shown, its pixels' own shape applied - 0 by 0 for music, or before it is known. */
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    /**
     * The track whose picture is on the surface - set once its first frame is drawn, and cleared as the next track
     * starts - so a screen shows a picture only once it is there, at its own size.
     */
    val pictureTrackId: Long? = null,
)
