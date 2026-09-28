package com.lhacenmed.sona.feature.settings.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.datastore.LibrarySettings
import com.lhacenmed.sona.core.model.Folder
import com.lhacenmed.sona.feature.scanner.MediaScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ExcludedFoldersViewModel @Inject constructor(
    private val librarySettings: LibrarySettings,
    private val mediaScanner: MediaScanner,
    repository: LibraryRepository,
) : ViewModel() {

    val excludedFolders: StateFlow<List<String>> = librarySettings.excludedFolders.flow
        .map { it.sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), librarySettings.excludedFolders.value.sorted())

    /**
     * What can be excluded: every folder the library found music or videos in, and not excluded already - so
     * the choice is among the folders that matter, never the whole of the device's storage.
     */
    val excludableFolders: StateFlow<List<String>> = combine(
        repository.folders,
        repository.videoFolders,
        librarySettings.excludedFolders.flow,
        ::excludableFoldersOf,
    ).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        excludableFoldersOf(repository.folders.value, repository.videoFolders.value, librarySettings.excludedFolders.value),
    )

    fun addFolder(path: String) {
        viewModelScope.launch {
            librarySettings.addExcludedFolder(path)
            requestRescan()
        }
    }

    fun removeFolder(path: String) {
        viewModelScope.launch {
            librarySettings.removeExcludedFolder(path)
            requestRescan()
        }
    }

    /**
     * Runs on the scanner's own application scope rather than this ViewModel's, so navigating away
     * from the settings screen mid-scan doesn't cancel it and leave the library half-reconciled.
     * The excluded-folder set is part of the scan signature, so this always does real work.
     */
    private fun requestRescan() {
        mediaScanner.requestScan()
    }
}

/** The paths of [musicFolders] and [videoFolders], each once, less those [excluded] - in path order. */
private fun excludableFoldersOf(
    musicFolders: LibraryContent<Folder>,
    videoFolders: LibraryContent<Folder>,
    excluded: Set<String>,
): List<String> =
    (musicFolders.itemsOrEmpty + videoFolders.itemsOrEmpty).map { it.path }.distinct().filterNot { it in excluded }.sorted()
