package com.lhacenmed.sona.feature.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.lhacenmed.sona.core.database.dao.ResumePositionDao
import com.lhacenmed.sona.core.database.entity.ResumePositionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How far into a video it has to be left to be resumed - less, and it starts over. */
private const val MinimumResumeMs = 5_000L

/** How close to its end a video can be left and still count as watched, so it starts over next time. */
private const val WatchedMarginMs = 10_000L

/**
 * Has every video carry on from where it was left - PLAYit's Resume - attached to the service's own [player],
 * so it holds however the video was played and left: the video player, the music player, the notification.
 *
 * A video is written down as it is left - paused, skipped, replaced or stopped - and forgotten once watched to
 * its end. As one starts from its beginning, it is moved straight to where it was left. Music is never touched.
 *
 * The positions are read once and then kept in memory, this being their only writer, so a video is moved the
 * moment it starts rather than after a read. Writes go to [writeScope], which outlives the service, so the
 * position written as the service stops is not lost with it.
 *
 * Runs on the player's thread, the main one, as its listener callbacks do.
 */
internal class ResumePositionRecorder(
    private val player: Player,
    private val writeScope: CoroutineScope,
    private val resumePositionDao: ResumePositionDao,
    private val isEnabled: () -> Boolean,
    private val durationMsOf: (trackId: Long) -> Long?,
) : Player.Listener {

    private val positionsMs = HashMap<Long, Long>()

    /** Whether [positionsMs] holds what was stored - until then, one missing from it may still be stored. */
    private var isLoaded = false

    fun attach() {
        writeScope.launch(Dispatchers.Main) {
            val stored = withContext(Dispatchers.IO) { resumePositionDao.getAll() }
            // Any written meanwhile is newer than what was read.
            stored.forEach { positionsMs.putIfAbsent(it.trackId, it.positionMs) }
            isLoaded = true
        }
        player.addListener(this)
    }

    fun detach() {
        player.currentMediaItem?.let { record(it, player.currentPosition) }
        player.removeListener(this)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (mediaItem == null || !mediaItem.isVideo || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) return
        if (!isEnabled() || player.currentPosition >= MinimumResumeMs) return
        val trackId = mediaItem.mediaId.toLongOrNull() ?: return
        positionsMs[trackId]?.let(player::seekTo)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (!isPlaying) player.currentMediaItem?.let { record(it, player.currentPosition) }
    }

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        val left = oldPosition.mediaItem ?: return
        val isLeft = reason == Player.DISCONTINUITY_REASON_REMOVE || oldPosition.mediaItemIndex != newPosition.mediaItemIndex
        if (!isLeft) return
        if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
            left.mediaId.toLongOrNull()?.let(::forget)
        } else {
            record(left, oldPosition.positionMs)
        }
    }

    /** Writes down where the video of [mediaItem] was left - or forgets it, barely begun or as good as watched. */
    private fun record(mediaItem: MediaItem, positionMs: Long) {
        if (!mediaItem.isVideo || !isEnabled()) return
        val trackId = mediaItem.mediaId.toLongOrNull() ?: return
        val durationMs = durationMsOf(trackId)?.takeIf { it > 0L }
        val isWatched = durationMs != null && durationMs - positionMs < WatchedMarginMs
        if (positionMs < MinimumResumeMs || isWatched) {
            forget(trackId)
            return
        }
        if (positionsMs.put(trackId, positionMs) == positionMs) return
        writeScope.launch(Dispatchers.IO) { resumePositionDao.upsert(ResumePositionEntity(trackId, positionMs)) }
    }

    private fun forget(trackId: Long) {
        if (positionsMs.remove(trackId) == null && isLoaded) return
        writeScope.launch(Dispatchers.IO) { resumePositionDao.delete(trackId) }
    }
}
