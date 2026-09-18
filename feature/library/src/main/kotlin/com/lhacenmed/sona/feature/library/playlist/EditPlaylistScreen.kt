package com.lhacenmed.sona.feature.library.playlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen

/**
 * Editing the playlist [playlistId] - a placeholder for now: its bar and nothing else, so the menu item
 * that opens it already leads somewhere the editor can grow into.
 */
data class EditPlaylistScreen(val playlistId: Long) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        Column(modifier = Modifier.fillMaxSize()) {
            SonaTopAppBar(title = "Edit playlist", onNavigateBack = navigator::back)
        }
    }
}
