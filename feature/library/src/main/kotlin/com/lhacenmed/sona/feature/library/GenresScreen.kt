package com.lhacenmed.sona.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.SelectionState
import com.lhacenmed.sona.core.navigation.LocalNavigator

@Composable
fun GenresScreen(
    viewModel: LibraryViewModel,
    selection: SelectionState,
    modifier: Modifier = Modifier,
) {
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current

    LibraryList(
        content = genres,
        hasPermission = hasPermission,
        isScanning = isScanning,
        emptyTitle = "No genres found",
        emptyMessage = "Add some music to your device to see it here.",
        key = { it.id },
        modifier = modifier,
    ) { genre ->
        LibraryEntityRow(
            selection = selection,
            selectionKey = genre.id,
            title = genre.name,
            subtitle = "${genre.trackCount} tracks",
            onClick = { navigator.go(GenreDetailScreen(genre.id)) },
        )
    }
}
