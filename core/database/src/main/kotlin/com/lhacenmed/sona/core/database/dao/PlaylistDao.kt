package com.lhacenmed.sona.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.lhacenmed.sona.core.database.entity.PlaylistEntity
import com.lhacenmed.sona.core.database.entity.PlaylistTrackEntity
import com.lhacenmed.sona.core.database.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

/** A playlist plus the one thing the list screen needs beyond its name. */
data class PlaylistWithCount(
    val id: Long,
    val name: String,
    val isBuiltIn: Boolean,
    val trackCount: Int,
)

@Dao
interface PlaylistDao {

    /** Built-in first, then the user's own alphabetically - the order the playlists tab shows. */
    @Query(
        """
        SELECT p.id AS id, p.name AS name, p.isBuiltIn AS isBuiltIn,
               COUNT(pt.trackId) AS trackCount
        FROM playlists p
        LEFT JOIN playlist_tracks pt ON pt.playlistId = p.id
        GROUP BY p.id
        ORDER BY p.isBuiltIn DESC, p.name COLLATE NOCASE ASC
        """,
    )
    fun observeAll(): Flow<List<PlaylistWithCount>>

    /** A playlist's tracks, in the order the user arranged them. */
    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks pt ON pt.trackId = t.id
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.position ASC
        """,
    )
    fun observeTracks(playlistId: Long): Flow<List<TrackEntity>>

    /** Just the ids, for the heart in the player and for "is this already in the playlist". */
    @Query("SELECT trackId FROM playlist_tracks WHERE playlistId = :playlistId")
    fun observeTrackIds(playlistId: Long): Flow<List<Long>>

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :playlistId AND isBuiltIn = 0")
    suspend fun rename(playlistId: Long, name: String)

    /** Built-in playlists are excluded here rather than in the caller, so nothing can delete them. */
    @Query("DELETE FROM playlists WHERE id = :playlistId AND isBuiltIn = 0")
    suspend fun delete(playlistId: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun lastPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMemberships(rows: List<PlaylistTrackEntity>)

    /**
     * Appends [trackIds] after whatever the playlist already holds.
     *
     * IGNORE rather than REPLACE on conflict: a track already in the playlist keeps the position the
     * user put it in, instead of jumping to the end because it was added again.
     */
    @Transaction
    suspend fun addTracks(playlistId: Long, trackIds: List<Long>) {
        if (trackIds.isEmpty()) return
        val startPosition = lastPosition(playlistId) + 1
        insertMemberships(
            trackIds.mapIndexed { index, trackId ->
                PlaylistTrackEntity(playlistId, trackId, startPosition + index)
            },
        )
    }

    // Removal deliberately leaves gaps in `position`. Only the relative order matters to the
    // ORDER BY, so renumbering every remaining row would be work with nothing to show for it.
    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId IN (:trackIds)")
    suspend fun removeTracks(playlistId: Long, trackIds: List<Long>)

    @Query("UPDATE playlist_tracks SET position = :position WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun setPosition(playlistId: Long, trackId: Long, position: Int)

    /**
     * Writes the order a drag ended on.
     *
     * Called once on drop, never per frame: dragging reorders the list in memory, and only the
     * result reaches the database.
     */
    @Transaction
    suspend fun setOrder(playlistId: Long, trackIds: List<Long>) {
        trackIds.forEachIndexed { position, trackId -> setPosition(playlistId, trackId, position) }
    }
}
