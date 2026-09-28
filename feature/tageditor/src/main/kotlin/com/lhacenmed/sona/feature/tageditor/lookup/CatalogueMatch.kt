package com.lhacenmed.sona.feature.tageditor.lookup

import androidx.compose.runtime.Immutable
import com.lhacenmed.sona.feature.tageditor.tags.TagField
import com.lhacenmed.sona.feature.tageditor.tags.TrackTags

/**
 * One song a [Catalogue] answered a lookup with - YTDLnis's `MusicMetadata`: its tags as the catalogue
 * knows them, its cover, and where it came from, so the tags the catalogue only gives out one song at a time
 * can be asked for later, for the one match that is shown.
 */
@Immutable
data class CatalogueMatch(
    val tags: TrackTags,
    /** The cover at the size it is embedded at, or blank. */
    val coverUrl: String = "",
    /** The same cover, small - what a card or a row draws. */
    val thumbnailUrl: String = "",
    /** The [Catalogue.name] it came from. */
    val catalogue: String,
    internal val trackId: String = "",
    internal val albumId: String = "",
) {
    val title: String get() = tags[TagField.TITLE]
    val artist: String get() = tags[TagField.ARTIST]

    /** "Album · Year", what a match is told apart by at a glance. */
    val details: String get() = listOf(tags[TagField.ALBUM], tags[TagField.YEAR]).filter { it.isNotBlank() }.joinToString(" · ")

    internal val isUsable: Boolean get() = title.isNotBlank() && artist.isNotBlank()
}

/**
 * A catalogue songs are looked up in - YTDLnis's `MusicProvider`.
 *
 * The contract is split in two on purpose: [search] stays one request, returning what a list of candidates
 * needs, and [details] fills in what the catalogue only gives out song by song - for the one match shown,
 * rather than every candidate.
 */
internal interface Catalogue {
    val name: String

    /** The songs for [query], or null when the catalogue could not be reached - an empty list is an answer. */
    suspend fun search(query: String, limit: Int): List<CatalogueMatch>?

    /** [match] with the fields [search] could not carry; a catalogue that returns everything leaves it alone. */
    suspend fun details(match: CatalogueMatch): CatalogueMatch = match
}
