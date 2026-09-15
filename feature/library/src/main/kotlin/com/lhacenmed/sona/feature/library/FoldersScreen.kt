package com.lhacenmed.sona.feature.library

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.SelectionState
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.navigation.LocalNavigator

@Composable
fun FoldersScreen(
    viewModel: LibraryViewModel,
    selection: SelectionState,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current

    LibraryList(
        content = folders,
        hasPermission = hasPermission,
        isScanning = isScanning,
        emptyTitle = "No folders found",
        emptyMessage = "Add some music to your device to see it here.",
        key = { it.path },
        loadingIcon = SonaIcons.Folder,
        modifier = modifier,
        listState = listState,
    ) { folder ->
        LibraryEntityRow(
            selection = selection,
            selectionKey = folder.path,
            title = folder.name,
            subtitle = "${folder.trackCount} tracks",
            onClick = { navigator.go(FolderDetailScreen(folder.path)) },
        )
    }
}
