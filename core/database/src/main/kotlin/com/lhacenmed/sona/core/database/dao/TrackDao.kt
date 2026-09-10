package com.lhacenmed.sona.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lhacenmed.sona.core.database.entity.FolderRow
import com.lhacenmed.sona.core.database.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks")
    fun observeAll(): Flow<List<TrackEntity>>

    /**
     * The folders tab, computed by SQLite instead of by grouping the whole track list in memory on
     * every emission. It also means the folders tab no longer re-runs when a track's *contents*
     * change - only when the set of folders or their counts actually does.
     */
    @Query("SELECT folderPath AS path, COUNT(*) AS trackCount FROM tracks GROUP BY folderPath")
    fun observeFolders(): Flow<List<FolderRow>>

    @Query("SELECT * FROM tracks WHERE id = :trackId")
    fun observeById(trackId: Long): Flow<TrackEntity?>

    @Query("SELECT * FROM tracks WHERE albumId = :albumId")
    fun observeByAlbum(albumId: Long): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE artistId = :artistId")
    fun observeByArtist(artistId: Long): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE genreId = :genreId")
    fun observeByGenre(genreId: Long): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE folderPath = :folderPath")
    fun observeByFolder(folderPath: String): Flow<List<TrackEntity>>

    /**
     * Whether the library has ever been populated. Used to decide if a scan may be skipped, and
     * read as a scalar so it never loads a single row.
     */
    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun count(): Int

    @Query("SELECT * FROM tracks WHERE title LIKE '%' || :query || '%' ORDER BY title COLLATE NOCASE ASC LIMIT :limit")
    fun search(query: String, limit: Int): Flow<List<TrackEntity>>

    /** Full rows, for the scanner to diff this scan's result against what is already stored. */
    @Query("SELECT * FROM tracks")
    suspend fun getAll(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<TrackEntity>

    @Upsert
    suspend fun upsertAll(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

}
