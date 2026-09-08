package com.lhacenmed.sona

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.feature.library.LibraryPagerScreen
import com.lhacenmed.sona.feature.library.SearchScreen
import com.lhacenmed.sona.feature.player.PlayerScreen
import com.lhacenmed.sona.feature.settings.SettingsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SonaApp(modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Sona") },
                    actions = {
                        IconButton(onClick = { navigator.go(SearchScreen) }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = { navigator.go(SettingsScreen) }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    },
                )
            },
        ) { innerPadding ->
            LibraryPagerScreen(modifier = Modifier.padding(innerPadding))
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
