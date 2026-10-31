package com.lhacenmed.sona.feature.settings.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.common.coroutines.launchOperation
import com.lhacenmed.sona.core.data.sort.LibrarySortOrders
import com.lhacenmed.sona.core.datastore.CoverMode
import com.lhacenmed.sona.core.datastore.ImageSettings
import com.lhacenmed.sona.core.datastore.LibrarySettings
import com.lhacenmed.sona.core.datastore.SortSettings
import com.lhacenmed.sona.core.model.FolderView
import com.lhacenmed.sona.core.model.sort.SortDirection
import com.lhacenmed.sona.core.model.sort.SortScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The stored library, sorting and image settings the [ContentScreen] rows are connected to. */
@HiltViewModel
class ContentSettingsViewModel @Inject constructor(
    private val librarySettings: LibrarySettings,
    private val imageSettings: ImageSettings,
    private val sortSettings: SortSettings,
    private val sortOrders: LibrarySortOrders,
) : ViewModel() {

    val coverMode: StateFlow<CoverMode> = imageSettings.coverMode.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), imageSettings.coverMode.value)

    fun setCoverMode(mode: CoverMode) {
        viewModelScope.launch { imageSettings.setCoverMode(mode) }
    }

    val forceSquareCovers: StateFlow<Boolean> = imageSettings.forceSquareCovers.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), imageSettings.forceSquareCovers.value)

    fun setForceSquareCovers(enabled: Boolean) {
        viewModelScope.launch { imageSettings.setForceSquareCovers(enabled) }
    }

    /**
     * Unlike Auxio, which re-reads the whole library when this changes, nothing needs rescanning here:
     * names are reduced to their sort keys at sort time, so every list simply re-sorts itself.
     */
    val intelligentSortingEnabled: StateFlow<Boolean> = librarySettings.intelligentSortingEnabled.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), librarySettings.intelligentSortingEnabled.value)

    fun setIntelligentSortingEnabled(enabled: Boolean) {
        viewModelScope.launch { librarySettings.setIntelligentSortingEnabled(enabled) }
    }

    /** Whether a credit naming several artists is each of them - the library rescans itself as it changes, see `MediaScanner`. */
    val separateArtists: StateFlow<Boolean> = librarySettings.separateArtists.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), librarySettings.separateArtists.value)

    fun setSeparateArtists(enabled: Boolean) {
        viewModelScope.launch { librarySettings.setSeparateArtists(enabled) }
    }

    /** How the Folders tab lists folders inside one another - every list follows it at once, with no rescan. */
    val folderView: StateFlow<FolderView> = librarySettings.folderView.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), librarySettings.folderView.value)

    fun setFolderView(view: FolderView) {
        viewModelScope.launch { librarySettings.setFolderView(view) }
    }

    /** Whether a moved file keeps its track - see `LibraryWriter`, which follows it on the next scan. */
    val keepsMovedFiles: StateFlow<Boolean> = librarySettings.keepsMovedFiles.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), librarySettings.keepsMovedFiles.value)

    fun setKeepsMovedFiles(enabled: Boolean) {
        viewModelScope.launch { librarySettings.setKeepsMovedFiles(enabled) }
    }

    val newTracksDirection: StateFlow<SortDirection> = sortSettings.newTracksDirection.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), sortSettings.newTracksDirection.value)

    fun setNewTracksDirection(direction: SortDirection) {
        viewModelScope.launch { sortSettings.setNewTracksDirection(direction) }
    }

    val defaultSortScope: StateFlow<SortScope> = sortSettings.defaultScope.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), sortSettings.defaultScope.value)

    fun setDefaultSortScope(scope: SortScope) {
        viewModelScope.launch { sortSettings.setDefaultScope(scope) }
    }

    fun resetSorting(onFinished: (succeeded: Boolean) -> Unit) {
        viewModelScope.launchOperation(onFinished) { sortOrders.reset() }
    }
}
