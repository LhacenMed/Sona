package com.lhacenmed.sona.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import com.lhacenmed.sona.core.model.VideoChapter

/**
 * One chapter of a video, as the site it was downloaded from marks it - see `VideoChapter`. Kept by its track, as the
 * site's Most replayed graph is - see [OnlineHeatmapEntity] - so it goes where the track goes, and with it.
 */
@Entity(
    tableName = "video_chapters",
    primaryKeys = ["trackId", "startMs"],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class VideoChapterEntity(
    val trackId: Long,
    val startMs: Long,
    val title: String,
)

fun VideoChapterEntity.toDomain() = VideoChapter(startMs, title)
