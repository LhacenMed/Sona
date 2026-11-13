package com.lhacenmed.sona.core.data

import android.os.SystemClock
import androidx.room.withTransaction
import com.lhacenmed.sona.core.common.di.IoDispatcher
import com.lhacenmed.sona.core.data.fingerprint.MediaFingerprints
import com.lhacenmed.sona.core.database.SonaDatabase
import com.lhacenmed.sona.core.database.dao.AddedDateDao
import com.lhacenmed.sona.core.database.dao.AlbumDao
import com.lhacenmed.sona.core.database.dao.ArtistDao
import com.lhacenmed.sona.core.database.dao.DownloadDao
import com.lhacenmed.sona.core.database.dao.FileYearDao
import com.lhacenmed.sona.core.database.dao.GenreDao
import com.lhacenmed.sona.core.database.dao.HeldLyricsDao
import com.lhacenmed.sona.core.database.dao.TrackDao
import com.lhacenmed.sona.core.database.dao.TrackMoveDao
import com.lhacenmed.sona.core.database.entity.AlbumEntity
import com.lhacenmed.sona.core.database.entity.ArtistEntity
import com.lhacenmed.sona.core.database.entity.GenreEntity
import com.lhacenmed.sona.core.database.entity.TrackEntity
import com.lhacenmed.sona.core.database.entity.toEntity
import com.lhacenmed.sona.core.database.stableIdOf
import com.lhacenmed.sona.core.model.Album
import com.lhacenmed.sona.core.model.Artist
import com.lhacenmed.sona.core.model.Genre
import com.lhacenmed.sona.core.model.Track
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * SQLite's bind-variable ceiling is 999 on older Android SQLite builds, so any `IN (:ids)` list is
 * deleted in chunks safely below it.
 */
private const val MAX_BIND_ARGS = 900

/**
 * How long a track whose file is gone waits to be taken out of the library: a file being moved or renamed is reported
 * as two changes, its old path gone and then its new one found, and those can reach two scans.
 */
const val GONE_FILE_SETTLE_MS = 10_000L

/**
 * What a [LibraryWriter.sync] actually changed. [isNoOp] is the interesting case. [heldTracks] counts the tracks left
 * in place for [GONE_FILE_SETTLE_MS] - for a later scan to find moved, or take out.
 */
data class SyncStats(
    val inserted: Int = 0,
    val updated: Int = 0,
    val deleted: Int = 0,
    val heldTracks: Int = 0,
) {
    val isNoOp: Boolean get() = inserted == 0 && updated == 0 && deleted == 0

    operator fun plus(other: SyncStats) = SyncStats(
        inserted = inserted + other.inserted,
        updated = updated + other.updated,
        deleted = deleted + other.deleted,
        heldTracks = heldTracks + other.heldTracks,
    )
}

/**
 * Applies a scan result to the database as a **difference**, never as a rewrite.
 *
 * The old scanner re-inserted every row it found on every scan (`OnConflictStrategy.REPLACE`) and
 * then deleted the leftovers. On a second launch of an unchanged library that is thousands of
 * pointless writes, each one firing Room's invalidation tracker, each invalidation re-emitting every
 * observing `Flow`, and every one of those re-rendering the library. The visible symptom was the
 * track list appearing empty and then snapping into place with the UI stuttering.
 *
 * Here, a scan that finds nothing new performs **no writes at all** - it opens no transaction, so
 * nothing is invalidated and nothing repaints. That is the single biggest reason relaunch is fast:
 * the fastest write is the one that is never issued.
 *
 * Rows are compared by value (`data class` equality), which is exact now that ids are stable rather
 * than auto-generated. Nothing user-owned lives on a track row any more - favorites are playlist
 * membership, keyed on the same stable id - so a scan has nothing to carry forward and the row it
 * writes is simply what it found.
 *
 * Under the id the track already has, which is what a track is: a file keeps its id for as long as it is
 * in the library, wherever it goes - see [movedTracks] - so a moved or renamed one is the same track to
 * everything that holds its id, the player, its queue, its playlists and plays alike, with nothing to
 * hand over. A new file takes the id its path derives. A move done as a copy and a delete is seen as a new
 * track and then the old one leaving: the two are one track from then on, under the old id, and what was
 * made of the copy meanwhile is handed to it - see [TrackMoveDao].
 */
@Singleton
class LibraryWriter @Inject constructor(
    private val database: SonaDatabase,
    private val trackDao: TrackDao,
    private val trackMoveDao: TrackMoveDao,
    private val addedDateDao: AddedDateDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val genreDao: GenreDao,
    private val fileYearDao: FileYearDao,
    private val heldLyricsDao: HeldLyricsDao,
    private val downloadDao: DownloadDao,
    private val mediaFingerprints: MediaFingerprints,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** The moves the app is making of its own - new path to old - each taken as a move by the next [sync] that finds it made. */
    private val expectedMoves = ConcurrentHashMap<String, String>()

    /**
     * Has the next [sync] that finds [to] - [from] moved, renamed, or turned into another format by the app - take it as [from]'s
     * track moved there, keeping all it has: however much its file changed, which a move found by itself must not.
     */
    fun expectMove(from: String, to: String) {
        expectedMoves[to] = from
    }

    /** Since when each track held for [GONE_FILE_SETTLE_MS] has been gone. Scans run one at a time. */
    private val goneSince = HashMap<Long, Long>()

    /**
     * @param deleteMissing whether stored rows absent from this result should be removed. The
     *   scanner's first pass passes `false`: it publishes MediaStore's results immediately, before
     *   the (slow, Q+ only) filesystem walk has had a chance to re-supply the manually-discovered
     *   tracks, and deleting them there would delete-then-reinsert them on every single scan. The
     *   final pass of the scan passes `true` and is the authority on what should no longer exist.
     */
    suspend fun sync(
        tracks: List<Track>,
        albums: List<Album>,
        artists: List<Artist>,
        genres: List<Genre>,
        deleteMissing: Boolean = true,
    ): SyncStats = withContext(ioDispatcher) {
        val storedTracks = trackDao.getAll().associateBy { it.id }
        val storedAlbums = albumDao.getAll().associateBy { it.id }
        val storedArtists = artistDao.getAll().associateBy { it.id }
        val storedGenres = genreDao.getAll().associateBy { it.id }

        val scannedTracks = tracks.map { it.toEntity() }
        val moves = movesIn(scannedTracks, storedTracks)
        val copies = copiesOfGoneIn(scannedTracks, storedTracks, moves)
        val desiredTracks = withStableIds(scannedTracks, storedTracks, moves + copies)
        val heldTrackIds = if (deleteMissing) heldGoneTracks(desiredTracks, storedTracks) else emptySet()
        val trackDiff = diff(
            desired = desiredTracks,
            stored = storedTracks,
            idOf = TrackEntity::id,
            deleteMissing = deleteMissing,
            heldIds = heldTrackIds,
        )
        val albumDiff = diff(albums.map { it.toEntity() }, storedAlbums, AlbumEntity::id, deleteMissing)
        val artistDiff = diff(artists.map { it.toEntity() }, storedArtists, ArtistEntity::id, deleteMissing)
        val genreDiff = diff(genres.map { it.toEntity() }, storedGenres, GenreEntity::id, deleteMissing)

        val stats = trackDiff.stats() + albumDiff.stats() + artistDiff.stats() + genreDiff.stats() + SyncStats(heldTracks = heldTrackIds.size)
        if (stats.isNoOp) return@withContext stats

        database.withTransaction {
            // Parents in first, so a track never points at an album row that isn't there; then the
            // tracks; and the old rows out last. Room delivers invalidations after the transaction
            // commits, so no observer ever sees the steps between.
            if (artistDiff.upserts.isNotEmpty()) artistDao.upsertAll(artistDiff.upserts)
            if (albumDiff.upserts.isNotEmpty()) albumDao.upsertAll(albumDiff.upserts)
            if (genreDiff.upserts.isNotEmpty()) genreDao.upsertAll(genreDiff.upserts)
            // Out of the way before the tracks go in: each copy's path is about to be its gone track's.
            copies.forEach { (path, gone) ->
                val copyId = storedTracks.values.first { it.path == path }.id
                trackMoveDao.move(copyId, gone.id)
                trackDao.deleteByIds(listOf(copyId))
            }
            if (trackDiff.upserts.isNotEmpty()) trackDao.upsertAll(trackDiff.upserts)

            moves.forEach { (path, moved) ->
                mediaFingerprints.moved(moved.path, path)
                addedDateDao.move(moved.path, path)
                heldLyricsDao.move(moved.path, path)
                // A download's file, wherever it went, is still the one it saved.
                downloadDao.moveSavedPath(moved.path, path)
                expectedMoves.remove(path)
            }
            trackDiff.deleted.chunked(MAX_BIND_ARGS).forEach { trackDao.deleteByIds(it) }
            // Gone with its track, so a file found at that path later is new as any.
            trackDiff.deleted.mapNotNull { storedTracks[it]?.path }.chunked(MAX_BIND_ARGS).forEach { paths ->
                addedDateDao.deleteByPaths(paths)
                heldLyricsDao.deleteByPaths(paths)
                fileYearDao.deleteByPaths(paths)
            }

            albumDiff.deleted.chunked(MAX_BIND_ARGS).forEach { albumDao.deleteByIds(it) }
            artistDiff.deleted.chunked(MAX_BIND_ARGS).forEach { artistDao.deleteByIds(it) }
            genreDiff.deleted.chunked(MAX_BIND_ARGS).forEach { genreDao.deleteByIds(it) }
        }

        stats
    }

    /**
     * Takes the tracks [trackIds] name out at once - their files just deleted - rather than waiting for
     * a scan to find them gone: every list follows the table, so they leave it on the next frame, and
     * their playlist entries, play counts and lyrics go with them. Their albums, artists and genres are
     * left to the next [sync], which is the one that derives them.
     */
    suspend fun deleteTracks(trackIds: Collection<Long>) = withContext(ioDispatcher) {
        database.withTransaction { trackIds.chunked(MAX_BIND_ARGS).forEach { trackDao.deleteByIds(it) } }
    }

    /** Which of the files of [scanned] new to the library are tracks of [stored] whose files are gone - see [movedTracks]. */
    private fun movesIn(scanned: List<TrackEntity>, stored: Map<Long, TrackEntity>): Map<String, TrackFile> {
        val scannedPaths = scanned.mapTo(HashSet()) { it.path }
        val storedByPath = stored.values.associateBy { it.path }
        // The app's own moves first, as it made them - see [expectMove].
        val expected = expectedMoves.mapNotNull { (to, from) ->
            storedByPath[from]?.takeIf { to in scannedPaths && to !in storedByPath && from !in scannedPaths }?.let { to to it.toTrackFile() }
        }.toMap()
        val expectedIds = expected.values.mapTo(HashSet()) { it.id }
        val gone = stored.values.filter { it.id !in expectedIds && it.path !in scannedPaths && !File(it.path).exists() }
        if (gone.isEmpty()) return expected
        val storedPaths = stored.values.mapTo(HashSet()) { it.path }
        val found = scanned.filter { it.path !in storedPaths && it.path !in expected }.map(TrackEntity::toTrackFile)
        return expected + movedTracks(gone.map(TrackEntity::toTrackFile), found)
    }

    /**
     * Which of the files of [scanned] already in the library are copies of tracks of [stored] whose files are gone and
     * did not move - see [copiesOfGoneTracks].
     */
    private fun copiesOfGoneIn(scanned: List<TrackEntity>, stored: Map<Long, TrackEntity>, moves: Map<String, TrackFile>): Map<String, TrackFile> {
        val scannedPaths = scanned.mapTo(HashSet()) { it.path }
        val movedIds = moves.values.mapTo(HashSet()) { it.id }
        val (present, missing) = stored.values.partition { it.path in scannedPaths }
        val gone = missing.filter { it.id !in movedIds && !File(it.path).exists() }
        if (gone.isEmpty()) return emptyMap()
        return copiesOfGoneTracks(gone.map(TrackEntity::toTrackFile), present.map(TrackEntity::toTrackFile))
    }

    /**
     * [scanned], each under the id its file already has: the one stored for the path it moved from, or for its path.
     * A new file takes the id its path derives - or, where a file that moved away from that very path still holds
     * that one, the next free one.
     */
    private fun withStableIds(scanned: List<TrackEntity>, stored: Map<Long, TrackEntity>, moves: Map<String, TrackFile>): List<TrackEntity> {
        val idByPath = stored.values.associate { it.path to it.id }
        val taken = HashSet(stored.keys)
        return scanned.map { track ->
            val id = moves[track.path]?.id ?: idByPath[track.path]
                ?: generateSequence(0) { it + 1 }
                    .map { attempt -> if (attempt == 0) track.id else stableIdOf(track.path, attempt.toString()) }
                    .first(taken::add)
            track.copy(id = id)
        }
    }

    /** The tracks of [stored] whose files are gone, not in [desired], and gone for less than [GONE_FILE_SETTLE_MS]. */
    private fun heldGoneTracks(desired: List<TrackEntity>, stored: Map<Long, TrackEntity>): Set<Long> {
        val desiredIds = desired.mapTo(HashSet()) { it.id }
        val gone = stored.values.filter { it.id !in desiredIds && !File(it.path).exists() }.map { it.id }
        goneSince.keys.retainAll(gone.toSet())
        val now = SystemClock.elapsedRealtime()
        return gone.filterTo(HashSet()) { now - goneSince.getOrPut(it) { now } < GONE_FILE_SETTLE_MS }
    }

    private class Diff<E>(
        val upserts: List<E>,
        val deleted: List<Long>,
        val insertedCount: Int,
    ) {
        fun stats() = SyncStats(
            inserted = insertedCount,
            updated = upserts.size - insertedCount,
            deleted = deleted.size,
        )
    }

    private fun <E> diff(
        desired: List<E>,
        stored: Map<Long, E>,
        idOf: (E) -> Long,
        deleteMissing: Boolean,
        heldIds: Set<Long> = emptySet(),
    ): Diff<E> {
        val upserts = ArrayList<E>()
        var insertedCount = 0
        val desiredIds = HashSet<Long>(desired.size)
        for (row in desired) {
            val id = idOf(row)
            desiredIds += id
            val existing = stored[id]
            when {
                existing == null -> {
                    upserts += row
                    insertedCount++
                }
                // Value equality is the whole diff: an unchanged file produces an identical row.
                existing != row -> upserts += row
            }
        }
        val deleted =
            if (deleteMissing) stored.keys.filterNot { it in desiredIds || it in heldIds } else emptyList()
        return Diff(upserts, deleted, insertedCount)
    }
}

private fun TrackEntity.toTrackFile() = TrackFile(id, path, sizeBytes, durationMs, isVideo, title, artist)
