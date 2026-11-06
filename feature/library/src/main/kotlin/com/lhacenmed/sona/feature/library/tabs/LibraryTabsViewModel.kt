package com.lhacenmed.sona.feature.library.tabs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.datastore.LibrarySettings
import com.lhacenmed.sona.core.datastore.LibraryTab
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One of [LibraryTabsScreen]'s rows: a tab, and whether the library shows it. */
data class LibraryTabRow(val tab: LibraryTab, val isVisible: Boolean)

@HiltViewModel
class LibraryTabsViewModel @Inject constructor(
    private val librarySettings: LibrarySettings,
) : ViewModel() {

    /** Every tab, in the strip's order. */
    val tabs: StateFlow<List<LibraryTabRow>> = combine(librarySettings.tabOrder.flow, librarySettings.visibleTabs.flow, ::rowsOf)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), rowsOf(librarySettings.tabOrder.value, librarySettings.visibleTabs.value))

    fun setTabVisible(tab: LibraryTab, visible: Boolean) {
        // Never allow the last visible tab to be hidden.
        val currentlyVisibleCount = tabs.value.count { it.isVisible }
        if (!visible && currentlyVisibleCount <= 1) return

        viewModelScope.launch {
            librarySettings.setTabVisible(tab, visible)
        }
    }

    /** Keeps [rows] - every tab, as a drag left them - as the strip's order. */
    fun arrange(rows: List<LibraryTabRow>) {
        viewModelScope.launch { librarySettings.setTabOrder(rows.map { it.tab }) }
    }
}

private fun rowsOf(order: List<LibraryTab>, visible: List<LibraryTab>): List<LibraryTabRow> =
    order.map { tab -> LibraryTabRow(tab, tab in visible) }
