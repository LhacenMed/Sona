package com.lhacenmed.sona.feature.scanner

import android.os.ParcelFileDescriptor
import com.kyant.taglib.TagLib
import com.lhacenmed.sona.core.database.dao.FileYearDao
import com.lhacenmed.sona.core.database.entity.FileYearEntity
import com.lhacenmed.sona.core.model.Track
import java.io.File
import javax.inject.Inject

/** Bumped whenever how a file's year is read changes, so every year read the old way is read again. */
private const val READ_VERSION = 1

/** The year a date tag gives - its first four digits, so a whole date, "2019-05-03", gives its year - or null for none. */
private val YearPattern = Regex("""\d{4}""")

/**
 * Gives every music track the year its own file's date tag holds, read through TagLib - the library the tag editor writes
 * them with, in every format alike - so a date written in Sona is the date Sona reads, and an album, made of its tracks'
 * years, follows it on the next scan. MediaStore's year stands only for a file TagLib cannot read: MediaStore reads a bare
 * year alone, and leaves a whole date, as taggers write one, undated.
 *
 * Each file is opened for it once, and again only once it changes: what was read is kept - see `FileYearEntity`.
 */
class FileYears @Inject constructor(private val dao: FileYearDao) {

    suspend fun dated(tracks: List<Track>): List<Track> {
        val music = tracks.filterNot { it.isVideo }
        if (music.isEmpty()) return tracks
        val stored = dao.getAll().associateBy { it.path }
        val read = mutableListOf<FileYearEntity>()
        val yearsById = music.associate { track ->
            val signature = fileSignature(track.path, READ_VERSION)
            val known = stored[track.path]?.takeIf { it.signature == signature }
            val year = if (known != null) known.year else readYear(track.path).also { read += FileYearEntity(track.path, signature, it) }
            track.id to year
        }
        if (read.isNotEmpty()) dao.upsertAll(read)
        return tracks.map { track -> yearsById[track.id]?.let { track.copy(year = it) } ?: track }
    }

    /** The year [path]'s own date tag gives, or null where it has none, or cannot be read. */
    private fun readYear(path: String): Int? = runCatching {
        ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY).use { file ->
            // TagLib takes the descriptor it is handed as its own and closes it: it is given a duplicate.
            val date = TagLib.getMetadata(file.dup().detachFd(), readPictures = false)?.propertyMap?.get("DATE")?.firstOrNull()
            date?.let { YearPattern.find(it)?.value?.toInt() }?.takeIf { it > 0 }
        }
    }.getOrNull()
}
