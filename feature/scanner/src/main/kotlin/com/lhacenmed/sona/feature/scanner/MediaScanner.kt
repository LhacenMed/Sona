package com.lhacenmed.sona.feature.scanner

import android.content.Context
import android.os.Build
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.common.di.IoDispatcher
import com.lhacenmed.sona.core.common.lifecycle.isAppInForeground
import com.lhacenmed.sona.core.data.GONE_FILE_SETTLE_MS
import com.lhacenmed.sona.core.data.LibraryWriter
import com.lhacenmed.sona.core.data.SyncStats
import com.lhacenmed.sona.core.data.commonestSpelling
import com.lhacenmed.sona.core.database.dao.TrackDao
import com.lhacenmed.sona.core.database.entity.toDomain
import com.lhacenmed.sona.core.database.albumIdOf
import com.lhacenmed.sona.core.database.artistIdOf
import com.lhacenmed.sona.core.database.genreIdOf
import com.lhacenmed.sona.core.database.nameKey
import com.lhacenmed.sona.core.datastore.LibrarySettings
import com.lhacenmed.sona.core.datastore.ScanSettings
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.core.model.UnknownNames
import com.lhacenmed.sona.feature.scanner.filesystem.ManualFileWalker
import com.lhacenmed.sona.feature.scanner.mediastore.MediaStoreChangeObserver
import com.lhacenmed.sona.feature.scanner.mediastore.MediaStoreQuerier
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
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
 * Scans the device's audio library - and its videos, played as audio - and reconciles it into Room.
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
 * So is every return to the foreground: a process in the background is frozen once nothing keeps it
 * alive - nothing playing - and MediaStore's reports wait until it thaws, arriving seconds late and
 * piecemeal. Re-reading on return makes the library current at once, playing or not; when nothing
 * changed meanwhile, the signature check makes it cost one counter read.
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

    private val _progress = MutableStateFlow<ScanProgress?>(null)

    /** Where the scan in progress is - what the rescan notification shows - or null while none runs. */
    val progress: StateFlow<ScanProgress?> = _progress.asStateFlow()

    /**
     * Whether a scan is currently in progress - lets the UI show "scanning" instead of a misleading
     * "no tracks"/"no permission" state while the library is still empty.
     */
    val isScanning: StateFlow<Boolean> = _progress
        .map { it != null }
        .stateIn(appScope, SharingStarted.Eagerly, false)

    private val scanMutex = Mutex()

    /** The widest scan asked for since the queue last took one; null when nothing is waiting. */
    private val pendingScan = AtomicReference<ScanKind?>(null)

    /** Wakes the queue. Conflated, since [pendingScan] already holds everything a wake-up means. */
    private val scanWakeups = Channel<Unit>(Channel.CONFLATED)

    /**
     * Follows MediaStore, and catches up on every return to the foreground, for as long as the
     * process lives. Lazy, because watching - like scanning - needs the permission the first
     * [requestScan] is only ever made with.
     */
    private val changeWatch = appScope.launch(start = CoroutineStart.LAZY) {
        merge(
            // What the device did is followed only while the user lets it; turning that back on catches up at once.
            merge(mediaStoreChangeObserver.changes.debounce(CHANGE_SETTLE_MS), returnsToForeground()).filter { librarySettings.automaticReloading.value },
            librarySettings.automaticReloading.flow.drop(1).filter { it }.map { },
            // Separating artists or not changes what every artist is, though no file changed.
            librarySettings.separateArtists.flow.drop(1).map { },
        ).collect { enqueue(ScanKind.REFRESH) }
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
     * `onCreate`, which restarted the whole scan on every configuration change.
     */
    fun requestScan() {
        changeWatch.start()
        enqueue(ScanKind.FULL)
    }

    /**
     * Rescans with the current exclusions and waits for it to finish - for a change the user is
     * watching take effect, such as excluding a folder. [force] reads every file again even when the
     * device reports nothing changed: the user asking for a rescan outright, where their intent
     * overrides the check.
     *
     * The scan itself runs on the application scope, like [requestScan], so a screen closing mid-way
     * cannot leave the library half-rewritten; only the waiting belongs to the caller. A failure is
     * thrown here, to the caller that is waiting on it.
     */
    suspend fun rescan(force: Boolean = false): SyncStats =
        appScope.async { scan(if (force) ScanKind.FORCED else ScanKind.FULL) }.await()

    /**
     * Re-reads MediaStore - without walking storage again - and waits for it: all a file Sona changed itself
     * needs, once Android has read it back, for every list to show the change. Runs on the application
     * scope, as [rescan] does.
     */
    suspend fun refresh(): SyncStats = appScope.async { scan(ScanKind.REFRESH) }.await()

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

    /**
     * Every time the app comes back to the foreground - not the first time it gets there, which is the
     * launch [requestScan] already scans for.
     */
    private fun returnsToForeground(): Flow<Unit> =
        isAppInForeground()
            .dropWhile { !it }
            .drop(1)
            .filter { it }
            .map { }

    private fun enqueue(kind: ScanKind) {
        pendingScan.getAndUpdate { pending -> if (pending == null || kind > pending) kind else pending }
        scanWakeups.trySend(Unit)
    }

    /** Runs a scan with the current exclusions, awaiting its completion. */
    private suspend fun scan(kind: ScanKind): SyncStats =
        scanMutex.withLock {
            withContext(ioDispatcher) {
                val excludedFolders = librarySettings.excludedFolders.value
                val signature = scanSignatureOf(context, excludedFolders, librarySettings.separateArtists.value)
                if (kind != ScanKind.FORCED && canSkip(signature)) return@withContext SyncStats()

                _progress.value = ScanProgress(ScanStep.READING_MEDIA_STORE)
                try {
                    val stats = runScan(excludedFolders, walksStorage = kind != ScanKind.REFRESH)
                    // A track left waiting - a file that may be moving - is not settled: the look again is not skipped.
                    if (stats.heldTracks == 0) {
                        scanSettings.setLastScanSignature(signature)
                    } else {
                        appScope.launch {
                            delay(GONE_FILE_SETTLE_MS)
                            enqueue(ScanKind.REFRESH)
                        }
                    }
                    stats
                } finally {
                    _progress.value = null
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
        val scanned = mediaStoreQuerier.query()
        fun List<Track>.included() = filterNot { it.folderPath in excludedFolders || it.path in excludedFolders }
        val music = scanned.tracks.included()
        val videos = scanned.videos.included()
        val credits = ArtistCredits(separatesArtists = librarySettings.separateArtists.value)

        var stats = SyncStats()
        val (manualVideos, manualTracks) = if (walksStorage) {
            // Stage 1: publish MediaStore's fast, already-indexed results right away, without
            // deletions - on a first run this is what paints the library, and the slow filesystem
            // walk below never gets to delay it. Because it is a diff, on any later scan it writes
            // nothing.
            stats = libraryWriter.sync(libraryTracksOf(music, credits) + videos, deleteMissing = false)
            // Stage 2: pick up files MediaStore hasn't indexed yet.
            findUnindexedTracks(music + videos, excludedFolders)
        } else {
            // A refresh: MediaStore is all that changed, so what the last walk found is kept as is.
            storedUnindexedTracks(music + videos)
        }.partition { it.isVideo }

        // Final pass: the authoritative one, and the only one allowed to delete. When stage 1
        // already stored exactly this, it opens no transaction at all.
        _progress.value = ScanProgress(ScanStep.SAVING)
        stats += libraryWriter.sync(libraryTracksOf(music + manualTracks, credits) + videos + manualVideos)
        return stats
    }

    /** Audio and video files on storage that MediaStore has not indexed, found by walking it (Q+ only). */
    private fun findUnindexedTracks(indexedTracks: List<Track>, excludedFolders: Set<String>): List<Track> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
        val pathsToSkip = indexedTracks.mapTo(HashSet()) { it.path }.apply { addAll(excludedFolders) }
        return manualFileWalker.findTracks(pathsToSkip) { _progress.value = it }
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
}

/**
 * [tracks] as the library lists them - however each was found, MediaStore or the walk - each named and identified the one
 * way: its credit, album and genre spelled as most spell them, whatever their case, its artists as [credits] reads them,
 * and on the one album its album artist and title make - its album artist tag, or else its own credit, which is what keeps
 * two singles of one name apart and one album's tracks together. Albums, artists and genres follow from these alone.
 */
internal fun libraryTracksOf(tracks: List<Track>, credits: ArtistCredits): List<Track> {
    fun Track.credit() = artist.orUnknownName(UnknownNames.ARTIST)
    fun Track.albumCredit() = albumArtist.orUnknownName(artist.orUnknownName(UnknownNames.ARTIST))
    fun Track.title() = album.orUnknownName(UnknownNames.ALBUM)
    fun Track.genreName() = genre?.trim()?.takeIf { it.isNotEmpty() } ?: UnknownNames.GENRE

    val creditSpellings = commonestSpellings(tracks.flatMap { listOf(it.credit(), it.albumCredit()) })
    val genreSpellings = commonestSpellings(tracks.map { it.genreName() })
    val artistSpellings = artistSpellingsOf(creditSpellings.values.flatMap(credits::artistsOf))
    fun Track.albumKey() = nameKey(albumCredit()) to nameKey(title())
    val albumTitles = tracks.groupBy({ it.albumKey() }, { it.title() }).mapValues { (_, titles) -> commonestSpelling(titles) }
    val albumCovers = tracks.groupBy { it.albumKey() }.mapValues { (_, albumTracks) ->
        albumTracks.sortedWith(AlbumOrder).firstNotNullOfOrNull { it.albumCoverArtUri ?: it.coverArtUri }
    }
    fun artistsNamedBy(credit: String) = credits.artistsOf(credit).map { artistSpellings.getValue(nameKey(it)) }.distinctBy(::nameKey)

    return tracks.map { track ->
        val credit = creditSpellings.getValue(nameKey(track.credit()))
        val albumCredit = creditSpellings.getValue(nameKey(track.albumCredit()))
        val genre = genreSpellings.getValue(nameKey(track.genreName()))
        val artistNames = artistsNamedBy(credit)
        val artistIds = artistNames.map(::artistIdOf)
        val title = albumTitles.getValue(track.albumKey())
        track.copy(
            artist = credit,
            artistId = artistIds.first(),
            artistIds = artistIds,
            artistNames = artistNames,
            album = title,
            albumId = albumIdOf(albumCredit, title),
            albumArtist = albumCredit,
            albumArtistNames = artistsNamedBy(albumCredit),
            albumCoverArtUri = albumCovers.getValue(track.albumKey()),
            genre = genre,
            genreId = genreIdOf(genre),
        )
    }
}

/** An album's tracks in the order it plays: by disc, then by number, the unnumbered last. */
private val AlbumOrder: Comparator<Track> =
    compareBy<Track>({ it.discNumber ?: Int.MAX_VALUE }, { it.trackNumber ?: Int.MAX_VALUE }, { it.title })

/**
 * The name a row goes under: what the file said, trimmed - or [unknown], when it said nothing.
 *
 * MediaStore normalises the names it reports; a file read straight off the disk by
 * [ManualFileWalker] is reported exactly as it was tagged. Both pass through here, so the two
 * cannot name the same artist or album differently and end up as two rows.
 */
private fun String?.orUnknownName(unknown: String): String =
    this?.trim()?.takeIf { nameKey(it).isNotEmpty() } ?: unknown

/** The [commonestSpelling] of every [nameKey] among [names]. */
private fun commonestSpellings(names: List<String>): Map<String, String> =
    names.groupBy(::nameKey).mapValues { (_, spellings) -> commonestSpelling(spellings) }

/**
 * How each artist's name among [names] is spelled, by its [nameKey]: as most spell it - and names differing only in their
 * spaces are one artist, `ImagineDragons` and `Imagine Dragons`, under the one with the most spaces, as a person writes it.
 */
private fun artistSpellingsOf(names: List<String>): Map<String, String> =
    names.groupBy { nameKey(it).replace(" ", "") }.values.flatMap { sameLetters ->
        val mostSpaces = sameLetters.maxOf { name -> name.count(Char::isWhitespace) }
        val spelling = commonestSpelling(sameLetters.filter { name -> name.count(Char::isWhitespace) == mostSpaces })
        sameLetters.map { nameKey(it) to spelling }
    }.toMap()
