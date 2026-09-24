package com.lhacenmed.sona.feature.library

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.SelectionState
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.feature.library.options.OptionsSheet
import com.lhacenmed.sona.feature.library.options.OptionsTarget

@Composable
fun AlbumsScreen(
    viewModel: LibraryViewModel,
    selection: SelectionState,
    listState: LazyListState,
    extraBottomPadding: Dp,
    onFastScrollingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val albumSections by viewModel.albumSections.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    var optionsTarget by remember { mutableStateOf<OptionsTarget.ForAlbum?>(null) }

    LibraryList(
        content = albums,
        hasPermission = hasPermission,
        isScanning = isScanning,
        emptyTitle = "No albums found",
        emptyMessage = searchEmptyMessage(searchQuery),
        key = { it.id },
        loadingIcon = SonaIcons.Album,
        sectionOf = albumSections,
        modifier = modifier,
        listState = listState,
        extraBottomPadding = extraBottomPadding,
        onFastScrollingChange = onFastScrollingChange,
    ) { album ->
        AlbumRow(
            album = album,
            selection = selection,
            isCurrent = { playback.marks(album) },
            isPlaying = { playback.isPlaying },
            onClick = { navigator.go(AlbumDetailScreen(album.id)) },
            onOpenOptions = { optionsTarget = OptionsTarget.ForAlbum(album) },
        )
    }

    optionsTarget?.let { target ->
        OptionsSheet(target = target, onDismissRequest = { optionsTarget = null })
    }
}
