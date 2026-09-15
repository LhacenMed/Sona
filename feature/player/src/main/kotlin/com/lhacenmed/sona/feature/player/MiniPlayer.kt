package com.lhacenmed.sona.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.PlaybackUiState
import kotlin.math.roundToInt

/**
 * The collapsed player: artwork ringed by progress, title and artist, and transport buttons, on the
 * theme's own surface. Swiping it sideways changes track. Ported from ArchiveTune's `MiniPlayer` in its
 * "follow theme" style.
 */
@Composable
internal fun MiniPlayer(
    track: Track?,
    playback: PlaybackUiState,
    position: Long,
    duration: Long,
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    val layoutDirection = LocalLayoutDirection.current
    val coroutineScope = rememberCoroutineScope()
    val contentColors = rememberMiniPlayerContentColors()

    SwipeableMiniPlayerBox(
        hasPreviousTrack = playback.hasPreviousTrack,
        hasNextTrack = playback.hasNextTrack,
        onSwipeToPrevious = viewModel::onSkipToPreviousTrack,
        onSwipeToNext = viewModel::onSkipNext,
        layoutDirection = layoutDirection,
        coroutineScope = coroutineScope,
        modifier = modifier,
    ) { offsetX ->
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(MiniPlayerHeight)
                    .offset { IntOffset(offsetX.roundToInt(), 0) }
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            MiniPlayerContent(
                track = track,
                playback = playback,
                position = position,
                duration = duration,
                viewModel = viewModel,
                colors = contentColors,
            )
        }
    }
}

@Composable
private fun rememberMiniPlayerContentColors(): MiniPlayerContentColors {
    val colorScheme = MaterialTheme.colorScheme
    return remember(colorScheme) {
        MiniPlayerContentColors(
            title = colorScheme.onSurface,
            secondary = colorScheme.onSurfaceVariant,
            progress = colorScheme.primary,
            progressTrack = colorScheme.outline.copy(alpha = 0.18f),
            artworkContainer = colorScheme.surfaceVariant,
            artworkBorder = colorScheme.outline.copy(alpha = 0.2f),
            primaryButtonContainer = colorScheme.primary,
            primaryButtonIcon = colorScheme.onPrimary,
            secondaryButtonContainer = colorScheme.surfaceContainerHighest,
            buttonIcon = colorScheme.onSurface,
            disabledButtonIcon = colorScheme.onSurface.copy(alpha = 0.38f),
        )
    }
}
