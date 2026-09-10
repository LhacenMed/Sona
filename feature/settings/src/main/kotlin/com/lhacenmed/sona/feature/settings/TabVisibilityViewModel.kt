package com.lhacenmed.sona.feature.settings

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
import kotlinx.coroutines.launch

@HiltViewModel
class TabVisibilityViewModel @Inject constructor(
    private val librarySettings: LibrarySettings,
) : ViewModel() {

    val tabs: StateFlow<List<Pair<LibraryTab, Boolean>>> = librarySettings.visibleTabs
        .map { visible -> LibraryTab.entries.map { tab -> tab to (tab in visible) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryTab.entries.map { it to true })

    fun setTabVisible(tab: LibraryTab, visible: Boolean) {
        // Never allow the last visible tab to be hidden.
        val currentlyVisibleCount = tabs.value.count { it.second }
        if (!visible && currentlyVisibleCount <= 1) return

        viewModelScope.launch {
            librarySettings.setTabVisible(tab, visible)
        }
    }
}
