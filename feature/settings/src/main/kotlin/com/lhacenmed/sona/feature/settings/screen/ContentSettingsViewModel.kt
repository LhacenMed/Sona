package com.lhacenmed.sona.feature.settings.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.datastore.LibrarySettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The stored library settings the [ContentScreen] rows are connected to. */
@HiltViewModel
class ContentSettingsViewModel @Inject constructor(
    private val librarySettings: LibrarySettings,
) : ViewModel() {

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
