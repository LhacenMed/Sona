package com.lhacenmed.sona.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.PrimaryKey

/**
 * A track's lyrics as search reads them - a copy, kept only so a search need not open every file: the file
 * is still the one place they are kept, and this is read again from it whenever the file changes. Losing
 * every row loses nothing but time, the lyrics being read back in the background.
 *
 * Full-text indexed on [searchText] alone, so a word is found in a moment however many tracks there are.
 * A track whose file holds no lyrics has a row too, blank, so its file is not read again until it changes.
 */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61, notIndexed = ["lyrics", "signature"])
@Entity(tableName = "lyrics_index")
data class LyricsIndexEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val trackId: Long,
    /** The lyrics as shown, a line each - what a row found by them shows the line of. */
    val lyrics: String,
    /** The same in search form - see `searchFormOf` - which is what is matched. */
    val searchText: String,
    /** The file's size and time it was last changed, as when it was read: a file showing others is read again. */
    val signature: Long,
)
