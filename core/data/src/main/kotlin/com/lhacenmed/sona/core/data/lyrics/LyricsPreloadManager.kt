package com.lhacenmed.sona.core.data.lyrics

import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.datastore.LyricsSettings
import com.lhacenmed.sona.core.model.Track
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val MAX_CONCURRENT_PRELOADS = 2

/**
 * Reads the lyrics of the tracks coming up in the queue, so they are already there when those tracks
 * start. Ported from ArchiveTune's `LyricsPreloadManager`.
 */
@Singleton
class LyricsPreloadManager @Inject constructor(
    private val lyricsRepository: LyricsRepository,
    private val lyricsSettings: LyricsSettings,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private var preloadJob: Job? = null
    private val preloadSemaphore = Semaphore(MAX_CONCURRENT_PRELOADS)

    /**
     * Preloads the tracks after [currentIndex] in [queue], which holds the queue in play order - a slot
     * whose track is not in the library is null, and skipped without counting towards the preload.
     */
    fun onSongChanged(currentIndex: Int, queue: List<Track?>) {
        preloadJob?.cancel()
        if (!lyricsSettings.preloadQueueLyricsEnabled.value || currentIndex < 0) return

        val nextSongs = queue
            .asSequence()
            .drop(currentIndex + 1)
            .filterNotNull()
            .take(lyricsSettings.queueLyricsPreloadCount.value)
            .distinctBy { it.id }
            .toList()
        if (nextSongs.isEmpty()) return

        preloadJob = scope.launch {
            nextSongs
                .map { song -> async { preloadSemaphore.withPermit { lyricsRepository.loadLyrics(song) } } }
                .awaitAll()
        }
    }
}
