package com.lhacenmed.sona.feature.tageditor

import android.content.Context
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.common.di.IoDispatcher
import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.data.contentUri
import com.lhacenmed.sona.core.data.lyrics.LyricsUtils
import com.lhacenmed.sona.core.data.search.SearchText
import com.lhacenmed.sona.core.data.search.searchFormOf
import com.lhacenmed.sona.core.database.dao.LyricsIndexDao
import com.lhacenmed.sona.core.database.entity.LyricsIndexEntity
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.scanner.MediaScanner
import com.lhacenmed.sona.feature.tageditor.tags.TagField
import com.lhacenmed.sona.feature.tageditor.tags.TagFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What a search found in the lyrics: for each track found, the line of them [query] matched best. */
class LyricsMatches(val query: String?, val lines: Map<Long, String>)

/** The fewest letters a search needs before it looks in the lyrics - fewer would find a line in nearly every song. */
private const val LYRICS_SEARCH_MIN_LENGTH = 3

/** How many tracks are read before their lyrics are written, together. */
private const val INDEX_BATCH_SIZE = 50

/** The most ids one statement takes - SQLite's limit on a statement's arguments, with room to spare. */
private const val DELETE_BATCH_SIZE = 500

/**
 * Every track's lyrics, as search finds tracks by them - see [LyricsIndexEntity]. The files alone hold the
 * lyrics; this keeps a copy for search, and keeps it as the files are.
 *
 * Whenever the library has been read - and no scan is running, so this never slows one - every file it has
 * not read, or that has changed since, is read in the background, a batch at a time; the tracks gone from
 * the library are let go. A file Sona writes is read again the moment it is written - see [reindex]. So a
 * search finds a song by its lyrics as soon as Sona has seen them, with nothing to turn on.
 *
 * Music alone: a video's sound has no lyrics in its file to speak of, and reading one is the slowest read.
 */
@Singleton
class LyricsSearchIndex @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: LyricsIndexDao,
    repository: LibraryRepository,
    mediaScanner: MediaScanner,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope scope: CoroutineScope,
) {
    init {
        scope.launch(ioDispatcher) {
            combine(repository.tracks, mediaScanner.isScanning) { tracks, isScanning -> tracks.takeUnless { isScanning } }
                .collectLatest { tracks -> if (tracks is LibraryContent.Ready) catchUp(tracks.items) }
        }
    }

    /**
     * The tracks whose lyrics hold every word of [query] - each word as how a word of them starts - with the
     * line each matched best. None for a query too short to tell songs apart by.
     */
    suspend fun search(query: String?): LyricsMatches {
        val words = SearchText(query.orEmpty()).words
        if (words.sumOf { it.length } < LYRICS_SEARCH_MIN_LENGTH) return LyricsMatches(query, emptyMap())
        // Search form holds letters and digits alone, so no word can be read as one of the query's operators.
        val hits = withContext(ioDispatcher) { dao.search(words.joinToString(" ") { "$it*" }) }
        return LyricsMatches(query, hits.associate { hit -> hit.trackId to bestLine(hit.lyrics, words) })
    }

    /** Reads [track]'s lyrics again - its file just written, by Sona - so search finds it by what they are now. */
    suspend fun reindex(track: Track) {
        if (track.isVideo) return
        withContext(ioDispatcher) { dao.replace(listOf(indexed(track))) }
    }

    private suspend fun catchUp(tracks: List<Track>) {
        val signatures = dao.signatures().associate { it.trackId to it.signature }
        val trackIds = tracks.mapTo(HashSet(tracks.size)) { it.id }
        signatures.keys.filterNot(trackIds::contains).chunked(DELETE_BATCH_SIZE).forEach { dao.delete(it) }
        tracks.filter { signatures[it.id] != signatureOf(it) }
            .chunked(INDEX_BATCH_SIZE)
            .forEach { batch -> dao.replace(batch.map(::indexed)) }
    }

    private fun indexed(track: Track): LyricsIndexEntity {
        // Read before the file, so a change made while it is read shows as a change the next time.
        val signature = signatureOf(track)
        val lyrics = LyricsUtils.displayLyricsText(TagFile.read(context, track.contentUri, track.path).tags[TagField.LYRICS])
        return LyricsIndexEntity(trackId = track.id, lyrics = lyrics, searchText = searchFormOf(lyrics), signature = signature)
    }

    /** The file's size and the time it was last changed, as one number - 0 for a file that cannot be looked at. */
    private fun signatureOf(track: Track): Long = File(track.path).let { file -> file.lastModified() * 31 + file.length() }
}

/** The line of [lyrics] holding the most of the [words] searched for, each as a word's start - the first, of equals. */
private fun bestLine(lyrics: String, words: List<String>): String =
    lyrics.lines().maxByOrNull { line ->
        val lineWords = SearchText(line).words
        words.count { word -> lineWords.any { it.startsWith(word) } }
    }.orEmpty()
