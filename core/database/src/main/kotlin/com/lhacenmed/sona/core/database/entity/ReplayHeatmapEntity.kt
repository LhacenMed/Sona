package com.lhacenmed.sona.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** How often each moment of a video has been watched - see `ReplayHeatmap`, whose bytes [playedMs] are. Goes with its track. */
@Entity(
    tableName = "replay_heatmaps",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
class ReplayHeatmapEntity(
    @PrimaryKey val trackId: Long,
    val durationMs: Long,
    val playedMs: ByteArray,
)
