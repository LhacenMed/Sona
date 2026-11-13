package com.lhacenmed.sona.feature.playback

import android.os.Handler
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.database.dao.ResumePositionDao
import com.lhacenmed.sona.core.database.entity.ResumePositionEntity
import com.lhacenmed.sona.core.datastore.VideoSettings
import com.lhacenmed.sona.core.downloader.LiveDownloads
import com.lhacenmed.sona.core.vault.VaultRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How far into a video it has to be left to be resumed - less, and it starts over. */
private const val MinimumResumeMs = 5_000L

/** How close to its end a video can be left and still count as watched, so it starts over next time. */
private const val WatchedMarginMs = 10_000L

/** How often a video playing has where it is written down - so the app closing, or stopped, loses no more of it. */
private const val CheckpointMs = 10_000L

/** Where one [PlaybackSpace]'s resume positions are kept, by the id each item has in it. */
internal interface ResumePositionStore {
    suspend fun getAll(): Map<Long, Long>
    suspend fun save(id: Long, positionMs: Long)
    suspend fun forget(id: Long)
}

/** The library's videos' positions, in its own database. */
private fun ResumePositionDao.asResumePositionStore() = object : ResumePositionStore {
    override suspend fun getAll() = this@asResumePositionStore.getAll().associate { it.trackId to it.positionMs }
    override suspend fun save(id: Long, positionMs: Long) = upsert(ResumePositionEntity(id, positionMs))
    override suspend fun forget(id: Long) = delete(id)
}

/** The Private Folder's videos' positions, kept in the vault with the rest of it. */
private fun VaultRepository.asResumePositionStore() = object : ResumePositionStore {
    override suspend fun getAll() = resumePositions()
    override suspend fun save(id: Long, positionMs: Long) = saveResumePosition(id, positionMs)
    override suspend fun forget(id: Long) = forgetResumePosition(id)
}

/** Positions held in memory alone - those of videos played as they download, until each is saved into the library. */
private object HeldInMemory : ResumePositionStore {
    override suspend fun getAll() = emptyMap<Long, Long>()
    override suspend fun save(id: Long, positionMs: Long) = Unit
    override suspend fun forget(id: Long) = Unit
}

/** One space's positions - read once and then kept in memory, this being their only writer. */
private class ResumeBook(private val store: ResumePositionStore, private val writeScope: CoroutineScope) {
    private val positionsMs = HashMap<Long, Long>()

    /** Whether [positionsMs] holds what was stored - until then, one missing from it may still be stored. */
    private var isLoaded = false

    init {
        writeScope.launch(Dispatchers.Main) {
            val stored = withContext(Dispatchers.IO) { store.getAll() }
            // Any written meanwhile is newer than what was read.
            stored.forEach { (id, positionMs) -> positionsMs.putIfAbsent(id, positionMs) }
            isLoaded = true
        }
    }

    operator fun get(id: Long): Long? = positionsMs[id]

    fun save(id: Long, positionMs: Long) {
        if (positionsMs.put(id, positionMs) == positionMs) return
        writeScope.launch(Dispatchers.IO) { store.save(id, positionMs) }
    }

    fun forget(id: Long) {
        if (positionsMs.remove(id) == null && isLoaded) return
        writeScope.launch(Dispatchers.IO) { store.forget(id) }
    }
}

/**
 * Where every video was left - PLAYit's Resume - the one record for every player that plays one. Read at once rather than
 * after a look in the database, so a video can start where it was left rather than be moved there.
 *
 * A video played as it downloads is kept by its download until it is saved into the library, then as the track it became -
 * so it is found again however it is next played.
 *
 * Used on the main thread, as the players' callbacks are. Writes go to the application's scope, so the position written
 * as a player goes is not lost with it.
 */
@Singleton
class ResumePositions @Inject constructor(
    resumePositionDao: ResumePositionDao,
    vault: VaultRepository,
    private val videoSettings: VideoSettings,
    private val liveDownloads: LiveDownloads,
    private val library: LibraryRepository,
    @ApplicationScope writeScope: CoroutineScope,
) {
    private val books = mapOf(
        PlaybackSpace.Library to ResumeBook(resumePositionDao.asResumePositionStore(), writeScope),
        PlaybackSpace.Private to ResumeBook(vault.asResumePositionStore(), writeScope),
    )

    private val libraryBook = books.getValue(PlaybackSpace.Library)

    /** The videos played as they download, by their downloads' ids. */
    private val downloadingBook = ResumeBook(HeldInMemory, writeScope)

    // After every property, which what it launches reads.
    init {
        // A download left part-watched is saved into the library: where it was left goes with it.
        writeScope.launch(Dispatchers.Main) {
            combine(liveDownloads.downloads, library.tracksById) { downloads, _ -> downloads.keys }.collect { ids -> ids.forEach(::savedTrackOf) }
        }
    }

    /** Where [this] video's position is kept, and by which id - null where none is kept. */
    private val MediaItem.place: Pair<ResumeBook, Long>?
        get() {
            if (!isVideo || !videoSettings.resume.value) return null
            val downloadId = liveDownloadId ?: return idInSpace?.let { books.getValue(space) to it }
            return savedTrackOf(downloadId)?.let { libraryBook to it } ?: (downloadingBook to downloadId)
        }

    /**
     * The library track [downloadId] saved, once the library has read it - where it was left while downloading moved to it, as
     * its own from then.
     */
    private fun savedTrackOf(downloadId: Long): Long? {
        val path = liveDownloads.downloads.value[downloadId]?.saved?.path ?: return null
        val trackId = library.tracksById.value.values.firstOrNull { it.path == path }?.id ?: return null
        downloadingBook[downloadId]?.let { positionMs ->
            libraryBook.save(trackId, positionMs)
            downloadingBook.forget(downloadId)
        }
        return trackId
    }

    /** Where [item] is to start: where it was left, or from its beginning. */
    internal fun startPositionOf(item: MediaItem): Long {
        val (book, id) = item.place ?: return 0L
        return book[id] ?: 0L
    }

    /** Writes down where the video of [item] was left - or forgets it, barely begun or as good as watched. */
    internal fun record(item: MediaItem, positionMs: Long) {
        val (book, id) = item.place ?: return
        val durationMs = item.mediaMetadata.durationMs?.takeIf { it > 0L }
        val isWatched = durationMs != null && durationMs - positionMs < WatchedMarginMs
        if (positionMs < MinimumResumeMs || isWatched) book.forget(id) else book.save(id, positionMs)
    }

    /** [item] has been watched to its end. */
    internal fun forget(item: MediaItem) {
        val (book, id) = item.place ?: return
        book.forget(id)
    }
}

/**
 * Keeps [positions] as [player] plays, attached to the player itself so it holds however the video was played and left. A
 * video is written down as it plays, and as it is left - paused, skipped, replaced or stopped - and forgotten once watched to
 * its end. As one comes up from its beginning, it is moved straight to where it was left. Music is never touched.
 *
 * Runs on the player's thread, the main one, as its listener callbacks do.
 */
internal class ResumePositionRecorder(
    private val player: Player,
    private val positions: ResumePositions,
) : Player.Listener {
    private val checkpoints = Handler(player.applicationLooper)

    private val checkpoint = object : Runnable {
        override fun run() {
            recordCurrent()
            checkpoints.postDelayed(this, CheckpointMs)
        }
    }

    fun attach() = player.addListener(this)

    fun detach() {
        checkpoints.removeCallbacks(checkpoint)
        recordCurrent()
        player.removeListener(this)
    }

    private fun recordCurrent() {
        player.currentMediaItem?.let { positions.record(it, player.currentPosition) }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (mediaItem == null || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) return
        if (player.currentPosition >= MinimumResumeMs) return
        positions.startPositionOf(mediaItem).takeIf { it > 0L }?.let(player::seekTo)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        checkpoints.removeCallbacks(checkpoint)
        if (isPlaying) checkpoints.postDelayed(checkpoint, CheckpointMs) else recordCurrent()
    }

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        val left = oldPosition.mediaItem ?: return
        val isLeft = reason == Player.DISCONTINUITY_REASON_REMOVE || oldPosition.mediaItemIndex != newPosition.mediaItemIndex
        if (!isLeft) return
        if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) positions.forget(left) else positions.record(left, oldPosition.positionMs)
    }
}
