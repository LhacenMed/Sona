package com.lhacenmed.sona.core.vault.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultResumePositionDao {

    @Query("SELECT * FROM vault_resume_positions")
    suspend fun getAll(): List<VaultResumePosition>

    @Query("SELECT * FROM vault_resume_positions")
    fun observeAll(): Flow<List<VaultResumePosition>>

    @Upsert
    suspend fun upsert(position: VaultResumePosition)

    @Query("DELETE FROM vault_resume_positions WHERE itemId = :itemId")
    suspend fun delete(itemId: Long)
}
