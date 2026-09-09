package com.lhacenmed.sona.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen

data class FolderDetailScreen(val folderPath: String) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel = hiltViewModel<FolderDetailViewModel, FolderDetailViewModel.Factory>(
            creationCallback = { factory -> factory.create(folderPath) },
        )
        val tracks by viewModel.tracks.collectAsStateWithLifecycle()

        TrackListDetail(
            title = viewModel.folderName,
            subtitle = "${tracks.itemsOrEmpty.size} tracks",
            onBack = navigator::back,
            viewModel = viewModel,
            emptyMessage = "This folder has no tracks.",
        )
    }
}
