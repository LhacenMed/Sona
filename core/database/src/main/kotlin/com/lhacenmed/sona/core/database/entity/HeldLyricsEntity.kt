package com.lhacenmed.sona.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A file at [path] Sona has seen holding lyrics - its own, or written by Sona - whatever it holds now. Missing lyrics are
 * looked for on the web only for a file never seen with any: lyrics taken out or changed by hand are left as they are, a
 * search's guess never put back over them.
 *
 * Keyed by path, as the scanner meets a file, and moved with the file - see `LibraryWriter`.
 */
@Entity(tableName = "held_lyrics")
data class HeldLyricsEntity(@PrimaryKey val path: String)
