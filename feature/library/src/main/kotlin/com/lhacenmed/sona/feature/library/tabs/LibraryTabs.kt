package com.lhacenmed.sona.feature.library.tabs

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.datastore.LibraryTab
import com.lhacenmed.sona.feature.library.R

/** What [this] is called - on the library's strip, and wherever its tabs are chosen or arranged. */
val LibraryTab.label: String
    @Composable get() = stringResource(
        when (this) {
            LibraryTab.TRACKS -> R.string.library_tracks_title
            LibraryTab.ARTISTS -> R.string.library_artists_title
            LibraryTab.ALBUMS -> R.string.library_albums_title
            LibraryTab.GENRES -> R.string.library_genres_title
            LibraryTab.FOLDERS -> R.string.library_folders_title
            LibraryTab.VIDEOS -> R.string.library_videos_title
        },
    )
