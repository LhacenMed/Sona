package com.lhacenmed.sona.feature.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.lhacenmed.sona.core.database.dao.PlayStatsDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * How much of a track has to be listened to before the listen counts towards Most played: four fifths of it.
 */
private const val CountedListenFraction = 0.8

/**
 * Keeps the play statistics Recent and Most played are made of - Fossify's `PlayHistoryRecorder`, attached to
 * the service's own [player], so every listen is recorded however playback was started - the app, the
 * notification, a headset, another controller - and whether or not any screen is open.
 *
 * Two things are recorded of each listen, one track playing through once:
 *
 *  - It is made recent the moment it actually starts playing - never merely by being put in the queue, as
 *    a queue restored at launch is.
 *  - It counts towards Most played once it has been listened to for [CountedListenFraction] of its length,
 *    and only once. What counts is time actually played: seeking skips what it passes over, a pause stops
 *    the clock without starting it over, and the playback speed is followed - so skimming to the end or
 *    skipping after the opening bars never counts, and a whole listen at any speed does.
 *
 * A track playing again - repeated, or come round in the queue - is a new listen. The queue changing
 * around the track playing is not.
 *
 * Runs on the player's thread, the main one, as its listener callbacks do.
 */
internal class PlayHistoryRecorder(
    private val player: Player,
    private val scope: CoroutineScope,
    private val playStatsDao: PlayStatsDao,
) : Player.Listener {

    /** The track of the listen under way, or null with nothing loaded. */
    private var trackId: Long? = null

    /** The time played in the segments already over - those a pause or a seek ended. */
    private var listenedMs = 0L

    /** Where the segment playing now began, or [C.TIME_UNSET] while nothing plays. */
    private var segmentStartMs = C.TIME_UNSET

    private var isRecent = false
    private var isCounted = false
    private var countJob: Job? = null

    fun attach() {
        player.addListener(this)
        startListen(player.currentMediaItem)
        onIsPlayingChanged(player.isPlaying)
    }

    fun detach() {
        player.removeListener(this)
        countJob?.cancel()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // A queue rebuilt or edited around the track playing leaves its listen as it was.
        val isSameTrack = mediaItem?.trackId() == trackId
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && isSameTrack) return
        startListen(mediaItem)
        if (player.isPlaying) startSegment()
        scheduleCount()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) startSegment() else endSegment(player.currentPosition)
        scheduleCount()
    }

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        if (reason != Player.DISCONTINUITY_REASON_SEEK && reason != Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) return
        // A seek to another track is that track's new listen - see onMediaItemTransition.
        if (oldPosition.mediaItemIndex != newPosition.mediaItemIndex) return
        // What was played up to the seek counts; what the seek passed over does not.
        if (segmentStartMs == C.TIME_UNSET) return
        endSegment(oldPosition.positionMs)
        segmentStartMs = newPosition.positionMs
        scheduleCount()
    }

    // The speed decides how soon the rest is played; the length becomes known once the track is prepared.
    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) = scheduleCount()

    override fun onTimelineChanged(timeline: Timeline, reason: Int) = scheduleCount()

    private fun startListen(mediaItem: MediaItem?) {
        countJob?.cancel()
        trackId = mediaItem?.trackId()
        listenedMs = 0L
        segmentStartMs = C.TIME_UNSET
        isRecent = false
        isCounted = false
    }

    private fun startSegment() {
        val id = trackId ?: return
        segmentStartMs = player.currentPosition
        if (!isRecent) {
            isRecent = true
            val playedAt = System.currentTimeMillis()
            scope.launch(Dispatchers.IO) { playStatsDao.recordPlayStarted(id, playedAt) }
        }
    }

    private fun endSegment(atMs: Long) {
        if (segmentStartMs == C.TIME_UNSET) return
        listenedMs += (atMs - segmentStartMs).coerceAtLeast(0L)
        segmentStartMs = C.TIME_UNSET
    }

    /** The time played of this listen so far, the segment playing now included. */
    private fun listened(): Long =
        listenedMs + if (segmentStartMs == C.TIME_UNSET) 0L else (player.currentPosition - segmentStartMs).coerceAtLeast(0L)

    /**
     * Arms the count for when the rest of [CountedListenFraction] will have been played at the current speed,
     * or disarms it while nothing plays. It looks again as it fires rather than trusting the arithmetic, so
     * nothing it missed can count a listen short of the mark.
     */
    private fun scheduleCount() {
        countJob?.cancel()
        val id = trackId ?: return
        if (isCounted || !player.isPlaying) return
        val durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
        val thresholdMs = (durationMs * CountedListenFraction).toLong()
        val remainingMs = thresholdMs - listened()
        if (remainingMs <= 0L) {
            count(id)
            return
        }
        val speed = player.playbackParameters.speed.takeIf { it > 0f } ?: 1f
        countJob = scope.launch {
            delay((remainingMs / speed).toLong() + 1)
            scheduleCount()
        }
    }

    private fun count(id: Long) {
        isCounted = true
        val playedAt = System.currentTimeMillis()
        scope.launch(Dispatchers.IO) { playStatsDao.recordPlayCounted(id, playedAt) }
    }

    private fun MediaItem.trackId(): Long? = mediaId.toLongOrNull()
}
