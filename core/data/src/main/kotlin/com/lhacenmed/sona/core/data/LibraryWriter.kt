package com.lhacenmed.sona.core.data

import androidx.room.withTransaction
import com.lhacenmed.sona.core.common.di.IoDispatcher
import com.lhacenmed.sona.core.data.fingerprint.MediaFingerprints
import com.lhacenmed.sona.core.database.SonaDatabase
import com.lhacenmed.sona.core.database.dao.AlbumDao
import com.lhacenmed.sona.core.database.dao.ArtistDao
import com.lhacenmed.sona.core.database.dao.GenreDao
import com.lhacenmed.sona.core.database.dao.TrackDao
import com.lhacenmed.sona.core.database.dao.TrackMoveDao
import com.lhacenmed.sona.core.database.entity.AlbumEntity
import com.lhacenmed.sona.core.database.entity.ArtistEntity
import com.lhacenmed.sona.core.database.entity.GenreEntity
import com.lhacenmed.sona.core.database.entity.TrackEntity
import com.lhacenmed.sona.core.database.entity.toEntity
import com.lhacenmed.sona.core.datastore.LibrarySettings
import com.lhacenmed.sona.core.model.Album
import com.lhacenmed.sona.core.model.Artist
import com.lhacenmed.sona.core.model.Genre
import com.lhacenmed.sona.core.model.Track
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * SQLite's bind-variable ceiling is 999 on older Android SQLite builds, so any `IN (:ids)` list is
 * deleted in chunks safely below it.
 */
private const val MAX_BIND_ARGS = 900

/** What a [LibraryWriter.sync] actually changed. [isNoOp] is the interesting case. */
data class SyncStats(
    val inserted: Int = 0,
    val updated: Int = 0,
    val deleted: Int = 0,
) {
    val isNoOp: Boolean get() = inserted == 0 && updated == 0 && deleted == 0

    operator fun plus(other: SyncStats) = SyncStats(
        inserted = inserted + other.inserted,
        updated = updated + other.updated,
        deleted = deleted + other.deleted,
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
 * Rows are compared by value (`data class` equality), which is exact now that ids are derived from
 * stable identity rather than auto-generated. Nothing user-owned lives on a track row any more -
 * favorites are playlist membership, keyed on the same stable id - so a scan has nothing to carry
 * forward and the row it writes is simply what it found.
 *
 * Except where a file moved, while [LibrarySettings.keepsMovedFiles]: a track whose file is gone, and a
 * new one of the very same bytes - see [movedTracks] - are one track, so what the user made of the old
 * one is handed to the new before the old goes - see [TrackMoveDao].
 */
@Singleton
class LibraryWriter @Inject constructor(
    private val database: SonaDatabase,
    private val trackDao: TrackDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val genreDao: GenreDao,
    private val trackMoveDao: TrackMoveDao,
    private val mediaFingerprints: MediaFingerprints,
    private val librarySettings: LibrarySettings,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

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

        val desiredTracks = tracks.map { it.toEntity() }
        val trackDiff = diff(
            desired = desiredTracks,
            stored = storedTracks,
            idOf = TrackEntity::id,
            deleteMissing = deleteMissing,
        )
        val moves = if (librarySettings.keepsMovedFiles.value) movesIn(desiredTracks, storedTracks) else emptyMap()
        // A moved track's old row goes even on a pass that deletes nothing else: its file is gone, and it lives on.
        val deletedTrackIds = (trackDiff.deleted + moves.keys).distinct()
        val albumDiff = diff(albums.map { it.toEntity() }, storedAlbums, AlbumEntity::id, deleteMissing)
        val artistDiff = diff(artists.map { it.toEntity() }, storedArtists, ArtistEntity::id, deleteMissing)
        val genreDiff = diff(genres.map { it.toEntity() }, storedGenres, GenreEntity::id, deleteMissing)

        val stats = trackDiff.stats() + albumDiff.stats() + artistDiff.stats() + genreDiff.stats()
        if (stats.isNoOp) return@withContext stats

        database.withTransaction {
            // Parents in first, so a track never points at an album row that isn't there; then the
            // tracks, so a moved one's new row is there to be handed the old one's; and the old rows
            // out last. Room delivers invalidations after the transaction commits, so no observer
            // ever sees the steps between.
            if (artistDiff.upserts.isNotEmpty()) artistDao.upsertAll(artistDiff.upserts)
            if (albumDiff.upserts.isNotEmpty()) albumDao.upsertAll(albumDiff.upserts)
            if (genreDiff.upserts.isNotEmpty()) genreDao.upsertAll(genreDiff.upserts)
            if (trackDiff.upserts.isNotEmpty()) trackDao.upsertAll(trackDiff.upserts)

            moves.forEach { (fromTrackId, toTrackId) -> trackMoveDao.move(fromTrackId, toTrackId) }
            deletedTrackIds.chunked(MAX_BIND_ARGS).forEach { trackDao.deleteByIds(it) }

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

    /**
     * The tracks of [stored] whose files are gone and are now tracks of [desired] new to the library: their ids to
     * the new ones'. Only files of a gone one's size are read for it - see [movedTracks].
     */
    private suspend fun movesIn(desired: List<TrackEntity>, stored: Map<Long, TrackEntity>): Map<Long, Long> {
        val desiredIds = desired.mapTo(HashSet()) { it.id }
        val gone = stored.values.filter { it.id !in desiredIds && !File(it.path).exists() }
        if (gone.isEmpty()) return emptyMap()
        val found = desired.filter { it.id !in stored }
        return movedTracks(
            gone = gone.map(TrackEntity::toTrackFile),
            found = found.map(TrackEntity::toTrackFile),
            lastFingerprintOf = { mediaFingerprints.lastKnown(it.path) },
            fingerprintOf = { mediaFingerprints.of(it.path) },
        )
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
            if (deleteMissing) stored.keys.filterNot { it in desiredIds } else emptyList()
        return Diff(upserts, deleted, insertedCount)
    }
}

private fun TrackEntity.toTrackFile() = TrackFile(id, path, sizeBytes, isVideo)
