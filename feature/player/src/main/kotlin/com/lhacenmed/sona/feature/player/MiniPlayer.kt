package com.lhacenmed.sona.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import com.lhacenmed.sona.core.datastore.MiniPlayerBackgroundStyle
import com.lhacenmed.sona.core.datastore.PlayerAppearance
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.PlaybackUiState
import com.lhacenmed.sona.feature.player.background.MiniPlayerBackground
import com.lhacenmed.sona.feature.player.background.rememberCoverGradientColors
import kotlin.math.roundToInt

/**
 * The collapsed player: artwork ringed by progress, title and artist, and transport buttons, over the
 * background [appearance] chooses. Swiping it sideways changes track, while [appearance] allows it.
 * Ported from ArchiveTune's `MiniPlayer`.
 */
@Composable
internal fun MiniPlayer(
    track: Track?,
    playback: PlaybackUiState,
    position: Long,
    duration: Long,
    appearance: PlayerAppearance,
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    val layoutDirection = LocalLayoutDirection.current
    val coroutineScope = rememberCoroutineScope()
    val gradientColors = rememberCoverGradientColors(
        coverArtUri = track?.coverArtUri,
        enabled = appearance.miniPlayerBackground != MiniPlayerBackgroundStyle.THEME,
    )
    // A cover style draws the theme's surface until the cover has colours to give - and so do its contents.
    val drawsCover = appearance.miniPlayerBackground != MiniPlayerBackgroundStyle.THEME && gradientColors.isNotEmpty()
    val contentColors = rememberMiniPlayerContentColors(useArtworkBackground = drawsCover)

    SwipeableMiniPlayerBox(
        hasPreviousTrack = playback.hasPreviousTrack,
        hasNextTrack = playback.hasNextTrack,
        swipeEnabled = appearance.swipeToChangeTrack,
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
                    .clip(MaterialTheme.shapes.extraLarge),
        ) {
            MiniPlayerBackground(
                style = appearance.miniPlayerBackground,
                gradientColors = gradientColors,
                modifier = Modifier.fillMaxSize(),
            )
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

/** ArchiveTune's mini player colours: white over the cover's colours, the theme's own over its surface. */
@Composable
private fun rememberMiniPlayerContentColors(useArtworkBackground: Boolean): MiniPlayerContentColors {
    val colorScheme = MaterialTheme.colorScheme
    return remember(useArtworkBackground, colorScheme) {
        if (useArtworkBackground) {
            MiniPlayerContentColors(
                title = Color.White,
                secondary = Color.White.copy(alpha = 0.72f),
                progress = Color.White,
                progressTrack = Color.White.copy(alpha = 0.24f),
                artworkContainer = Color.White.copy(alpha = 0.14f),
                artworkBorder = Color.White.copy(alpha = 0.22f),
                primaryButtonContainer = Color.White.copy(alpha = 0.92f),
                primaryButtonIcon = Color.Black,
                secondaryButtonContainer = Color.Black.copy(alpha = 0.22f),
                buttonIcon = Color.White,
                disabledButtonIcon = Color.White.copy(alpha = 0.38f),
            )
        } else {
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
}
