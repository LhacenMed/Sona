package com.lhacenmed.sona.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.designsystem.component.CoverArtDefaults
import com.lhacenmed.sona.core.designsystem.component.SonaFolderCover
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.library.options.FolderOptionsContext
import com.lhacenmed.sona.feature.library.options.OptionsTarget

/** The folder at [folderPath]: its music - or, where [isVideo], its videos, as the Videos tab opens it. */
data class FolderDetailScreen(val folderPath: String, val isVideo: Boolean) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel = hiltViewModel<FolderDetailViewModel, FolderDetailViewModel.Factory>(
            creationCallback = { factory -> factory.create(folderPath, isVideo) },
        )
        val folder by viewModel.folder.collectAsStateWithLifecycle()
        val tracks by viewModel.tracks.collectAsStateWithLifecycle()

        TrackListDetail(
            title = viewModel.folderName,
            header = DetailHeaderContent(
                type = "Folder",
                subhead = folderPath,
                info = if (isVideo) videoCountAndDuration(tracks.itemsOrEmpty) else trackCountAndDuration(tracks.itemsOrEmpty),
                cover = {
                    SonaFolderCover(
                        coverArtUris = folder?.coverArtUris.orEmpty(),
                        seed = folderPath.hashCode(),
                        size = CoverArtDefaults.DetailHeaderSize,
                        cornerRadius = CoverArtDefaults.DetailHeaderCornerRadius,
                    )
                },
            ),
            onBack = navigator::back,
            viewModel = viewModel,
            emptyMessage = if (isVideo) "This folder has no videos." else "This folder has no tracks.",
            collection = folder?.let { OptionsTarget.ForFolder(it, FolderOptionsContext.FROM_DETAIL) },
        )
    }
}
