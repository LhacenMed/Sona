package com.lhacenmed.sona.feature.scanner.mediastore

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.lhacenmed.sona.core.common.cover.withCoverVersion
import com.lhacenmed.sona.core.common.format.repairedTagText
import com.lhacenmed.sona.core.database.stableIdOf
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.core.model.UnknownNames
import com.lhacenmed.sona.feature.scanner.trackTitleOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/** The device's music and videos as MediaStore has them - no folder-exclusion or cross-referencing applied yet. */
data class MediaStoreScanResult(
    val tracks: List<Track>,
    val videos: List<Track>,
)

private val ALBUM_ART_URI: Uri = Uri.parse("content://media/external/audio/albumart")

/**
 * What changes whenever a file does: MediaStore's own change counter from Android 11, which moves on every
 * change however close together, and the file's modification time before it.
 */
private val FILE_VERSION_COLUMN: String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) MediaStore.MediaColumns.GENERATION_MODIFIED else MediaStore.MediaColumns.DATE_MODIFIED

/** MediaStore's cover for the album [albumId], stamped with [version] - the latest change to any of its files. */
private fun albumArtUriOf(albumId: Long, version: Long?): String =
    ContentUris.withAppendedId(ALBUM_ART_URI, albumId).withCoverVersion(version)
private val GENRE_MEMBERS_URI: Uri = Uri.parse("content://media/external/audio/genres/all/members")

/**
 * Reads the device's audio library out of [MediaStore]. Ported from Fossify Music Player's
 * `MediaScanner.getTracksSync/getArtistsSync/getAlbumsSync/getGenresSync/assignGenreToTracks`.
 */
class MediaStoreQuerier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun query(): MediaStoreScanResult {
        val coverVersions = HashMap<Long, Long>()
        val tracks = queryTracks(coverVersions).map { track ->
            track.copy(albumCoverArtUri = albumArtUriOf(track.albumId, coverVersions[track.albumId]))
        }
        // MediaStore.Audio.Media.GENRE only exists starting on API 30 (R). Below that, a track's genre has
        // to be looked up through the separate Genres tables instead.
        return MediaStoreScanResult(
            tracks = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) tracks else withGenresLookedUp(tracks),
            videos = queryVideos(),
        )
    }

    private fun withGenresLookedUp(tracks: List<Track>): List<Track> {
        val genreNames = queryGenres()
        val genreIds = queryGenreMembership()
        return tracks.map { track -> track.copy(genre = genreIds[track.mediaStoreId]?.let(genreNames::get)) }
    }

    /**
     * Every music file MediaStore has - a ringtone or a notification sound is no music - as a track - and into [coverVersions], each album's latest change to
     * any of its files, which versions its cover (see [albumArtUriOf]).
     *
     * A track's cover is its own file's, stamped with its own latest change - see [withCoverVersion] - so a
     * track shows the picture it holds whatever album it is filed under. Before Android 10, which keeps no
     * thumbnail of an audio file, it is its album's instead.
     */
    private fun queryTracks(coverVersions: MutableMap<Long, Long>): List<Track> {
        val tracks = mutableListOf<Track>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.DATA)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(FILE_VERSION_COLUMN)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(MediaStore.Audio.Media.GENRE)
                add(MediaStore.Audio.Media.ALBUM_ARTIST)
                add(MediaStore.Audio.Media.DISC_NUMBER)
            }
        }.toTypedArray()

        context.contentResolver.query(uri, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val path = cursor.stringOrNull(MediaStore.Audio.Media.DATA)
                if (path.isNullOrEmpty()) continue

                val title = trackTitleOf(cursor.tagTextOrNull(MediaStore.Audio.Media.TITLE), path)

                val mediaStoreId = cursor.long(MediaStore.Audio.Media._ID)
                val durationMs = cursor.long(MediaStore.Audio.Media.DURATION)
                val artist = cursor.tagTextOrNull(MediaStore.Audio.Media.ARTIST).orUnknown(UnknownNames.ARTIST)
                val folderPath = File(path).parent.orEmpty()
                val album = cursor.tagTextOrNull(MediaStore.Audio.Media.ALBUM)
                    .orUnknown(folderPath.substringAfterLast('/').ifEmpty { UnknownNames.ALBUM })
                val albumId = cursor.long(MediaStore.Audio.Media.ALBUM_ID)
                val year = cursor.int(MediaStore.Audio.Media.YEAR).takeIf { it > 0 }
                val dateAddedSeconds = cursor.long(MediaStore.Audio.Media.DATE_ADDED)
                val fileVersion = cursor.long(FILE_VERSION_COLUMN)
                coverVersions.merge(albumId, fileVersion, ::maxOf)

                var trackNumber = cursor.stringOrNull(MediaStore.Audio.Media.TRACK).firstNumber()
                    ?: cursor.intOrNull(MediaStore.Audio.Media.TRACK)

                var genre: String? = null
                var albumArtist: String? = null
                var discNumber: Int? = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    genre = cursor.tagTextOrNull(MediaStore.Audio.Media.GENRE)
                    albumArtist = cursor.tagTextOrNull(MediaStore.Audio.Media.ALBUM_ARTIST)?.takeUnless { it == MediaStore.UNKNOWN_STRING }
                    discNumber = cursor.stringOrNull(MediaStore.Audio.Media.DISC_NUMBER).firstNumber()
                }

                // Pre-R (and some pre-R-tagged) files pack the disc number into the upper digits of
                // TRACK, e.g. disc 2 track 5 is stored as 2005. Unpack it whenever no explicit
                // DISC_NUMBER value was already found.
                if (trackNumber != null && trackNumber >= 1000) {
                    if (discNumber == null) {
                        discNumber = trackNumber / 1000
                    }
                    trackNumber %= 1000
                }

                tracks += Track(
                    // Path-derived rather than auto-generated by Room; see TrackEntity for why.
                    id = stableIdOf(path),
                    mediaStoreId = mediaStoreId,
                    title = title,
                    artist = artist,
                    artistId = 0L,
                    album = album,
                    albumId = albumId,
                    genre = genre,
                    genreId = null,
                    path = path,
                    folderPath = folderPath,
                    durationMs = durationMs,
                    trackNumber = trackNumber,
                    discNumber = discNumber,
                    year = year,
                    dateAddedSeconds = dateAddedSeconds,
                    coverArtUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ContentUris.withAppendedId(uri, mediaStoreId).withCoverVersion(fileVersion)
                    } else {
                        null
                    },
                    isManuallyScanned = false,
                    // MediaStore knows nothing about this; LibraryWriter carries the stored value
                    // forward on every sync, so a rescan never clears favorites.
                    isVideo = false,
                    sizeBytes = cursor.long(MediaStore.Audio.Media.SIZE),
                    albumArtist = albumArtist.orEmpty(),
                )
            }
        }

        // Before Android 10 a track's cover is its album's, whose latest change is only known once all its files are read.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tracks
        } else {
            tracks.map { it.copy(coverArtUri = albumArtUriOf(it.albumId, coverVersions[it.albumId])) }
        }
    }

    /**
     * Every video MediaStore has - none until the user lets Sona read them, which Android answers with an
     * empty collection rather than an error. A video is its own cover - its picture, or a frame of it - drawn
     * from its own uri, stamped with its latest change as a track's is.
     */
    private fun queryVideos(): List<Track> {
        val videos = mutableListOf<Track>()
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.ARTIST,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.SIZE,
            FILE_VERSION_COLUMN,
        )

        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val path = cursor.stringOrNull(MediaStore.Video.Media.DATA)
                if (path.isNullOrEmpty()) continue

                val mediaStoreId = cursor.long(MediaStore.Video.Media._ID)
                val folderPath = File(path).parent.orEmpty()
                videos += Track(
                    id = stableIdOf(path),
                    mediaStoreId = mediaStoreId,
                    title = trackTitleOf(cursor.tagTextOrNull(MediaStore.Video.Media.TITLE), path),
                    artist = cursor.tagTextOrNull(MediaStore.Video.Media.ARTIST).orUnknown(UnknownNames.ARTIST),
                    artistId = 0L,
                    album = folderPath.substringAfterLast('/').ifEmpty { UnknownNames.ALBUM },
                    albumId = 0L,
                    genre = null,
                    genreId = null,
                    path = path,
                    folderPath = folderPath,
                    durationMs = cursor.long(MediaStore.Video.Media.DURATION),
                    trackNumber = null,
                    discNumber = null,
                    year = null,
                    dateAddedSeconds = cursor.long(MediaStore.Video.Media.DATE_ADDED),
                    coverArtUri = ContentUris.withAppendedId(uri, mediaStoreId).withCoverVersion(cursor.long(FILE_VERSION_COLUMN)),
                    isManuallyScanned = false,
                    isVideo = true,
                    width = cursor.int(MediaStore.Video.Media.WIDTH),
                    height = cursor.int(MediaStore.Video.Media.HEIGHT),
                    sizeBytes = cursor.long(MediaStore.Video.Media.SIZE),
                )
            }
        }

        return videos
    }

    private fun queryGenres(): Map<Long, String> {
        val genres = HashMap<Long, String>()
        val uri = MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME)

        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.tagTextOrNull(MediaStore.Audio.Genres.NAME)
                if (!name.isNullOrEmpty()) genres[cursor.long(MediaStore.Audio.Genres._ID)] = name
            }
        }

        return genres
    }

    /**
     * Maps `mediaStoreId -> genreId` via [MediaStore.Audio.Genres.Members], the only way to learn
     * genre membership on API < 30 where [MediaStore.Audio.Media.GENRE_ID] doesn't exist.
     */
    private fun queryGenreMembership(): Map<Long, Long> {
        val result = mutableMapOf<Long, Long>()
        val projection = arrayOf(
            MediaStore.Audio.Genres.Members.GENRE_ID,
            MediaStore.Audio.Genres.Members.AUDIO_ID,
        )

        context.contentResolver.query(GENRE_MEMBERS_URI, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val genreId = cursor.long(MediaStore.Audio.Genres.Members.GENRE_ID)
                val audioId = cursor.long(MediaStore.Audio.Genres.Members.AUDIO_ID)
                result[audioId] = genreId
            }
        }

        return result
    }
}

/**
 * [placeholder] wherever the file named nothing: a missing value, and MediaStore's own `<unknown>`,
 * which it substitutes for one.
 */
private fun String?.orUnknown(placeholder: String): String =
    if (isNullOrEmpty() || this == MediaStore.UNKNOWN_STRING) placeholder else this

private fun Cursor.stringOrNull(column: String): String? {
    val index = getColumnIndex(column)
    return if (index == -1 || isNull(index)) null else getString(index)
}

/** A tag MediaStore read from the file, as the file meant it - see [repairedTagText]. */
private fun Cursor.tagTextOrNull(column: String): String? = stringOrNull(column)?.repairedTagText()

private fun Cursor.long(column: String): Long {
    val index = getColumnIndex(column)
    return if (index == -1) 0L else getLong(index)
}

private fun Cursor.longOrNull(column: String): Long? {
    val index = getColumnIndex(column)
    return if (index == -1 || isNull(index)) null else getLong(index)
}

private fun Cursor.int(column: String): Int {
    val index = getColumnIndex(column)
    return if (index == -1) 0 else getInt(index)
}

private fun Cursor.intOrNull(column: String): Int? {
    val index = getColumnIndex(column)
    return if (index == -1 || isNull(index)) null else getInt(index)
}

/**
 * Parses the leading run of digits out of MediaStore's `TRACK`/`DISC_NUMBER` string values, which
 * are sometimes formatted as `"3/12"` (track 3 of 12).
 */
private fun String?.firstNumber(): Int? =
    this?.trim()
        ?.substringBefore('/')
        ?.takeWhile { it.isDigit() }
        ?.toIntOrNull()
        ?.takeIf { it > 0 }
