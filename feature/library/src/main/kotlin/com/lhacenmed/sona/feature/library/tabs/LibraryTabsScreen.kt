package com.lhacenmed.sona.feature.library.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.LocalDragHandle
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.screen.screenList
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.library.rememberReorderableRows
import com.lhacenmed.sona.feature.library.reorderableRows

/**
 * The library's tabs - which are shown, and in what order, dragged by their handles. The first shown is the one the
 * library opens on; the strip itself is reordered the same way, a tab held and dragged along it.
 */
data object LibraryTabsScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel: LibraryTabsViewModel = hiltViewModel()
        val tabs by viewModel.tabs.collectAsStateWithLifecycle()
        val visibleCount = tabs.count { it.isVisible }
        val openedAtLaunch = tabs.firstOrNull { it.isVisible }?.tab
        val listState = rememberLazyListState()
        val rows = rememberReorderableRows(tabs, key = { it.tab.name }, listState, onReorder = viewModel::arrange)

        Column(modifier = Modifier.fillMaxSize()) {
            SonaTopAppBar(title = "Library tabs", onNavigateBack = navigator::back)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().screenList(listState),
                contentPadding = PaddingValues(bottom = LocalBottomContentPadding.current),
            ) {
                reorderableRows(rows, key = { it.tab.name }) { (tab, isVisible) ->
                    ListItem(
                        headlineContent = { Text(tab.label) },
                        supportingContent = if (tab == openedAtLaunch) {
                            { Text("Opened at launch") }
                        } else {
                            null
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Filled.DragHandle,
                                contentDescription = "Reorder",
                                modifier = LocalDragHandle.current ?: Modifier,
                            )
                        },
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
}
