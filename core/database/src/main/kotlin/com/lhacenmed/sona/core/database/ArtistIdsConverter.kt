package com.lhacenmed.sona.core.database

import androidx.room.TypeConverter

/**
 * Stores the artists a track or album is credited to in one column, as ",12,34," - every id between commas,
 * so "the tracks of artist 12" is a match on ",12," that no other id can make.
 */
class ArtistIdsConverter {

    @TypeConverter
    fun toColumn(artistIds: List<Long>): String =
        if (artistIds.isEmpty()) "" else artistIds.joinToString(SEPARATOR, prefix = SEPARATOR, postfix = SEPARATOR)

    @TypeConverter
    fun fromColumn(column: String): List<Long> = column.split(SEPARATOR).filter { it.isNotEmpty() }.map(String::toLong)

    private companion object {
        const val SEPARATOR = ","
    }
}
