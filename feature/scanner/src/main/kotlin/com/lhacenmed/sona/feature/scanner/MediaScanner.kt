package com.lhacenmed.sona.feature.scanner

import android.content.Context
import android.os.Build
import com.lhacenmed.sona.core.common.cover.rankedCoverArtUris
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
import com.lhacenmed.sona.core.model.UnknownNames
import com.lhacenmed.sona.feature.scanner.filesystem.ManualFileWalker
import com.lhacenmed.sona.feature.scanner.mediastore.MediaStoreQuerier
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
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
            val excluded = librarySettings.excludedFolders.value
            scan(excludedFolders = excluded, force = force)
        }
    }

    /**
     * Rescans with the current exclusions and waits for it to finish - for a change the user is
     * watching take effect, such as excluding a folder.
     *
     * The scan itself runs on the application scope, like [requestScan], so a screen closing mid-way
     * cannot leave the library half-rewritten; only the waiting belongs to the caller. A failure is
     * thrown here, to the caller that is waiting on it.
     */
    suspend fun rescan(): SyncStats =
        appScope.async { scan(excludedFolders = librarySettings.excludedFolders.value) }.await()

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

        // From here on every track names exactly one genre, and every genre is one row per name.
        tracks = canonicalGenreTracks(tracks, mediaStoreResult.genres)

        // And exactly one artist and one album, each of them a row per name rather than per
        // MediaStore id - see canonicalArtistTracks.
        val canonicalAlbums = canonicalAlbumsByMediaStoreId(mediaStoreResult.albums)
        tracks = canonicalAlbumTracks(canonicalArtistTracks(tracks), canonicalAlbums)

        var albums = recomputeAlbums(canonicalAlbums.values.distinctBy { it.id }, tracks)
        var artists = recomputeArtists(artistsOf(tracks, albums), albums, tracks)
        var genres = recomputeGenres(genresOf(tracks), tracks)

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

    /**
     * Every track pointed at the one genre for its name, and named after it.
     *
     * MediaStore hands out a genre row per tag occurrence rather than per genre, so the same name
     * arrives several times under different ids - which is what listed "Urbano latino" twice. Auxio
     * groups genres by name, so a genre's id is derived from its name here and every track is
     * repointed at it. A track naming no genre joins [UnknownNames.GENRE], as Auxio gathers those.
     */
    private fun canonicalGenreTracks(tracks: List<Track>, genres: List<Genre>): List<Track> {
        val nameByMediaStoreId = genres.associate { it.id to it.name.trim() }
        return tracks.map { track ->
            val name = track.genreId?.let { nameByMediaStoreId[it] }?.takeIf { it.isNotEmpty() }
                ?: track.genre?.trim()?.takeIf { it.isNotEmpty() }
                ?: UnknownNames.GENRE
            track.copy(genre = name, genreId = stableIdOf("genre", name))
        }
    }

    /**
     * Every track pointed at the one artist for its name, and named after it.
     *
     * MediaStore keys artists by an id of its own, and a file it has not indexed yet has no such id -
     * so [mergeManualTracks] derives one from the name instead. A newly downloaded track therefore
     * arrives under two different artist rows: the walk's, and then MediaStore's once it has indexed
     * the file. Until the scan's final pass sweeps the first one away, both are listed, which is what
     * showed an artist twice. Deriving the id from the name, the way a genre's already is, makes the
     * two arrivals the same row and leaves nothing to sweep.
     */
    private fun canonicalArtistTracks(tracks: List<Track>): List<Track> =
        tracks.map { track ->
            val name = track.artist.orUnknownName(UnknownNames.ARTIST)
            track.copy(artist = name, artistId = artistIdOf(name))
        }

    /**
     * Each of [albums] under the id derived from its artist and title rather than MediaStore's own,
     * for the same reason as [canonicalArtistTracks], kept under the MediaStore id it arrived with so
     * a track can be repointed from one to the other.
     *
     * The cover MediaStore found for the album rides along: it is a URI the row already holds, not
     * something read back from the id.
     */
    private fun canonicalAlbumsByMediaStoreId(albums: List<Album>): Map<Long, Album> =
        albums.associate { album ->
            val artistName = album.artistName.orUnknownName(UnknownNames.ARTIST)
            val title = album.title.orUnknownName(UnknownNames.ALBUM)
            album.id to album.copy(
                id = albumIdOf(artistName, title),
                title = title,
                artistName = artistName,
                artistId = artistIdOf(artistName),
            )
        }

    /** Every track pointed at the one album for its artist and title, and named after it. */
    private fun canonicalAlbumTracks(tracks: List<Track>, albums: Map<Long, Album>): List<Track> =
        tracks.map { track ->
            // Orphaned tracks are already filtered out, so every track's album is one of these.
            val album = albums.getValue(track.albumId)
            track.copy(album = album.title, albumId = album.id)
        }

    /** The artists [tracks] and [albums] name, one row per name - as [genresOf] is for genres. */
    private fun artistsOf(tracks: List<Track>, albums: List<Album>): List<Artist> =
        (tracks.map { it.artist } + albums.map { it.artistName }).distinct().map { name ->
            Artist(
                id = artistIdOf(name),
                name = name,
                trackCount = 0,
                albumCount = 0,
                coverArtUris = emptyList(),
            )
        }

    /** The genres [tracks] name, one row per name - the only genres that can exist after the pass above. */
    private fun genresOf(tracks: List<Track>): List<Genre> =
        tracks.mapNotNull { it.genre }.distinct().map { name ->
            Genre(
                id = stableIdOf("genre", name),
                name = name,
                trackCount = 0,
                artistCount = 0,
                coverArtUris = emptyList(),
            )
        }

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
                    // Auxio falls back to the artist's albums only when none of its tracks has a cover.
                    coverArtUris = rankedCoverArtUris(artistTracks.map { it.coverArtUri })
                        .ifEmpty { rankedCoverArtUris(artistAlbums.map { it.coverArtUri }) },
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
                    artistCount = genreTracks.distinctBy { it.artistId }.size,
                    coverArtUris = rankedCoverArtUris(genreTracks.map { it.coverArtUri }),
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
            // Named the way a MediaStore track is, so the row this joins is the one that already
            // holds it - a tag's stray whitespace is not a second artist.
            val artistName = track.artist.orUnknownName(UnknownNames.ARTIST)
            val albumTitle = track.album.orUnknownName(UnknownNames.ALBUM)

            val artistId = artistByName.getOrPut(artistName) {
                Artist(
                    id = artistIdOf(artistName),
                    name = artistName,
                    trackCount = 0,
                    albumCount = 0,
                    coverArtUris = emptyList(),
                ).also { newArtists += it }
            }.id

            val albumId = albumByKey.getOrPut(artistName to albumTitle) {
                Album(
                    id = albumIdOf(artistName, albumTitle),
                    title = albumTitle,
                    artistId = artistId,
                    artistName = artistName,
                    coverArtUri = null,
                    year = track.year,
                    trackCount = 0,
                    dateAddedSeconds = track.dateAddedSeconds,
                ).also { newAlbums += it }
            }.id

            // A manual file names its genre or joins the unknown one, the rule every track follows.
            val genreName = track.genre?.trim()?.takeIf { it.isNotEmpty() } ?: UnknownNames.GENRE
            val genreId = genreByName.getOrPut(genreName) {
                Genre(
                    id = stableIdOf("genre", genreName),
                    name = genreName,
                    trackCount = 0,
                    artistCount = 0,
                    coverArtUris = emptyList(),
                ).also { newGenres += it }
            }.id

            track.copy(
                artist = artistName,
                artistId = artistId,
                album = albumTitle,
                albumId = albumId,
                genre = genreName,
                genreId = genreId,
            )
        }

        val allTracks = existingTracks + resolvedTracks
        val allAlbums = recomputeAlbums(existingAlbums + newAlbums, allTracks)
        val allArtists = recomputeArtists(existingArtists + newArtists, allAlbums, allTracks)
        val allGenres = recomputeGenres(existingGenres + newGenres, allTracks)

        return MergedResult(allTracks, allAlbums, allArtists, allGenres)
    }
}

/**
 * The name a row goes under: what the file said, trimmed - or [unknown], when it said nothing.
 *
 * MediaStore normalises the names it reports; a file read straight off the disk by
 * [ManualFileWalker] is reported exactly as it was tagged. Both pass through here, so the two
 * cannot name the same artist or album differently and end up as two rows.
 */
private fun String?.orUnknownName(unknown: String): String =
    this?.trim()?.takeIf { it.isNotEmpty() } ?: unknown

/** An artist's id, derived from its name - the one place it is worked out. */
private fun artistIdOf(name: String): Long = stableIdOf("artist", name)

/** An album's id, derived from the artist and title that tell it apart from every other album. */
private fun albumIdOf(artistName: String, title: String): Long = stableIdOf("album", artistName, title)
