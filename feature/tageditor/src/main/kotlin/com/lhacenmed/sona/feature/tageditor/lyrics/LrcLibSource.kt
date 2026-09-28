package com.lhacenmed.sona.feature.tageditor.lyrics

import com.lhacenmed.sona.feature.tageditor.lookup.TrackMatcher
import com.lhacenmed.sona.feature.tageditor.net.Http
import com.lhacenmed.sona.feature.tageditor.net.objects
import com.lhacenmed.sona.feature.tageditor.net.string
import kotlin.math.abs

/**
 * LRCLIB - ArchiveTune's `LrcLib`: an open library of synced lyrics, searched by title, artist and album. Its
 * entries within two seconds of the track's length come nearest first; with no length to go by, the ones whose
 * names are likest do. Each gives its synced lyrics, and the first to have them its plain ones too.
 */
internal object LrcLibSource : LyricsSource {
    private const val Api = "https://lrclib.net/api/search"
    private const val MaxDurationDeltaSeconds = 2
    private const val MaxResults = 5

    override val name = "LrcLib"

    private class Entry(val title: String, val artist: String, val durationSeconds: Double, val synced: String, val plain: String)

    override suspend fun fetchAll(query: LyricsQuery): List<SourceLyrics> {
        val url = buildString {
            append("$Api?track_name=${Http.encode(query.title)}&artist_name=${Http.encode(query.artist)}")
            query.album?.takeIf { it.isNotBlank() }?.let { append("&album_name=${Http.encode(it)}") }
        }
        val entries = Http.jsonArray(url)?.objects().orEmpty()
            .map { Entry(it.string("trackName"), it.string("artistName"), it.optDouble("duration"), it.string("syncedLyrics"), it.string("plainLyrics")) }
            .filter { it.synced.isNotBlank() || it.plain.isNotBlank() }
        val ordered = if (query.durationSeconds == -1) {
            entries.sortedByDescending { entry ->
                val names = (likeness(query.title, entry.title) + likeness(query.artist, entry.artist)) / 2.0
                names + (if (entry.synced.isNotBlank()) 1.0 else 0.0) + (if (entry.plain.isNotBlank()) 0.25 else 0.0)
            }
        } else {
            entries
                .filter { abs(it.durationSeconds.toInt() - query.durationSeconds) <= MaxDurationDeltaSeconds }
                .sortedBy { abs(it.durationSeconds.toInt() - query.durationSeconds) }
        }
        var hasPlain = false
        return buildList {
            for (entry in ordered) {
                // Each entry is one recording, its length given in seconds.
                val durationMs = (entry.durationSeconds * 1000).toLong().takeIf { it > 0 }
                if (entry.synced.isNotBlank()) add(SourceLyrics(entry.synced, durationMs))
                if (!hasPlain && entry.plain.isNotBlank()) {
                    add(SourceLyrics(entry.plain, durationMs))
                    hasPlain = true
                }
            }
        }.take(MaxResults)
    }

    private fun likeness(a: String, b: String): Double = TrackMatcher.similarity(a.trim().lowercase(), b.trim().lowercase())
}
