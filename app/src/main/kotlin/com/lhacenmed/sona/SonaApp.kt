package com.lhacenmed.sona

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.feature.library.TracksScreen

@Composable
fun SonaApp(modifier: Modifier = Modifier) {
    val miniPlayerViewModel: MiniPlayerViewModel = hiltViewModel()
    val miniPlayerState by miniPlayerViewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        bottomBar = {
            // Only compose bar content when something is loaded; an empty composable in the
            // bottomBar slot collapses to zero height, so the body never reflows once playback
            // starts (Scaffold measures the slot's actual content, not a fixed reservation).
            if (miniPlayerState.currentTrack != null) {
                MiniPlayerBar(
                    title = miniPlayerState.currentTrack?.title.orEmpty(),
                    artist = miniPlayerState.currentTrack?.artist.orEmpty(),
                    isPlaying = miniPlayerState.playback.isPlaying,
                    onTogglePlayPause = miniPlayerViewModel::onTogglePlayPause,
                )
            }
        },
    ) { innerPadding ->
        TracksScreen(modifier = Modifier.padding(innerPadding))
    }
}

@Composable
private fun MiniPlayerBar(
    title: String,
    artist: String,
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniPlayerTrackInfo(title = title, artist = artist)
        IconButton(onClick = onTogglePlayPause) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
            )
        }
    }
}

@Composable
private fun RowScope.MiniPlayerTrackInfo(title: String, artist: String) {
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(end = 8.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
        Text(
            text = artist,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
