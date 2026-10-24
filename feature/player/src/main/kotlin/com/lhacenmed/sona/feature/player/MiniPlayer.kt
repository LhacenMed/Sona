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
 *
 * Given a [seekBar] - while the screen beneath asks for it, see `ShowMiniPlayerSeekBar` - it shows where the
 * track is and a slider to move through it in place of the names, at the same height, and holds still under
 * the finger rather than swiping to another track.
 */
@Composable
internal fun MiniPlayer(
    track: Track?,
    playback: PlaybackUiState,
    position: Long,
    duration: Long,
    appearance: PlayerAppearance,
    viewModel: PlayerViewModel,
    seekBar: MiniPlayerSeekBarState?,
    modifier: Modifier = Modifier,
) {
    val layoutDirection = LocalLayoutDirection.current
    val coroutineScope = rememberCoroutineScope()
    val gradientColors = rememberCoverGradientColors(
        coverArtUri = track?.coverArtUri,
        enabled = appearance.miniPlayerBackground != MiniPlayerBackgroundStyle.FOLLOW_THEME,
    )
    val contentColors = rememberMiniPlayerContentColors()

    SwipeableMiniPlayerBox(
        hasPreviousTrack = playback.hasPreviousTrack,
        hasNextTrack = playback.hasNextTrack,
        swipeEnabled = appearance.swipeToChangeTrack && seekBar == null,
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
                seekBar = seekBar,
                sliderStyle = appearance.sliderStyle,
            )
        }
    }
}

/**
 * The mini player's colours: the theme's own, over every background - each is drawn in the theme's tones, see
 * [MiniPlayerBackground].
 */
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
