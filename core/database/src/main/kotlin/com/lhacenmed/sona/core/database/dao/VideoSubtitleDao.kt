package com.lhacenmed.sona.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lhacenmed.sona.core.database.entity.VideoSubtitleEntity

@Dao
interface VideoSubtitleDao {

    @Query("SELECT * FROM video_subtitles WHERE trackId = :trackId")
    suspend fun get(trackId: Long): VideoSubtitleEntity?

    @Upsert
    suspend fun upsert(subtitle: VideoSubtitleEntity)

    @Query("DELETE FROM video_subtitles WHERE trackId IN (:trackIds)")
    suspend fun deleteByIds(trackIds: List<Long>)
}
