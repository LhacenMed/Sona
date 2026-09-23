package com.lhacenmed.sona.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.lhacenmed.sona.core.database.entity.PlaylistCoverSource
import com.lhacenmed.sona.core.database.entity.PlaylistEntity
import com.lhacenmed.sona.core.database.entity.PlaylistTrackEntity
import com.lhacenmed.sona.core.database.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

/**
 * A playlist plus what the list screen shows and sorts it by beyond its name - and its cover choice,
 * with [coverTrackArtUri] the chosen track's cover, or null when that track is not in the library.
 */
data class PlaylistWithCount(
    val id: Long,
    val name: String,
    val isBuiltIn: Boolean,
    val trackCount: Int,
    val modifiedAt: Long,
    val coverSource: PlaylistCoverSource,
    val coverTrackId: Long?,
    val coverImageUri: String?,
    val coverTrackArtUri: String?,
)

/** How many of a playlist's tracks share one cover, which is what ranks a playlist's composed cover. */
data class PlaylistCoverRow(
    val playlistId: Long,
    val coverArtUri: String,
    val trackCount: Int,
)

/** A playlist's track, with when it was added to that playlist. */
data class PlaylistTrackRow(
    @Embedded val track: TrackEntity,
    val addedAt: Long,
)

@Dao
interface PlaylistDao {

    /**
     * Every playlist, unordered: the repository sorts them the way the user chose. A cover chosen from
     * one track is read here with the rest, so it arrives in the same emission as its playlist.
     */
    @Query(
        """
        SELECT p.id AS id, p.name AS name, p.isBuiltIn AS isBuiltIn, p.modifiedAt AS modifiedAt,
               p.coverSource AS coverSource, p.coverTrackId AS coverTrackId,
               p.coverImageUri AS coverImageUri, ct.coverArtUri AS coverTrackArtUri,
               COUNT(pt.trackId) AS trackCount
        FROM playlists p
        LEFT JOIN playlist_tracks pt ON pt.playlistId = p.id
        LEFT JOIN tracks ct ON ct.id = p.coverTrackId
        GROUP BY p.id
        """,
    )
    fun observeAll(): Flow<List<PlaylistWithCount>>

    /** The playlists whose cover is their first or last track, which only their sorted tracks can say. */
    @Query("SELECT id FROM playlists WHERE coverSource IN ('FIRST_TRACK', 'LAST_TRACK')")
    fun observeIdsCoveredBySortedTrack(): Flow<List<Long>>

    /**
     * How many tracks in each playlist share each cover, which is what a playlist row composes its
     * cover from. One aggregate for every playlist rather than a query per row, so the list costs the
     * same whether there is one playlist or fifty.
     */
    @Query(
        """
        SELECT pt.playlistId AS playlistId, t.coverArtUri AS coverArtUri, COUNT(*) AS trackCount
        FROM playlist_tracks pt
        INNER JOIN tracks t ON t.id = pt.trackId
        WHERE t.coverArtUri IS NOT NULL AND t.coverArtUri != ''
        GROUP BY pt.playlistId, t.coverArtUri
        """,
    )
    fun observeCoverArt(): Flow<List<PlaylistCoverRow>>

    /** A playlist's tracks, in the order the user arranged them. */
    @Query(
        """
        SELECT t.*, pt.addedAt AS addedAt FROM tracks t
        INNER JOIN playlist_tracks pt ON pt.trackId = t.id
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.position ASC
        """,
    )
    fun observeTracks(playlistId: Long): Flow<List<PlaylistTrackRow>>

    /** Just the ids, for the heart in the player and for "is this already in the playlist". */
    @Query("SELECT trackId FROM playlist_tracks WHERE playlistId = :playlistId")
    fun observeTrackIds(playlistId: Long): Flow<List<Long>>

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, modifiedAt = :modifiedAt WHERE id = :playlistId AND isBuiltIn = 0")
    suspend fun rename(playlistId: Long, name: String, modifiedAt: Long)

    /** Built-in playlists are excluded here rather than in the caller, so nothing can delete them. */
    @Query("DELETE FROM playlists WHERE id = :playlistId AND isBuiltIn = 0")
    suspend fun delete(playlistId: Long): Int

    @Query("SELECT coverImageUri FROM playlists WHERE id = :playlistId")
    suspend fun coverImageUri(playlistId: Long): String?

    @Query(
        "UPDATE playlists SET coverSource = :source, coverTrackId = :trackId, coverImageUri = :imageUri, " +
            "modifiedAt = :modifiedAt WHERE id = :playlistId",
    )
    suspend fun setCover(
        playlistId: Long,
        source: PlaylistCoverSource,
        trackId: Long?,
        imageUri: String?,
        modifiedAt: Long,
    )

    /**
     * Renames a playlist and sets its cover as one change. A built-in playlist keeps its name - see
     * [rename] - and takes the cover all the same.
     */
    @Transaction
    suspend fun edit(
        playlistId: Long,
        name: String,
        coverSource: PlaylistCoverSource,
        coverTrackId: Long?,
        coverImageUri: String?,
        editedAt: Long,
    ) {
        rename(playlistId, name, editedAt)
        setCover(playlistId, coverSource, coverTrackId, coverImageUri, editedAt)
    }

    @Query("UPDATE playlists SET modifiedAt = :modifiedAt WHERE id = :playlistId")
    suspend fun setModifiedAt(playlistId: Long, modifiedAt: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun lastPosition(playlistId: Long): Int

    /** The row id of each inserted membership, or -1 for one that was already there. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMemberships(rows: List<PlaylistTrackEntity>): List<Long>

    /**
     * Appends [trackIds] after whatever the playlist already holds.
     *
     * IGNORE rather than REPLACE on conflict: a track already in the playlist keeps the position the
     * user put it in, instead of jumping to the end because it was added again. For the same reason
     * the playlist only counts as changed when something was actually inserted.
     */
    @Transaction
    suspend fun addTracks(playlistId: Long, trackIds: List<Long>, addedAt: Long) {
        if (trackIds.isEmpty()) return
        val startPosition = lastPosition(playlistId) + 1
        val inserted = insertMemberships(
            trackIds.mapIndexed { index, trackId ->
                PlaylistTrackEntity(playlistId, trackId, startPosition + index, addedAt)
            },
        )
        if (inserted.any { it != -1L }) setModifiedAt(playlistId, addedAt)
    }

    // Removal deliberately leaves gaps in `position`. Only the relative order matters to the
    // ORDER BY, so renumbering every remaining row would be work with nothing to show for it.
    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId IN (:trackIds)")
    suspend fun deleteMemberships(playlistId: Long, trackIds: List<Long>): Int

    @Transaction
    suspend fun removeTracks(playlistId: Long, trackIds: List<Long>, removedAt: Long) {
        if (deleteMemberships(playlistId, trackIds) > 0) setModifiedAt(playlistId, removedAt)
    }

    @Query("UPDATE playlist_tracks SET position = :position WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun setPosition(playlistId: Long, trackId: Long, position: Int)

    /**
     * Writes the order a drag ended on.
     *
     * Called once on drop, never per frame: dragging reorders the list in memory, and only the
     * result reaches the database.
     */
    @Transaction
    suspend fun setOrder(playlistId: Long, trackIds: List<Long>, reorderedAt: Long) {
        trackIds.forEachIndexed { position, trackId -> setPosition(playlistId, trackId, position) }
        setModifiedAt(playlistId, reorderedAt)
    }
}
