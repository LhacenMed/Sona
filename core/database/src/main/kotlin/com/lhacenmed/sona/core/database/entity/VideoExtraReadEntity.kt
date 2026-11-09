package com.lhacenmed.sona.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * That a video's file was read for what it carries of its site - its Most replayed graph and its chapters, kept by its track
 * in [OnlineHeatmapEntity] and [VideoChapterEntity] - as it stood: [signature] its last change, size and how it was read. It
 * is read again only once that changes, and goes with its track.
 */
@Entity(
    tableName = "video_extra_reads",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class VideoExtraReadEntity(
    @PrimaryKey val trackId: Long,
    val signature: Long,
)
