package com.lhacenmed.sona.feature.playback

import android.content.Context
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaButtonReceiver
import com.lhacenmed.sona.core.datastore.PlaybackSettings
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Where a press of play from a headset, earbuds or a car reaches Sona while it is closed: media3's own receiver,
 * starting [PlaybackService] to play the saved queue on - see its `onPlaybackResumption` - only while
 * [PlaybackSettings.persistedPlaybackNotification] is on. While Sona runs, presses go to its session itself and never
 * come here.
 */
@UnstableApi
class PlaybackButtonReceiver : MediaButtonReceiver() {
    override fun shouldStartForegroundService(context: Context, intent: Intent): Boolean =
        EntryPointAccessors.fromApplication<PlaybackSettingsEntryPoint>(context).playbackSettings().persistedPlaybackNotification.value
}

/** The receiver is made by the system, not Hilt, so it reaches the stored settings through here. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlaybackSettingsEntryPoint {
    fun playbackSettings(): PlaybackSettings
}
