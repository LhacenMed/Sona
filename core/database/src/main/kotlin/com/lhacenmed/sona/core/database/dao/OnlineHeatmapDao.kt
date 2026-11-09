package com.lhacenmed.sona.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.lhacenmed.sona.core.database.entity.OnlineHeatmapEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OnlineHeatmapDao {

    @Query("SELECT * FROM online_heatmaps WHERE trackId = :trackId")
    fun observe(trackId: Long): Flow<OnlineHeatmapEntity?>
}
