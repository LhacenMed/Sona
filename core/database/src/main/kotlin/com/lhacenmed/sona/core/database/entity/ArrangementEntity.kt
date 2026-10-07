package com.lhacenmed.sona.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.lhacenmed.sona.core.model.sort.SortableList

/**
 * Where the user dragged one track to in one list - a playlist, an album, an artist, a genre or a
 * folder, named by its [list] kind and [instanceId] as its sort is.
 *
 * Every list's hand-made order is kept here, alike, so membership stays whatever its list's own is: a
 * playlist's rows, an album's tags. A track of the list with no row here joined it after the order was
 * arranged - which is what places it as a new track. A drop rewrites its list's rows whole, and a track
 * that leaves the device takes its rows with it.
 */
@Entity(
    tableName = "arrangements",
    primaryKeys = ["list", "instanceId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["trackId"])],
)
data class ArrangementEntity(
    val list: SortableList,
    val instanceId: String,
    val trackId: Long,
    val position: Int,
)
