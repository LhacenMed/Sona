package com.lhacenmed.sona.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lhacenmed.sona.core.database.entity.GenreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GenreDao {
    @Query("SELECT * FROM genres")
    fun observeAll(): Flow<List<GenreEntity>>

    @Query("SELECT * FROM genres WHERE id = :id")
    fun observeById(id: Long): Flow<GenreEntity?>

    @Query("SELECT * FROM genres WHERE name LIKE '%' || :query || '%' ORDER BY name COLLATE NOCASE ASC LIMIT :limit")
    fun search(query: String, limit: Int): Flow<List<GenreEntity>>

    /** Full rows, for the scanner to diff this scan's result against what is already stored. */
    @Query("SELECT * FROM genres")
    suspend fun getAll(): List<GenreEntity>

    @Upsert
    suspend fun upsertAll(items: List<GenreEntity>)

    @Query("DELETE FROM genres WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
