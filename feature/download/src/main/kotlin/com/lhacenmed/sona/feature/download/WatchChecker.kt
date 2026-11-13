package com.lhacenmed.sona.feature.download

import com.lhacenmed.sona.core.database.dao.DownloadDao
import com.lhacenmed.sona.core.database.entity.DownloadWatchEntity
import com.lhacenmed.sona.core.database.entity.toDomain
import com.lhacenmed.sona.core.datastore.DownloadSettings
import com.lhacenmed.sona.core.downloader.MediaResolver
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
 * has appeared on it since queued as the user downloads. A watch added starts from what it holds then: only what comes after
 * is downloaded.
 */
@Singleton
class WatchChecker @Inject constructor(
    private val dao: DownloadDao,
    private val resolver: MediaResolver,
    private val queue: DownloadQueue,
    private val settings: DownloadSettings,
) {
    val watches: Flow<List<DownloadWatch>> = dao.observeWatches().map { rows -> rows.map(DownloadWatchEntity::toDomain) }

    /** Watches [url], which must lead to a playlist or a channel's videos. Fails as reading it does. */
    suspend fun add(url: String) {
        val info = read(url)
        require(info.isPlaylist) { "Not a channel or a playlist" }
        dao.insertWatch(DownloadWatchEntity(url = url, title = info.title, knownIds = info.entries.orEmpty().joinToString("\n") { it.id }, createdAt = System.currentTimeMillis()))
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
        if (watch.knownIds.isNotEmpty() && watch.knownIds.all { it.startsWith("UC") && it.length == ChannelIdLength }) {
            dao.setKnownIds(watch.id, entries.joinToString("\n") { it.id })
            return
        }
        val new = entries.filter { it.id !in watch.knownIds }
        if (new.isEmpty()) return
        val choices = settings.choices.value
        // Listed as one, under the channel's name, with whatever else it has queued.
        val batch = DownloadBatch("$WatchBatchPrefix${watch.id}", watch.title)
        queue.add(new.reversed().map { DownloadRequest(it.url, it.title, it.thumbnailUrl, it.uploader, choices, batch = batch) })
        dao.setKnownIds(watch.id, (watch.knownIds + new.map { it.id }).joinToString("\n"))
    }

    private suspend fun read(url: String) = resolver.forget(url).let { resolver.resolve(url, settings.preferences.value).info }
}
