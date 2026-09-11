package com.lhacenmed.sona.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-ordered list of tracks.
 *
 * Favourites is one of these rather than a flag on a track, because everything a playlist can do -
 * reorder by dragging, take a whole folder, export to M3U - is something favourites has to do too.
 * As a column it would have needed each of those actions to carry an "unless it's favourites"
 * branch; as a row it simply is a playlist, and there is one code path.
 *
 * [isBuiltIn] marks the ones the app owns rather than the user: they cannot be renamed or deleted,
 * because the heart button needs somewhere to write. Kept as a column rather than a reserved id, so
 * a user playlist can never collide with one and need displacing.
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
)
