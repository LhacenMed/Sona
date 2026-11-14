package com.lhacenmed.sona.feature.player

import com.lhacenmed.sona.feature.player.PlayerSectionGap
import android.content.res.Configuration
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.datastore.PlayerAppearance
import com.lhacenmed.sona.core.designsystem.theme.roundedRadius
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.PlaybackUiState
import com.lhacenmed.sona.feature.player.background.PlayerBackground
import com.lhacenmed.sona.feature.player.background.playerColors
import com.lhacenmed.sona.feature.player.style.DefaultSeekBar
import com.lhacenmed.sona.feature.player.style.DefaultTransportControls
import com.lhacenmed.sona.feature.player.style.sheetColor

/** The space kept beneath the transport, above the navigation bar. */
private val LyricsSheetFootMargin = 24.dp

/**
 * Grows the lyrics sheet up out of the player while [isOpen], and shrinks it back down as it closes - ArchiveTune's
 * `MikoLyricsTransition`, for lyrics that open in a sheet of their own rather than in the player. Nothing is composed while
 * it is fully hidden.
 *
 * Everything that moves is read as it is drawn, so the sheet grows and shrinks without composing again on any frame.
 */
@Composable
internal fun LyricsSheetTransition(
    isOpen: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val progress =
        animateFloatAsState(
            targetValue = if (isOpen) 1f else 0f,
            animationSpec =
                spring(
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            label = "lyricsSheetTransition",
        )
    val isShown by remember { derivedStateOf { progress.value > 0.001f } }
    val cornerRadius = roundedRadius(32.dp)

    if (isOpen || isShown) {
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                    .drawBehind { drawRect(Color.Black.copy(alpha = 0.24f * progress.value.coerceIn(0f, 1f))) },
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val shown = progress.value.coerceIn(0f, 1f)
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            scaleX = 0.92f + (0.08f * shown)
                            scaleY = 0.78f + (0.22f * shown)
                            alpha = (0.2f + (0.8f * shown)).coerceIn(0f, 1f)
                            translationY = size.height * 0.16f * (1f - shown)
                            shape = RoundedCornerShape(cornerRadius * (1f - shown))
                            clip = true
                        }.background(MaterialTheme.colorScheme.surface),
            ) {
                content()
            }
        }
    }
}

/**
 * The current track's lyrics in a sheet of their own, laid out as the player is: a header along the top - the way back, the
 * track's artwork beside its title and artist, the lyrics' menu - the lyrics where the artwork was, and the player's own
 * seek bar and transport at the foot. The seek bar follows the player's own position and drag - see `BottomSheetPlayer`.
 *
 * Without [PlayerLyricsState.showsPlayerControls], the seek bar and transport give the lyrics their room. In landscape, they
 * stand beside the lyrics.
 */
@Composable
internal fun LyricsSheet(
    track: Track,
    lyrics: TrackLyrics,
    playback: PlaybackUiState,
    appearance: PlayerAppearance,
    lyricsState: PlayerLyricsState,
    isLoading: Boolean,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    lyricsSyncOffset: Int,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    viewModel: PlayerViewModel,
    lyricsViewModel: LyricsViewModel,
) {
    // Drawn in the player's colours, over the player's own background.
    val colors = playerColors()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val showsPlayerControls = lyricsState.showsPlayerControls
    // The touch target everything but the player's own bars keeps - the lyrics' buttons among them.
    val defaultTouchTarget = LocalMinimumInteractiveComponentSize.current

    val lyricsPane: @Composable (Modifier) -> Unit = { paneModifier ->
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides defaultTouchTarget) {
            LyricsV2(
                track = track,
                timeline = lyrics.timeline,
                isFindingLyrics = lyrics.isFinding,
                positionMsProvider = viewModel::currentPositionMs,
                sliderPositionProvider = { sliderPosition },
                lyricsSyncOffset = lyricsSyncOffset,
                onSeek = viewModel::onSeek,
                textColor = colors.content,
                lyricsViewModel = lyricsViewModel,
                // Their text set on the player's keyline, as the title's is.
                modifier = paneModifier.padding(horizontal = PlayerHorizontalPadding - LyricsLineHorizontalPadding),
            )
        }
    }
    val seekBarAndTransport: @Composable () -> Unit = {
        Column {
            DefaultSeekBar(
                sliderStyle = appearance.sliderStyle,
                isPlaying = playback.isPlaying,
                colors = colors,
                sliderPosition = sliderPosition,
                position = position,
                duration = duration,
                onSliderValueChange = onSliderValueChange,
                onSliderValueChangeFinished = onSliderValueChangeFinished,
                viewModel = viewModel,
            )
            Spacer(Modifier.height(PlayerSectionGap))
            DefaultTransportControls(
                playback = playback,
                isLoading = isLoading,
                colors = colors,
                lyrics = lyricsState,
                hidesControls = !showsPlayerControls,
                viewModel = viewModel,
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PlayerBackground(
            style = appearance.background,
            coverArtUri = track.coverArtUri,
            customBackground = appearance.customBackground,
            modifier = Modifier.background(appearance.style.sheetColor()),
        )

        // Nothing the sheet leaves unanswered falls through to the player beneath it.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .consumeUnhandledPointerInput(),
        )

        // The bars' buttons are their own touch targets, as in the player.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides PlayerBarButtonSize) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                        .padding(bottom = LyricsSheetFootMargin),
            ) {
                LyricsHeader(
                    track = track,
                    contentColor = colors.content,
                    onClose = lyricsState::close,
                    onMenuClick = lyricsState::showMenu,
                )
                Spacer(Modifier.height(16.dp))
                if (isLandscape && showsPlayerControls) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) {
                        lyricsPane(Modifier.weight(1f).fillMaxHeight())
                        Box(modifier = Modifier.weight(1f)) {
                            seekBarAndTransport()
                        }
                    }
                } else {
                    lyricsPane(Modifier.weight(1f).fillMaxWidth())
                    if (showsPlayerControls) {
                        Spacer(Modifier.height(16.dp))
                        seekBarAndTransport()
                    }
                }
            }
        }
    }
}
