package com.lhacenmed.sona.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.LocalMiniPlayerSeekBarRequests
import com.lhacenmed.sona.core.designsystem.component.LocalPlayerSheetHeight
import com.lhacenmed.sona.core.designsystem.component.MiniPlayerSeekBarRequests
import com.lhacenmed.sona.core.designsystem.component.PlayerSheetHeight
import com.lhacenmed.sona.core.designsystem.player.LocalPlayerCheckedColors
import com.lhacenmed.sona.core.model.PlaybackParent
import com.lhacenmed.sona.feature.player.background.playerColors
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.PlaybackSpace

/** How far above the mini player the screen starts fading under it, and how far it fades by the screen's foot. */
private val MiniPlayerScrimRise = 32.dp
private const val MiniPlayerScrimAlpha = 0.85f

/**
 * The player an activity lays over its content: a mini player along the bottom that expands into the
 * full player and its queue, hosted the way ArchiveTune's `MainActivity` hosts its `BottomSheetPlayer`.
 *
 * Every activity has its own sheet, all following the one playback state. A screen opened while a track
 * is loaded starts with the mini player already in place; the sheet rises when a track arrives and goes
 * when the queue is emptied - and holds still while neither is known yet.
 *
 * [content] is the screen it lays itself over, told through [LocalBottomContentPadding] how much of its
 * bottom the mini player covers as it comes and goes, and the gap to keep above it, and through [LocalPlayerSheetHeight]
 * how high the player reaches up over it at every moment.
 *
 * It plays [space]'s tracks alone - see [PlayerViewModel]. What only the library's tracks have - an album
 * and an artist to go to, the list the queue plays from, lyrics to edit - is offered where given.
 *
 * [expandsOnOpen] opens on the full player, rather than the mini player, from the first frame where the track is
 * known by then, or rising into it once there is one - once only, so a screen recreated after it opened is left as
 * the user then left the player.
 */
@Composable
fun BottomSheetPlayerHost(
    space: PlaybackSpace,
    onGoToAlbum: ((Long) -> Unit)?,
    onGoToArtist: ((Long) -> Unit)?,
    onGoToPlayingFrom: ((PlaybackParent?) -> Unit)?,
    onOpenEqualizer: () -> Unit,
    onEditLyrics: ((trackId: Long) -> Unit)?,
    trackOptionsSheet: @Composable (track: Track, onDismissRequest: () -> Unit) -> Unit,
    expandsOnOpen: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val viewModel = hiltViewModel<PlayerViewModel, PlayerViewModel.Factory>(creationCallback = { it.create(space) })
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
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
                initialAnchor = when {
                    hasTrack != true -> DISMISSED_ANCHOR
                    expandsOnOpen -> EXPANDED_ANCHOR
                    else -> COLLAPSED_ANCHOR
                },
            )

        // Keyed on what it is asked, so an ask arriving after the sheet is composed is carried out too.
        var isExpandPending by rememberSaveable(expandsOnOpen) { mutableStateOf(expandsOnOpen && !state.isExpandedOrExpanding) }

        // Read from where the sheet is going rather than where it is, so a track arriving while the sheet
        // is still sliding away brings it back instead of being missed.
        LaunchedEffect(hasTrack, isExpandPending) {
            when (hasTrack) {
                true -> if (isExpandPending) {
                    state.expandSoft()
                    isExpandPending = false
                } else if (state.isDismissedOrDismissing) {
                    state.collapseSoft()
                }
                false -> if (!state.isDismissedOrDismissing) state.dismiss()
                null -> Unit
            }
        }

        // Read by the screen where it lays out or draws, so following every frame of a drag recomposes nothing.
        val sheetHeight = remember(state) { PlayerSheetHeight(current = { state.value }, collapsed = state.collapsedBound) }
        val miniPlayerSeekBarRequests = remember { MiniPlayerSeekBarRequests() }

        // As much as the mini player covers, as it covers it: it grows as the mini player slides in and shrinks to the
        // navigation bar alone as it is swiped away, frame by frame with it - and holds still while the player opens
        // above it, so nothing recomposes for a drag of the open player.
        val bottomContentPadding by remember(state, bottomInset) {
            derivedStateOf {
                val presence = (state.value / state.collapsedBound).coerceIn(0f, 1f)
                bottomInset + (state.collapsedBound - bottomInset + MiniPlayerContentSpacing) * presence
            }
        }
        CompositionLocalProvider(
            LocalBottomContentPadding provides bottomContentPadding,
            LocalPlayerSheetHeight provides sheetHeight,
            LocalMiniPlayerSeekBarRequests provides miniPlayerSeekBarRequests,
        ) {
            content()
        }

        // The screen fading into its own colour beneath the mini player, from its foot up past its top - faintly, so the
        // mini player reads as standing above the screen rather than cut into it. Drawn only: it takes no touch, and it
        // goes with the mini player - as the sheet leaves, and as it opens over it.
        val screenColor = MaterialTheme.colorScheme.surface
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(miniPlayerClearance + MiniPlayerScrimRise)
                    .drawBehind {
                        val presence = (state.value / state.collapsedBound).coerceIn(0f, 1f) * (1f - state.revealFraction)
                        if (presence > 0f) {
                            drawRect(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    1f to screenColor.copy(alpha = MiniPlayerScrimAlpha * presence),
                                ),
                            )
                        }
                    },
        )

        // Every control within it - the player's, its lyrics sheet's and its queue bar's - is on in the one second accent.
        CompositionLocalProvider(LocalPlayerCheckedColors provides playerColors().checked) {
            BottomSheetPlayer(
                state = state,
                uiState = uiState,
                appearance = appearance,
                sleepTimer = sleepTimer,
                viewModel = viewModel,
                miniPlayerSeekBarRequests = miniPlayerSeekBarRequests,
                onGoToAlbum = onGoToAlbum,
                onGoToArtist = onGoToArtist,
                onGoToPlayingFrom = onGoToPlayingFrom,
                onOpenEqualizer = onOpenEqualizer,
                onEditLyrics = onEditLyrics,
                trackOptionsSheet = trackOptionsSheet,
            )
        }
    }
}
