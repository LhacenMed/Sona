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
import com.lhacenmed.sona.core.model.sort.SortableList
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
     * same whether there is one playlist or fifty. A track counts for its album's cover, as it does in
     * every collage - a video, on no album, for its own.
     */
    @Query(
        """
        SELECT pt.playlistId AS playlistId, COALESCE(a.coverArtUri, t.coverArtUri) AS coverArtUri, COUNT(*) AS trackCount
        FROM playlist_tracks pt
        INNER JOIN tracks t ON t.id = pt.trackId
        LEFT JOIN albums a ON a.id = t.albumId
        WHERE COALESCE(a.coverArtUri, t.coverArtUri) IS NOT NULL AND COALESCE(a.coverArtUri, t.coverArtUri) != ''
        GROUP BY pt.playlistId, COALESCE(a.coverArtUri, t.coverArtUri)
        """,
    )
    fun observeCoverArt(): Flow<List<PlaylistCoverRow>>

    /** A playlist's tracks, in the order they joined it: the repository sorts them the way the user chose. */
    @Query(
        """
        SELECT t.*, pt.addedAt AS addedAt FROM tracks t
        INNER JOIN playlist_tracks pt ON pt.trackId = t.id
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.addedAt ASC
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
    suspend fun deleteRow(playlistId: Long): Int

    /**
     * Deletes a playlist with its hand-made order, which no foreign key reaches - so a playlist later
     * given the same id never opens in an order it did not make. Whether it was deleted.
     */
    @Transaction
    suspend fun delete(playlistId: Long): Boolean {
        if (deleteRow(playlistId) == 0) return false
        deleteArrangement(SortableList.PLAYLIST_TRACKS, playlistId.toString())
        return true
    }

    @Query("DELETE FROM arrangements WHERE list = :list AND instanceId = :instanceId")
    suspend fun deleteArrangement(list: SortableList, instanceId: String)

    @Query("DELETE FROM arrangements WHERE list = :list AND instanceId = :instanceId AND trackId IN (:trackIds)")
    suspend fun deleteArranged(list: SortableList, instanceId: String, trackIds: List<Long>)

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

    /** The row id of each inserted membership, or -1 for one that was already there. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMemberships(rows: List<PlaylistTrackEntity>): List<Long>

    /**
     * Adds [rows] to their playlist, as of [changedAt].
     *
     * IGNORE rather than REPLACE on conflict: a track already in the playlist keeps the place and the
     * date it has, instead of turning new because it was added again. For the same reason the
     * playlist only counts as changed when something was actually inserted.
     */
    @Transaction
    suspend fun addTracks(playlistId: Long, rows: List<PlaylistTrackEntity>, changedAt: Long) {
        if (rows.isEmpty()) return
        if (insertMemberships(rows).any { it != -1L }) setModifiedAt(playlistId, changedAt)
    }

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId IN (:trackIds)")
    suspend fun deleteMemberships(playlistId: Long, trackIds: List<Long>): Int

    /**
     * Takes [trackIds] out of the playlist, and out of its hand-made order with them, so one added
     * back later arrives as a new track rather than returning to where it once was.
     */
    @Transaction
    suspend fun removeTracks(playlistId: Long, trackIds: List<Long>, removedAt: Long) {
        if (deleteMemberships(playlistId, trackIds) == 0) return
        deleteArranged(SortableList.PLAYLIST_TRACKS, playlistId.toString(), trackIds)
        setModifiedAt(playlistId, removedAt)
    }
}
