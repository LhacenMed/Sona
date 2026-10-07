package com.lhacenmed.sona.feature.library.favorite

import com.lhacenmed.sona.core.database.FAVORITES_PLAYLIST_ID
import com.lhacenmed.sona.core.model.PlaybackParent
import com.lhacenmed.sona.core.model.playbackParent
import com.lhacenmed.sona.feature.library.options.OptionsTarget
import com.lhacenmed.sona.feature.library.selection.SelectionKey

/**
 * What Favorites holds at a moment: its tracks - the tracks of its playlist - and the collections favorited
 * as themselves, which it lists one tap away.
 */
internal data class Favorites(
    val trackIds: Set<Long> = emptySet(),
    val collections: Set<PlaybackParent> = emptySet(),
) {
    /** Whether every one of [items] is in Favorites - what a heart is filled by. */
    fun containsAll(items: FavoriteItems): Boolean =
        trackIds.containsAll(items.trackIds) && collections.containsAll(items.collections)
}

/**
 * What favoriting one thing puts in Favorites: [trackIds] into its playlist, [collections] as themselves. A
 * selection can hold both - a few tracks, an album, an artist - and favorites each its own way.
 */
internal data class FavoriteItems(
    val trackIds: List<Long>,
    val collections: List<PlaybackParent>,
)

/**
 * [parent] favorited as itself - null for what is no collection to favorite: Favorites itself, which it would
 * only list inside itself, and the lists that are not the library's own - the listening histories, the Videos tab.
 */
internal fun favoriteItemsOf(parent: PlaybackParent): FavoriteItems? = when (parent) {
    is PlaybackParent.Album,
    is PlaybackParent.Artist,
    is PlaybackParent.Genre,
    is PlaybackParent.Folder,
    -> FavoriteItems(emptyList(), listOf(parent))

    is PlaybackParent.Playlist -> FavoriteItems(emptyList(), listOf(parent)).takeIf { parent.playlistId != FAVORITES_PLAYLIST_ID }

    PlaybackParent.Videos, PlaybackParent.RecentlyPlayed, PlaybackParent.MostPlayed -> null
}

/** What favoriting [this] puts in Favorites - see [FavoriteItems] - or null where there is nothing to favorite. */
internal fun OptionsTarget.favoriteItems(): FavoriteItems? = when (this) {
    is OptionsTarget.ForTrack -> FavoriteItems(listOf(track.id), emptyList())
    is OptionsTarget.ForAlbum -> favoriteItemsOf(PlaybackParent.Album(album.id))
    is OptionsTarget.ForArtist -> favoriteItemsOf(PlaybackParent.Artist(artist.id))
    is OptionsTarget.ForGenre -> favoriteItemsOf(PlaybackParent.Genre(genre.id))
    is OptionsTarget.ForPlaylist -> favoriteItemsOf(PlaybackParent.Playlist(playlist.id))
    is OptionsTarget.ForFolder -> favoriteItemsOf(folder.playbackParent)
    is OptionsTarget.ForSelection -> {
        val parts = keys.map { key -> if (key is SelectionKey.Track) FavoriteItems(listOf(key.trackId), emptyList()) else favoriteItemsOf(key.parent) }
        FavoriteItems(
            trackIds = parts.flatMap { it?.trackIds.orEmpty() },
            collections = parts.flatMap { it?.collections.orEmpty() },
        ).takeIf { it.trackIds.isNotEmpty() || it.collections.isNotEmpty() }
    }
}

/** The collection a selected collection's row stands for. */
private val SelectionKey.parent: PlaybackParent
    get() = when (this) {
        is SelectionKey.Album -> PlaybackParent.Album(albumId)
        is SelectionKey.Artist -> PlaybackParent.Artist(artistId)
        is SelectionKey.Genre -> PlaybackParent.Genre(genreId)
        is SelectionKey.Playlist -> PlaybackParent.Playlist(playlistId)
        is SelectionKey.Folder -> PlaybackParent.Folder(folderPath, isVideo)
        is SelectionKey.Track -> error("A track is favorited into Favorites' playlist, not as a collection")
    }
