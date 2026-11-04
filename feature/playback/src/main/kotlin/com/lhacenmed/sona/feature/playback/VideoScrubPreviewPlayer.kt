package com.lhacenmed.sona.feature.playback

import android.content.Context
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.ScrubbingModeParameters

/** A scrub lands on a keyframe within this share of the video: a pixel of the seek bar already covers more. */
private const val ScrubTolerance = 0.005

/** How much of the file it holds: enough for a frame or two, whatever the bitrate. */
private const val PreviewBufferBytes = 8 * 1024 * 1024

/**
 * The frames of a video at wherever a scrub has reached, on a player and a decoder of their own - so the video being watched
 * plays on untouched until the scrub lets go. Silent, paused and seeking as fast as it can, and holding nothing between scrubs.
 *
 * A device with no second decoder to give it simply shows no frames; the video being watched is none the wiser.
 */
@OptIn(UnstableApi::class)
internal class VideoScrubPreviewPlayer(private val context: Context, private val trackFiles: TrackFiles) {
    private val player: ExoPlayer by lazy(LazyThreadSafetyMode.NONE, ::buildPlayer)
    private var shownOn: TextureView? = null

    fun start(item: MediaItem, view: TextureView, positionMs: Long) {
        shownOn = view
        player.setVideoTextureView(view)
        player.setMediaItem(item, positionMs)
        player.prepare()
    }

    fun moveTo(positionMs: Long) {
        if (shownOn != null) player.seekTo(positionMs)
    }

    fun stop(view: TextureView) {
        if (shownOn !== view) return
        shownOn = null
        player.clearVideoTextureView(view)
        player.stop()
        player.clearMediaItems()
    }

    private fun buildPlayer(): ExoPlayer =
        ExoPlayer.Builder(context, sonaRenderersFactory(context))
            .setMediaSourceFactory(videoMediaSourceFactory(context, trackFiles))
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        /* minBufferMs = */ 500,
                        /* maxBufferMs = */ 1_000,
                        /* bufferForPlaybackMs = */ 0,
                        /* bufferForPlaybackAfterRebufferMs = */ 0,
                    )
                    .setTargetBufferBytes(PreviewBufferBytes)
                    .build(),
            )
            .setScrubbingModeParameters(ScrubbingModeParameters.Builder().setFractionalSeekTolerance(ScrubTolerance, ScrubTolerance).build())
            .build()
            .apply {
                playWhenReady = false
                isScrubbingModeEnabled = true
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
            }
}
