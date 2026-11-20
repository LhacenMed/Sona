package com.lhacenmed.sona.feature.download

import com.lhacenmed.sona.core.database.dao.DownloadDao
import com.lhacenmed.sona.core.database.entity.DownloadWatchEntity
import com.lhacenmed.sona.core.database.entity.toDomain
import com.lhacenmed.sona.core.datastore.DownloadSettings
import com.lhacenmed.sona.core.downloader.MediaInfo
import com.lhacenmed.sona.core.downloader.MediaResolver
import com.lhacenmed.sona.core.downloader.PlaylistEntry
import com.lhacenmed.sona.core.model.DownloadBatch
import com.lhacenmed.sona.core.model.DownloadWatch
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** How long a YouTube channel's id is - a video's is 11. */
private const val ChannelIdLength = 24

/**
 * The channels and playlists the user watches - YTDLnis's observed sources: each looked at now and then, and every video that
 * has appeared on it since queued as the user downloads. A watch added starts from what it holds then - its latest few
 * downloaded at once, where the user asks for them - and every video that comes after is downloaded.
 */
@Singleton
class WatchChecker @Inject constructor(
    private val dao: DownloadDao,
    private val resolver: MediaResolver,
    private val queue: DownloadQueue,
    private val settings: DownloadSettings,
) {
    val watches: Flow<List<DownloadWatch>> = dao.observeWatches().map { rows -> rows.map(DownloadWatchEntity::toDomain) }

    /**
     * Watches [url], which must lead to a playlist or a channel's videos, downloading its [latest] videos now - none for only
     * what comes after. Fails as reading it does.
     */
    suspend fun add(url: String, latest: Int) {
        val info = read(url)
        require(info.isPlaylist) { "Not a channel or a playlist" }
        val entries = info.entries.orEmpty()
        // The latest are left unknown, so they are queued as new - now, as any new video is.
        val known = entries.drop(latest).map { it.id }.toSet()
        val id = dao.insertWatch(DownloadWatchEntity(url = url, title = info.title, knownIds = known.joinToString("\n"), createdAt = System.currentTimeMillis()))
        queueNew(id, info.title, known, entries)
    }

    suspend fun remove(ids: Collection<Long>) = dao.deleteWatches(ids)

    /** Looks at every watch, queueing what is new on each - one that cannot be read now is looked at again next time. */
    suspend fun checkAll() {
        dao.watches().forEach { watch ->
            try {
                check(watch.toDomain())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Offline, or the channel gone for now: next time.
            }
        }
    }

    private suspend fun check(watch: DownloadWatch) {
        val entries = read(watch.url).entries ?: return
        // A channel watched by its main page was kept as its tabs - its own id, not its videos': it starts again from what it holds.
        if (watch.knownIds.isNotEmpty() && watch.knownIds.all(::isChannelId)) {
            dao.setKnownIds(watch.id, entries.joinToString("\n") { it.id })
            return
        }
        queueNew(watch.id, watch.title, watch.knownIds, entries)
    }

    /** Queues every one of [entries] the watch [id] has not [known], the oldest first, and knows them from then on. */
    private suspend fun queueNew(id: Long, title: String, known: Set<String>, entries: List<PlaylistEntry>) {
        val new = entries.filter { it.id !in known }
        if (new.isEmpty()) return
        val choices = settings.choices.value
        // Listed as one, under the channel's name, with whatever else it has queued.
        val batch = DownloadBatch("$WatchBatchPrefix$id", title)
        queue.add(new.reversed().map { DownloadRequest(it.url, it.title, it.thumbnailUrl, it.uploader, choices, batch = batch) })
        dao.setKnownIds(id, (known + new.map { it.id }).joinToString("\n"))
    }

    /**
     * [url]'s videos, the newest first. A channel's main page read by yt-dlp - through a proxy, or where the app's own read fails -
     * lists its tabs, each by the channel's own id: its first, its Videos, is read in its place.
     */
    private suspend fun read(url: String): MediaInfo {
        val info = readFresh(url)
        val videosTab = info.entries?.takeIf { entries -> entries.isNotEmpty() && entries.all { isChannelId(it.id) } }?.first() ?: return info
        return readFresh(videosTab.url).copy(title = info.title)
    }

    private suspend fun readFresh(url: String) = resolver.forget(url).let { resolver.resolve(url, settings.preferences.value).info }

    /** Whether [id] is a YouTube channel's - a tab's, read from its main page - not a video's. */
    private fun isChannelId(id: String) = id.startsWith("UC") && id.length == ChannelIdLength
}
