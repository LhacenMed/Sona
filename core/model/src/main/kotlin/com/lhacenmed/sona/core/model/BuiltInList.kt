package com.lhacenmed.sona.core.model

/** Favorites' id among the playlists: seeded first, before any of the user's own can exist. */
const val FAVORITES_PLAYLIST_ID = 1L

/**
 * The lists the app keeps of its own: Favorites, a playlist the user fills, and the two listening histories it fills itself.
 * Each is one identity wherever it appears - named, drawn and told apart by this, never by what it is called - so its name is
 * the user's language's, whatever the database holds. See the design system's names and covers for each.
 */
enum class BuiltInList(val playlistId: Long?) {
    FAVORITES(FAVORITES_PLAYLIST_ID),
    RECENTLY_PLAYED(null),
    MOST_PLAYED(null),
}

/** The built-in list [this] playlist is - null for one of the user's own. */
val Playlist.builtInList: BuiltInList? get() = BuiltInList.entries.firstOrNull { it.playlistId == id }

/** The built-in list [this] plays from - null for any other source. */
val PlaybackParent.builtInList: BuiltInList?
    get() = when (this) {
        PlaybackParent.RecentlyPlayed -> BuiltInList.RECENTLY_PLAYED
        PlaybackParent.MostPlayed -> BuiltInList.MOST_PLAYED
        is PlaybackParent.Playlist -> BuiltInList.entries.firstOrNull { it.playlistId == playlistId }
        else -> null
    }
