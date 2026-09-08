package com.lhacenmed.sona.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.datastore.LibrarySettings
import com.lhacenmed.sona.feature.scanner.MediaScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ExcludedFoldersViewModel @Inject constructor(
    private val librarySettings: LibrarySettings,
    private val mediaScanner: MediaScanner,
) : ViewModel() {

    val excludedFolders: StateFlow<List<String>> = librarySettings.excludedFolders
        .map { it.sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addFolder(path: String) {
        if (path.isBlank()) return
        viewModelScope.launch {
            librarySettings.addExcludedFolder(path)
            val updated = librarySettings.excludedFolders.first()
            mediaScanner.scan(excludedFolders = updated)
        }
    }

    fun removeFolder(path: String) {
        viewModelScope.launch {
            librarySettings.removeExcludedFolder(path)
            val updated = librarySettings.excludedFolders.first()
            mediaScanner.scan(excludedFolders = updated)
        }
    }
}
