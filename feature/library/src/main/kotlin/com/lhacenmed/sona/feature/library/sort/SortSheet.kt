package com.lhacenmed.sona.feature.library.sort

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaSortSheet
import com.lhacenmed.sona.core.designsystem.component.sheet.SortScopeLabels
import com.lhacenmed.sona.core.model.sort.SortableList

/** Choosing how a library list is sorted - [SonaSortSheet], opened on [sort]'s order and applied to it. */
@Composable
internal fun SortSheet(
    sort: SortControl,
    onDismiss: () -> Unit,
) {
    val initialOrder = remember { sort.currentOrder() }
    SonaSortSheet(
        criteria = sort.criteria,
        initialOrder = initialOrder,
        initialNewTracksDirection = remember { sort.customOrderFrom(initialOrder).direction },
        defaultScope = remember { sort.defaultScope },
        // Only a list there are many of has anywhere else to reach; the library's own lists are the
        // only list of their kind, so they never ask the question.
        scopeLabels = sort.target.list.scopeLabels(),
        onApply = sort::apply,
        onDismiss = onDismiss,
    )
}

/** What "this one" and "all of them" are called, or null for a list that is the only one of its kind. */
private fun SortableList.scopeLabels(): SortScopeLabels? = when (this) {
    SortableList.ALBUM_TRACKS -> SortScopeLabels("This album", "All albums")
    SortableList.ARTIST_TRACKS -> SortScopeLabels("This artist", "All artists")
    SortableList.GENRE_TRACKS -> SortScopeLabels("This genre", "All genres")
    SortableList.FOLDER_TRACKS -> SortScopeLabels("This folder", "All folders")
    SortableList.PLAYLIST_TRACKS -> SortScopeLabels("This playlist", "All playlists")
    SortableList.TRACKS,
    SortableList.ALBUMS,
    SortableList.ARTISTS,
    SortableList.GENRES,
    SortableList.FOLDERS,
    SortableList.VIDEOS,
    SortableList.PLAYLISTS,
    -> null
}
