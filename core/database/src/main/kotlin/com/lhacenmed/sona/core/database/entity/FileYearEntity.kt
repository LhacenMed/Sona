package com.lhacenmed.sona.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The year read from the file at [path] itself, where MediaStore gives it none - [year] null for a file holding no date -
 * as the file stood at [signature], its last change and size. MediaStore reads a date only as a bare year: one written
 * whole, "2019-05-03", as taggers write it, it leaves undated, though the file says when it is from.
 *
 * Read again only once the file changes, so a file is opened for it once, not on every scan - see `FileYears`.
 */
@Entity(tableName = "file_years")
data class FileYearEntity(
    @PrimaryKey val path: String,
    val signature: Long,
    val year: Int?,
)
