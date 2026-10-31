package com.lhacenmed.sona.feature.settings.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.datastore.LibrarySettings
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.scanner.MediaScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
     * What can be excluded: the library's music, and its videos - whatever is excluded already is out of it - so the
     * choice is among the folders and files that matter, never the whole of the device's storage.
     */
    val music: StateFlow<List<Track>> = repository.tracks.itemsOf()
    val videos: StateFlow<List<Track>> = repository.videos.itemsOf()

    fun exclude(paths: Set<String>) {
        viewModelScope.launch {
            librarySettings.addExcludedFolders(paths)
            requestRescan()
        }
    }

    fun include(paths: Set<String>) {
        viewModelScope.launch {
            librarySettings.removeExcludedFolders(paths)
            requestRescan()
        }
    }

    private fun StateFlow<LibraryContent<Track>>.itemsOf(): StateFlow<List<Track>> =
        map { it.itemsOrEmpty }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), value.itemsOrEmpty)

    /**
     * Runs on the scanner's own application scope rather than this ViewModel's, so navigating away
     * from the settings screen mid-scan doesn't cancel it and leave the library half-reconciled.
     * The excluded-folder set is part of the scan signature, so this always does real work.
     */
    private fun requestRescan() {
        mediaScanner.requestScan()
    }
}
