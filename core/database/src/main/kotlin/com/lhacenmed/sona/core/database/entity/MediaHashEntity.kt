package com.lhacenmed.sona.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The fingerprint of the file at [path] - the SHA-256 of its bytes - as it was when it was last read.
 *
 * A cache: the fingerprint holds only while the file still has the [sizeBytes] and [modifiedAt] it had then,
 * so a file is read again only once it changes. Keyed by path rather than by track, since a file need not be
 * in the library to be compared - one in an excluded folder, or one just restored.
 */
@Entity(tableName = "media_hashes")
data class MediaHashEntity(
    @PrimaryKey val path: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val fingerprint: String,
)
