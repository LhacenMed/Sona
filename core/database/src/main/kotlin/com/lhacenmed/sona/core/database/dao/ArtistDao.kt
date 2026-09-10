package com.lhacenmed.sona.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lhacenmed.sona.core.database.entity.ArtistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistDao {
    @Query("SELECT * FROM artists")
    fun observeAll(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists WHERE id = :id")
    fun observeById(id: Long): Flow<ArtistEntity?>

    @Query("SELECT * FROM artists WHERE name LIKE '%' || :query || '%' ORDER BY name COLLATE NOCASE ASC LIMIT :limit")
    fun search(query: String, limit: Int): Flow<List<ArtistEntity>>

    /** Full rows, for the scanner to diff this scan's result against what is already stored. */
    @Query("SELECT * FROM artists")
    suspend fun getAll(): List<ArtistEntity>

    @Upsert
    suspend fun upsertAll(items: List<ArtistEntity>)

    @Query("DELETE FROM artists WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
