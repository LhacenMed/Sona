package com.lhacenmed.sona.feature.playback

import com.lhacenmed.sona.core.model.PlaybackParent
import com.lhacenmed.sona.core.model.RepeatMode

/**
 * Which part of the app the queue belongs to - each has its own players, and shows nothing of the other's.
 * A queue is all of one or all of the other.
 */
enum class PlaybackSpace { Library, Private }

/**
 * Snapshot of playback state exposed to the UI layer.
 *
 * Every track id here - [currentTrackId], [queue]'s - is an id in [space]: a library
 * track's, or a Private Folder item's. What only knows the library reads [libraryTrackId].
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
    val space: PlaybackSpace = PlaybackSpace.Library,
) {
    /** [currentTrackId] where it is the library's - null while the Private Folder plays. */
    val libraryTrackId: Long? get() = currentTrackId.takeIf { space == PlaybackSpace.Library }

    /**
     * Whether a tap on the notification or the widget opens the full player - a library track. Nothing of the Private
     * Folder is named outside it: it is brought back as left.
     */
    val opensNowPlaying: Boolean get() = libraryTrackId != null
}
