package com.lhacenmed.sona.feature.library

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.SelectionState
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.feature.library.options.OptionsSheet
import com.lhacenmed.sona.feature.library.options.OptionsTarget

@Composable
fun FoldersScreen(
    viewModel: LibraryViewModel,
    selection: SelectionState,
    listState: LazyListState,
    onFastScrollingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val folderSections by viewModel.folderSections.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    var optionsTarget by remember { mutableStateOf<OptionsTarget.ForFolder?>(null) }

    LibraryList(
        content = folders,
        selection = selection,
        hasPermission = hasPermission,
        isScanning = isScanning,
        emptyTitle = "No folders found",
        emptyMessage = searchEmptyMessage(searchQuery),
        key = { it.path },
        loadingIcon = SonaIcons.Folder,
        sectionOf = folderSections,
        modifier = modifier,
        listState = listState,
        onFastScrollingChange = onFastScrollingChange,
    ) { folder ->
        FolderRow(
            folder = folder,
            selection = selection,
            isCurrent = { playback.marks(folder) },
            isPlaying = { playback.isPlaying },
            onClick = { navigator.go(FolderDetailScreen(folder.path)) },
            onOpenOptions = { optionsTarget = OptionsTarget.ForFolder(folder) },
        )
    }

    optionsTarget?.let { target ->
        OptionsSheet(target = target, onDismissRequest = { optionsTarget = null })
    }
}
