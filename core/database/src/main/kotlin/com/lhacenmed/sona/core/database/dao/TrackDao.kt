package com.lhacenmed.sona.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lhacenmed.sona.core.database.entity.FolderCoverRow
import com.lhacenmed.sona.core.database.entity.FolderRow
import com.lhacenmed.sona.core.database.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    /** The music - or, where [isVideo], the videos - every one of them. */
    @Query("SELECT * FROM tracks WHERE isVideo = :isVideo")
    fun observeAll(isVideo: Boolean): Flow<List<TrackEntity>>

    /**
     * The folders tab, computed by SQLite instead of by grouping the whole track list in memory on
     * every emission. It also means the folders tab no longer re-runs when a track's *contents*
     * change - only when the set of folders or their counts actually does.
     */
    @Query("SELECT folderPath AS path, COUNT(*) AS trackCount FROM tracks WHERE isVideo = :isVideo GROUP BY folderPath")
    fun observeFolders(isVideo: Boolean): Flow<List<FolderRow>>

    /**
     * How many tracks in each folder share each cover, which is what the folders tab composes its
     * covers from. Counted by SQLite rather than by grouping the track list, for the same reason the
     * folders themselves are. A track counts for its album's cover, as it does in every collage, so an
     * album's tracks are its one cover there rather than the same picture each - a video, on no album,
     * for its own.
     */
    @Query(
        """
        SELECT t.folderPath AS path, COALESCE(a.coverArtUri, t.coverArtUri) AS coverArtUri, COUNT(*) AS trackCount
        FROM tracks t LEFT JOIN albums a ON a.id = t.albumId
        WHERE t.isVideo = :isVideo AND COALESCE(a.coverArtUri, t.coverArtUri) IS NOT NULL
            AND COALESCE(a.coverArtUri, t.coverArtUri) != ''
        GROUP BY t.folderPath, COALESCE(a.coverArtUri, t.coverArtUri)
        """,
    )
    fun observeFolderCoverArt(isVideo: Boolean): Flow<List<FolderCoverRow>>

    @Query("SELECT * FROM tracks WHERE id = :trackId")
    fun observeById(trackId: Long): Flow<TrackEntity?>

    @Query("SELECT * FROM tracks WHERE albumId = :albumId")
    fun observeByAlbum(albumId: Long): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE artistId = :artistId")
    fun observeByArtist(artistId: Long): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE genreId = :genreId")
    fun observeByGenre(genreId: Long): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE folderPath = :folderPath AND isVideo = :isVideo")
    fun observeByFolder(folderPath: String, isVideo: Boolean): Flow<List<TrackEntity>>

    /**
     * Whether the library has ever been populated. Used to decide if a scan may be skipped, and
     * read as a scalar so it never loads a single row.
     */
    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun count(): Int

    /** Full rows, for the scanner to diff this scan's result against what is already stored. */
    @Query("SELECT * FROM tracks")
    suspend fun getAll(): List<TrackEntity>

    /** The tracks the storage walk found rather than MediaStore, for a refresh that does not walk. */
    @Query("SELECT * FROM tracks WHERE isManuallyScanned = 1")
    suspend fun getManuallyScanned(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<TrackEntity>

    @Upsert
    suspend fun upsertAll(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

}
