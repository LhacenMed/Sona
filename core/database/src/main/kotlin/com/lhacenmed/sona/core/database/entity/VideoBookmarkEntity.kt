package com.lhacenmed.sona.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey

/** A place in a video the user marked, to come back to - see `VideoBookmarkStore`. Goes with its track. */
@Entity(
    tableName = "video_bookmarks",
    primaryKeys = ["trackId", "positionMs"],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class VideoBookmarkEntity(
    val trackId: Long,
    val positionMs: Long,
)
