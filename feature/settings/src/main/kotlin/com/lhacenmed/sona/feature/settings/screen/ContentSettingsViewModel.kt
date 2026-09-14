package com.lhacenmed.sona.feature.settings.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.datastore.CoverMode
import com.lhacenmed.sona.core.datastore.ImageSettings
import com.lhacenmed.sona.core.datastore.LibrarySettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The stored library and image settings the [ContentScreen] rows are connected to. */
@HiltViewModel
class ContentSettingsViewModel @Inject constructor(
    private val librarySettings: LibrarySettings,
    private val imageSettings: ImageSettings,
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
}
