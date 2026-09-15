package com.lhacenmed.sona

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.feature.equalizer.EqualizerScreen
import com.lhacenmed.sona.feature.library.LibraryPagerScreen
import com.lhacenmed.sona.feature.library.SearchScreen
import com.lhacenmed.sona.feature.player.PlayerScreen
import com.lhacenmed.sona.feature.settings.SettingsScreen

@Composable
fun AppShell(modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current

    // The library owns the top bar rather than this Scaffold, because the bar has to become a
    // context bar when a tab has a selection - and only the library knows which tab that is. These
    // are the actions the shell itself contributes.
    val libraryActions = remember(navigator) {
        listOf(
            TopBarAction(label = "Search", icon = Icons.Filled.Search) { navigator.go(SearchScreen) },
            TopBarAction(label = "Equalizer", icon = Icons.Filled.Equalizer) { navigator.go(EqualizerScreen) },
            TopBarAction(label = "Settings", icon = Icons.Filled.Settings) { navigator.go(SettingsScreen) },
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        // No top bar slot and no content insets: the library's own bar handles the status bar
        // inset, so letting the Scaffold add it too would pad the screen twice.
        Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) { innerPadding ->
            LibraryPagerScreen(
                modifier = Modifier.padding(innerPadding),
                actions = libraryActions,
            )
        }

        // The expandable player overlays the whole screen - collapsed, it's just a mini-bar
        // pinned to the bottom; expanded, it covers everything. Nothing else occupies the bottom
        // of the screen anymore (tabs moved to the top, under the app bar), so it needs no
        // reserved space.
        PlayerScreen(
            modifier = Modifier.fillMaxSize(),
            onGoToAlbum = { /* album detail navigation lands in a later phase's settings/nav work */ },
            onGoToArtist = { /* artist detail navigation lands in a later phase's settings/nav work */ },
        )
    }
}
