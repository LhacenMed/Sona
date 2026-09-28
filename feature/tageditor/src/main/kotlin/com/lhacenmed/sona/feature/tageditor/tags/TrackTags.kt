package com.lhacenmed.sona.feature.tageditor.tags

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Title
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One tag the editor reads, shows and writes: how it is labelled, drawn and typed, and the name TagLib
 * gives it in every format alike. The order here is the order the fields are laid out in.
 */
enum class TagField(
    val label: String,
    val icon: ImageVector,
    internal val key: String,
    val isNumeric: Boolean = false,
    val isMultiline: Boolean = false,
) {
    TITLE("Title", Icons.Filled.Title, "TITLE"),
    ARTIST("Artist", Icons.Filled.Person, "ARTIST"),
    ALBUM("Album", Icons.Filled.Album, "ALBUM"),
    ALBUM_ARTIST("Album artist", Icons.Filled.Group, "ALBUMARTIST"),
    GENRE("Genre", Icons.Filled.Sell, "GENRE"),
    YEAR("Year", Icons.Filled.CalendarMonth, "DATE", isNumeric = true),
    TRACK_NUMBER("Track number", Icons.Filled.Numbers, "TRACKNUMBER", isNumeric = true),
    TRACK_TOTAL("Track count", Icons.Filled.Numbers, "TRACKTOTAL", isNumeric = true),
    DISC_NUMBER("Disc number", Icons.Filled.Numbers, "DISCNUMBER", isNumeric = true),
    COMPOSER("Composer", Icons.Filled.MusicNote, "COMPOSER"),
    COMMENT("Comment", Icons.AutoMirrored.Filled.Comment, "COMMENT"),
    LYRICS("Lyrics", Icons.Filled.Lyrics, "LYRICS", isMultiline = true),
}

/**
 * A track's tags, one text per [TagField]. A field left blank is one the file does not carry, and is taken
 * out of it on save - so two sets of tags are equal exactly when saving one over the other would change
 * nothing.
 */
@Immutable
data class TrackTags(private val values: Map<TagField, String> = emptyMap()) {

    operator fun get(field: TagField): String = values[field].orEmpty()

    fun with(field: TagField, value: String): TrackTags =
        TrackTags(if (value.isBlank()) values - field else values + (field to value))

    /** These tags with every field [other] fills taken from it, the rest left as they are. */
    fun overlaidWith(other: TrackTags): TrackTags = TrackTags(values + other.values)

    /** The fields that carry something, in [TagField]'s order. */
    val filledFields: List<TagField> get() = TagField.entries.filter { it in values }
}
