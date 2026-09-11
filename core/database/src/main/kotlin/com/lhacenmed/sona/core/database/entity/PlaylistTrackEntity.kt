package com.lhacenmed.sona.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * One track's membership of one playlist, at one position.
 *
 * Membership is a row of its own rather than a copy of the track. Fossify Music Player, which this
 * is otherwise ported from, instead duplicates the whole track row once per playlist - which forces
 * it to fan metadata out across every copy after each scan, and to de-duplicate in memory on every
 * library read. Keeping membership separate costs one join and avoids both.
 *
 * [position] is dense within a playlist, so a drag reorder rewrites the run it moved through in one
 * transaction. Cascading on both sides is what keeps this table honest without any cleanup code:
 * deleting a playlist drops its membership, and a track that disappears from the device takes its
 * memberships with it.
 *
 * The composite key means a track appears at most once in a playlist, matching the source app.
 */
@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["playlistId", "position"]),
        Index(value = ["trackId"]),
    ],
)
data class PlaylistTrackEntity(
    val playlistId: Long,
    val trackId: Long,
    val position: Int,
)
