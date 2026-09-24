package com.lhacenmed.sona.feature.playback

import android.os.Bundle
import androidx.media3.session.SessionCommand

/**
 * Custom session commands beyond media3's automatically-derived play/pause/skip.
 *
 * The toggle commands follow ArchiveTune's `MediaSessionConstants`: a notification button carries a
 * command, pressing it arrives at the service's `onCustomCommand`, and the button's own appearance
 * is never touched there - it follows from the state the press changed.
 */
internal object PlaybackSessionCommands {
    const val ACTION_TOGGLE_FAVORITE = "TOGGLE_FAVORITE"
    const val ACTION_TOGGLE_SHUFFLE = "TOGGLE_SHUFFLE"
    const val ACTION_TOGGLE_REPEAT_MODE = "TOGGLE_REPEAT_MODE"
    const val ACTION_PLAY_NEXT = "PLAY_NEXT"
    const val ACTION_ADD_TO_QUEUE = "ADD_TO_QUEUE"
    const val ACTION_RESTORE_SHUFFLE_ORDER = "RESTORE_SHUFFLE_ORDER"

    /** The ids of the tracks [ACTION_PLAY_NEXT] and [ACTION_ADD_TO_QUEUE] act on, as a `LongArray`. */
    const val EXTRA_TRACK_IDS = "TRACK_IDS"

    /** The saved shuffle order [ACTION_RESTORE_SHUFFLE_ORDER] restores, as an `IntArray`. */
    const val EXTRA_SHUFFLE_ORDER = "SHUFFLE_ORDER"

    val toggleFavoriteCommand = SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY)
    val toggleShuffleCommand = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
    val toggleRepeatModeCommand = SessionCommand(ACTION_TOGGLE_REPEAT_MODE, Bundle.EMPTY)
    val playNextCommand = SessionCommand(ACTION_PLAY_NEXT, Bundle.EMPTY)
    val addToQueueCommand = SessionCommand(ACTION_ADD_TO_QUEUE, Bundle.EMPTY)
    val restoreShuffleOrderCommand = SessionCommand(ACTION_RESTORE_SHUFFLE_ORDER, Bundle.EMPTY)
}
