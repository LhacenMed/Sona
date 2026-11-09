package com.lhacenmed.sona.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.nio.ByteBuffer

/**
 * The Most replayed graph the site a video was downloaded from gives it - YouTube's, from everyone's watching - its heights
 * from 0 to 1 along the video, as floats. Shown in place of the device's own. Kept by its track, as the device's own is - see
 * `ReplayHeatmapEntity` - so it goes where the track goes, and with it.
 */
@Entity(
    tableName = "online_heatmaps",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
class OnlineHeatmapEntity(
    @PrimaryKey val trackId: Long,
    val heights: ByteArray,
) {
    /** The graph's heights along the video, from 0 to 1. */
    fun heightsAlong(): FloatArray = FloatArray(heights.size / Float.SIZE_BYTES).also { ByteBuffer.wrap(heights).asFloatBuffer().get(it) }

    constructor(trackId: Long, heights: FloatArray) :
        this(trackId, ByteBuffer.allocate(heights.size * Float.SIZE_BYTES).apply { asFloatBuffer().put(heights) }.array())
}
