package com.lhacenmed.sona.feature.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.database.dao.ReplayHeatmapDao
import com.lhacenmed.sona.core.database.entity.ReplayHeatmapEntity
import com.lhacenmed.sona.core.model.ReplayHeatmap
import com.lhacenmed.sona.core.model.ReplayHeatmapStore
import com.lhacenmed.sona.core.vault.VaultRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** How far a video's length may be from the one its heatmap was made for and still be the same file. */
private const val SameLengthToleranceMs = 1_000L

/** One video, by its id in its [PlaybackSpace]. */
internal data class VideoKey(val space: PlaybackSpace, val id: Long)

internal val MediaItem.videoKey: VideoKey? get() = idInSpace?.let { VideoKey(space, it) }

/** The library's videos' heatmaps, in its own database. */
private fun ReplayHeatmapDao.asReplayHeatmapStore() = object : ReplayHeatmapStore {
    override suspend fun read(videoId: Long) = get(videoId)?.let { ReplayHeatmap.fromBytes(it.durationMs, it.playedMs) }
    override suspend fun write(videoId: Long, heatmap: ReplayHeatmap) =
        upsert(ReplayHeatmapEntity(videoId, heatmap.durationMs, heatmap.toBytes()))
}

/**
 * How often each moment of every video has been watched - see [ReplayHeatmap] - the one record the video player draws its
 * Most replayed graph from. Each video's is read once, and kept in memory as it grows, so the graph follows the watching.
 */
@Singleton
class ReplayHeatmaps @Inject constructor(
    replayHeatmapDao: ReplayHeatmapDao,
    vault: VaultRepository,
    @ApplicationScope private val writeScope: CoroutineScope,
) {
    private val stores = mapOf(
        PlaybackSpace.Library to replayHeatmapDao.asReplayHeatmapStore(),
        PlaybackSpace.Private to vault.replayHeatmaps,
    )

    /** Every video's read so far, as it stands. */
    private val heatmaps = MutableStateFlow<Map<VideoKey, ReplayHeatmap>>(emptyMap())

    /** Which have been read - with or without one stored. Only touched holding [lock], as everything read and written is. */
    private val read = HashSet<VideoKey>()
    private val lock = Mutex()

    /** [video]'s heatmap, and each time it grows - null for one with none. */
    internal fun observe(video: VideoKey): Flow<ReplayHeatmap?> = flow {
        lock.withLock { readOnce(video) }
        emitAll(heatmaps.map { it[video] })
    }.distinctUntilChanged()

    /** Adds the stretch of [video], [durationMs] long, from [fromMs] to [toMs] as played once more - and keeps it. */
    internal fun addPlayed(video: VideoKey, durationMs: Long, fromMs: Long, toMs: Long) {
        if (toMs <= fromMs) return
        writeScope.launch {
            lock.withLock {
                // Another length is another file under the same name: its watching starts afresh.
                val stored = readOnce(video)?.takeIf { abs(it.durationMs - durationMs) <= SameLengthToleranceMs }
                val heatmap = (stored ?: ReplayHeatmap.of(durationMs) ?: return@withLock).played(fromMs, toMs)
                heatmaps.update { it + (video to heatmap) }
                withContext(Dispatchers.IO) { stores.getValue(video.space).write(video.id, heatmap) }
            }
        }
    }

    private suspend fun readOnce(video: VideoKey): ReplayHeatmap? {
        if (read.add(video)) {
            withContext(Dispatchers.IO) { stores.getValue(video.space).read(video.id) }
                ?.let { stored -> heatmaps.update { it + (video to stored) } }
        }
        return heatmaps.value[video]
    }
}

/**
 * Adds to [heatmaps] each stretch of a video [player] plays, as it ends - paused, sought away from, left or closed - from
 * where playing began to where it stopped, in the video's own time. Nothing between: a scrub, a seek, a pause add nothing.
 *
 * Runs on the player's thread, the main one, as its listener callbacks do.
 */
internal class ReplayHeatmapRecorder(
    private val player: Player,
    private val heatmaps: ReplayHeatmaps,
) : Player.Listener {

    /** What is playing: the video, how long it is, and where it began playing from. */
    private class Stretch(val mediaId: String, val video: VideoKey, val durationMs: Long, val fromMs: Long)

    private var playing: Stretch? = null

    /**
     * Whether a scrub is on: the video plays on under it, unwatched, the eyes on the preview - so what plays meanwhile adds
     * nothing. Counted otherwise, every scrub after a replay would add the seconds it took just past the replayed moment.
     */
    var isScrubbing = false
        set(value) {
            if (field == value) return
            field = value
            // Begun, what has played until now is added, for the graph it shows to stand as the video does.
            if (value) end(player.currentMediaItem, player.currentPosition) else if (player.isPlaying) begin(player.currentPosition)
        }

    fun attach() = player.addListener(this)

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) begin(player.currentPosition) else end(player.currentMediaItem, player.currentPosition)
    }

    /** A seek, a video come round again, or another taken up: what played ends where it was, and plays on from the new place. */
    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        end(oldPosition.mediaItem, oldPosition.positionMs)
        if (player.isPlaying) begin(newPosition.positionMs)
    }

    private fun begin(fromMs: Long) {
        if (isScrubbing) return
        val item = player.currentMediaItem ?: return
        val video = item.videoKey ?: return
        val durationMs = player.duration.takeUnless { it == C.TIME_UNSET } ?: item.mediaMetadata.durationMs ?: return
        playing = Stretch(item.mediaId, video, durationMs, fromMs)
    }

    /** Adds what played of the video [item] up to [toMs] - nothing should another one be there now. */
    private fun end(item: MediaItem?, toMs: Long) {
        val stretch = playing ?: return
        playing = null
        if (item?.mediaId == stretch.mediaId) heatmaps.addPlayed(stretch.video, stretch.durationMs, stretch.fromMs, toMs)
    }
}
