package com.lhacenmed.sona.feature.playback

import android.os.Bundle
import androidx.media3.session.SessionCommand

/**
 * Custom session commands beyond media3's automatically-derived play/pause/skip - ported from
 * Fossify's `CustomCommands` enum. Sona only needs the notification's "Close" action.
 */
internal object PlaybackSessionCommands {
    const val ACTION_CLOSE = "com.lhacenmed.sona.playback.CLOSE"

    val closeSessionCommand = SessionCommand(ACTION_CLOSE, Bundle.EMPTY)
}
