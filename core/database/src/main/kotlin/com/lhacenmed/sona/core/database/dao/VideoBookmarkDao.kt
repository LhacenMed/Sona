package com.lhacenmed.sona.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lhacenmed.sona.core.database.entity.VideoBookmarkEntity

@Dao
interface VideoBookmarkDao {

    @Query("SELECT positionMs FROM video_bookmarks WHERE trackId = :trackId ORDER BY positionMs")
    suspend fun get(trackId: Long): List<Long>

    @Upsert
    suspend fun upsert(bookmark: VideoBookmarkEntity)

    @Query("DELETE FROM video_bookmarks WHERE trackId = :trackId AND positionMs = :positionMs")
    suspend fun delete(trackId: Long, positionMs: Long)
}
