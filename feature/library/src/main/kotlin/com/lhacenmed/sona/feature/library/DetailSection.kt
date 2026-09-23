package com.lhacenmed.sona.feature.library

import com.lhacenmed.sona.core.model.Album
import com.lhacenmed.sona.core.model.Artist

/**
 * A section a detail screen lists above its tracks - Auxio's `DetailSection`s, less the tracks
 * themselves, which every detail screen ends with. An artist lists its albums and those it appears
 * on; a genre, the artists in it.
 */
internal sealed interface DetailSection {
    val title: String

    data class Albums(override val title: String, val albums: List<Album>) : DetailSection

    data class Artists(val artists: List<Artist>) : DetailSection {
        override val title: String get() = "Artists"
    }
}

/** An artist's albums, newest first and undated last - Auxio's `ARTIST_ALBUM_SORT`, by date descending. */
internal val ArtistAlbumOrder: Comparator<Album> =
    compareByDescending<Album, Int?>(nullsFirst()) { it.year }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
