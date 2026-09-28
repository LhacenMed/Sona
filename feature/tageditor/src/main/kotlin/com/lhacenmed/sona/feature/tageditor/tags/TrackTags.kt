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
import org.jaudiotagger.tag.FieldKey

/**
 * One tag the editor reads, shows and writes: how it is labelled, drawn and typed, and the key it is stored
 * under in the file. The order here is the order the fields are laid out in.
 */
enum class TagField(
    val label: String,
    val icon: ImageVector,
    internal val key: FieldKey,
    val isNumeric: Boolean = false,
    val isMultiline: Boolean = false,
) {
    TITLE("Title", Icons.Filled.Title, FieldKey.TITLE),
    ARTIST("Artist", Icons.Filled.Person, FieldKey.ARTIST),
    ALBUM("Album", Icons.Filled.Album, FieldKey.ALBUM),
    ALBUM_ARTIST("Album artist", Icons.Filled.Group, FieldKey.ALBUM_ARTIST),
    GENRE("Genre", Icons.Filled.Sell, FieldKey.GENRE),
    YEAR("Year", Icons.Filled.CalendarMonth, FieldKey.YEAR, isNumeric = true),
    TRACK_NUMBER("Track number", Icons.Filled.Numbers, FieldKey.TRACK, isNumeric = true),
    TRACK_TOTAL("Track count", Icons.Filled.Numbers, FieldKey.TRACK_TOTAL, isNumeric = true),
    DISC_NUMBER("Disc number", Icons.Filled.Numbers, FieldKey.DISC_NO, isNumeric = true),
    COMPOSER("Composer", Icons.Filled.MusicNote, FieldKey.COMPOSER),
    COMMENT("Comment", Icons.AutoMirrored.Filled.Comment, FieldKey.COMMENT),
    LYRICS("Lyrics", Icons.Filled.Lyrics, FieldKey.LYRICS, isMultiline = true),
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
