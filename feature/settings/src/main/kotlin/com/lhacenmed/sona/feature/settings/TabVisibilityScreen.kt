package com.lhacenmed.sona.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.datastore.LibraryTab
import com.lhacenmed.sona.core.navigation.Screen

/** "Manage Tabs" screen: toggle which library tabs show up in the main bottom navigation. */
object TabVisibilityScreen : Screen {
    override val titleRes: Int get() = R.string.manage_tabs_title

    @Composable
    override fun Content() {
        val viewModel: TabVisibilityViewModel = hiltViewModel()
        val tabs by viewModel.tabs.collectAsStateWithLifecycle()
        val visibleCount = tabs.count { it.second }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(tabs, key = { it.first.name }) { (tab, isVisible) ->
                ListItem(
                    headlineContent = { Text(tab.displayName()) },
                    trailingContent = {
                        Switch(
                            checked = isVisible,
                            onCheckedChange = { checked -> viewModel.setTabVisible(tab, checked) },
                            // Disabled rather than allowed-then-ignored, so the last visible tab's
                            // switch visibly can't be turned off.
                            enabled = !(isVisible && visibleCount <= 1),
                        )
                    },
                )
            }
        }
    }
}

private fun LibraryTab.displayName(): String = when (this) {
    LibraryTab.TRACKS -> "Tracks"
    LibraryTab.ARTISTS -> "Artists"
    LibraryTab.ALBUMS -> "Albums"
    LibraryTab.GENRES -> "Genres"
    LibraryTab.FOLDERS -> "Folders"
}
