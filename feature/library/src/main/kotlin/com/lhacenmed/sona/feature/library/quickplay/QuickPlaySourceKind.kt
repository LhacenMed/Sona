package com.lhacenmed.sona.feature.library.quickplay

import androidx.compose.ui.graphics.vector.ImageVector
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.model.PlaybackParent

/** The kinds of collection quick play can be set to play, each chosen on its own picker. */
enum class QuickPlaySourceKind(internal val icon: ImageVector) {
    PLAYLIST(SonaIcons.Playlist),
    ARTIST(SonaIcons.Artist),
    ALBUM(SonaIcons.Album),
    GENRE(SonaIcons.Genre),
    FOLDER(SonaIcons.Folder),
}

/**
 * The kind of collection [this] is, or null for what is chosen without a picker - the listening histories,
 * which are one each - and for the videos, never a quick play source.
 */
val PlaybackParent.quickPlaySourceKind: QuickPlaySourceKind?
    get() = when (this) {
        is PlaybackParent.Playlist -> QuickPlaySourceKind.PLAYLIST
        is PlaybackParent.Artist -> QuickPlaySourceKind.ARTIST
        is PlaybackParent.Album -> QuickPlaySourceKind.ALBUM
        is PlaybackParent.Genre -> QuickPlaySourceKind.GENRE
        is PlaybackParent.Folder -> QuickPlaySourceKind.FOLDER
        PlaybackParent.Videos, PlaybackParent.RecentlyPlayed, PlaybackParent.MostPlayed -> null
    }
