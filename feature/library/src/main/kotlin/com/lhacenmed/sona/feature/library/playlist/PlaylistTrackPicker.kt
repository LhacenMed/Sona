package com.lhacenmed.sona.feature.library.playlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.designsystem.component.SelectionState
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.TopBarSearch
import com.lhacenmed.sona.core.designsystem.component.topBarBackground
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaConfirmationDialog
import com.lhacenmed.sona.core.designsystem.component.selectAllAction
import com.lhacenmed.sona.core.designsystem.component.toast
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.feature.library.R
import com.lhacenmed.sona.feature.library.options.OptionsAction
import com.lhacenmed.sona.feature.library.options.label
import com.lhacenmed.sona.feature.library.selection.SelectionKey
import com.lhacenmed.sona.feature.library.selection.SelectionOptionsHost
import com.lhacenmed.sona.feature.library.selection.toLibraryTopBarSelection
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons

/**
 * The shape both of a playlist's pickers share: a bar naming what is being picked, with [tabs] beneath it - the
 * two lifting as one once the list leaves its top, as the library's bar and tabs do - then [content]'s
 * rows - ordinary library rows, which play or open with a tap, offer their options sheet, and start a
 * selection with a long press, exactly as they do in the library.
 *
 * Once rows are selected the bar is the library's selection bar, with Add beside Select all. An open
 * search keeps its field on screen instead, so the query can still be changed with rows picked, and
 * Add and Select all stay reachable beside it. Select all works on [visibleKeys] - the rows on screen,
 * narrowed by any search.
 *
 * That same order is what back follows: it first closes the search, then drops whatever is picked, and
 * only then leaves - so nothing picked is lost to a search being closed.
 *
 * Add first works out what the picks would really add - each track once, none the playlist already
 * holds - and asks to confirm that many. The screen leaves only after the tracks are in [playlistId].
 */
@Composable
internal fun PlaylistTrackPicker(
    title: String,
    playlistId: Long,
    selection: SelectionState,
    searchQuery: String?,
    onSearchQueryChange: (String?) -> Unit,
    visibleKeys: () -> List<SelectionKey>,
    tabs: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val viewModel: PlaylistPickerViewModel = hiltViewModel()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    // The tracks Add worked out, waiting on the user to confirm adding them.
    var confirmingTrackIds by remember { mutableStateOf<List<Long>>(emptyList()) }

    val addAction = TopBarAction(label = OptionsAction.PLAYLIST_ADD.label, icon = SonaIcons.Check) {
        viewModel.resolveAddition(playlistId, selection.selectedKeys.filterIsInstance<SelectionKey>()) { trackIds ->
            if (trackIds.isEmpty()) context.toast(context.getString(R.string.track_picker_already_added)) else confirmingTrackIds = trackIds
        }
    }

    SelectionOptionsHost(selection) { openSelectionOptions, _ ->
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.topBarBackground()) {
                SonaTopAppBar(
                    title = title,
                drawsBackground = false,
                    onNavigateBack = navigator::back,
                    actions = listOf(TopBarAction(label = stringResource(R.string.library_search), icon = SonaIcons.Search) { onSearchQueryChange("") }),
                    search = searchQuery?.let { query ->
                        TopBarSearch(
                            query = query,
                            onQueryChange = onSearchQueryChange,
                            onClose = { onSearchQueryChange(null) },
                            actions = if (selection.isActive) {
                                listOf(addAction, selection.selectAllAction(visibleKeys()))
                            } else {
                                emptyList()
                            },
                        )
                    },
                    // Left out while searching, so the field outranks the selection bar - and so the bar's
                    // back handling closes the search before it drops the picks.
                    selection = if (searchQuery == null) {
                        selection.toLibraryTopBarSelection(
                            listKeys = visibleKeys,
                            actions = listOf(addAction),
                            onMoreOptions = openSelectionOptions,
                        )
                    } else {
                        null
                    },
                )
                tabs()
            }
            content()
        }
    }

    if (confirmingTrackIds.isNotEmpty()) {
        val trackIds = confirmingTrackIds
        val isSingle = trackIds.size == 1
        val playlistName = playlists.itemsOrEmpty.find { it.id == playlistId }?.name ?: stringResource(R.string.track_picker_the_playlist)
        SonaConfirmationDialog(
            title = if (isSingle) stringResource(R.string.track_picker_add_title) else pluralStringResource(R.plurals.track_picker_add_some_title, trackIds.size, trackIds.size),
            message = stringResource(if (isSingle) R.string.track_picker_add_message else R.string.track_picker_add_some_message, playlistName),
            confirmLabel = stringResource(R.string.track_picker_add),
            successMessage = if (isSingle) stringResource(R.string.track_picker_added) else pluralStringResource(R.plurals.track_picker_added_some, trackIds.size, trackIds.size),
            failureMessage = stringResource(if (isSingle) R.string.track_picker_add_failed else R.string.track_picker_add_some_failed),
            onDismiss = { confirmingTrackIds = emptyList() },
            operation = { onFinished ->
                viewModel.addToPlaylist(playlistId, trackIds) { succeeded ->
                    onFinished(succeeded)
                    // Closed rather than backed out of: back would only close the search or the selection.
                    if (succeeded) navigator.close()
                }
            },
        )
    }
}
