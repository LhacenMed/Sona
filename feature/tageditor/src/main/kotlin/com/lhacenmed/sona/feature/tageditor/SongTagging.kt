package com.lhacenmed.sona.feature.tageditor

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.lhacenmed.sona.feature.tageditor.lookup.TagLookup
import com.lhacenmed.sona.feature.tageditor.lookup.TrackMatcher
import com.lhacenmed.sona.feature.tageditor.lookup.TrackQueries
import com.lhacenmed.sona.feature.tageditor.lookup.TrackQuery
import com.lhacenmed.sona.feature.tageditor.lyrics.FoundLyrics
import com.lhacenmed.sona.feature.tageditor.lyrics.LyricsQuery
import com.lhacenmed.sona.feature.tageditor.lyrics.OnlineLyrics
import com.lhacenmed.sona.feature.tageditor.net.Http
import com.lhacenmed.sona.feature.tageditor.tags.CoverChoice
import com.lhacenmed.sona.feature.tageditor.tags.TagField
import com.lhacenmed.sona.feature.tageditor.tags.TagFile
import com.lhacenmed.sona.feature.tageditor.tags.TrackTags
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** How long a downloaded song's lyrics are waited for - every source asked at once, the best of those in by then kept. */
private const val LyricsBudgetMs = 8_000L

/** What a channel adds to its name that no catalogue names the artist by. */
private val ChannelNoise = Regex("""\s*(?:-\s*Topic|VEVO|Official)$""", RegexOption.IGNORE_CASE)

/**
 * What a download saves as a song, as kept with it until its file is tagged: its [tags], its [cover] - the file's own for
 * the video's picture - and its [lyrics]: none where blank, and still to be found where null.
 */
internal data class SongPlan(val tags: TrackTags, val cover: CoverChoice, val lyrics: String?) {
    val encoded: String
        get() = JSONObject()
            .put("v", Version)
            .put("tags", JSONObject(tags.filledFields.associate { it.name to tags[it] }))
            .apply {
                when (cover) {
                    is CoverChoice.Web -> put("cover", cover.url)
                    is CoverChoice.Device -> put("picture", cover.uri)
                    CoverChoice.Own -> Unit
                }
                lyrics?.let { put("lyrics", it) }
            }
            .toString()

    companion object {
        private const val Version = 2

        /** The plan [encoded] kept - null for one it did not write. */
        fun of(text: String): SongPlan? = runCatching {
            val json = JSONObject(text)
            if (json.optInt("v") != Version) return null
            val saved = json.getJSONObject("tags")
            SongPlan(
                tags = TagField.entries.fold(TrackTags()) { tags, field -> tags.with(field, saved.optString(field.name)) },
                cover = when {
                    json.has("cover") -> CoverChoice.Web(json.getString("cover"))
                    json.has("picture") -> CoverChoice.Device(json.getString("picture"))
                    else -> CoverChoice.Own
                },
                lyrics = if (json.has("lyrics")) json.getString("lyrics") else null,
            )
        }.getOrNull()
    }
}

/**
 * What saving a download as a song asks of the tag editor: the song a video is, found in its catalogues - every one at
 * once, ranked by how well each fits - with its cover and lyrics, as a [VideoSong] the user can change before it downloads;
 * and the song's tags, cover and lyrics written into the file once it is saved, as the editor writes any track's. So a song
 * found and tagged from a download is found and tagged the one way the app does it.
 */
object SongTagging {

    /**
     * The song a video [title], published by [channel] and [durationSeconds] long, is to be saved as - found in [scope] at
     * once. [thumbnailUrl] is the video's own picture. [VideoSong.close] stops what it still finds.
     */
    fun songFor(title: String, channel: String?, durationSeconds: Long?, thumbnailUrl: String?, scope: CoroutineScope): VideoSong {
        val own = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
        return VideoSong(title, channel, (durationSeconds ?: 0L) * 1000, thumbnailUrl, own).also { it.find() }
    }

    /**
     * Tags [file], a song just downloaded, as [plan] - [VideoSong.plan] - or, null, as the song its own title and artist surely
     * are - with every tag the song has, its cover, and lyrics timed to a recording of its length where none
     * were picked. What the file holds that the song does not name is kept. Whether it was tagged: a song not surely found
     * leaves it as it is.
     */
    suspend fun tag(context: Context, file: File, plan: String?): Boolean = withContext(Dispatchers.IO) {
        val uri = Uri.fromFile(file)
        val own = TagFile.read(context, uri, file.path).tags
        val song = plan?.let(SongPlan::of) ?: foundFor(own, file) ?: return@withContext false
        val durationMs = durationMsOf(file)
        try {
            // Both at once - the download keeps its place in the queue until it is tagged - and lyrics no longer than they are worth.
            val (lyrics, cover) = coroutineScope {
                val lyrics = async { song.lyrics ?: bestLyrics(song.tags, durationMs)?.lyrics?.text.orEmpty() }
                val cover = async { coverOf(context, song.cover) }
                lyrics.await() to cover.await()
            }
            val tags = own.overlaidWith(song.tags).let { if (lyrics.isBlank()) it else it.with(TagField.LYRICS, lyrics) }
            runCatching { TagFile.write(context, uri, file.path, tags, cover) }.isSuccess
        } finally {
            (song.cover as? CoverChoice.Device)?.let { picture -> Uri.parse(picture.uri).path?.let(::File)?.delete() }
        }
    }

    /** The song a file's own [tags] - or its name - surely are, as a plan; null where none surely is. */
    private suspend fun foundFor(tags: TrackTags, file: File): SongPlan? {
        val title = tags[TagField.TITLE].ifBlank { file.nameWithoutExtension }
        val readings = readingsOf(title, tags[TagField.ARTIST])
        val best = TagLookup.search(readings)?.firstOrNull()?.takeIf { match -> readings.any { TrackMatcher.score(it, match) >= TrackMatcher.Confident } }
            ?: return null
        val complete = TagLookup.complete(best)
        return SongPlan(complete.tags, complete.coverUrl.takeIf { it.isNotBlank() }?.let { CoverChoice.Web(it) } ?: CoverChoice.Own, lyrics = null)
    }

    /** The best lyrics for the song [tags] name, a recording [durationMs] long - of those in within [LyricsBudgetMs]. */
    private suspend fun bestLyrics(tags: TrackTags, durationMs: Long): FoundLyrics? {
        val query = LyricsQuery(tags[TagField.TITLE], tags[TagField.ARTIST], tags[TagField.ALBUM].ifBlank { null }, (durationMs / 1000).toInt().takeIf { it > 0 } ?: -1)
        val found = mutableListOf<FoundLyrics>()
        withTimeoutOrNull(LyricsBudgetMs) { OnlineLyrics.search(query).collect { found += it } }
        return found.filter { it.isTrustedFor(durationMs) }.minWithOrNull(OnlineLyrics.order(durationMs))
    }

    /** The picture [choice] embeds - null for the file's own, or one that cannot be had. */
    private suspend fun coverOf(context: Context, choice: CoverChoice): ByteArray? = when (choice) {
        CoverChoice.Own -> null
        is CoverChoice.Web -> Http.bytes(choice.url)
        is CoverChoice.Device -> runCatching { scaledCoverOf(context, Uri.parse(choice.uri)) }.getOrNull()
    }

    internal fun readingsOf(title: String, channel: String?): List<TrackQuery> =
        TrackQueries.of(title = title, artist = channel?.replace(ChannelNoise, "").orEmpty(), fileName = "")

    private fun durationMsOf(file: File): Long = runCatching {
        MediaMetadataRetriever().run {
            try {
                setDataSource(file.path)
                extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            } finally {
                release()
            }
        }
    }.getOrNull() ?: 0L
}
