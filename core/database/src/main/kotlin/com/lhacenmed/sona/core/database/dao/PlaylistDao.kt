package com.lhacenmed.sona.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.lhacenmed.sona.core.database.entity.PlaylistEntity
import com.lhacenmed.sona.core.database.entity.PlaylistTrackEntity
import com.lhacenmed.sona.core.database.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

/** A playlist plus what the list screen shows and sorts it by beyond its name. */
data class PlaylistWithCount(
    val id: Long,
    val name: String,
    val isBuiltIn: Boolean,
    val trackCount: Int,
    val modifiedAt: Long,
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

    /** Every playlist, unordered: the repository sorts them the way the user chose. */
    @Query(
        """
        SELECT p.id AS id, p.name AS name, p.isBuiltIn AS isBuiltIn, p.modifiedAt AS modifiedAt,
               COUNT(pt.trackId) AS trackCount
        FROM playlists p
        LEFT JOIN playlist_tracks pt ON pt.playlistId = p.id
        GROUP BY p.id
        """,
    )
    fun observeAll(): Flow<List<PlaylistWithCount>>

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
    suspend fun delete(playlistId: Long)

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
