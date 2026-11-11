package com.lhacenmed.sona.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.designsystem.component.shortTitle
import com.lhacenmed.sona.core.model.Album
import com.lhacenmed.sona.core.model.Artist
import com.lhacenmed.sona.core.model.BuiltInList
import com.lhacenmed.sona.core.model.Folder
import com.lhacenmed.sona.core.model.Genre
import com.lhacenmed.sona.core.model.PlaybackParent
import com.lhacenmed.sona.core.model.Playlist
import com.lhacenmed.sona.core.model.builtInList

/** What the playing queue was built from, as the player's header names it. */
internal sealed interface PlayingFrom {
    /** One of the library's collections, named alongside what kind of collection it is. */
    data class Collection(val name: String, val kind: CollectionKind) : PlayingFrom

    /** The whole library - the tracks tab, or a search of it. */
    data object AllTracks : PlayingFrom

    /** Every video - the Videos tab, or a search of it. */
    data object Videos : PlayingFrom

    /** One of the app's own lists - Favorites, or a listening history. */
    data class BuiltIn(val list: BuiltInList) : PlayingFrom
}

internal enum class CollectionKind { ALBUM, ARTIST, GENRE, PLAYLIST, FOLDER }

/**
 * [parent] named from the library as it stands, or null while the collection it names is not in the
 * library - gone since, or not read yet.
 */
internal fun playingFromOf(
    parent: PlaybackParent?,
    albums: List<Album>,
    artists: List<Artist>,
    genres: List<Genre>,
    playlists: List<Playlist>,
    folders: List<Folder>,
): PlayingFrom? =
    when (parent) {
        null -> PlayingFrom.AllTracks
        PlaybackParent.RecentlyPlayed -> PlayingFrom.BuiltIn(BuiltInList.RECENTLY_PLAYED)
        PlaybackParent.MostPlayed -> PlayingFrom.BuiltIn(BuiltInList.MOST_PLAYED)
        PlaybackParent.Videos -> PlayingFrom.Videos
        is PlaybackParent.Album -> albums.firstOrNull { it.id == parent.albumId }?.title?.named(CollectionKind.ALBUM)
        is PlaybackParent.Artist -> artists.firstOrNull { it.id == parent.artistId }?.name?.named(CollectionKind.ARTIST)
        is PlaybackParent.Genre -> genres.firstOrNull { it.id == parent.genreId }?.name?.named(CollectionKind.GENRE)
        is PlaybackParent.Playlist -> parent.builtInList?.let(PlayingFrom::BuiltIn) ?:
            playlists.firstOrNull { it.id == parent.playlistId }?.name?.named(CollectionKind.PLAYLIST)
        is PlaybackParent.Folder ->
            folders.firstOrNull { it.path == parent.folderPath }?.name?.named(CollectionKind.FOLDER)
    }

private fun String.named(kind: CollectionKind) = PlayingFrom.Collection(name = this, kind = kind)

/** What the header names: the collection's own name, or the whole library, the videos or a listening history. */
@Composable
internal fun PlayingFrom.title(): String =
    when (this) {
        is PlayingFrom.Collection -> name
        PlayingFrom.AllTracks -> stringResource(R.string.player_playing_from_all_tracks)
        PlayingFrom.Videos -> stringResource(R.string.player_playing_from_videos)
        is PlayingFrom.BuiltIn -> list.shortTitle
    }

/**
 * What kind of collection it is, said beneath its name - or null for the whole library, the videos and the two
 * listening histories, none of which is a collection of a kind the library has.
 */
@Composable
internal fun PlayingFrom.kindLabel(): String? = (this as? PlayingFrom.Collection)?.kind?.label()

@Composable
private fun CollectionKind.label(): String =
    stringResource(
        when (this) {
            CollectionKind.ALBUM -> R.string.player_collection_kind_album
            CollectionKind.ARTIST -> R.string.player_collection_kind_artist
            CollectionKind.GENRE -> R.string.player_collection_kind_genre
            CollectionKind.PLAYLIST -> R.string.player_collection_kind_playlist
            CollectionKind.FOLDER -> R.string.player_collection_kind_folder
        },
    )
