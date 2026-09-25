package com.lhacenmed.sona.feature.library.shuffle

import androidx.compose.ui.graphics.vector.ImageVector
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.model.PlaybackParent

/** The kinds of collection shuffle-all can be set to play instead of every track. */
enum class ShuffleSourceKind(internal val icon: ImageVector) {
    PLAYLIST(SonaIcons.Playlist),
    ARTIST(SonaIcons.Artist),
    ALBUM(SonaIcons.Album),
    GENRE(SonaIcons.Genre),
    FOLDER(SonaIcons.Folder),
}

/** The kind of collection [this] is, or null for the listening histories - never a shuffle-all source. */
val PlaybackParent.shuffleSourceKind: ShuffleSourceKind?
    get() = when (this) {
        is PlaybackParent.Playlist -> ShuffleSourceKind.PLAYLIST
        is PlaybackParent.Artist -> ShuffleSourceKind.ARTIST
        is PlaybackParent.Album -> ShuffleSourceKind.ALBUM
        is PlaybackParent.Genre -> ShuffleSourceKind.GENRE
        is PlaybackParent.Folder -> ShuffleSourceKind.FOLDER
        PlaybackParent.RecentlyPlayed, PlaybackParent.MostPlayed -> null
    }
