package com.lhacenmed.sona.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.lhacenmed.sona.core.database.entity.VideoChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoChapterDao {

    /** The chapters of the track [trackId], in the order they come. */
    @Query("SELECT * FROM video_chapters WHERE trackId = :trackId ORDER BY startMs")
    fun observe(trackId: Long): Flow<List<VideoChapterEntity>>
}
