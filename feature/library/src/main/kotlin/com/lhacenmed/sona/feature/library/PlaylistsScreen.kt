package com.lhacenmed.sona.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.rememberSelectionState
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen

/**
 * Every playlist, Favourites first.
 *
 * Reached from the Playlists shortcut rather than a tab: the tabs browse the library by one of its
 * own dimensions, and a playlist is not one of those - it is something the user made.
 */
object PlaylistsScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel: PlaylistsViewModel = hiltViewModel()
        val playlists by viewModel.playlists.collectAsStateWithLifecycle()
        val selection = rememberSelectionState()

        Column(modifier = Modifier.fillMaxSize()) {
            SonaTopAppBar(title = "Playlists", onNavigateBack = navigator::back)
            LibraryList(
                content = playlists,
                // A playlist exists whether or not the library has been scanned, so neither the
                // permission nor the scanning explanation can apply to this list being empty.
                hasPermission = true,
                isScanning = false,
                emptyTitle = "No playlists yet",
                emptyMessage = "Create one to start collecting tracks.",
                key = { it.id },
                modifier = Modifier.fillMaxSize(),
            ) { playlist ->
                LibraryEntityRow(
                    selection = selection,
                    selectionKey = playlist.id,
                    title = playlist.name,
                    subtitle = "${playlist.trackCount} tracks",
                    onClick = { navigator.go(PlaylistDetailScreen(playlist.id)) },
                )
            }
        }
    }
}
