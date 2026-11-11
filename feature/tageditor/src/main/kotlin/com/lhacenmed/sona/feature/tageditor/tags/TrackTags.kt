package com.lhacenmed.sona.feature.tageditor.tags

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons

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
    TITLE("Title", SonaIcons.Title, "TITLE"),
    ARTIST("Artist", SonaIcons.Person, "ARTIST"),
    ALBUM("Album", SonaIcons.Album, "ALBUM"),
    ALBUM_ARTIST("Album artist", SonaIcons.Group, "ALBUMARTIST"),
    GENRE("Genre", SonaIcons.Sell, "GENRE"),
    YEAR("Year", SonaIcons.CalendarMonth, "DATE", isNumeric = true),
    TRACK_NUMBER("Track number", SonaIcons.Numbers, "TRACKNUMBER", isNumeric = true),
    TRACK_TOTAL("Track count", SonaIcons.Numbers, "TRACKTOTAL", isNumeric = true),
    DISC_NUMBER("Disc number", SonaIcons.Numbers, "DISCNUMBER", isNumeric = true),
    COMPOSER("Composer", SonaIcons.Song, "COMPOSER"),
    COMMENT("Comment", SonaIcons.Comment, "COMMENT"),
    LYRICS("Lyrics", SonaIcons.Lyrics, "LYRICS", isMultiline = true),
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

    /** These tags with every field they leave blank taken from [other], the rest left as they are. */
    fun filledFrom(other: TrackTags): TrackTags = TrackTags(other.values + values)

    /** The fields that carry something, in [TagField]'s order. */
    val filledFields: List<TagField> get() = TagField.entries.filter { it in values }
}
