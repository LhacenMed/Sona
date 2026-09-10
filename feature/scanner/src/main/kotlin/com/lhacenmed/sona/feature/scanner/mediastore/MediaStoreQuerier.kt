package com.lhacenmed.sona.feature.scanner.mediastore

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.lhacenmed.sona.core.model.Album
import com.lhacenmed.sona.core.model.Artist
import com.lhacenmed.sona.core.model.Genre
import com.lhacenmed.sona.core.database.stableIdOf
import com.lhacenmed.sona.core.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/** Raw result of querying MediaStore's audio collection - no folder-exclusion or cross-referencing applied yet. */
data class MediaStoreScanResult(
    val tracks: List<Track>,
    val albums: List<Album>,
    val artists: List<Artist>,
    val genres: List<Genre>,
)

private val ALBUM_ART_URI: Uri = Uri.parse("content://media/external/audio/albumart")
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
        val albums = queryAlbums(artists)
        var tracks = queryTracks()
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

        return MediaStoreScanResult(tracks = tracks, albums = albums, artists = artists, genres = genres)
    }

    private fun queryTracks(): List<Track> {
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
                val artist = cursor.stringOrNull(MediaStore.Audio.Media.ARTIST) ?: MediaStore.UNKNOWN_STRING
                val folderPath = File(path).parent.orEmpty()
                val album = cursor.stringOrNull(MediaStore.Audio.Media.ALBUM)
                    ?: folderPath.substringAfterLast('/').ifEmpty { MediaStore.UNKNOWN_STRING }
                val albumId = cursor.long(MediaStore.Audio.Media.ALBUM_ID)
                val artistId = cursor.long(MediaStore.Audio.Media.ARTIST_ID)
                val year = cursor.int(MediaStore.Audio.Media.YEAR).takeIf { it > 0 }
                val dateAddedSeconds = cursor.long(MediaStore.Audio.Media.DATE_ADDED)
                val coverArtUri = ContentUris.withAppendedId(ALBUM_ART_URI, albumId).toString()

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
                    coverArtUri = coverArtUri,
                    isManuallyScanned = false,
                    // MediaStore knows nothing about this; LibraryWriter carries the stored value
                    // forward on every sync, so a rescan never clears favourites.
                    isFavorite = false,
                )
            }
        }

        return tracks
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
                val name = cursor.stringOrNull(MediaStore.Audio.Artists.ARTIST) ?: MediaStore.UNKNOWN_STRING
                val trackCount = cursor.int(MediaStore.Audio.Artists.NUMBER_OF_TRACKS)
                val albumCount = cursor.int(MediaStore.Audio.Artists.NUMBER_OF_ALBUMS)
                if (trackCount > 0 && albumCount > 0) {
                    artists += Artist(id = id, name = name, trackCount = trackCount, albumCount = albumCount, coverArtUri = null)
                }
            }
        }

        return artists
    }

    private fun queryAlbums(artists: List<Artist>): List<Album> {
        val albums = mutableListOf<Album>()
        val uri = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI
        val projection = buildList {
            add(MediaStore.Audio.Albums._ID)
            add(MediaStore.Audio.Albums.ARTIST)
            add(MediaStore.Audio.Albums.FIRST_YEAR)
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
                val artistName = cursor.stringOrNull(MediaStore.Audio.Albums.ARTIST) ?: MediaStore.UNKNOWN_STRING
                val title = cursor.stringOrNull(MediaStore.Audio.Albums.ALBUM) ?: MediaStore.UNKNOWN_STRING
                val year = cursor.int(MediaStore.Audio.Albums.FIRST_YEAR).takeIf { it > 0 }
                val coverArtUri = ContentUris.withAppendedId(ALBUM_ART_URI, id).toString()

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
                    year = year,
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
                    genres += Genre(id = id, name = name, trackCount = 0, coverArtUri = null)
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
