package com.lhacenmed.sona.feature.scanner

import android.os.Build
import com.lhacenmed.sona.core.common.di.IoDispatcher
import com.lhacenmed.sona.core.database.dao.AlbumDao
import com.lhacenmed.sona.core.database.dao.ArtistDao
import com.lhacenmed.sona.core.database.dao.GenreDao
import com.lhacenmed.sona.core.database.dao.TrackDao
import com.lhacenmed.sona.core.database.entity.*
import com.lhacenmed.sona.core.model.Album
import com.lhacenmed.sona.core.model.Artist
import com.lhacenmed.sona.core.model.Genre
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.scanner.filesystem.ManualFileWalker
import com.lhacenmed.sona.feature.scanner.mediastore.MediaStoreQuerier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Scans the device's audio library and persists the result to Room.
 *
 * Ported from Fossify Music Player's `MediaScanner`: query [MediaStore] first (cheap, indexed,
 * gives cached album art), then - on API 29+ (Q) only - walk storage manually with [ManualFileWalker]
 * to pick up files MediaStore hasn't indexed yet, then diff the final result against what's already
 * in the database and delete anything stale ([cleanup]).
 */
class MediaScanner @Inject constructor(
    private val mediaStoreQuerier: MediaStoreQuerier,
    private val manualFileWalker: ManualFileWalker,
    private val trackDao: TrackDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val genreDao: GenreDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun scan(excludedFolders: Set<String> = emptySet()) = withContext(ioDispatcher) {
        val mediaStoreResult = mediaStoreQuerier.query()

        val albumIds = mediaStoreResult.albums.map { it.id }.toSet()
        val artistIds = mediaStoreResult.artists.map { it.id }.toSet()

        // Drop tracks in an excluded folder, and tracks whose album/artist row didn't make it
        // through MediaStore's own query (Audio.Albums/Audio.Artists only return rows with at
        // least one track/album, so a track referencing a since-emptied album/artist is orphaned).
        var tracks = mediaStoreResult.tracks.filterNot { track ->
            track.folderPath in excludedFolders || track.albumId !in albumIds || track.artistId !in artistIds
        }

        var albums = recomputeAlbums(mediaStoreResult.albums, tracks)
        var artists = recomputeArtists(mediaStoreResult.artists, albums, tracks)
        var genres = recomputeGenres(mediaStoreResult.genres, tracks)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pathsToSkip = tracks.mapTo(mutableSetOf()) { it.path }.apply { addAll(excludedFolders) }
            val manualTracks = manualFileWalker.findTracks(pathsToSkip)

            if (manualTracks.isNotEmpty()) {
                val merged = mergeManualTracks(
                    manualTracks = manualTracks,
                    existingTracks = tracks,
                    existingAlbums = albums,
                    existingArtists = artists,
                    existingGenres = genres,
                )
                tracks = merged.tracks
                albums = merged.albums
                artists = merged.artists
                genres = merged.genres
            }
        }

        persist(tracks, albums, artists, genres)
        cleanup(tracks, albums, artists, genres)
    }

    private fun recomputeAlbums(albums: List<Album>, tracks: List<Track>): List<Album> =
        albums.mapNotNull { album ->
            val albumTracks = tracks.filter { it.albumId == album.id }
            if (albumTracks.isEmpty()) {
                null
            } else {
                album.copy(trackCount = albumTracks.size, dateAddedSeconds = albumTracks.minOf { it.dateAddedSeconds })
            }
        }

    private fun recomputeArtists(artists: List<Artist>, albums: List<Album>, tracks: List<Track>): List<Artist> =
        artists.mapNotNull { artist ->
            val artistAlbums = albums.filter { it.artistId == artist.id }
            val artistTracks = tracks.filter { it.artistId == artist.id }
            if (artistAlbums.isEmpty() || artistTracks.isEmpty()) {
                null
            } else {
                artist.copy(
                    trackCount = artistTracks.size,
                    albumCount = artistAlbums.size,
                    coverArtUri = artistAlbums.firstOrNull { !it.coverArtUri.isNullOrEmpty() }?.coverArtUri,
                )
            }
        }

    private fun recomputeGenres(genres: List<Genre>, tracks: List<Track>): List<Genre> =
        genres.mapNotNull { genre ->
            val genreTracks = tracks.filter { it.genreId == genre.id }
            if (genreTracks.isEmpty()) {
                null
            } else {
                genre.copy(
                    trackCount = genreTracks.size,
                    coverArtUri = genreTracks.firstOrNull { !it.coverArtUri.isNullOrEmpty() }?.coverArtUri,
                )
            }
        }

    private class MergedResult(
        val tracks: List<Track>,
        val albums: List<Album>,
        val artists: List<Artist>,
        val genres: List<Genre>,
    )

    /**
     * Groups the manually-discovered [manualTracks] into artists/albums/genres, matching by name
     * against what MediaStore already produced. A manual track whose artist/album/genre name
     * already exists is attached to that existing row; only genuinely new names get a fresh,
     * stably-hashed id (deterministic across scans, unlike Fossify's original `Any.hashCode()` of
     * the whole mutable object, which changes whenever a count field changes).
     */
    private fun mergeManualTracks(
        manualTracks: List<Track>,
        existingTracks: List<Track>,
        existingAlbums: List<Album>,
        existingArtists: List<Artist>,
        existingGenres: List<Genre>,
    ): MergedResult {
        val artistByName = existingArtists.associateBy { it.name }.toMutableMap()
        val albumByKey = existingAlbums.associateBy { it.artistName to it.title }.toMutableMap()
        val genreByName = existingGenres.associateBy { it.name }.toMutableMap()

        val newArtists = mutableListOf<Artist>()
        val newAlbums = mutableListOf<Album>()
        val newGenres = mutableListOf<Genre>()

        val resolvedTracks = manualTracks.map { track ->
            val artistId = artistByName.getOrPut(track.artist) {
                Artist(id = stableId("artist", track.artist), name = track.artist, trackCount = 0, albumCount = 0, coverArtUri = null)
                    .also { newArtists += it }
            }.id

            val albumId = albumByKey.getOrPut(track.artist to track.album) {
                Album(
                    id = stableId("album", track.artist, track.album),
                    title = track.album,
                    artistId = artistId,
                    artistName = track.artist,
                    coverArtUri = null,
                    year = track.year,
                    trackCount = 0,
                    dateAddedSeconds = track.dateAddedSeconds,
                ).also { newAlbums += it }
            }.id

            val genreId = track.genre?.takeIf { it.isNotEmpty() }?.let { genreName ->
                genreByName.getOrPut(genreName) {
                    Genre(id = stableId("genre", genreName), name = genreName, trackCount = 0, coverArtUri = null)
                        .also { newGenres += it }
                }.id
            }

            track.copy(artistId = artistId, albumId = albumId, genreId = genreId)
        }

        val allTracks = existingTracks + resolvedTracks
        val allAlbums = recomputeAlbums(existingAlbums + newAlbums, allTracks)
        val allArtists = recomputeArtists(existingArtists + newArtists, allAlbums, allTracks)
        val allGenres = recomputeGenres(existingGenres + newGenres, allTracks)

        return MergedResult(tracks = allTracks, albums = allAlbums, artists = allArtists, genres = allGenres)
    }

    private fun stableId(vararg parts: String): Long = parts.joinToString(" ").hashCode().toLong()

    private suspend fun persist(tracks: List<Track>, albums: List<Album>, artists: List<Artist>, genres: List<Genre>) {
        // Artists/albums/genres first so tracks never briefly reference a missing parent row.
        artistDao.insertAll(artists.map { it.toEntity() })
        albumDao.insertAll(albums.map { it.toEntity() })
        genreDao.insertAll(genres.map { it.toEntity() })
        trackDao.insertAll(tracks.map { it.toEntity() })
    }

    /** Deletes whatever is left in the database that isn't part of this scan's final result. */
    private suspend fun cleanup(tracks: List<Track>, albums: List<Album>, artists: List<Artist>, genres: List<Genre>) {
        val keepPaths = tracks.map { it.path }.toSet()
        val stalePaths = trackDao.getAllPaths().filterNot { it in keepPaths }
        if (stalePaths.isNotEmpty()) {
            trackDao.deleteByPaths(stalePaths)
        }

        val keepAlbumIds = albums.map { it.id }.toSet()
        val staleAlbumIds = albumDao.getAllIds().filterNot { it in keepAlbumIds }
        if (staleAlbumIds.isNotEmpty()) {
            albumDao.deleteByIds(staleAlbumIds)
        }

        val keepArtistIds = artists.map { it.id }.toSet()
        val staleArtistIds = artistDao.getAllIds().filterNot { it in keepArtistIds }
        if (staleArtistIds.isNotEmpty()) {
            artistDao.deleteByIds(staleArtistIds)
        }

        val keepGenreIds = genres.map { it.id }.toSet()
        val staleGenreIds = genreDao.getAllIds().filterNot { it in keepGenreIds }
        if (staleGenreIds.isNotEmpty()) {
            genreDao.deleteByIds(staleGenreIds)
        }
    }
}
