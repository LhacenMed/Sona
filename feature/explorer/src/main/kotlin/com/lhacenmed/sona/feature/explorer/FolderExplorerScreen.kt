package com.lhacenmed.sona.feature.explorer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.common.di.IoDispatcher
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.data.itemsOrNull
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.scanner.mediastore.MediaStoreQuerier
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/**
 * The library's folders as they lie on the device, opened at [folderPath] - or, for null, at the top: one screen,
 * every folder opened in it - see [FolderExplorerList] - and Back going to the folder before until the first.
 *
 * [showsExcluded], it is the excluded folder [folderPath] instead - the files the library leaves out, read from
 * the device - to see what was left out, and listen to it.
 */
data class FolderExplorerScreen(val folderPath: String? = null, val showsExcluded: Boolean = false) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel = hiltViewModel<FolderExplorerViewModel, FolderExplorerViewModel.Factory>(
            creationCallback = { factory -> factory.create(folderPath, showsExcluded) },
        )
        val previewViewModel: ExplorerPreviewViewModel = hiltViewModel()
        val files by viewModel.files.collectAsStateWithLifecycle()
        val preview by previewViewModel.preview.collectAsStateWithLifecycle()
        val navigation = rememberExplorerNavigation(start = folderPath)
        val goBack = { if (navigation.canGoBack) navigation.back() else navigator.back() }

        BackHandler(enabled = navigation.canGoBack, onBack = navigation::back)
        // Leaving the explorer, what it played stops with it.
        DisposableEffect(previewViewModel) { onDispose(previewViewModel::stop) }

        Column(modifier = Modifier.fillMaxSize()) {
            val explored = files?.let { remember(it) { ExploredFiles(it) } }
            val path = explored?.pathTo(navigation.open).orEmpty()
            SonaTopAppBar(
                title = path.lastOrNull()?.let { folderDisplayName(it) } ?: stringResource(R.string.explorer_all_folders),
                onNavigateBack = goBack,
            )
            FolderPathBar(folders = path, onOpen = navigation::open)
            explored ?: return@Column
            FolderExplorerList(
                explored = explored,
                navigation = navigation,
                preview = preview,
                onPlay = previewViewModel::play,
                picking = null,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = LocalBottomContentPadding.current),
            )
        }
    }
}

@HiltViewModel(assistedFactory = FolderExplorerViewModel.Factory::class)
class FolderExplorerViewModel @AssistedInject constructor(
    @Assisted folderPath: String?,
    @Assisted showsExcluded: Boolean,
    repository: LibraryRepository,
    mediaStoreQuerier: MediaStoreQuerier,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(folderPath: String?, showsExcluded: Boolean): FolderExplorerViewModel
    }

    /** The files explored - null until read. */
    val files: StateFlow<List<Track>?> =
        if (showsExcluded) {
            // Out of the library, they are only on the device: read where they are.
            flow { emit(mediaStoreQuerier.query().let { it.tracks + it.videos }.filter { it.folderPath == folderPath }) }.flowOn(ioDispatcher)
        } else {
            combine(repository.tracks, repository.videos) { music, videos -> music.itemsOrNull?.let { m -> videos.itemsOrNull?.let { m + it } } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
