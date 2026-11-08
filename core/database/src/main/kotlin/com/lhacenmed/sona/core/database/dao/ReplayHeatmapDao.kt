package com.lhacenmed.sona.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lhacenmed.sona.core.database.entity.ReplayHeatmapEntity

@Dao
interface ReplayHeatmapDao {

    @Query("SELECT * FROM replay_heatmaps WHERE trackId = :trackId")
    suspend fun get(trackId: Long): ReplayHeatmapEntity?

    @Upsert
    suspend fun upsert(heatmap: ReplayHeatmapEntity)
}
