package com.lhacenmed.sona.core.downloader

import com.lhacenmed.sona.core.downloader.sites.SiteHttp
import com.lhacenmed.sona.core.downloader.sites.SiteReaders
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.json.JSONArray
import org.json.JSONObject

/** The most of a page read for its graph: a video's page, its player's settings within it, is a megabyte or two. */
private const val PageMaxBytes = 4 * 1024 * 1024

/** The fewest slices a graph is taken with: fewer is not a graph of the video. */
private const val FewestSlices = 10

/**
 * A video's Most replayed graph, where its page hands its player one: `hotspots`, how many views each equal slice of the
 * video had, from its start to its end - what such a player draws above its seek bar. Read for a video downloaded with no
 * graph of its own, from the page it was downloaded from, and given yt-dlp's shape - see [EmbeddedExtras] - so it is kept,
 * and shown, as any other site's graph is.
 */
@Singleton
class PlayerHotspots @Inject internal constructor(private val newPipe: NewPipeSource) {

    /**
     * The graph of [url]'s video, [durationSeconds] long, as yt-dlp's heatmap JSON - null where its page has none, or cannot
     * be read. Read as no one, straight from the site.
     */
    suspend fun heatmapJson(url: String, durationSeconds: Long): String? {
        // The sites the app reads itself hand their players no such graph: their pages are not read for one.
        if (newPipe.accepts(url) || SiteReaders.readerFor(url) != null) return null
        return runInterruptible(Dispatchers.IO) {
            SiteHttp().get(url, PageMaxBytes, SiteHttp.BrowserPage, follow = { _, to -> to })?.let { heatmapJsonOf(it.text, durationSeconds) }
        }
    }
}

private val HotspotsPattern = Regex(""""hotspots"\s*:\s*\[([^\]]*)]""")

/**
 * The `hotspots` of [page] - its player's views per slice, written as numbers or as strings of them - as yt-dlp's heatmap
 * JSON for a video [durationSeconds] long: each slice's start, end and views. Null where the page has none.
 */
internal fun heatmapJsonOf(page: String, durationSeconds: Long): String? {
    if (durationSeconds <= 0) return null
    val written = HotspotsPattern.find(page)?.groupValues?.get(1) ?: return null
    val views = written.split(',').map { it.trim().removeSurrounding("\"").toDoubleOrNull()?.takeIf { views -> views >= 0 } ?: return null }
    if (views.size < FewestSlices) return null
    // Everyone who opens the video is counted at its start: held to the slice after it, or the rest would be flat beside it.
    val counted = views.toMutableList().apply { this[0] = minOf(this[0], this[1]) }
    if (counted.max() <= 0) return null
    val slice = durationSeconds.toDouble() / counted.size
    return JSONArray().apply {
        counted.forEachIndexed { index, value ->
            put(JSONObject().put("start_time", index * slice).put("end_time", (index + 1) * slice).put("value", value))
        }
    }.toString()
}
