package com.lhacenmed.sona.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Where a video was left part-way through, so it carries on from there the next time it plays - see
 * `ResumePositionRecorder`. A video watched to its end, or barely begun, has no row.
 */
@Entity(
    tableName = "resume_positions",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ResumePositionEntity(
    @PrimaryKey val trackId: Long,
    val positionMs: Long,
)
