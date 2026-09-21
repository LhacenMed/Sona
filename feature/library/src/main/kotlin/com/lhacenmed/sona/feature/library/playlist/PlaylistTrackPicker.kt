package com.lhacenmed.sona.feature.library.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.lhacenmed.sona.core.designsystem.component.SelectionState
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.TopBarSearch
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.feature.library.options.toast
import com.lhacenmed.sona.feature.library.selection.SelectionKey

/**
 * The shape both of a playlist's pickers share: a bar naming what is being picked and how many are
 * picked so far, then [content]'s rows, each picked with a tap.
 *
 * The bar keeps its icons for Add alone, which appears once anything is picked; Search and Select all
 * live in its menu. Searching turns the bar into the search field, [searchQuery] being null while it is
 * closed, and Add and Select all stay reachable beside it. Select all works on [visibleKeys] - the rows
 * on screen, narrowed by any search - and once every one of them is picked it deselects them instead.
 *
 * Back, by gesture or by the bar's arrow, first drops whatever is picked, then closes the search, and
 * only then leaves - so nothing picked is ever lost to one press.
 *
 * Add leaves only after the tracks are in [playlistId], in the order they were picked, with a toast
 * saying how it went. The count sits in the bar from the first frame, so picking the first row moves
 * nothing.
 */
@Composable
internal fun PlaylistTrackPicker(
    title: String,
    playlistId: Long,
    selection: SelectionState,
    searchQuery: String?,
    onSearchQueryChange: (String?) -> Unit,
    visibleKeys: () -> List<SelectionKey>,
    content: @Composable ColumnScope.() -> Unit,
) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val viewModel: PlaylistPickerViewModel = hiltViewModel()
    var isAdding by remember { mutableStateOf(false) }

    val addActions = if (selection.isActive && !isAdding) {
        listOf(
            TopBarAction(label = "Add", icon = Icons.Filled.Check) {
                isAdding = true
                viewModel.addToPlaylist(playlistId, selection.selectedKeys.filterIsInstance<SelectionKey>()) { succeeded ->
                    isAdding = false
                    context.toast(if (succeeded) "Added to playlist" else "Could not add to playlist")
                    if (succeeded) navigator.back()
                }
            },
        )
    } else {
        emptyList()
    }
    val keys = visibleKeys()
    val selectAllAction = if (keys.isNotEmpty() && selection.selectedKeys.containsAll(keys)) {
        TopBarAction(label = "Deselect all", icon = Icons.Filled.Deselect) { selection.deselectAll(keys) }
    } else {
        TopBarAction(label = "Select all", icon = Icons.Filled.SelectAll) { selection.selectAll(keys) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SonaTopAppBar(
            title = title,
            subtitle = "${selection.count} selected",
            onNavigateBack = { if (selection.isActive) selection.clear() else navigator.back() },
            actions = addActions,
            menuActions = listOf(
                TopBarAction(label = "Search", icon = Icons.Filled.Search) { onSearchQueryChange("") },
                selectAllAction,
            ),
            search = searchQuery?.let { query ->
                TopBarSearch(
                    query = query,
                    onQueryChange = { onSearchQueryChange(it) },
                    onClose = { if (selection.isActive) selection.clear() else onSearchQueryChange(null) },
                    actions = addActions,
                    menuActions = listOf(selectAllAction),
                )
            },
        )
        content()
    }

    // Composed after the bar, so it outranks the search's own back handling: a press drops the picks first.
    BackHandler(enabled = selection.isActive) { selection.clear() }
}

/** What tapping a picker's row does: picks or unpicks it - nothing, for a row that cannot be picked. */
internal fun SelectionState.pickerClick(key: SelectionKey?): () -> Unit = { key?.let(::toggle) }
