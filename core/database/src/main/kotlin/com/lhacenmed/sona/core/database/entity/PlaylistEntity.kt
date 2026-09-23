package com.lhacenmed.sona.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-ordered list of tracks.
 *
 * Favorites is one of these rather than a flag on a track, because everything a playlist can do -
 * reorder by dragging, take a whole folder, export to M3U - is something favorites has to do too.
 * As a column it would have needed each of those actions to carry an "unless it's favorites"
 * branch; as a row it simply is a playlist, and there is one code path.
 *
 * [isBuiltIn] marks the ones the app owns rather than the user: they cannot be renamed or deleted,
 * because the heart button needs somewhere to write. Kept as a column rather than a reserved id, so
 * a user playlist can never collide with one and need displacing.
 *
 * The cover is [coverSource], with [coverTrackId] or [coverImageUri] for the sources that name one.
 * The track is not a foreign key: a track that leaves the library - an unmounted card - falls back
 * to the stacked cover while it is gone, and is the cover again once it is back.
 */
@Entity(
    tableName = "playlists",
    indices = [Index(value = ["name"], unique = true)],
)
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isBuiltIn: Boolean = false,
    val createdAt: Long,
    /** When the playlist last changed - made, edited, or its tracks added, removed or moved. */
    val modifiedAt: Long,
    // A default in the table too, so a row inserted by raw SQL - Favorites' seed - is stacked as well.
    @ColumnInfo(defaultValue = "STACKED") val coverSource: PlaylistCoverSource = PlaylistCoverSource.STACKED,
    val coverTrackId: Long? = null,
    val coverImageUri: String? = null,
)

/** Where a playlist's cover comes from - stored by name. */
enum class PlaylistCoverSource {
    STACKED,
    FIRST_TRACK,
    LAST_TRACK,
    TRACK,
    IMAGE,
}
