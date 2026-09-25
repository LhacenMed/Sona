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
    const val ACTION_MOVE_QUEUE_ITEM = "MOVE_QUEUE_ITEM"
    const val ACTION_RESTORE_QUEUE_ITEM = "RESTORE_QUEUE_ITEM"
    const val ACTION_REMOVE_QUEUE_ITEM = "REMOVE_QUEUE_ITEM"
    const val ACTION_REMOVE_TRACKS = "REMOVE_TRACKS"

    /** The ids of the tracks [ACTION_PLAY_NEXT], [ACTION_ADD_TO_QUEUE] and [ACTION_REMOVE_TRACKS] act on, as a `LongArray`. */
    const val EXTRA_TRACK_IDS = "TRACK_IDS"

    /** The saved shuffle order [ACTION_RESTORE_SHUFFLE_ORDER] restores, as an `IntArray`. */
    const val EXTRA_SHUFFLE_ORDER = "SHUFFLE_ORDER"

    /** Where [ACTION_MOVE_QUEUE_ITEM] moves a track from and to, as `Int`s: places in the order the queue plays. */
    const val EXTRA_FROM_POSITION = "FROM_POSITION"
    const val EXTRA_TO_POSITION = "TO_POSITION"

    /**
     * The track [ACTION_RESTORE_QUEUE_ITEM] puts back, as a `Long`, and where it was, as `Int`s: its index
     * in the player's own order, and its place in the order the queue plays. [ACTION_REMOVE_QUEUE_ITEM]
     * takes out the item at that index.
     */
    const val EXTRA_TRACK_ID = "TRACK_ID"
    const val EXTRA_MEDIA_ITEM_INDEX = "MEDIA_ITEM_INDEX"
    const val EXTRA_PLAY_POSITION = "PLAY_POSITION"

    val toggleFavoriteCommand = SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY)
    val toggleShuffleCommand = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
    val toggleRepeatModeCommand = SessionCommand(ACTION_TOGGLE_REPEAT_MODE, Bundle.EMPTY)
    val playNextCommand = SessionCommand(ACTION_PLAY_NEXT, Bundle.EMPTY)
    val addToQueueCommand = SessionCommand(ACTION_ADD_TO_QUEUE, Bundle.EMPTY)
    val restoreShuffleOrderCommand = SessionCommand(ACTION_RESTORE_SHUFFLE_ORDER, Bundle.EMPTY)
    val moveQueueItemCommand = SessionCommand(ACTION_MOVE_QUEUE_ITEM, Bundle.EMPTY)
    val restoreQueueItemCommand = SessionCommand(ACTION_RESTORE_QUEUE_ITEM, Bundle.EMPTY)
    val removeQueueItemCommand = SessionCommand(ACTION_REMOVE_QUEUE_ITEM, Bundle.EMPTY)
    val removeTracksCommand = SessionCommand(ACTION_REMOVE_TRACKS, Bundle.EMPTY)
}
