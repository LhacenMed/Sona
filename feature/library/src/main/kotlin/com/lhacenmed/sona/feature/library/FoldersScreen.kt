package com.lhacenmed.sona.feature.library

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.SelectionState
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.model.Folder
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.feature.library.R
import com.lhacenmed.sona.feature.library.options.OptionsSheet
import com.lhacenmed.sona.feature.library.options.OptionsTarget

/** Every folder holding music or videos, each kind in a section of its own: its music's, or its videos'. */
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
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val folderSections by viewModel.folderSections.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    var optionsTarget by remember { mutableStateOf<OptionsTarget.ForFolder?>(null) }

    val musicHeading = stringResource(R.string.library_music_title)
    val videosHeading = stringResource(R.string.library_videos_title)
    LibraryList(
        content = folders,
        selection = selection,
        hasPermission = hasPermission,
        isScanning = isScanning,
        emptyTitle = stringResource(R.string.library_no_folders_found),
        emptyMessage = searchEmptyMessage(searchQuery),
        key = { it.rowKey },
        loadingIcon = SonaIcons.Folder,
        sectionOf = folderSections,
        isCurrent = { playback.marks(it) },
        isPlaying = { playback.isPlaying },
        modifier = modifier,
        listState = listState,
        onRefresh = rememberLibraryRefresh(viewModel),
        headingOf = { folder -> if (folder.isVideo) videosHeading else musicHeading },
    ) { folder ->
        FolderRow(
            folder = folder,
            selection = selection,
            isCurrent = { playback.marks(folder) },
            isPlaying = { playback.isPlaying },
            onClick = { navigator.go(FolderDetailScreen(folder.path, folder.isVideo)) },
            onOpenOptions = { optionsTarget = OptionsTarget.ForFolder(folder) },
        )
    }

    optionsTarget?.let { target ->
        OptionsSheet(target = target, onDismissRequest = { optionsTarget = null })
    }
}

