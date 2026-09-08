package com.lhacenmed.sona.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted playback queue row - mirrors Fossify Music Player's `queue_items` table shape so the
 * full queue (track ids + order + which one is current + its last playback position) survives a
 * process death/restart.
 *
 * Deviation from Fossify: `lastPositionMs` is stored directly in milliseconds (a `Long`) rather
 * than Fossify's whole-seconds `Int`, since Room/Kotlin has no reason to round-trip through second
 * precision here - there is no legacy column format to stay compatible with.
 */
@Entity(tableName = "queue_items")
data class QueueItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val trackOrder: Int,
    val isCurrent: Boolean,
    val lastPositionMs: Long,
)
