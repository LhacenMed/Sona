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
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** How long NewPipe is given a link before yt-dlp reads it instead - Nezzel's budget. */
private const val NewPipeBudgetMs = 10_000L

/** How long a site's own reader is given a post - its requests' own limit, so a reader is never cut off mid-answer. */
private const val SiteBudgetMs = 20_000L

/** How long a read is kept in memory, at most: long enough to reopen a link, short of what its site changes in. */
private const val KeptForMs = 15 * 60 * 1000L

private const val KeptReads = 8

/** How many links NewPipe could not read are remembered, so yt-dlp reads them straight away the next time. */
private const val RememberedMisses = 16

/**
 * What every link is read through - looked up as it is typed, and downloaded: NewPipe first, in the app itself, for YouTube -
 * Nezzel's fast path - and yt-dlp for everything it does not read in full.
 *
 * A link read is kept a while, so reopening it, or downloading it, reads nothing again; a link being read is read once, every
 * caller waiting on the same read; and a link NewPipe could not read goes straight to yt-dlp the next time.
 */
@Singleton
class MediaResolver @Inject internal constructor(
    private val newPipe: NewPipeSource,
    private val ytDlp: YtDlp,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private class Kept(val extraction: Extraction, val readAt: Long)

    private val kept = object : LinkedHashMap<String, Kept>(KeptReads, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Kept>?) = size > KeptReads
    }
    private val reading = mutableMapOf<String, Deferred<Extraction>>()
    /** The links the app's own readers could not read lately - yt-dlp's, straight away, the next time. */
    private val appMisses = LinkedHashSet<String>()

    /** [url], as it was last read, while that read still holds. */
    fun cached(url: String): Extraction? = synchronized(kept) {
        kept[url]?.takeIf { it.extraction.isFresh() && System.currentTimeMillis() - it.readAt < KeptForMs }?.extraction
    }

    /**
     * [url], read - from memory where it was just read. Fails as yt-dlp does, or with [DownloaderMissingException] for a link
     * only yt-dlp reads while the downloader is not here.
     */
    suspend fun resolve(url: String, preferences: DownloaderPreferences): Extraction {
        cached(url)?.let { return it }
        val read = synchronized(reading) {
            reading.getOrPut(url) {
                // In the app's scope: one caller leaving does not stop the read the others wait on.
                scope.async(Dispatchers.IO) {
                    try {
                        read(url, preferences).also { synchronized(kept) { kept[url] = Kept(it, System.currentTimeMillis()) } }
                    } finally {
                        synchronized(reading) { reading.remove(url) }
                    }
                }
            }
        }
        return read.await()
    }

    /** Lets [url]'s read go - one whose streams were found to work no longer. */
    fun forget(url: String) {
        synchronized(kept) { kept.remove(url) }
    }

    /**
     * [url] read the fastest way there is: by NewPipe for YouTube, by its site's own reader for a post of one of [SiteReaders],
     * each within its budget - and by yt-dlp for everything they do not read in full.
     */
    private suspend fun read(url: String, preferences: DownloaderPreferences): Extraction {
        // Through a proxy, or signed in, it is yt-dlp's to read: the app's own readers go straight to the site, as no one.
        val readsItself = preferences.proxy.isBlank() && !preferences.usesCookies && synchronized(appMisses) { url !in appMisses }
        if (readsItself) {
            val appRead = when {
                newPipe.accepts(url) -> readWithin(NewPipeBudgetMs) { newPipe.read(url) }?.let { extractionOf(it, isFromNewPipe = true) }
                else -> SiteReaders.readerFor(url)?.let { reader ->
                    // Its own requests, its own budget: one read's never spends another's.
                    readWithin(SiteBudgetMs) { reader.read(url, SiteHttp()) }?.let { extractionOf(it, isFromNewPipe = false) }
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
        return extractionOf(ytDlp.infoJson(url, preferences).toString(), isFromNewPipe = false)
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

    private fun extractionOf(json: String, isFromNewPipe: Boolean): Extraction {
        val parsed = JSONObject(json)
        return Extraction(json, mediaInfoOf(parsed), isFromNewPipe, expiryOf(parsed))
    }
}
