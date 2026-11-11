package com.lhacenmed.sona.feature.tageditor.tags

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.feature.tageditor.R

/**
 * One tag the editor reads, shows and writes: how it is labelled, drawn and typed, and the name TagLib
 * gives it in every format alike. The order here is the order the fields are laid out in.
 */
enum class TagField(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    internal val key: String,
    val isNumeric: Boolean = false,
    val isMultiline: Boolean = false,
) {
    TITLE(R.string.tag_field_title, SonaIcons.Title, "TITLE"),
    ARTIST(R.string.tag_field_artist, SonaIcons.Person, "ARTIST"),
    ALBUM(R.string.tag_field_album, SonaIcons.Album, "ALBUM"),
    ALBUM_ARTIST(R.string.tag_field_album_artist, SonaIcons.Group, "ALBUMARTIST"),
    GENRE(R.string.tag_field_genre, SonaIcons.Sell, "GENRE"),
    YEAR(R.string.tag_field_year, SonaIcons.CalendarMonth, "DATE", isNumeric = true),
    TRACK_NUMBER(R.string.tag_field_track_number, SonaIcons.Numbers, "TRACKNUMBER", isNumeric = true),
    TRACK_TOTAL(R.string.tag_field_track_count, SonaIcons.Numbers, "TRACKTOTAL", isNumeric = true),
    DISC_NUMBER(R.string.tag_field_disc_number, SonaIcons.Numbers, "DISCNUMBER", isNumeric = true),
    COMPOSER(R.string.tag_field_composer, SonaIcons.Song, "COMPOSER"),
    COMMENT(R.string.tag_field_comment, SonaIcons.Comment, "COMMENT"),
    LYRICS(R.string.tag_field_lyrics, SonaIcons.Lyrics, "LYRICS", isMultiline = true),
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

/** What [this] tag is called, wherever it is edited or shown. */
val TagField.label: String
    @Composable get() = stringResource(labelRes)
