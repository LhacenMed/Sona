package com.lhacenmed.sona.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * How often, and how recently, a track has been listened to.
 *
 * This one row is what both derived lists are made of: "Recent" orders by [lastPlayedAt], "Most
 * played" by [playCount]. Fossify keeps each of those as a real playlist full of rows instead,
 * which is why its history grows without bound and is never pruned - here they are queries, so
 * there is nothing to accumulate and nothing that can drift out of step with the counts.
 *
 * The two columns are written at different moments, following the source app: [lastPlayedAt] the
 * instant a track starts, [playCount] only once a listen passes the threshold.
 */
@Entity(
    tableName = "play_stats",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlayStatsEntity(
    @PrimaryKey val trackId: Long,
    val playCount: Int,
    val lastPlayedAt: Long,
)
