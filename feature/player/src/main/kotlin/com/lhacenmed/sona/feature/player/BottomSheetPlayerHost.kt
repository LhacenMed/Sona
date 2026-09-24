package com.lhacenmed.sona.feature.player

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.model.Track
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The player an activity lays over its content: a mini player along the bottom that expands into the
 * full player and its queue, hosted the way ArchiveTune's `MainActivity` hosts its `BottomSheetPlayer`.
 *
 * Every activity has its own sheet, all following the one playback state. A screen opened while a track
 * is loaded starts with the mini player already in place; the sheet rises when a track arrives and goes
 * when the queue is emptied - and holds still while neither is known yet.
 *
 * [content] is the screen it lays itself over, told through [LocalBottomContentPadding] how much of its
 * bottom the mini player covers, and the gap to keep above it.
 */
@Composable
fun BottomSheetPlayerHost(
    onGoToAlbum: (Long) -> Unit,
    onGoToArtist: (Long) -> Unit,
    onOpenEqualizer: () -> Unit,
    trackOptionsSheet: @Composable (track: Track, onDismissRequest: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerStyle by viewModel.playerStyle.collectAsStateWithLifecycle()
    val sliderStyle by viewModel.sliderStyle.collectAsStateWithLifecycle()
    val sleepTimer by viewModel.sleepTimer.collectAsStateWithLifecycle()
    // Null until the playback and the library have both loaded: a process started again by the system
    // knows of no track for its first moments, and taking that as none would dismiss the sheet the
    // restored activity put back.
    val hasTrack = if (uiState.isResolved) uiState.currentTrack != null else null

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val bottomInset = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
        val miniPlayerClearance = bottomInset + MiniPlayerBottomSpacing + MiniPlayerHeight
        val state =
            rememberBottomSheetState(
                dismissedBound = 0.dp,
                collapsedBound = miniPlayerClearance,
                expandedBound = maxHeight,
                initialAnchor = if (hasTrack == true) COLLAPSED_ANCHOR else DISMISSED_ANCHOR,
            )

        // Read from where the sheet is going rather than where it is, so a track arriving while the sheet
        // is still sliding away brings it back instead of being missed.
        LaunchedEffect(hasTrack) {
            when (hasTrack) {
                true -> if (state.isDismissedOrDismissing) state.collapseSoft()
                false -> if (!state.isDismissedOrDismissing) state.dismiss()
                null -> Unit
            }
        }

        // Held clear whether or not a track is loaded, so a list's end stays where it is as the mini
        // player comes and goes rather than jumping under it.
        CompositionLocalProvider(LocalBottomContentPadding provides miniPlayerClearance + MiniPlayerContentSpacing) {
            content()
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
            onOpenEqualizer = onOpenEqualizer,
            trackOptionsSheet = trackOptionsSheet,
        )
    }
}
