package com.lhacenmed.sona.feature.scanner.filesystem

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.os.Build
import android.os.storage.StorageManager
import com.lhacenmed.sona.core.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject

/**
 * Walks the device's storage roots with plain [File] I/O to find audio files that [MediaStore]
 * missed - e.g. files pushed via `adb push` that haven't been indexed yet. Ported from Fossify
 * Music Player's `MediaScanner.findTracksManually`/`findAudioFiles`.
 *
 * Only meaningful on API 29+ (Q), matching the source: the caller is expected to gate on that.
 */
class ManualFileWalker @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * @param pathsToSkip paths (individual files already known from MediaStore) or folders
     * (user-excluded directories) to leave out of the walk entirely - and their whole subtree, in
     * the case of a folder.
     */
    fun findTracks(pathsToSkip: Set<String>): List<Track> {
        val audioFilePaths = mutableListOf<String>()
        for (root in storageRoots()) {
            findAudioFiles(File(root), audioFilePaths, pathsToSkip)
        }

        if (audioFilePaths.isEmpty()) {
            return emptyList()
        }

        val tracks = audioFilePaths.mapNotNull { path -> extractTrack(path) }

        // Ask the system indexer to pick these files up so a future MediaStore query returns them
        // with full metadata (art, stable ids, etc.) instead of relying on this manual fallback again.
        val newPaths = audioFilePaths.filter { it !in pathsToSkip }
        if (newPaths.isNotEmpty()) {
            runCatching {
                MediaScannerConnection.scanFile(context, newPaths.toTypedArray(), null, null)
            }
        }

        return tracks
    }

    private fun findAudioFiles(file: File, destination: MutableList<String>, pathsToSkip: Set<String>) {
        if (file.isHidden) {
            return
        }

        val path = file.absolutePath
        if (path in pathsToSkip) {
            return
        }

        if (file.isFile) {
            if (path.isAudioFile()) {
                destination += path
            }
        } else if (file.isDirectory && !file.containsNoMedia()) {
            file.listFiles()?.forEach { child ->
                findAudioFiles(child, destination, pathsToSkip)
            }
        }
    }

    private fun extractTrack(path: String): Track? {
        val retriever = MediaMetadataRetriever()
        var inputStream: FileInputStream? = null

        try {
            try {
                retriever.setDataSource(path)
            } catch (_: Exception) {
                inputStream = FileInputStream(path)
                retriever.setDataSource(inputStream.fd)
            }

            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: path.substringAfterLast('/')
            if (title.isEmpty()) {
                return null
            }

            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                ?: android.provider.MediaStore.UNKNOWN_STRING
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val folderPath = File(path).parent.orEmpty()
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?: folderPath.substringAfterLast('/').ifEmpty { android.provider.MediaStore.UNKNOWN_STRING }
            val trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER).firstNumber()
            val discNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER).firstNumber()
            val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.toIntOrNull()?.takeIf { it > 0 }
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
            val dateAddedSeconds = runCatching { File(path).lastModified() / 1000L }.getOrDefault(0L)

            return Track(
                id = 0,
                // There's no MediaStore row for a manually-discovered file, so derive a stable
                // stand-in id from its path (the same path is also the DB's real unique key).
                mediaStoreId = path.hashCode().toLong(),
                title = title,
                artist = artist,
                artistId = 0L,
                album = album,
                albumId = 0L,
                genre = genre,
                genreId = null,
                path = path,
                folderPath = folderPath,
                durationMs = durationMs,
                trackNumber = trackNumber,
                discNumber = discNumber,
                year = year,
                dateAddedSeconds = dateAddedSeconds,
                coverArtUri = null,
                isManuallyScanned = true,
            )
        } catch (_: Exception) {
            return null
        } finally {
            runCatching {
                inputStream?.close()
                retriever.release()
            }
        }
    }

    /** The device's internal storage root plus any mounted SD card / USB storage roots. */
    private fun storageRoots(): List<String> {
        val roots = mutableSetOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            storageManager?.storageVolumes?.forEach { volume ->
                volume.directory?.absolutePath?.let { roots += it }
            }
        }

        if (roots.isEmpty()) {
            // Fallback for API < 30 (StorageVolume.getDirectory() requires R) and as a safety net if
            // StorageManager reports nothing: the primary shared storage root, plus any secondary
            // storage roots inferred from the app's package-specific external directories
            // (".../Android/data/<pkg>/files" -> the volume root above "Android").
            android.os.Environment.getExternalStorageDirectory()?.absolutePath?.let { roots += it }

            context.getExternalFilesDirs(null)?.forEach { dir ->
                val path = dir?.absolutePath ?: return@forEach
                val androidDirIndex = path.indexOf("/Android/data")
                if (androidDirIndex > 0) {
                    roots += path.substring(0, androidDirIndex)
                }
            }
        }

        return roots.toList()
    }
}

private val AUDIO_EXTENSIONS = setOf(
    "mp3", "m4a", "m4b", "m4p", "wav", "wma", "ogg", "oga", "opus", "flac", "aac",
    "mid", "midi", "3gp", "3ga", "amr", "awb", "mka", "ape", "aiff", "aif", "dsf", "alac",
)

private fun String.isAudioFile(): Boolean =
    substringAfterLast('.', missingDelimiterValue = "").lowercase() in AUDIO_EXTENSIONS

private fun File.containsNoMedia(): Boolean = File(this, ".nomedia").exists()

private fun String?.firstNumber(): Int? =
    this?.trim()
        ?.substringBefore('/')
        ?.takeWhile { it.isDigit() }
        ?.toIntOrNull()
        ?.takeIf { it > 0 }
