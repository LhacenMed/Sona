package com.lhacenmed.sona.feature.scanner.mediastore

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.lhacenmed.sona.core.common.cover.withCoverVersion
import com.lhacenmed.sona.core.model.Album
import com.lhacenmed.sona.core.model.Artist
import com.lhacenmed.sona.core.model.Genre
import com.lhacenmed.sona.core.database.stableIdOf
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.core.model.UnknownNames
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Raw result of querying MediaStore's audio and video collections - no folder-exclusion or
 * cross-referencing applied yet. [videos] stand apart from the music: no album, artist or genre is
 * made of them.
 */
data class MediaStoreScanResult(
    val tracks: List<Track>,
    val albums: List<Album>,
    val artists: List<Artist>,
    val genres: List<Genre>,
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
        val artists = queryArtists()
        val coverVersions = HashMap<Long, Long>()
        var tracks = queryTracks(coverVersions)
        val albums = queryAlbums(artists, coverVersions)
        val genres = queryGenres()

        // MediaStore.Audio.Media.GENRE_ID only exists starting on API 30 (R). Below that, genre
        // membership has to be resolved through the separate Genres.Members table instead, and it
        // must run after the tracks are loaded so their mediaStoreId values are known.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val trackIdToGenreId = queryGenreMembership()
            if (trackIdToGenreId.isNotEmpty()) {
                tracks = tracks.map { track ->
                    val genreId = trackIdToGenreId[track.mediaStoreId]
                    if (genreId != null) track.copy(genreId = genreId) else track
                }
            }
        }

        return MediaStoreScanResult(tracks = tracks, albums = albums, artists = artists, genres = genres, videos = queryVideos())
    }

    /**
     * Every audio file MediaStore has, as a track - and into [coverVersions], each album's latest change to
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
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.ARTIST_ID)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(FILE_VERSION_COLUMN)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(MediaStore.Audio.Media.GENRE)
                add(MediaStore.Audio.Media.GENRE_ID)
                add(MediaStore.Audio.Media.DISC_NUMBER)
            }
        }.toTypedArray()

        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val title = cursor.stringOrNull(MediaStore.Audio.Media.TITLE)
                if (title.isNullOrEmpty()) continue

                val path = cursor.stringOrNull(MediaStore.Audio.Media.DATA)
                if (path.isNullOrEmpty()) continue

                val mediaStoreId = cursor.long(MediaStore.Audio.Media._ID)
                val durationMs = cursor.long(MediaStore.Audio.Media.DURATION)
                val artist = cursor.stringOrNull(MediaStore.Audio.Media.ARTIST).orUnknown(UnknownNames.ARTIST)
                val folderPath = File(path).parent.orEmpty()
                val album = cursor.stringOrNull(MediaStore.Audio.Media.ALBUM)
                    .orUnknown(folderPath.substringAfterLast('/').ifEmpty { UnknownNames.ALBUM })
                val albumId = cursor.long(MediaStore.Audio.Media.ALBUM_ID)
                val artistId = cursor.long(MediaStore.Audio.Media.ARTIST_ID)
                val year = cursor.int(MediaStore.Audio.Media.YEAR).takeIf { it > 0 }
                val dateAddedSeconds = cursor.long(MediaStore.Audio.Media.DATE_ADDED)
                val fileVersion = cursor.long(FILE_VERSION_COLUMN)
                coverVersions.merge(albumId, fileVersion, ::maxOf)

                var trackNumber = cursor.stringOrNull(MediaStore.Audio.Media.TRACK).firstNumber()
                    ?: cursor.intOrNull(MediaStore.Audio.Media.TRACK)

                var genre: String? = null
                var genreId: Long? = null
                var discNumber: Int? = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    genre = cursor.stringOrNull(MediaStore.Audio.Media.GENRE)
                    genreId = cursor.longOrNull(MediaStore.Audio.Media.GENRE_ID)
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
                    artistId = artistId,
                    album = album,
                    albumId = albumId,
                    genre = genre,
                    genreId = genreId,
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
                    title = cursor.stringOrNull(MediaStore.Video.Media.TITLE)?.takeIf { it.isNotBlank() }
                        ?: File(path).nameWithoutExtension,
                    artist = cursor.stringOrNull(MediaStore.Video.Media.ARTIST).orUnknown(UnknownNames.ARTIST),
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
                )
            }
        }

        return videos
    }

    private fun queryArtists(): List<Artist> {
        val artists = mutableListOf<Artist>()
        val uri = MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Artists._ID,
            MediaStore.Audio.Artists.ARTIST,
            MediaStore.Audio.Artists.NUMBER_OF_TRACKS,
            MediaStore.Audio.Artists.NUMBER_OF_ALBUMS,
        )

        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.long(MediaStore.Audio.Artists._ID)
                val name = cursor.stringOrNull(MediaStore.Audio.Artists.ARTIST).orUnknown(UnknownNames.ARTIST)
                val trackCount = cursor.int(MediaStore.Audio.Artists.NUMBER_OF_TRACKS)
                val albumCount = cursor.int(MediaStore.Audio.Artists.NUMBER_OF_ALBUMS)
                if (trackCount > 0 && albumCount > 0) {
                    artists += Artist(id = id, name = name, trackCount = trackCount, albumCount = albumCount, coverArtUris = emptyList())
                }
            }
        }

        return artists
    }

    private fun queryAlbums(artists: List<Artist>, coverVersions: Map<Long, Long>): List<Album> {
        val albums = mutableListOf<Album>()
        val uri = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI
        val projection = buildList {
            add(MediaStore.Audio.Albums._ID)
            add(MediaStore.Audio.Albums.ARTIST)
            add(MediaStore.Audio.Albums.ALBUM)
            add(MediaStore.Audio.Albums.NUMBER_OF_SONGS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Albums.ARTIST_ID)
            }
        }.toTypedArray()

        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val trackCount = cursor.int(MediaStore.Audio.Albums.NUMBER_OF_SONGS)
                if (trackCount <= 0) continue

                val id = cursor.long(MediaStore.Audio.Albums._ID)
                val artistName = cursor.stringOrNull(MediaStore.Audio.Albums.ARTIST).orUnknown(UnknownNames.ARTIST)
                val title = cursor.stringOrNull(MediaStore.Audio.Albums.ALBUM).orUnknown(UnknownNames.ALBUM)
                val coverArtUri = albumArtUriOf(id, coverVersions[id])

                // ARTIST_ID was only added to Audio.Albums on API 29 (Q). Below that, join by name
                // against the artists already queried.
                val artistId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.long(MediaStore.Audio.Albums.ARTIST_ID)
                } else {
                    artists.firstOrNull { it.name == artistName }?.id ?: 0L
                }

                albums += Album(
                    id = id,
                    title = title,
                    artistId = artistId,
                    artistName = artistName,
                    coverArtUri = coverArtUri,
                    // Its year, like its count and when it was added, is its tracks' - worked out once the
                    // scanner knows which tracks it has, since MediaStore groups them its own way.
                    year = null,
                    trackCount = trackCount,
                    dateAddedSeconds = 0L,
                )
            }
        }

        return albums
    }

    private fun queryGenres(): List<Genre> {
        val genres = mutableListOf<Genre>()
        val uri = MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME)

        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.long(MediaStore.Audio.Genres._ID)
                val name = cursor.stringOrNull(MediaStore.Audio.Genres.NAME)
                if (!name.isNullOrEmpty()) {
                    genres += Genre(id = id, name = name, trackCount = 0, artistCount = 0, coverArtUris = emptyList())
                }
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
