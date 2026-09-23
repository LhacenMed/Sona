package com.lhacenmed.sona

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.PlayerOverlay
import com.lhacenmed.sona.feature.library.LibraryPagerScreen
import com.lhacenmed.sona.feature.settings.SettingsScreen

@Composable
fun AppShell(playerOverlay: PlayerOverlay, modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current

    // The library owns the top bar rather than this Scaffold, because the bar has to become a
    // context bar when a tab has a selection - and only the library knows which tab that is. These
    // are the actions the shell itself contributes.
    val libraryActions = remember(navigator) {
        listOf(
            TopBarAction(label = "Settings", icon = Icons.Filled.Settings) { navigator.go(SettingsScreen) },
        )
    }

    // The expandable player overlays the whole screen - collapsed, it's just a mini-bar pinned to the
    // bottom; expanded, it covers everything. Each list keeps its own end clear of it, rather than the
    // screen being cut short above it, so rows still scroll behind the mini player.
    playerOverlay.Content {
        // No top bar slot and no content insets: the library's own bar handles the status bar
        // inset, so letting the Scaffold add it too would pad the screen twice.
        Scaffold(modifier = modifier, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { innerPadding ->
            LibraryPagerScreen(
                modifier = Modifier.padding(innerPadding),
                actions = libraryActions,
            )
        }
    }
}
