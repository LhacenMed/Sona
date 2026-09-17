package com.lhacenmed.sona.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * The lyrics a track shows, once they have been looked for. Ported from ArchiveTune's `LyricsEntity`.
 *
 * A row exists as soon as a track's tags have been read, even when they held nothing - then [lyrics] is
 * [LYRICS_NOT_FOUND], so a track without lyrics is not read again every time it plays. Keyed by the
 * track, and removed with it.
 */
@Entity(
    tableName = "lyrics",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class LyricsEntity(
    @PrimaryKey val trackId: Long,
    val lyrics: String,
    val source: String,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val LYRICS_NOT_FOUND = "LYRICS_NOT_FOUND"
    }

    /** Where [lyrics] came from: the file's own tags, or the user typing them in. */
    enum class Source {
        EMBEDDED,
        USER_EDIT,
    }
}
