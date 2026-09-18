package com.lhacenmed.sona.core.database

import androidx.room.TypeConverter

/** Stores a list of cover addresses in one column, one address per line - an address never holds a line break. */
class CoverArtUrisConverter {

    @TypeConverter
    fun toColumn(coverArtUris: List<String>): String = coverArtUris.joinToString(SEPARATOR)

    @TypeConverter
    fun fromColumn(column: String): List<String> = if (column.isEmpty()) emptyList() else column.split(SEPARATOR)

    private companion object {
        const val SEPARATOR = "\n"
    }
}
