package com.lhacenmed.sona.feature.library.playlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import com.lhacenmed.sona.core.designsystem.component.rememberSelectionState
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.feature.library.options.toast
import com.lhacenmed.sona.feature.library.selection.SelectionKey

/**
 * The shape both of a playlist's pickers share: a bar naming what is being picked and how many are
 * picked so far, then [content]'s rows, each picked with a tap.
 *
 * Add appears once anything is picked and leaves only after the tracks are in [playlistId], in the
 * order they were picked, with a toast saying how it went. The count sits in the bar from the first
 * frame, so picking the first row moves nothing.
 */
@Composable
internal fun PlaylistTrackPicker(
    title: String,
    playlistId: Long,
    content: @Composable ColumnScope.(selection: SelectionState) -> Unit,
) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val viewModel: PlaylistPickerViewModel = hiltViewModel()
    val selection = rememberSelectionState()
    var isAdding by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        SonaTopAppBar(
            title = title,
            subtitle = "${selection.count} selected",
            onNavigateBack = navigator::back,
            actions = if (selection.isActive && !isAdding) {
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
            },
        )
        content(selection)
    }
}

/** What tapping a picker's row does: picks or unpicks it - nothing, for a row that cannot be picked. */
internal fun SelectionState.pickerClick(key: SelectionKey?): () -> Unit = { key?.let(::toggle) }
