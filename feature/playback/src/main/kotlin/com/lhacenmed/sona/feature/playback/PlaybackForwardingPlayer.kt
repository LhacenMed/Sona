package com.lhacenmed.sona.feature.playback

import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

/** Where a press in the app moves the queue - see [PlaybackForwardingPlayer.skip]. */
internal enum class TrackSkip {
    NEXT,

    /** The previous track - or, where the user rewinds before skipping back, the start of this one first. */
    PREVIOUS,

    /** The previous track, never rewinding first - what a swipe means. */
    PREVIOUS_TRACK,
}

/**
 * Wraps the real [androidx.media3.exoplayer.ExoPlayer] and is the [Player] actually handed to the
 * [androidx.media3.session.MediaSession]. Intercepting `seekToPrevious`/`seekToNext` at this layer
 * (rather than only inside [PlaybackController]'s own transport methods) means the behavior below
 * applies no matter which controller issues the command - our own UI, the system media
 * notification's transport buttons, the lock screen, etc.
 *
 * Ported from Auxio's `ExoPlaybackStateHolder.next()`/`prev()`:
 * - rewind-before-skip-back off: skip-back always jumps to the literal previous queue item
 *   (or seeks to 0 if there is none) instead of using media3's stock rewind-vs-previous threshold.
 * - remember-pause off (the default): resume playback after any track-change action.
 *
 * The app's own players skip through [skip] instead, which always moves.
 */
internal class PlaybackForwardingPlayer(
    player: Player,
    private val settings: () -> Snapshot,
) : ForwardingPlayer(player) {

    data class Snapshot(val rememberPause: Boolean, val rewindBeforeSkipBack: Boolean)

    override fun seekToPrevious() {
        val snapshot = settings()
        if (snapshot.rewindBeforeSkipBack) {
            super.seekToPrevious()
        } else if (hasPreviousMediaItem()) {
            super.seekToPreviousMediaItem()
        } else {
            seekTo(0L)
        }
        resumeUnlessPauseRemembered()
    }

    override fun seekToPreviousMediaItem() {
        super.seekToPreviousMediaItem()
        resumeUnlessPauseRemembered()
    }

    override fun seekToNext() {
        super.seekToNext()
        resumeUnlessPauseRemembered()
    }

    override fun seekToNextMediaItem() {
        super.seekToNextMediaItem()
        resumeUnlessPauseRemembered()
    }

    /**
     * Moves as a press in the app asks: to the track before or after this one in the order the queue plays - past
     * either end, round to the other - whatever the repeat mode, which only says what follows a track that ends by
     * itself. Hidden from the system's controls while a track repeats, next is still the user's to press here.
     */
    fun skip(skip: TrackSkip) {
        val timeline = currentTimeline
        if (timeline.isEmpty) return
        val rewinds = skip == TrackSkip.PREVIOUS && settings().rewindBeforeSkipBack && currentPosition > maxSeekToPreviousPosition
        if (rewinds) {
            seekTo(0L)
        } else {
            val index = if (skip == TrackSkip.NEXT) {
                timeline.getNextWindowIndex(currentMediaItemIndex, Player.REPEAT_MODE_ALL, shuffleModeEnabled)
            } else {
                timeline.getPreviousWindowIndex(currentMediaItemIndex, Player.REPEAT_MODE_ALL, shuffleModeEnabled)
            }
            if (index == C.INDEX_UNSET) return
            seekToDefaultPosition(index)
        }
        resumeUnlessPauseRemembered()
    }

    private fun resumeUnlessPauseRemembered() {
        if (!settings().rememberPause) play()
    }

    // Repeating the current track (RepeatMode.ONE and STOP_AFTER_CURRENT both map to
    // Player.REPEAT_MODE_ONE) makes "next" as pointless as it already is on the last queue item, so
    // it is hidden the same way: the notification, lock screen, Android Auto, and headset buttons
    // all read this, and all already know how to free the slot/button when a next track is
    // unavailable - no separate notification-layout logic needed.
    override fun getAvailableCommands(): Player.Commands {
        val commands = super.getAvailableCommands()
        if (repeatMode != Player.REPEAT_MODE_ONE) return commands
        return commands.buildUpon()
            .remove(Player.COMMAND_SEEK_TO_NEXT)
            .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .build()
    }
}
