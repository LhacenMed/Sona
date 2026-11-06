package com.lhacenmed.sona.feature.library.tabs

import com.lhacenmed.sona.core.datastore.LibraryTab

/** What [this] is called - on the library's strip, and wherever its tabs are chosen or arranged. */
val LibraryTab.label: String
    get() = when (this) {
        LibraryTab.TRACKS -> "Tracks"
        LibraryTab.ARTISTS -> "Artists"
        LibraryTab.ALBUMS -> "Albums"
        LibraryTab.GENRES -> "Genres"
        LibraryTab.FOLDERS -> "Folders"
        LibraryTab.VIDEOS -> "Videos"
    }
