package com.lhacenmed.sona.feature.player

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.model.Track
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The player an activity lays over its content: a mini player along the bottom that expands into the
 * full player and its queue, hosted the way ArchiveTune's `MainActivity` hosts its `BottomSheetPlayer`.
 *
 * Every activity has its own sheet, all following the one playback state. A screen opened while a track
 * is loaded starts with the mini player already in place; the sheet rises when a track arrives and goes
 * when the queue is emptied.
 */
@Composable
fun BottomSheetPlayerHost(
    onGoToAlbum: (Long) -> Unit,
    onGoToArtist: (Long) -> Unit,
    trackOptionsSheet: @Composable (track: Track, onDismissRequest: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerStyle by viewModel.playerStyle.collectAsStateWithLifecycle()
    val sliderStyle by viewModel.sliderStyle.collectAsStateWithLifecycle()
    val sleepTimer by viewModel.sleepTimer.collectAsStateWithLifecycle()
    val hasTrack = uiState.currentTrack != null

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val bottomInset = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
        val state =
            rememberBottomSheetState(
                dismissedBound = 0.dp,
                collapsedBound = bottomInset + MiniPlayerBottomSpacing + MiniPlayerHeight,
                expandedBound = maxHeight,
                initialAnchor = if (hasTrack) COLLAPSED_ANCHOR else DISMISSED_ANCHOR,
            )

        LaunchedEffect(hasTrack) {
            if (hasTrack) {
                if (state.isDismissed) state.collapseSoft()
            } else if (!state.isDismissed) {
                state.dismiss()
            }
        }

        BottomSheetPlayer(
            state = state,
            uiState = uiState,
            playerStyle = playerStyle,
            sliderStyle = sliderStyle,
            sleepTimer = sleepTimer,
            viewModel = viewModel,
            onGoToAlbum = onGoToAlbum,
            onGoToArtist = onGoToArtist,
            trackOptionsSheet = trackOptionsSheet,
        )
    }
}
