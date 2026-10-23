package com.lhacenmed.sona.feature.library

import com.lhacenmed.sona.core.datastore.LibraryTab
import com.lhacenmed.sona.core.model.PlaybackParent
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.core.navigation.ScreenArrival

/** Where the list the queue plays from is shown: a screen of its own, or one of the library's tabs. */
sealed interface PlayingList {
    data class OnScreen(val screen: Screen) : PlayingList

    data class OnTab(val tab: LibraryTab) : PlayingList
}

/** The list [parent] names - the Tracks tab for the whole library, the Videos tab for every video. */
fun playingListOf(parent: PlaybackParent?): PlayingList = when (parent) {
    null -> PlayingList.OnTab(LibraryTab.TRACKS)
    PlaybackParent.Videos -> PlayingList.OnTab(LibraryTab.VIDEOS)
    is PlaybackParent.Album -> PlayingList.OnScreen(AlbumDetailScreen(parent.albumId))
    is PlaybackParent.Artist -> PlayingList.OnScreen(ArtistDetailScreen(parent.artistId))
    is PlaybackParent.Genre -> PlayingList.OnScreen(GenreDetailScreen(parent.genreId))
    is PlaybackParent.Playlist -> PlayingList.OnScreen(PlaylistDetailScreen(parent.playlistId))
    is PlaybackParent.Folder -> PlayingList.OnScreen(FolderDetailScreen(parent.folderPath, parent.isVideo))
    PlaybackParent.RecentlyPlayed -> PlayingList.OnScreen(RecentlyPlayedScreen)
    PlaybackParent.MostPlayed -> PlayingList.OnScreen(MostPlayedScreen)
}

/** The library opened on [tab] to do [arrival] there - see [ScreenArrival]. */
data class LibraryTabArrival(val tab: LibraryTab, val arrival: ScreenArrival)
