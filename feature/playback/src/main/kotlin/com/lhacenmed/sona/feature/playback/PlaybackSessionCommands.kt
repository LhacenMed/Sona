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

    val toggleFavoriteCommand = SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY)
    val toggleShuffleCommand = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
    val toggleRepeatModeCommand = SessionCommand(ACTION_TOGGLE_REPEAT_MODE, Bundle.EMPTY)
}
