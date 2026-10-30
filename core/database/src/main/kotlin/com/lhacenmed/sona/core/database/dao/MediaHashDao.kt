package com.lhacenmed.sona.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lhacenmed.sona.core.database.entity.MediaHashEntity

@Dao
interface MediaHashDao {

    @Query("SELECT * FROM media_hashes WHERE path = :path")
    suspend fun get(path: String): MediaHashEntity?

    @Upsert
    suspend fun upsert(hash: MediaHashEntity)
}
