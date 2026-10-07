package com.lhacenmed.sona.feature.library

import com.lhacenmed.sona.core.data.playlist.PlaylistFile
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.core.model.sort.SortCriterion
import com.lhacenmed.sona.core.model.sort.SortDirection
import com.lhacenmed.sona.core.model.sort.SortOrder
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val M3U_HEADER = "#EXTM3U"
private const val M3U_ENTRY = "#EXTINF:"
private const val M3U_DURATION_SEPARATOR = ","

// Sona's own lines: comments to every other player, which skips any line starting with '#'.
private const val SONA_SORT = "#SONA-SORT:"
private const val SONA_ADDED = "#SONA-ADDED:"
private const val SONA_SORT_SEPARATOR = ","

/** The mime type a playlist file is created with. */
const val M3U_MIME_TYPE = "audio/x-mpegurl"

/**
 * The mime types the file picker will offer, which is how it is kept to playlists alone.
 *
 * Two of them, because the same `.m3u` is announced under either name depending on which provider
 * is listing it. A provider that reports one as `application/octet-stream` will grey it out - the
 * price of a picker that shows nothing else.
 */
val M3U_PICKER_MIME_TYPES = arrayOf(M3U_MIME_TYPE, "audio/mpegurl")

/**
 * Writes [file] as an extended M3U, matching what Fossify Music Player produces: a header, then two
 * lines per track - the `#EXTINF` metadata and the file's own path - in the order the file holds them.
 *
 * What only Sona reads goes in comment lines of its own, which other players skip: how the playlist is
 * sorted, after the header, and when each track joined it, above the track.
 *
 * The duration is written in whole seconds because that is what the format specifies; Sona stores
 * milliseconds, so this is the one place the two disagree and the conversion belongs here.
 */
fun writeM3u(outputStream: OutputStream, file: PlaylistFile) {
    outputStream.bufferedWriter().use { writer ->
        writer.appendLine(M3U_HEADER)
        file.order?.let { order ->
            writer.appendLine("$SONA_SORT${order.criterion.name}$SONA_SORT_SEPARATOR${order.direction.name}")
        }
        file.entries.forEach { (track, addedAt) ->
            addedAt?.let { writer.appendLine("$SONA_ADDED$it") }
            val seconds = track.durationMs / 1000
            writer.appendLine("$M3U_ENTRY$seconds$M3U_DURATION_SEPARATOR${track.artist} - ${track.title}")
            writer.appendLine(track.path)
        }
    }
}

/**
 * Resolves the tracks an M3U file refers to, in the order it lists them.
 *
 * Deliberately not a port of Fossify's matcher, which compares every entry against every track with
 * `location == path || title == title`. That is O(entries x library), and because the title arm is
 * an `or` with no `break`, one entry can pull in every unrelated track that happens to share a
 * title. Here the library is indexed once and each entry resolves at most one track: by full path
 * first, then - so a playlist written on another device still imports - by file name.
 *
 * Entries that match nothing are skipped rather than reported; a playlist referring to music this
 * device does not have is an ordinary thing for it to do. Sona's own lines - see [writeM3u] - are read
 * where present, and any that cannot be read are skipped like any other comment.
 */
fun readM3u(inputStream: InputStream, library: List<Track>): PlaylistFile {
    val byPath = library.associateBy { it.path }
    val byFileName = library.groupBy { it.path.substringAfterLast('/') }
    val entries = mutableListOf<PlaylistFile.Entry>()
    var order: SortOrder? = null
    var addedAt: Long? = null

    inputStream.bufferedReader().useLines { lines ->
        lines.map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            when {
                line.startsWith(SONA_SORT) -> order = line.removePrefix(SONA_SORT).toSortOrder()
                line.startsWith(SONA_ADDED) -> addedAt = line.removePrefix(SONA_ADDED).toLongOrNull()
                line.startsWith("#") -> Unit
                else -> {
                    val track = byPath[line] ?: byFileName[line.substringAfterLast('/')]?.singleOrNull()
                    if (track != null) entries += PlaylistFile.Entry(track, addedAt)
                    addedAt = null
                }
            }
        }
    }
    return PlaylistFile(entries, order)
}

private fun String.toSortOrder(): SortOrder? = runCatching {
    SortOrder(
        criterion = SortCriterion.valueOf(substringBefore(SONA_SORT_SEPARATOR)),
        direction = SortDirection.valueOf(substringAfter(SONA_SORT_SEPARATOR)),
    )
}.getOrNull()

/**
 * What an M3U file says of the tracks in [library] it names - nothing when the file cannot be opened,
 * which is what the picked file having been moved or its permission revoked between the pick and the
 * import looks like.
 *
 * Reading and matching happen off the main thread, but against the library already held in memory
 * rather than the database - the whole point of resolving by path is that it needs no query per entry.
 */
suspend fun readPlaylistFile(openStream: () -> InputStream?, library: List<Track>): PlaylistFile =
    withContext(Dispatchers.IO) {
        runCatching {
            openStream()?.use { stream -> readM3u(stream, library) }
        }.getOrNull() ?: PlaylistFile(emptyList())
    }
