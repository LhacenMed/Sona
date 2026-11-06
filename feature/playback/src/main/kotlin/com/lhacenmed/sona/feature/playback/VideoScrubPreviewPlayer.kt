package com.lhacenmed.sona.feature.playback

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.ScrubbingModeParameters
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** A scrub lands on a keyframe within this share of the video: a pixel of the seek bar already covers more. */
private const val ScrubTolerance = 0.005

/** How much of the file it holds: enough for a frame or two, whatever the bitrate. */
private const val PreviewBufferBytes = 8 * 1024 * 1024

/**
 * The frames of a video at wherever a scrub has reached, on a player and a decoder of their own - so the video being watched
 * plays on untouched until the scrub lets go. Silent, paused and seeking as fast as it can.
 *
 * Everything it does happens on a thread of its own, which the screen only hands work to: reading a long film's file, seeking
 * it, taking up and letting go of the picture's surface - each can take a moment the screen never waits for. The file is read
 * once, at the first scrub of a video, and kept until the video closes, so every scrub after it is only a seek; the decoder
 * is held only while a preview is on screen, leaving the video being watched every one the device has to spare between.
 *
 * A device with no second decoder to give it simply shows no frames; the video being watched is none the wiser.
 */
@OptIn(UnstableApi::class)
internal class VideoScrubPreviewPlayer(private val context: Context, private val trackFiles: TrackFiles) {
    private val handler: Handler by lazy(LazyThreadSafetyMode.NONE) {
        Handler(HandlerThread("VideoScrubPreview").apply { start() }.looper)
    }

    // Built, and only ever touched, on the preview's thread - with the file it has read and the surface it draws to.
    private val player: ExoPlayer by lazy(LazyThreadSafetyMode.NONE, ::buildPlayer)
    private var loadedMediaId: String? = null
    private var shownSurface: Surface? = null

    // Where the latest scrub has reached, and whether a seek to it is already on its way: however fast the finger moves,
    // the thread is never more than one seek behind it.
    private val latestTargetMs = AtomicLong()
    private val isSeekQueued = AtomicBoolean(false)

    /** Shows on [view] the frames of [item] the scrub reaches, starting at [positionMs], for as long as [view] is on screen. */
    fun start(item: MediaItem, view: TextureView, positionMs: Long) {
        latestTargetMs.set(positionMs)
        val previewSurface = PreviewSurface()
        view.surfaceTextureListener = previewSurface
        view.surfaceTexture?.let(previewSurface::attach)
        handler.post {
            // Read already, it only seeks - unless it stopped on an error, when it reads the file afresh.
            if (loadedMediaId == item.mediaId && player.playbackState != Player.STATE_IDLE) {
                player.seekTo(latestTargetMs.get())
            } else {
                loadedMediaId = item.mediaId
                player.setMediaItem(item, latestTargetMs.get())
                player.prepare()
            }
        }
    }

    fun moveTo(positionMs: Long) {
        latestTargetMs.set(positionMs)
        if (!isSeekQueued.compareAndSet(false, true)) return
        handler.post {
            isSeekQueued.set(false)
            if (loadedMediaId != null) player.seekTo(latestTargetMs.get())
        }
    }

    /** Lets go of the video's file, once the video is closed. */
    fun clear() {
        handler.post {
            if (loadedMediaId == null) return@post
            loadedMediaId = null
            player.stop()
            player.clearMediaItems()
        }
    }

    /**
     * A preview's surface, given to the player as its texture arrives and taken back on the preview's thread as it goes - the
     * texture released only once the player has let go of it, so nothing the screen does waits on the decoder.
     */
    private inner class PreviewSurface : TextureView.SurfaceTextureListener {
        private var surface: Surface? = null

        fun attach(texture: SurfaceTexture) {
            if (surface != null) return
            val shown = Surface(texture).also { surface = it }
            handler.post {
                shownSurface = shown
                player.setVideoSurface(shown)
                setDecoding(true)
            }
        }

        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) = attach(texture)

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            val shown = surface
            surface = null
            handler.post {
                // A preview that came up before this one went keeps the surface, and the decoder, it already has.
                if (shown != null && shown === shownSurface) {
                    shownSurface = null
                    player.clearVideoSurface(shown)
                    setDecoding(false)
                }
                shown?.release()
                texture.release()
            }
            // Released above, on the preview's thread, once the player no longer draws to it.
            return false
        }
    }

    /** Takes up the decoder while a preview shows, and gives it back once none does - the file staying read either way. */
    private fun setDecoding(isDecoding: Boolean) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !isDecoding)
            .build()
    }

    private fun buildPlayer(): ExoPlayer =
        ExoPlayer.Builder(context, sonaRenderersFactory(context))
            .setLooper(handler.looper)
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
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                    .build()
            }
}
