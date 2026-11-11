package com.lhacenmed.sona.core.downloader

import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.downloader.sites.SiteHttp
import com.lhacenmed.sona.core.downloader.sites.SiteReaders
import com.lhacenmed.sona.core.model.DownloaderPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** How long NewPipe is given a link before yt-dlp reads it instead - Nezzel's budget. */
private const val NewPipeBudgetMs = 10_000L

/** How long a site's own reader is given a post - its requests' own limit, so a reader is never cut off mid-answer. */
private const val SiteBudgetMs = 20_000L

/** How many links NewPipe could not read are remembered, so yt-dlp reads them straight away the next time. */
private const val RememberedMisses = 16

/**
 * What every link is read through - looked up as it is typed, and downloaded: NewPipe first, in the app itself, for YouTube -
 * Nezzel's fast path - and yt-dlp for everything it does not read in full.
 *
 * A link read is kept - see [ReadCache] - so reopening it, or downloading it, reads nothing again; a link being read is read
 * once, every caller waiting on the same read; and a link NewPipe could not read goes straight to yt-dlp the next time. Links
 * are known by [linkKeyOf]: the same video, however it was shared, is one link.
 */
@Singleton
class MediaResolver @Inject internal constructor(
    private val newPipe: NewPipeSource,
    private val ytDlp: YtDlp,
    private val cache: ReadCache,
    private val readingArguments: ReadingArguments,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val reading = mutableMapOf<String, Deferred<Extraction>>()
    /** The links the app's own readers could not read lately - yt-dlp's, straight away, the next time. */
    private val appMisses = LinkedHashSet<String>()

    /** [url], as it was last read, while that read still holds - from memory alone, so it is answered at once. */
    fun cached(url: String): Extraction? = cache.inMemory(linkKeyOf(url))

    /**
     * Readies the reads to come - the app's own, where they are its to make: what the first link pasted would otherwise wait
     * on is done before it is there.
     */
    fun warmUp(preferences: DownloaderPreferences) {
        if (readsItself(preferences)) scope.launch(Dispatchers.IO) { runCatching { newPipe.warmUp() } }
    }

    /**
     * [url], read - from what is kept where it was read before. Fails as yt-dlp does, or with [DownloaderMissingException] for
     * a link only yt-dlp reads while the downloader is not here.
     */
    suspend fun resolve(url: String, preferences: DownloaderPreferences): Extraction {
        val key = linkKeyOf(url)
        cache.inMemory(key)?.let { return it }
        val read = synchronized(reading) {
            reading.getOrPut(key) {
                // In the app's scope: one caller leaving does not stop the read the others wait on.
                scope.async(Dispatchers.IO) {
                    try {
                        cache.get(key) ?: read(key, preferences).also { cache.put(key, it) }
                    } finally {
                        synchronized(reading) { reading.remove(key) }
                    }
                }
            }
        }
        return read.await()
    }

    /**
     * [url], read as it is downloaded: [resolve]'s read, completed with all yt-dlp's would hold - see [Extraction.isComplete] -
     * so yt-dlp is handed it, and does not read the link again.
     */
    suspend fun resolveForDownload(url: String, preferences: DownloaderPreferences): Extraction {
        val extraction = resolve(url, preferences)
        if (extraction.isComplete) return extraction
        return withContext(Dispatchers.IO) { extractionOf(newPipe.complete(extraction.json), isComplete = true).also { cache.put(linkKeyOf(url), it) } }
    }

    /**
     * Through a proxy, links are yt-dlp's to read: the app's own readers go straight to the site. Signed in, they are read by
     * the app first all the same: what signing in is for - what a site keeps from no one in particular - is what they fail on,
     * and such a link goes on to yt-dlp, signed in.
     */
    private fun readsItself(preferences: DownloaderPreferences) = preferences.proxy.isBlank()

    /** Lets [url]'s read go - one whose streams were found to work no longer. */
    fun forget(url: String) {
        cache.remove(linkKeyOf(url))
    }

    /**
     * [url] read the fastest way there is: by NewPipe for YouTube, by its site's own reader for a post of one of [SiteReaders],
     * each within its budget - and by yt-dlp for everything they do not read in full.
     */
    private suspend fun read(url: String, preferences: DownloaderPreferences): Extraction {
        if (readsItself(preferences) && synchronized(appMisses) { url !in appMisses }) {
            val appRead = when {
                newPipe.accepts(url) -> readWithin(NewPipeBudgetMs) { newPipe.read(url) }?.let { extractionOf(it, isComplete = false) }
                else -> SiteReaders.readerFor(url)?.let { reader ->
                    // Its own requests, its own budget: one read's never spends another's.
                    readWithin(SiteBudgetMs) { reader.read(url, SiteHttp()) }?.let { extractionOf(it, isComplete = true) }
                }
            }
            appRead?.let { return it }
            if (newPipe.accepts(url) || SiteReaders.readerFor(url) != null) {
                synchronized(appMisses) {
                    appMisses += url
                    if (appMisses.size > RememberedMisses) appMisses.remove(appMisses.first())
                }
            }
        }
        return extractionOf(ytDlp.infoJson(url, preferences, readingArguments.forLink(url)).toString(), isComplete = true)
    }

    /**
     * What [read] answers within [budgetMs] - null past it, or where it fails. Not a child of the caller: a reader may not stop
     * when asked, and must not hold yt-dlp up once out of time.
     */
    private suspend fun readWithin(budgetMs: Long, read: () -> String?): String? {
        val reading = scope.async(Dispatchers.IO) { runCatching(read).getOrNull() }
        return withTimeoutOrNull(budgetMs) { reading.await() } ?: run {
            reading.cancel()
            null
        }
    }
}
