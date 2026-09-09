package com.lhacenmed.sona.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lhacenmed.sona.core.database.entity.AlbumEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums")
    fun observeAll(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE id = :id")
    fun observeById(id: Long): Flow<AlbumEntity?>

    @Query("SELECT * FROM albums WHERE title LIKE '%' || :query || '%' ORDER BY title COLLATE NOCASE ASC LIMIT :limit")
    fun search(query: String, limit: Int): Flow<List<AlbumEntity>>

    /** Full rows, for the scanner to diff this scan's result against what is already stored. */
    @Query("SELECT * FROM albums")
    suspend fun getAll(): List<AlbumEntity>

    @Upsert
    suspend fun upsertAll(items: List<AlbumEntity>)

    @Query("DELETE FROM albums WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
