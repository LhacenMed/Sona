package com.lhacenmed.sona.core.model

/**
 * The collection the playing queue was built from: Auxio's `MusicParent` playback parent.
 *
 * It is what lets a list say "this is the one playing" rather than merely "this holds the playing
 * track" - an album row lights up when playback came from that album, and stays dark when the same
 * track is playing from a genre instead. Null is Auxio's null parent: the queue is the whole library
 * rather than any one collection, which is what the tracks tab and search play.
 *
 * Identities rather than the objects themselves, so the parent stays comparable across a rescan and
 * can be written down and read back on the next launch.
 */
sealed interface PlaybackParent {
    data class Album(val albumId: Long) : PlaybackParent

    data class Artist(val artistId: Long) : PlaybackParent

    data class Genre(val genreId: Long) : PlaybackParent

    data class Playlist(val playlistId: Long) : PlaybackParent

    data class Folder(val folderPath: String) : PlaybackParent

    data object RecentlyPlayed : PlaybackParent

    data object MostPlayed : PlaybackParent
}

/** How a parent is written down so the next launch can show the same list as playing. */
fun PlaybackParent.toStorageKey(): String = when (this) {
    is PlaybackParent.Album -> "album:$albumId"
    is PlaybackParent.Artist -> "artist:$artistId"
    is PlaybackParent.Genre -> "genre:$genreId"
    is PlaybackParent.Playlist -> "playlist:$playlistId"
    is PlaybackParent.Folder -> "folder:$folderPath"
    PlaybackParent.RecentlyPlayed -> "recentlyPlayed"
    PlaybackParent.MostPlayed -> "mostPlayed"
}

/** [toStorageKey] read back, or null for anything this version no longer recognises. */
fun playbackParentOf(storageKey: String): PlaybackParent? {
    val value = storageKey.substringAfter(':', missingDelimiterValue = "")
    return when (storageKey.substringBefore(':')) {
        "album" -> value.toLongOrNull()?.let(PlaybackParent::Album)
        "artist" -> value.toLongOrNull()?.let(PlaybackParent::Artist)
        "genre" -> value.toLongOrNull()?.let(PlaybackParent::Genre)
        "playlist" -> value.toLongOrNull()?.let(PlaybackParent::Playlist)
        "folder" -> value.takeIf { it.isNotEmpty() }?.let(PlaybackParent::Folder)
        "recentlyPlayed" -> PlaybackParent.RecentlyPlayed
        "mostPlayed" -> PlaybackParent.MostPlayed
        else -> null
    }
}
