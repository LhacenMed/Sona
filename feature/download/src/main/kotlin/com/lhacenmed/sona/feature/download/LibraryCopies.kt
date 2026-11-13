package com.lhacenmed.sona.feature.download

import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.database.dao.DownloadDao
import com.lhacenmed.sona.core.database.entity.DownloadEntity
import com.lhacenmed.sona.core.database.entity.toDomain
import com.lhacenmed.sona.core.downloader.EmbeddedExtras
import com.lhacenmed.sona.core.model.Download
import com.lhacenmed.sona.core.model.DownloadKind
import com.lhacenmed.sona.core.model.Track
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** How far apart in length two files may be and still be the same recording. */
private const val SameLengthSeconds = 2

/** The fewest letters a title is compared by - shorter ones say too little to tell two files apart. */
private const val TitleLetters = 6

/** The most files of the very length whose tags are read for the video's link - the rest of the library is never opened. */
private const val TagReads = 24

/** A YouTube video's id, in any of its links. */
private val VideoIdInLink = Regex("""(?:[?&]v=|youtu\.be/|/shorts/|/live/|/embed/)([\w-]{11})""")

/** Words a video's title adds to its song's - "(Official Video)", "[Lyrics]" - and whatever is not a letter or a digit. */
private val TitleNoise = Regex("""\s*[(\[][^)\]]*[)\]]""")
private val NotAWord = Regex("""[^\p{L}\p{N}]+""")

/**
 * What a download would save, as it is looked for: its link, its video's id, its kind, its [title], its length - and, for a
 * sound, the song it is.
 */
data class CopyQuery(
    val url: String,
    val videoId: String?,
    val kind: DownloadKind,
    val title: String?,
    val songTitle: String?,
    val songArtist: String?,
    val durationSeconds: Long?,
)

/**
 * The library looked through for what is to be downloaded, as it goes: what it has found so far, for each kind it may be
 * downloaded as, by link - each of a playlist's videos by its own - and whether it [isDone].
 */
data class LibraryCheck(val copies: Map<DownloadKind, Map<String, List<LibraryCopy>>> = emptyMap(), val isDone: Boolean = false) {
    /** What it has found of each link, downloaded as [kind]. */
    fun of(kind: DownloadKind): Map<String, List<LibraryCopy>> = copies[kind].orEmpty()

    /** This, with [found] - copies of [url], downloaded as [kind] - added to it. */
    fun with(kind: DownloadKind, url: String, found: List<LibraryCopy>): LibraryCheck {
        val byLink = of(kind)
        return copy(copies = copies + (kind to (byLink + (url to (byLink[url].orEmpty() + found).distinctBy(LibraryCopy::path)))))
    }
}

/** A copy of what a download would save, already in the library: [title] at [path] - the very one where [isExact], else a song like it. */
data class LibraryCopy(val title: String, val path: String, val isExact: Boolean)

/**
 * What the library holds already of what is to be downloaded - "Already in your library" - whichever app saved it:
 * - downloaded by Sona before and still saved, by its link;
 * - a file of the library of the same kind naming the video's id;
 * - one as long, to the second or two, whose title is the video's - or holds it, or is held in it - or whose tags carry the
 *   video's link, as yt-dlp, Seal and YTDLnis write it;
 * - for a sound, the same song by the same artist, as long: a song like it.
 *
 * Its length narrows the library to a handful of files before any title is compared or any file opened: everything else is
 * read from what is in memory - the history and the library as they stand - each file's names worked out once for every link
 * looked for, so a playlist of hundreds is looked through as fast as one video.
 */
@Singleton
class LibraryCopies @Inject constructor(
    private val dao: DownloadDao,
    private val library: LibraryRepository,
) {
    /** What the library holds of each of [queries], by its link - those it holds nothing of left out. */
    suspend fun of(queries: List<CopyQuery>): Map<String, List<LibraryCopy>> {
        val found = mutableMapOf<String, List<LibraryCopy>>()
        find(queries) { url, copies -> found[url] = (found[url].orEmpty() + copies).distinctBy(LibraryCopy::path) }
        return found
    }

    /**
     * Looks for what the library holds of each of [queries], saying what it finds to [onFound] - by its query's link - the moment
     * it is found: first all that is read from memory, for every query at once; then the tags of the few files as long as each,
     * the one thing that opens files. Returns once it has looked everywhere.
     */
    suspend fun find(queries: List<CopyQuery>, onFound: (url: String, copies: List<LibraryCopy>) -> Unit) {
        val history = dao.saved().map(DownloadEntity::toDomain)
        // The library as it is once read - never the empty list it is while it is read, which would hold nothing of anything.
        val tracks = library.tracks.first { it is LibraryContent.Ready }.itemsOrEmpty
        val videos = library.videos.first { it is LibraryContent.Ready }.itemsOrEmpty
        withContext(Dispatchers.IO) {
            val files = (tracks + videos).map(::LibraryFile)
            val songs = files.filterNot { it.track.isVideo }.groupBy { songKey(it.track.title, it.track.artist) }
            val searches = queries.map { query -> Search(query, history, files, songs) }
            searches.forEach { search -> search.copies.takeIf { it.isNotEmpty() }?.let { onFound(search.query.url, it) } }
            searches.forEach { search -> search.taggedCopies().takeIf { it.isNotEmpty() }?.let { onFound(search.query.url, it) } }
        }
    }
}

/**
 * One query looked for: [copies] - what is found from memory alone, as it is made - then [taggedCopies], the files as long as it
 * that are not found yet whose tags carry its video's link.
 */
private class Search(val query: CopyQuery, history: List<Download>, files: List<LibraryFile>, songs: Map<String, List<LibraryFile>>) {
    private val isVideo = query.kind == DownloadKind.VIDEO
    private val videoId = query.videoId
    private val sameLength: List<LibraryFile>
    private val found: Set<LibraryFile>
    val copies: List<LibraryCopy>

    init {
        val downloaded = history.filter { saved ->
            saved.choices.kind == query.kind && (saved.url == query.url || (videoId != null && videoIdOf(saved.url) == videoId))
        }.flatMap { saved -> saved.savedPaths.filter { File(it).exists() }.map { LibraryCopy(saved.title ?: File(it).nameWithoutExtension, it, isExact = true) } }
        val sameKind = files.filter { it.track.isVideo == isVideo }
        val named = if (videoId == null) emptyList() else sameKind.filter { videoId in it.fileName }
        sameLength = query.durationSeconds?.let { seconds -> sameKind.filter { abs(it.track.durationMs / 1000 - seconds) <= SameLengthSeconds } }.orEmpty()
        val title = query.title?.let(::titleKey)?.takeIf { it.length >= TitleLetters }
        val titled = if (title == null) emptyList() else sameLength.filter { file -> file.titleKeys.any { it.length >= TitleLetters && (title in it || it in title) } }
        val similar = if (isVideo || query.songTitle == null || query.durationSeconds == null) {
            emptyList()
        } else {
            songs[songKey(query.songTitle, query.songArtist.orEmpty())].orEmpty().filter { abs(it.track.durationMs / 1000 - query.durationSeconds) <= SameLengthSeconds }
        }
        found = (named + titled + similar).toSet()
        copies = (downloaded + (named + titled).map { LibraryCopy(it.track.title, it.track.path, isExact = true) } +
            similar.map { LibraryCopy(it.track.title, it.track.path, isExact = false) }).distinctBy(LibraryCopy::path)
    }

    fun taggedCopies(): List<LibraryCopy> = if (videoId == null) {
        emptyList()
    } else {
        (sameLength - found).take(TagReads).filter { EmbeddedExtras.mentions(File(it.track.path), videoId) }.map { LibraryCopy(it.track.title, it.track.path, isExact = true) }
    }
}

/** A file of the library, with the names it is compared by worked out once: its file's own, and its title's and name's keys. */
private class LibraryFile(val track: Track) {
    val fileName: String = File(track.path).name
    val titleKeys: List<String> = listOf(titleKey(track.title), titleKey(File(track.path).nameWithoutExtension)).distinct()
}

/** [url]'s YouTube video id - null for any other link. */
internal fun videoIdOf(url: String): String? = VideoIdInLink.find(url)?.groupValues?.get(1)

/** A title as it is compared: without what a video adds to it, nor anything but its letters and digits, run together. */
private fun titleKey(title: String): String = title.replace(TitleNoise, "").lowercase().replace(NotAWord, "")

/** A song as it is compared: its title without what a video adds to it, and its artist, each of its words alone. */
private fun songKey(title: String, artist: String): String =
    listOf(title.replace(TitleNoise, ""), artist).joinToString("|") { it.lowercase().replace(NotAWord, " ").trim() }
