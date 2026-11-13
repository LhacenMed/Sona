package com.lhacenmed.sona.feature.tageditor

import android.content.Context
import android.media.MediaScannerConnection
import com.lhacenmed.sona.core.data.LibraryWriter
import com.lhacenmed.sona.core.downloader.FileFormat
import com.lhacenmed.sona.core.downloader.MediaConverter
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.scanner.MediaScanner
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** What a file name cannot hold. */
private val NotInAName = Regex("""[\\/:*?"<>|\x00-\x1f]""")

/** A file of that name is there already: nothing is written over. */
class FileExistsException(val file: File) : Exception("${file.name} is there already")

/**
 * A track's file renamed, moved to another folder, or turned into another format - and the track still the very track: its id,
 * its playlists, play counts, lyrics and downloads follow it to its new file, which the library reads at once. The one way the
 * app changes where a track's file is, or what it is.
 */
@Singleton
class TrackFileChanges @Inject constructor(
    @ApplicationContext private val context: Context,
    private val libraryWriter: LibraryWriter,
    private val mediaScanner: MediaScanner,
    private val converter: MediaConverter,
) {
    /** Renames [track]'s file [name] - its extension kept. */
    suspend fun rename(track: Track, name: String) {
        val file = File(track.path)
        val cleanName = name.replace(NotInAName, "_").trim()
        require(cleanName.isNotEmpty()) { "A name is needed" }
        relocate(track, File(file.parentFile, if (file.extension.isEmpty()) cleanName else "$cleanName.${file.extension}")) { from, to -> moveFile(from, to) }
    }

    /** Moves [track]'s file into [folder], under its own name. */
    suspend fun move(track: Track, folder: File) = relocate(track, File(folder, File(track.path).name)) { from, to -> moveFile(from, to) }

    /** Turns [track]'s file into [format], beside it under its own name - in place of the file it was. */
    suspend fun convert(track: Track, format: FileFormat) {
        val file = File(track.path)
        relocate(track, File(file.parentFile, "${file.nameWithoutExtension}.${format.extension}")) { from, to ->
            converter.convert(from, to, format)
            from.delete()
        }
    }

    /**
     * Has [change] make [track]'s file - nothing written over - the file [to], then has the library take that as its file moved,
     * Android index both paths, and the library read them.
     */
    private suspend fun relocate(track: Track, to: File, change: suspend (from: File, to: File) -> Unit) = withContext(Dispatchers.IO) {
        val from = File(track.path)
        if (to.path == from.path) return@withContext
        if (to.exists()) throw FileExistsException(to)
        to.parentFile?.mkdirs()
        change(from, to)
        libraryWriter.expectMove(from.path, to.path)
        scan(listOf(from.path, to.path))
        mediaScanner.refresh()
    }

    /** Moves [from] to [to] - in one step on the same storage, by copying it across another. */
    private fun moveFile(from: File, to: File) {
        if (from.renameTo(to)) return
        from.copyTo(to)
        from.delete()
    }

    /** Has Android index [paths] - the one gone, the other there - so other apps see the file where it is now. */
    private suspend fun scan(paths: List<String>) = suspendCancellableCoroutine { continuation ->
        var left = paths.size
        MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { _, _ ->
            synchronized(paths) { if (--left == 0) continuation.resume(Unit) }
        }
    }
}
