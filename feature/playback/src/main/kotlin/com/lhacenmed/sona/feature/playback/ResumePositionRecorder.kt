package com.lhacenmed.sona.feature.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.lhacenmed.sona.core.database.dao.ResumePositionDao
import com.lhacenmed.sona.core.database.entity.ResumePositionEntity
import com.lhacenmed.sona.core.vault.VaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How far into a video it has to be left to be resumed - less, and it starts over. */
private const val MinimumResumeMs = 5_000L

/** How close to its end a video can be left and still count as watched, so it starts over next time. */
private const val WatchedMarginMs = 10_000L

/** Where one [PlaybackSpace]'s resume positions are kept, by the id each item has in it. */
internal interface ResumePositionStore {
    suspend fun getAll(): Map<Long, Long>
    suspend fun save(id: Long, positionMs: Long)
    suspend fun forget(id: Long)
}

/** The library's videos' positions, in its own database. */
internal fun ResumePositionDao.asResumePositionStore() = object : ResumePositionStore {
    override suspend fun getAll() = this@asResumePositionStore.getAll().associate { it.trackId to it.positionMs }
    override suspend fun save(id: Long, positionMs: Long) = upsert(ResumePositionEntity(id, positionMs))
    override suspend fun forget(id: Long) = delete(id)
}

/** The Private Folder's videos' positions, kept in the vault with the rest of it. */
internal fun VaultRepository.asResumePositionStore() = object : ResumePositionStore {
    override suspend fun getAll() = resumePositions()
    override suspend fun save(id: Long, positionMs: Long) = saveResumePosition(id, positionMs)
    override suspend fun forget(id: Long) = forgetResumePosition(id)
}

/**
 * Has every video of [space] carry on from where it was left - PLAYit's Resume - attached to the service's
 * own [player], so it holds however the video was played and left: the video player, the music player, the
 * notification. One is attached per space, each keeping its positions in its own [store].
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
    private val space: PlaybackSpace,
    private val writeScope: CoroutineScope,
    private val store: ResumePositionStore,
    private val isEnabled: () -> Boolean,
) : Player.Listener {

    private val positionsMs = HashMap<Long, Long>()

    /** Whether [positionsMs] holds what was stored - until then, one missing from it may still be stored. */
    private var isLoaded = false

    fun attach() {
        writeScope.launch(Dispatchers.Main) {
            val stored = withContext(Dispatchers.IO) { store.getAll() }
            // Any written meanwhile is newer than what was read.
            stored.forEach { (id, positionMs) -> positionsMs.putIfAbsent(id, positionMs) }
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
        val id = mediaItem.ownId() ?: return
        positionsMs[id]?.let(player::seekTo)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (!isPlaying) player.currentMediaItem?.let { record(it, player.currentPosition) }
    }

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        val left = oldPosition.mediaItem ?: return
        val isLeft = reason == Player.DISCONTINUITY_REASON_REMOVE || oldPosition.mediaItemIndex != newPosition.mediaItemIndex
        if (!isLeft) return
        if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
            left.ownId()?.let(::forget)
        } else {
            record(left, oldPosition.positionMs)
        }
    }

    /** [MediaItem]'s id in [space] - null for one of the other space's, which its own recorder keeps. */
    private fun MediaItem.ownId(): Long? = idInSpace.takeIf { this.space == this@ResumePositionRecorder.space }

    /** Writes down where the video of [mediaItem] was left - or forgets it, barely begun or as good as watched. */
    private fun record(mediaItem: MediaItem, positionMs: Long) {
        if (!mediaItem.isVideo || !isEnabled()) return
        val id = mediaItem.ownId() ?: return
        val durationMs = mediaItem.mediaMetadata.durationMs?.takeIf { it > 0L }
        val isWatched = durationMs != null && durationMs - positionMs < WatchedMarginMs
        if (positionMs < MinimumResumeMs || isWatched) {
            forget(id)
            return
        }
        if (positionsMs.put(id, positionMs) == positionMs) return
        writeScope.launch(Dispatchers.IO) { store.save(id, positionMs) }
    }

    private fun forget(id: Long) {
        if (positionsMs.remove(id) == null && isLoaded) return
        writeScope.launch(Dispatchers.IO) { store.forget(id) }
    }
}
