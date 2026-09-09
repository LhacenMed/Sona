package com.lhacenmed.sona.feature.scanner

import android.content.Context
import android.os.Build
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.common.di.IoDispatcher
import com.lhacenmed.sona.core.data.LibraryWriter
import com.lhacenmed.sona.core.data.SyncStats
import com.lhacenmed.sona.core.database.dao.TrackDao
import com.lhacenmed.sona.core.database.stableIdOf
import com.lhacenmed.sona.core.datastore.LibrarySettings
import com.lhacenmed.sona.core.datastore.ScanSettings
import com.lhacenmed.sona.core.model.Album
import com.lhacenmed.sona.core.model.Artist
import com.lhacenmed.sona.core.model.Genre
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.scanner.filesystem.ManualFileWalker
import com.lhacenmed.sona.feature.scanner.mediastore.MediaStoreQuerier
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Scans the device's audio library and reconciles it into Room.
 *
 * Still the Fossify pipeline - query MediaStore first because it is cheap and already indexed, then
 * (Q+ only) top it up with a manual filesystem walk for files MediaStore hasn't picked up. Two
 * things changed, and both exist to make a *relaunch* cost nothing:
 *
 *  1. **The scan is skipped when the device says nothing changed.** [scanSignatureOf] asks
 *     MediaStore for its version and generation counter; if they match the last completed scan and
 *     the database already holds tracks, the whole pipeline is skipped.
 *  2. **When it does run, it writes a difference, not a rewrite.** [LibraryWriter] compares the
 *     result against what is stored and issues only genuine changes - usually none.
 *
 * Together those mean the second launch of an unchanged library performs zero database writes, so
 * nothing invalidates, nothing re-emits and nothing repaints. Previously every launch rewrote every
 * row, which is exactly what the UI was reacting to.
 *
 * Scans are serialised by a [Mutex] and launched on the [ApplicationScope], so a rotation or a
 * finished activity can neither start a second concurrent scan nor cancel one in flight.
 */
@Singleton
class MediaScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaStoreQuerier: MediaStoreQuerier,
    private val manualFileWalker: ManualFileWalker,
    private val libraryWriter: LibraryWriter,
    private val trackDao: TrackDao,
    private val librarySettings: LibrarySettings,
    private val scanSettings: ScanSettings,
    @ApplicationScope private val appScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val _isScanning = MutableStateFlow(false)

    /**
     * Whether a scan is currently in progress - lets the UI show "scanning" instead of a misleading
     * "no tracks"/"no permission" state while the library is still empty.
     */
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val scanMutex = Mutex()
    private var scanJob: Job? = null

    /**
     * Requests a scan and returns immediately.
     *
     * This is what launch should call. It runs on the application scope, so it is not tied to the
     * activity that asked for it - the previous code launched it from `lifecycleScope` in
     * `onCreate`, which restarted the whole scan on every configuration change.
     */
    fun requestScan(force: Boolean = false) {
        if (scanJob?.isActive == true && !force) return
        scanJob = appScope.launch {
            val excluded = librarySettings.excludedFolders.first()
            scan(excludedFolders = excluded, force = force)
        }
    }

    /**
     * Runs a scan, awaiting its completion. [force] skips the unchanged-library check (used by an
     * explicit "rescan" action, where the user's intent overrides the optimisation).
     */
    suspend fun scan(excludedFolders: Set<String> = emptySet(), force: Boolean = false): SyncStats =
        scanMutex.withLock {
            withContext(ioDispatcher) {
                val signature = scanSignatureOf(context, excludedFolders)
                if (!force && canSkip(signature)) return@withContext SyncStats()

                _isScanning.value = true
                try {
                    val stats = runScan(excludedFolders)
                    scanSettings.setLastScanSignature(signature)
                    stats
                } finally {
                    _isScanning.value = false
                }
            }
        }

    /**
     * The library is considered current when the device reports the same media state as the last
     * completed scan *and* there is actually something stored - a matching signature over an empty
     * database would otherwise permanently suppress the first real scan.
     */
    private suspend fun canSkip(signature: String): Boolean {
        if (!signature.isSkippableSignature()) return false
        if (scanSettings.lastScanSignature.first() != signature) return false
        return trackDao.count() > 0
    }

    private suspend fun runScan(excludedFolders: Set<String>): SyncStats {
        val mediaStoreResult = mediaStoreQuerier.query()

        val albumIds = mediaStoreResult.albums.mapTo(HashSet()) { it.id }
        val artistIds = mediaStoreResult.artists.mapTo(HashSet()) { it.id }

        // Drop tracks in an excluded folder, and tracks whose album/artist row didn't make it
        // through MediaStore's own query (Audio.Albums/Audio.Artists only return rows with at least
        // one track/album, so a track referencing a since-emptied album/artist is orphaned).
        var tracks = mediaStoreResult.tracks.filterNot { track ->
            track.folderPath in excludedFolders ||
                track.albumId !in albumIds ||
                track.artistId !in artistIds
        }

        var albums = recomputeAlbums(mediaStoreResult.albums, tracks)
        var artists = recomputeArtists(mediaStoreResult.artists, albums, tracks)
        var genres = recomputeGenres(mediaStoreResult.genres, tracks)

        // Stage 1: publish MediaStore's fast, already-indexed results right away, without
        // deletions - on a first run this is what paints the library, and the slow filesystem walk
        // below never gets to delay it. Because it is a diff, on any later scan it writes nothing.
        var stats = libraryWriter.sync(tracks, albums, artists, genres, deleteMissing = false)

        // Stage 2 (Q+ only): pick up files MediaStore hasn't indexed yet.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pathsToSkip = tracks.mapTo(HashSet()) { it.path }.apply { addAll(excludedFolders) }
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

        // Final pass: the authoritative one, and the only one allowed to delete. When stage 1
        // already stored exactly this, it opens no transaction at all.
        stats += libraryWriter.sync(tracks, albums, artists, genres)
        return stats
    }

    // Grouping once up front turns what used to be an O(entities * tracks) scan (each album/
    // artist/genre re-filtering the *entire* track list) into a single O(tracks) pass - the
    // dominant cost of every scan on any library past a few hundred tracks.

    private fun recomputeAlbums(albums: List<Album>, tracks: List<Track>): List<Album> {
        val tracksByAlbumId = tracks.groupBy { it.albumId }
        return albums.mapNotNull { album ->
            val albumTracks = tracksByAlbumId[album.id]
            if (albumTracks.isNullOrEmpty()) {
                null
            } else {
                album.copy(
                    trackCount = albumTracks.size,
                    dateAddedSeconds = albumTracks.minOf { it.dateAddedSeconds },
                )
            }
        }
    }

    private fun recomputeArtists(
        artists: List<Artist>,
        albums: List<Album>,
        tracks: List<Track>,
    ): List<Artist> {
        val albumsByArtistId = albums.groupBy { it.artistId }
        val tracksByArtistId = tracks.groupBy { it.artistId }
        return artists.mapNotNull { artist ->
            val artistAlbums = albumsByArtistId[artist.id]
            val artistTracks = tracksByArtistId[artist.id]
            if (artistAlbums.isNullOrEmpty() || artistTracks.isNullOrEmpty()) {
                null
            } else {
                artist.copy(
                    trackCount = artistTracks.size,
                    albumCount = artistAlbums.size,
                    coverArtUri = artistAlbums.firstOrNull { !it.coverArtUri.isNullOrEmpty() }?.coverArtUri,
                )
            }
        }
    }

    private fun recomputeGenres(genres: List<Genre>, tracks: List<Track>): List<Genre> {
        val tracksByGenreId = tracks.groupBy { it.genreId }
        return genres.mapNotNull { genre ->
            val genreTracks = tracksByGenreId[genre.id]
            if (genreTracks.isNullOrEmpty()) {
                null
            } else {
                genre.copy(
                    trackCount = genreTracks.size,
                    coverArtUri = genreTracks.firstOrNull { !it.coverArtUri.isNullOrEmpty() }?.coverArtUri,
                )
            }
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
     * against what MediaStore already produced. A manual track whose artist/album/genre name already
     * exists is attached to that existing row; only genuinely new names get a fresh id, derived with
     * [stableIdOf] so it is identical on every future scan and cannot collide with a MediaStore id.
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
                Artist(
                    id = stableIdOf("artist", track.artist),
                    name = track.artist,
                    trackCount = 0,
                    albumCount = 0,
                    coverArtUri = null,
                ).also { newArtists += it }
            }.id

            val albumId = albumByKey.getOrPut(track.artist to track.album) {
                Album(
                    id = stableIdOf("album", track.artist, track.album),
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
                    Genre(
                        id = stableIdOf("genre", genreName),
                        name = genreName,
                        trackCount = 0,
                        coverArtUri = null,
                    ).also { newGenres += it }
                }.id
            }

            track.copy(artistId = artistId, albumId = albumId, genreId = genreId)
        }

        val allTracks = existingTracks + resolvedTracks
        val allAlbums = recomputeAlbums(existingAlbums + newAlbums, allTracks)
        val allArtists = recomputeArtists(existingArtists + newArtists, allAlbums, allTracks)
        val allGenres = recomputeGenres(existingGenres + newGenres, allTracks)

        return MergedResult(allTracks, allAlbums, allArtists, allGenres)
    }
}
