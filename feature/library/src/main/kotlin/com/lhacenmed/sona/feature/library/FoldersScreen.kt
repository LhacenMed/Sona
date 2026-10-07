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
import com.lhacenmed.sona.core.model.Folder
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.feature.library.options.OptionsSheet
import com.lhacenmed.sona.feature.library.options.OptionsTarget

/** Every folder holding music or videos, each kind in a section of its own - see [folderHeading]. */
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

    LibraryList(
        content = folders,
        selection = selection,
        hasPermission = hasPermission,
        isScanning = isScanning,
        emptyTitle = "No folders found",
        emptyMessage = searchEmptyMessage(searchQuery),
        // One path can hold music and videos alike, and is then listed once in each section.
        key = { folder -> if (folder.isVideo) "video:${folder.path}" else folder.path },
        loadingIcon = SonaIcons.Folder,
        sectionOf = folderSections,
        isCurrent = { playback.marks(it) },
        isPlaying = { playback.isPlaying },
        modifier = modifier,
        listState = listState,
        onRefresh = rememberLibraryRefresh(viewModel),
        headingOf = ::folderHeading,
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

/** The section a folder is listed in: its music's, or its videos'. */
private fun folderHeading(folder: Folder): String = if (folder.isVideo) "Videos" else "Music"
