package com.lhacenmed.sona.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.datastore.LibrarySettings
import com.lhacenmed.sona.core.datastore.LibraryTab
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val CANONICAL_TAB_ORDER = listOf(
    LibraryTab.TRACKS,
    LibraryTab.ARTISTS,
    LibraryTab.ALBUMS,
    LibraryTab.GENRES,
    LibraryTab.FOLDERS,
)

@HiltViewModel
class LibraryTabsViewModel @Inject constructor(
    librarySettings: LibrarySettings,
) : ViewModel() {

    // Defaults to "all visible" so the nav bar never flashes empty before the first DataStore
    // emission lands.
    val visibleTabs: StateFlow<List<LibraryTab>> = librarySettings.visibleTabs
        .map { visible -> CANONICAL_TAB_ORDER.filter { it in visible } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryTab.entries.toList())
}
