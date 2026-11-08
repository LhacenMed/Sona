package com.lhacenmed.sona.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * When Sona counts the file at [path] as added to the library, in place of the time Android gives it - for a file a
 * restore brought back, so the restored files keep the order they were added in on the device backed up, and all
 * stand newest here. Android's own time cannot be set: it is the moment its row was made.
 *
 * Keyed by path, as the scanner meets a file, and moved with the file - see `LibraryWriter`.
 */
@Entity(tableName = "added_dates")
data class AddedDateEntity(
    @PrimaryKey val path: String,
    val addedAtSeconds: Long,
)
