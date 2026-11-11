package com.lhacenmed.sona.feature.library

import androidx.annotation.StringRes
import com.lhacenmed.sona.core.model.Album
import com.lhacenmed.sona.core.model.Artist
import com.lhacenmed.sona.core.model.Folder
import com.lhacenmed.sona.core.model.Genre
import com.lhacenmed.sona.core.model.Playlist
import com.lhacenmed.sona.feature.library.R

/**
 * A section a detail screen lists above its tracks - Auxio's `DetailSection`s, less the tracks
 * themselves, which every detail screen ends with. An artist lists its albums and those it appears
 * on; a genre, the artists in it; Favorites, the collections favorited as themselves, a section per kind.
 */
internal sealed interface DetailSection {
    @get:StringRes
    val titleRes: Int

    data class Albums(@StringRes override val titleRes: Int, val albums: List<Album>) : DetailSection

    data class Artists(val artists: List<Artist>) : DetailSection {
        override val titleRes get() = R.string.library_artists_title
    }

    data class Genres(val genres: List<Genre>) : DetailSection {
        override val titleRes get() = R.string.library_genres_title
    }

    data class Folders(val folders: List<Folder>) : DetailSection {
        override val titleRes get() = R.string.library_folders_title
    }

    data class Playlists(val playlists: List<Playlist>) : DetailSection {
        override val titleRes get() = R.string.playlists_title
    }
}

/** An artist's albums, newest first and undated last - Auxio's `ARTIST_ALBUM_SORT`, by date descending. */
internal val ArtistAlbumOrder: Comparator<Album> =
    compareByDescending<Album, Int?>(nullsFirst()) { it.year }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
