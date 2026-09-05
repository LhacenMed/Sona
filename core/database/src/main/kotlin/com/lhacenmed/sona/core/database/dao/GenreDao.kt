package com.lhacenmed.sona.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lhacenmed.sona.core.database.entity.GenreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GenreDao {
    @Query("SELECT * FROM genres ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<GenreEntity>>

    @Query("SELECT id FROM genres")
    suspend fun getAllIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(genres: List<GenreEntity>)

    @Query("DELETE FROM genres WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
