package com.lhacenmed.sona.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lhacenmed.sona.core.database.entity.ResumePositionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ResumePositionDao {

    @Query("SELECT * FROM resume_positions")
    suspend fun getAll(): List<ResumePositionEntity>

    @Query("SELECT * FROM resume_positions")
    fun observeAll(): Flow<List<ResumePositionEntity>>

    @Upsert
    suspend fun upsert(position: ResumePositionEntity)

    @Query("DELETE FROM resume_positions WHERE trackId = :trackId")
    suspend fun delete(trackId: Long)
}
