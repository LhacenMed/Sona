package com.lhacenmed.sona.feature.playback

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Foreground [MediaSessionService] that owns the [ExoPlayer] instance and its [MediaSession].
 *
 * Hosting the player here (rather than in the UI process) gives us, for free, a system media
 * notification, lock screen transport controls, and headset button handling via
 * `androidx.media3.session` - none of that is hand-built.
 */
class PlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val exoPlayer = ExoPlayer.Builder(this).build()
        player = exoPlayer
        mediaSession = MediaSession.Builder(this, exoPlayer).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession =
        checkNotNull(mediaSession) { "MediaSession requested before onCreate() ran" }

    override fun onDestroy() {
        mediaSession?.release()
        player?.release()
        mediaSession = null
        player = null
        super.onDestroy()
    }
}
