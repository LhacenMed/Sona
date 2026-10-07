package com.lhacenmed.sona.core.vault.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultItemDao {

    @Query("SELECT * FROM vault_items WHERE isVideo = :isVideo ORDER BY addedAt DESC")
    fun observe(isVideo: Boolean): Flow<List<VaultItem>>

    @Query("SELECT * FROM vault_items")
    fun observeAll(): Flow<List<VaultItem>>

    @Insert
    suspend fun insert(item: VaultItem): Long

    @Delete
    suspend fun delete(item: VaultItem)
}
