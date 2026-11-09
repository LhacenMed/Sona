package com.lhacenmed.sona.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.lhacenmed.sona.core.database.entity.OnlineHeatmapEntity
import com.lhacenmed.sona.core.database.entity.VideoChapterEntity
import com.lhacenmed.sona.core.database.entity.VideoExtraReadEntity

/** What the library keeps of what each video's file carries of its site - written as each file is read, see `VideoExtras`. */
@Dao
interface VideoExtrasDao {

    /** Every video read, by its track, with how its file stood. */
    @Query("SELECT * FROM video_extra_reads")
    suspend fun reads(): List<VideoExtraReadEntity>

    /** Keeps what the file of the track [trackId] carries - in place of what it carried before - as read at [signature]. */
    @Transaction
    suspend fun keep(trackId: Long, signature: Long, heatmap: OnlineHeatmapEntity?, chapters: List<VideoChapterEntity>) {
        deleteHeatmap(trackId)
        heatmap?.let { upsertHeatmap(it) }
        deleteChapters(trackId)
        insertChapters(chapters)
        upsertRead(VideoExtraReadEntity(trackId, signature))
    }

    @Query("DELETE FROM online_heatmaps WHERE trackId = :trackId")
    suspend fun deleteHeatmap(trackId: Long)

    @Upsert
    suspend fun upsertHeatmap(heatmap: OnlineHeatmapEntity)

    @Query("DELETE FROM video_chapters WHERE trackId = :trackId")
    suspend fun deleteChapters(trackId: Long)

    @Upsert
    suspend fun insertChapters(chapters: List<VideoChapterEntity>)

    @Upsert
    suspend fun upsertRead(read: VideoExtraReadEntity)
}
