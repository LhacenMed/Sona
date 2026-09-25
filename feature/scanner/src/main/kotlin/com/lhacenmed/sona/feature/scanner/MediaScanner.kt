package com.lhacenmed.sona.feature.scanner

import android.content.Context
import android.os.Build
import com.lhacenmed.sona.core.common.cover.rankedCoverArtUris
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.common.di.IoDispatcher
import com.lhacenmed.sona.core.data.LibraryWriter
import com.lhacenmed.sona.core.data.SyncStats
import com.lhacenmed.sona.core.database.dao.TrackDao
import com.lhacenmed.sona.core.database.entity.toDomain
import com.lhacenmed.sona.core.database.stableIdOf
import com.lhacenmed.sona.core.datastore.LibrarySettings
import com.lhacenmed.sona.core.datastore.ScanSettings
import com.lhacenmed.sona.core.model.Album
import com.lhacenmed.sona.core.model.Artist
import com.lhacenmed.sona.core.model.Genre
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.core.model.UnknownNames
import com.lhacenmed.sona.feature.scanner.filesystem.ManualFileWalker
import com.lhacenmed.sona.feature.scanner.mediastore.MediaStoreChangeObserver
import com.lhacenmed.sona.feature.scanner.mediastore.MediaStoreQuerier
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * How long MediaStore must stay quiet before a change is acted on. It reports a single download
 * several times over (inserted pending, then scanned, then published) and an album as dozens of
 * files; waiting for the burst to settle turns all of it into one refresh.
 */
private const val CHANGE_SETTLE_MS = 500L

/** How much of the pipeline a scan runs, ordered narrowest first so two requests merge as the wider. */
private enum class ScanKind {
    /** MediaStore reported a change: re-read it, keeping what the last storage walk found. */
    REFRESH,

    /** MediaStore and the storage walk - skipped when the device reports nothing changed. */
    FULL,

    /** [FULL], even when the device reports nothing changed. */
    FORCED,
}

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
 * Once the first scan is requested, the library also follows the device live: every change
 * MediaStore reports ([MediaStoreChangeObserver]) is a [ScanKind.REFRESH] - MediaStore re-read and
 * diffed, without walking storage again - so a download appears without a relaunch or a rescan.
 *
 * Requests are queued, not dropped: one arriving mid-scan runs once that scan ends, and any number
 * arriving meanwhile collapse into that one, as the widest of them. Scans are serialised by a
 * [Mutex] and run on the [ApplicationScope], so a rotation or a finished activity can neither start
 * a second concurrent scan nor cancel one in flight.
 */
@OptIn(FlowPreview::class)
@Singleton
class MediaScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaStoreQuerier: MediaStoreQuerier,
    private val manualFileWalker: ManualFileWalker,
    private val libraryWriter: LibraryWriter,
    private val trackDao: TrackDao,
    private val librarySettings: LibrarySettings,
    private val scanSettings: ScanSettings,
    mediaStoreChangeObserver: MediaStoreChangeObserver,
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

    /** The widest scan asked for since the queue last took one; null when nothing is waiting. */
    private val pendingScan = AtomicReference<ScanKind?>(null)

    /** Wakes the queue. Conflated, since [pendingScan] already holds everything a wake-up means. */
    private val scanWakeups = Channel<Unit>(Channel.CONFLATED)

    /**
     * Follows MediaStore for as long as the process lives. Lazy, because watching - like scanning -
     * needs the permission the first [requestScan] is only ever made with.
     */
    private val changeWatch = appScope.launch(start = CoroutineStart.LAZY) {
        mediaStoreChangeObserver.changes
            .debounce(CHANGE_SETTLE_MS)
            .collect { enqueue(ScanKind.REFRESH) }
    }

    init {
        appScope.launch {
            scanWakeups.consumeEach { pendingScan.getAndSet(null)?.let { scan(it) } }
        }
    }

    /**
     * Requests a scan and returns immediately.
     *
     * This is what launch should call. It runs on the application scope, so it is not tied to the
     * activity that asked for it - the previous code launched it from `lifecycleScope` in
     * `onCreate`, which restarted the whole scan on every configuration change. [force] skips the
     * unchanged-library check (an explicit "rescan", where the user's intent overrides it).
     */
    fun requestScan(force: Boolean = false) {
        changeWatch.start()
        enqueue(if (force) ScanKind.FORCED else ScanKind.FULL)
    }

    /**
     * Rescans with the current exclusions and waits for it to finish - for a change the user is
     * watching take effect, such as excluding a folder.
     *
     * The scan itself runs on the application scope, like [requestScan], so a screen closing mid-way
     * cannot leave the library half-rewritten; only the waiting belongs to the caller. A failure is
     * thrown here, to the caller that is waiting on it.
     */
    suspend fun rescan(): SyncStats = appScope.async { scan(ScanKind.FULL) }.await()

    /**
     * Takes the tracks [trackIds] name out of the library the moment their files are deleted, then
     * queues a [ScanKind.REFRESH] behind it to bring their albums, artists and genres up to date -
     * MediaStore re-read without walking storage, which is all a deletion changes. Only the first part
     * is waited on, so a deletion is gone from every list at once.
     */
    suspend fun forgetTracks(trackIds: Collection<Long>) {
        libraryWriter.deleteTracks(trackIds)
        enqueue(ScanKind.REFRESH)
    }

    private fun enqueue(kind: ScanKind) {
        pendingScan.getAndUpdate { pending -> if (pending == null || kind > pending) kind else pending }
        scanWakeups.trySend(Unit)
    }

    /** Runs a scan with the current exclusions, awaiting its completion. */
    private suspend fun scan(kind: ScanKind): SyncStats =
        scanMutex.withLock {
            withContext(ioDispatcher) {
                val excludedFolders = librarySettings.excludedFolders.value
                val signature = scanSignatureOf(context, excludedFolders)
                if (kind != ScanKind.FORCED && canSkip(signature)) return@withContext SyncStats()

                _isScanning.value = true
                try {
                    val stats = runScan(excludedFolders, walksStorage = kind != ScanKind.REFRESH)
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

    private suspend fun runScan(excludedFolders: Set<String>, walksStorage: Boolean): SyncStats {
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
        // MediaStore id - see canonicalArtistTracks. An artist is spelled one way on its tracks and
        // on the albums credited to it, so both are counted towards how it is spelled.
        val artistSpellings = commonestSpellings(
            tracks.map { it.artist.orUnknownName(UnknownNames.ARTIST) } +
                mediaStoreResult.albums.map { it.artistName.orUnknownName(UnknownNames.ARTIST) },
        )
        val canonicalAlbums = canonicalAlbumsByMediaStoreId(mediaStoreResult.albums, tracks, artistSpellings)
        tracks = canonicalAlbumTracks(canonicalArtistTracks(tracks, artistSpellings), canonicalAlbums)

        var albums = recomputeAlbums(canonicalAlbums.values.distinctBy { it.id }, tracks)
        var artists = recomputeArtists(artistsOf(tracks, albums), albums, tracks)
        var genres = recomputeGenres(genresOf(tracks), tracks)

        var stats = SyncStats()
        val manualTracks = if (walksStorage) {
            // Stage 1: publish MediaStore's fast, already-indexed results right away, without
            // deletions - on a first run this is what paints the library, and the slow filesystem
            // walk below never gets to delay it. Because it is a diff, on any later scan it writes
            // nothing.
            stats = libraryWriter.sync(tracks, albums, artists, genres, deleteMissing = false)
            // Stage 2: pick up files MediaStore hasn't indexed yet.
            findUnindexedTracks(tracks, excludedFolders)
        } else {
            // A refresh: MediaStore is all that changed, so what the last walk found is kept as is.
            storedUnindexedTracks(tracks)
        }

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

        // Final pass: the authoritative one, and the only one allowed to delete. When stage 1
        // already stored exactly this, it opens no transaction at all.
        stats += libraryWriter.sync(tracks, albums, artists, genres)
        return stats
    }

    /** Audio files on storage that MediaStore has not indexed, found by walking it (Q+ only). */
    private fun findUnindexedTracks(indexedTracks: List<Track>, excludedFolders: Set<String>): List<Track> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
        val pathsToSkip = indexedTracks.mapTo(HashSet()) { it.path }.apply { addAll(excludedFolders) }
        return manualFileWalker.findTracks(pathsToSkip)
    }

    /**
     * What the last walk found and MediaStore still has not indexed, less any file since deleted -
     * the walk's result, carried into a refresh without walking again. A file MediaStore has indexed
     * since is left to MediaStore's row, which shares its path-derived id.
     */
    private suspend fun storedUnindexedTracks(indexedTracks: List<Track>): List<Track> {
        val indexedPaths = indexedTracks.mapTo(HashSet()) { it.path }
        return trackDao.getManuallyScanned()
            .filter { it.path !in indexedPaths && File(it.path).exists() }
            .map { it.toDomain() }
    }

    // Grouping once up front turns what used to be an O(entities * tracks) scan (each album/
    // artist/genre re-filtering the *entire* track list) into a single O(tracks) pass - the
    // dominant cost of every scan on any library past a few hundred tracks.

    /**
     * Every track pointed at the one genre for its name, and named after it.
     *
     * MediaStore hands out a genre row per tag occurrence rather than per genre, so the same name
     * arrives several times under different ids - which is what listed "Urbano latino" twice. Auxio
     * groups genres by name, whatever its case, so a genre's id is derived from its [nameKey] here,
     * every track is repointed at it and named the way most of them spell it. A track naming no
     * genre joins [UnknownNames.GENRE], as Auxio gathers those.
     */
    private fun canonicalGenreTracks(tracks: List<Track>, genres: List<Genre>): List<Track> {
        val nameByMediaStoreId = genres.associate { it.id to it.name.trim() }
        val names = tracks.map { track ->
            track.genreId?.let { nameByMediaStoreId[it] }?.takeIf { it.isNotEmpty() }
                ?: track.genre?.trim()?.takeIf { it.isNotEmpty() }
                ?: UnknownNames.GENRE
        }
        val spellings = commonestSpellings(names)
        return tracks.mapIndexed { index, track ->
            val name = spellings.getValue(nameKey(names[index]))
            track.copy(genre = name, genreId = genreIdOf(name))
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
     *
     * The name is taken whatever its case, as Auxio clusters artists: "5 Seconds of Summer" and "5
     * Seconds Of Summer" are one artist, spelled on every track as [spellings] says most spell it.
     */
    private fun canonicalArtistTracks(tracks: List<Track>, spellings: Map<String, String>): List<Track> =
        tracks.map { track ->
            val name = spellings.getValue(nameKey(track.artist.orUnknownName(UnknownNames.ARTIST)))
            track.copy(artist = name, artistId = artistIdOf(name))
        }

    /**
     * Each of [albums] under the id derived from its artist and title rather than MediaStore's own,
     * for the same reason as [canonicalArtistTracks], kept under the MediaStore id it arrived with so
     * a track can be repointed from one to the other.
     *
     * The cover MediaStore found for the album rides along: it is a URI the row already holds, not
     * something read back from the id.
     *
     * Like an artist, an album is its artist and title whatever their case, so MediaStore rows that
     * differ only in that are one album - titled the way most of its [tracks] have it, and credited to
     * its artist as [artistSpellings] spells them.
     */
    private fun canonicalAlbumsByMediaStoreId(
        albums: List<Album>,
        tracks: List<Track>,
        artistSpellings: Map<String, String>,
    ): Map<Long, Album> {
        fun artistNameOf(album: Album) =
            artistSpellings.getValue(nameKey(album.artistName.orUnknownName(UnknownNames.ARTIST)))
        fun keyOf(album: Album) = nameKey(artistNameOf(album)) to nameKey(album.title.orUnknownName(UnknownNames.ALBUM))

        val albumsByMediaStoreId = albums.associateBy { it.id }
        // Each track votes for the title its own MediaStore row gave the album.
        val titleSpellings = tracks
            .mapNotNull { track -> albumsByMediaStoreId[track.albumId] }
            .groupBy(::keyOf) { it.title.orUnknownName(UnknownNames.ALBUM) }
            .mapValues { (_, titles) -> commonest(titles) }

        return albums.associate { album ->
            val artistName = artistNameOf(album)
            val title = titleSpellings[keyOf(album)] ?: album.title.orUnknownName(UnknownNames.ALBUM)
            album.id to album.copy(
                id = albumIdOf(artistName, title),
                title = title,
                artistName = artistName,
                artistId = artistIdOf(artistName),
            )
        }
    }

    /** Every track pointed at the one album for its artist and title, and named after it. */
    private fun canonicalAlbumTracks(tracks: List<Track>, albums: Map<Long, Album>): List<Track> =
        tracks.map { track ->
            // Orphaned tracks are already filtered out, so every track's album is one of these.
            val album = albums.getValue(track.albumId)
            track.copy(album = album.title, albumId = album.id)
        }

    /** The artists [tracks] and [albums] name, one row per name - as [genresOf] is for genres. Both are spelled canonically by now. */
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
                id = genreIdOf(name),
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
        // Keyed by name whatever its case, so a file MediaStore missed joins the row its name already
        // has, and takes that row's spelling rather than adding another.
        val artistByName = existingArtists.associateBy { nameKey(it.name) }.toMutableMap()
        val albumByKey = existingAlbums.associateBy { nameKey(it.artistName) to nameKey(it.title) }.toMutableMap()
        val genreByName = existingGenres.associateBy { nameKey(it.name) }.toMutableMap()

        val newArtists = mutableListOf<Artist>()
        val newAlbums = mutableListOf<Album>()
        val newGenres = mutableListOf<Genre>()

        val resolvedTracks = manualTracks.map { track ->
            // Named the way a MediaStore track is, so the row this joins is the one that already
            // holds it - a tag's stray whitespace is not a second artist.
            val taggedArtist = track.artist.orUnknownName(UnknownNames.ARTIST)
            val taggedAlbum = track.album.orUnknownName(UnknownNames.ALBUM)

            val artist = artistByName.getOrPut(nameKey(taggedArtist)) {
                Artist(
                    id = artistIdOf(taggedArtist),
                    name = taggedArtist,
                    trackCount = 0,
                    albumCount = 0,
                    coverArtUris = emptyList(),
                ).also { newArtists += it }
            }

            val album = albumByKey.getOrPut(nameKey(artist.name) to nameKey(taggedAlbum)) {
                Album(
                    id = albumIdOf(artist.name, taggedAlbum),
                    title = taggedAlbum,
                    artistId = artist.id,
                    artistName = artist.name,
                    coverArtUri = null,
                    year = track.year,
                    trackCount = 0,
                    dateAddedSeconds = track.dateAddedSeconds,
                ).also { newAlbums += it }
            }

            // A manual file names its genre or joins the unknown one, the rule every track follows.
            val taggedGenre = track.genre?.trim()?.takeIf { it.isNotEmpty() } ?: UnknownNames.GENRE
            val genre = genreByName.getOrPut(nameKey(taggedGenre)) {
                Genre(
                    id = genreIdOf(taggedGenre),
                    name = taggedGenre,
                    trackCount = 0,
                    artistCount = 0,
                    coverArtUris = emptyList(),
                ).also { newGenres += it }
            }

            track.copy(
                artist = artist.name,
                artistId = artist.id,
                album = album.title,
                albumId = album.id,
                genre = genre.name,
                genreId = genre.id,
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

/**
 * What tells one name from another: the name, whatever its case - Auxio's `rawName.lowercase()`. Two
 * spellings with the same key are one artist, album or genre.
 */
private fun nameKey(name: String): String = name.lowercase(Locale.ROOT)

/**
 * The spelling most of [spellings] use - Auxio melds a cluster into its most popular variant. A tie
 * goes to the first in order, so every scan settles on the same one.
 */
private fun commonest(spellings: List<String>): String =
    spellings.groupingBy { it }.eachCount().entries
        .minWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .key

/** The [commonest] spelling of every [nameKey] among [names]. */
private fun commonestSpellings(names: List<String>): Map<String, String> =
    names.groupBy(::nameKey).mapValues { (_, spellings) -> commonest(spellings) }

/** An artist's id, derived from its name - the one place it is worked out. */
private fun artistIdOf(name: String): Long = stableIdOf("artist", nameKey(name))

/** An album's id, derived from the artist and title that tell it apart from every other album. */
private fun albumIdOf(artistName: String, title: String): Long = stableIdOf("album", nameKey(artistName), nameKey(title))

/** A genre's id, derived from its name. */
private fun genreIdOf(name: String): Long = stableIdOf("genre", nameKey(name))
