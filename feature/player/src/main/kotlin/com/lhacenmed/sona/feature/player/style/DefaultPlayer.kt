@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.player.style

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.datastore.PlayerAppearance
import com.lhacenmed.sona.core.datastore.PlayerSliderStyle
import com.lhacenmed.sona.core.designsystem.icon.PlayerIcons
import com.lhacenmed.sona.core.designsystem.theme.iconButtonPressShapes
import com.lhacenmed.sona.core.model.RepeatMode
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.PlaybackUiState
import com.lhacenmed.sona.feature.player.InactiveToggleAlpha
import com.lhacenmed.sona.feature.player.LyricsPillSlot
import com.lhacenmed.sona.feature.player.NowPlayingHeader
import com.lhacenmed.sona.feature.player.PlayerBarButton
import com.lhacenmed.sona.feature.player.PlayerBarButtonSize
import com.lhacenmed.sona.feature.player.PlayerBarIconSize
import com.lhacenmed.sona.feature.player.PlayerButtonRowPadding
import com.lhacenmed.sona.feature.player.PlayerHorizontalPadding
import com.lhacenmed.sona.feature.player.PlayerLitDot
import com.lhacenmed.sona.feature.player.PlayerLyricsState
import com.lhacenmed.sona.feature.player.PlayerPlayButtonSize
import com.lhacenmed.sona.feature.player.PlayerPlayIconSize
import com.lhacenmed.sona.feature.player.PlayerSeekBarHeight
import com.lhacenmed.sona.feature.player.PlayerSeekThumbSize
import com.lhacenmed.sona.feature.player.PlayerSeekTimeGap
import com.lhacenmed.sona.feature.player.PlayerSkipButtonSize
import com.lhacenmed.sona.feature.player.PlayerSkipIconSize
import com.lhacenmed.sona.feature.player.PlayerSlider
import com.lhacenmed.sona.feature.player.PlayerTimeLabel
import com.lhacenmed.sona.feature.player.PlayerTitleActions
import com.lhacenmed.sona.feature.player.PlayerTitleSection
import com.lhacenmed.sona.feature.player.PlayerTransportGap
import com.lhacenmed.sona.feature.player.PlayerUiState
import com.lhacenmed.sona.feature.player.PlayerViewModel
import com.lhacenmed.sona.feature.player.R
import com.lhacenmed.sona.feature.player.Thumbnail
import com.lhacenmed.sona.feature.player.background.PlayerColors
import com.lhacenmed.sona.feature.player.favoriteIcon
import com.lhacenmed.sona.feature.player.lyricsPane
import com.lhacenmed.sona.feature.player.playPauseIcon
import com.lhacenmed.sona.feature.player.transportIcon

/** The sheet the default player is drawn on: the app theme's own surface, black under a black theme. */
internal object DefaultPlayerColors {
    val sheet: Color
        @Composable get() = MaterialTheme.colorScheme.surface
}

/** The least the transport keeps between its corners and the three in its middle. */
private val TransportCornerClearance = 8.dp

/**
 * The default player, laid out as the video player is: a header along the top, the artwork with the title and the line
 * playing beneath it, then the seek bar and its times, the transport, and the queue bar along the foot - the artwork beside
 * the rest in landscape. Both `PlayerStyle.DEFAULT` and `IMMERSIVE` are drawn with it, told apart by where their [lyrics]
 * open.
 *
 * Lyrics that open in a sheet of their own - the default style's - leave the player as it is under them: the sheet draws
 * the same seek bar, transport and queue bar in the same places - see `LyricsSheet`. Beneath the title in portrait, their
 * line playing - [lyricsPreview] - opens them; landscape has no room for it beside the seek bar and transport.
 *
 * Lyrics that open in the player - the immersive style's - show their line in a pill above the seek bar, and grow from it
 * over the header and the artwork, which recede: in portrait the title goes with them, and the seek bar and transport glide
 * down into the room the queue bar leaves; in landscape they stay beside the lyrics. Shuffle becomes the lyrics' menu and
 * repeat their close, in place. With the player controls hidden under the lyrics, the seek bar and the middle of the
 * transport fade, and the lyrics take their room too.
 */
@Composable
internal fun DefaultPlayer(
    track: Track,
    uiState: PlayerUiState,
    appearance: PlayerAppearance,
    colors: PlayerColors,
    isLoading: Boolean,
    isPlayerExpanded: Boolean,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    queueBarHeight: Dp,
    titleActions: PlayerTitleActions,
    lyrics: PlayerLyricsState,
    lyricsPreview: @Composable (Modifier) -> Unit,
    onMenuClick: () -> Unit,
    onCollapse: () -> Unit,
    onOpenEqualizer: () -> Unit,
    viewModel: PlayerViewModel,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
) {
    val contentColor = colors.content
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val hidesControls = !lyrics.showsPlayerControls
    val density = LocalDensity.current
    val playingFrom by viewModel.playingFrom.collectAsStateWithLifecycle()
    // The touch target everything but the player's own bars keeps - the line playing's search among them.
    val defaultTouchTarget = LocalMinimumInteractiveComponentSize.current

    // Portrait only: how far the seek bar and transport glide down - the queue bar's room above the navigation bar.
    val bottomInset = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
    val glidePx = if (isLandscape) 0f else with(density) { (queueBarHeight - bottomInset).toPx() }
    var seekBarHeightPx by remember { mutableIntStateOf(0) }
    SideEffect {
        lyrics.paneExtensionPx = if (isLandscape) 0f else glidePx + if (hidesControls) seekBarHeightPx.toFloat() else 0f
    }

    val coverRecede =
        Modifier.graphicsLayer {
            val recede = lyrics.phase(0f, 0.6f)
            alpha = 1f - recede
            translationY = -40.dp.toPx() * recede
            scaleX = lerp(1f, 0.88f, recede)
            scaleY = scaleX
        }
    val titleRecede =
        Modifier.graphicsLayer {
            val recede = lyrics.phase(0f, 0.35f)
            alpha = 1f - recede
            translationY = -14.dp.toPx() * recede
        }
    val glide = Modifier.graphicsLayer { translationY = glidePx * lyrics.phase(0f, 0.8f) }
    val seekBarModifier =
        glide
            .graphicsLayer { alpha = if (hidesControls) 1f - lyrics.phase(0f, 0.3f) else 1f }
            .onSizeChanged { seekBarHeightPx = it.height }

    val header: @Composable (Modifier) -> Unit = { modifier ->
        NowPlayingHeader(
            playingFrom = playingFrom,
            queue = uiState.queue,
            contentColor = contentColor,
            onCollapse = onCollapse,
            onOpenEqualizer = onOpenEqualizer,
            onMoreClick = onMenuClick,
            modifier = modifier,
        )
    }
    val titleRow: @Composable (Modifier) -> Unit = { modifier ->
        DefaultTitleRow(
            track = track,
            isFavorite = uiState.isCurrentTrackFavorite,
            onToggleFavorite = if (uiState.canFavorite) viewModel::onToggleFavorite else null,
            colors = colors,
            titleActions = titleActions,
            modifier = modifier,
        )
    }
    val thumbnail: @Composable (Modifier) -> Unit = { modifier ->
        Thumbnail(
            uiState = uiState,
            durationMs = duration,
            textBackgroundColor = contentColor,
            hideThumbnail = appearance.hideThumbnail,
            swipeToChangeTrack = appearance.swipeToChangeTrack,
            isPlayerExpanded = isPlayerExpanded,
            viewModel = viewModel,
            modifier = modifier,
        )
    }
    // The seek bar and its times, then the transport.
    val seekBarAndTransport: @Composable (Modifier) -> Unit = { transportModifier ->
        Column {
            DefaultSeekBar(
                sliderStyle = appearance.sliderStyle,
                isPlaying = uiState.playback.isPlaying,
                colors = colors,
                sliderPosition = sliderPosition,
                position = position,
                duration = duration,
                onSliderValueChange = onSliderValueChange,
                onSliderValueChangeFinished = onSliderValueChangeFinished,
                modifier = seekBarModifier,
            )
            Spacer(Modifier.height(16.dp))
            DefaultTransportControls(
                playback = uiState.playback,
                isLoading = isLoading,
                colors = colors,
                lyrics = lyrics,
                hidesControls = hidesControls,
                viewModel = viewModel,
                modifier = transportModifier,
            )
        }
    }

    // The bars' buttons are their own touch targets: Material would otherwise pad each out past the player's keyline.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides PlayerBarButtonSize) {
        if (isLandscape) {
            Row(
                modifier =
                    Modifier
                        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                        .padding(bottom = queueBarHeight),
            ) {
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .lyricsPane(lyrics),
                ) {
                    header(coverRecede)
                    thumbnail(Modifier.weight(1f).fillMaxWidth().then(coverRecede))
                }
                Column(
                    verticalArrangement = Arrangement.Center,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .statusBarsPadding(),
                ) {
                    titleRow(Modifier)
                    Spacer(Modifier.height(12.dp))
                    // Only lyrics that open in the player have a pill; their sheet is opened from the queue bar.
                    if (lyrics.opensInPlayer) {
                        LyricsPillSlot(lyrics, Modifier.padding(horizontal = PlayerHorizontalPadding))
                        Spacer(Modifier.height(12.dp))
                    }
                    seekBarAndTransport(Modifier)
                }
            }
        } else {
            Column(
                modifier =
                    Modifier
                        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                        .padding(bottom = queueBarHeight),
            ) {
                // Everything the open lyrics are drawn over: the header, the artwork, the title and the pill - or, for
                // lyrics in a sheet, the line playing, whose faded top and bottom keep it clear of the title and the seek bar.
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .lyricsPane(lyrics),
                ) {
                    header(coverRecede)
                    thumbnail(Modifier.weight(1f).fillMaxWidth().then(coverRecede))
                    titleRow(titleRecede)
                    if (lyrics.opensInPlayer) {
                        Spacer(Modifier.height(12.dp))
                        LyricsPillSlot(lyrics, Modifier.padding(horizontal = PlayerHorizontalPadding))
                        Spacer(Modifier.height(12.dp))
                    } else {
                        Spacer(Modifier.height(10.dp))
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides defaultTouchTarget) {
                            lyricsPreview(Modifier)
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }

                seekBarAndTransport(glide)

                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

/** The title and artist, with the favourite beside them. */
@Composable
private fun DefaultTitleRow(
    track: Track,
    isFavorite: Boolean,
    /** Null for a track that cannot be a favourite, which leaves its button out. */
    onToggleFavorite: (() -> Unit)?,
    colors: PlayerColors,
    titleActions: PlayerTitleActions,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = PlayerHorizontalPadding,
                    // The button's glyph on the keyline; the title alone reaches the keyline itself.
                    end = if (onToggleFavorite != null) PlayerButtonRowPadding else PlayerHorizontalPadding,
                ),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            PlayerTitleSection(
                track = track,
                textBackgroundColor = colors.content,
                titleActions = titleActions,
            )
        }

        if (onToggleFavorite != null) {
            Spacer(modifier = Modifier.width(12.dp))
            // The player's accent, as everything that is on in it.
            PlayerBarButton(
                icon = favoriteIcon(isFavorite),
                contentDescription = stringResource(R.string.player_like),
                color = if (isFavorite) colors.button else colors.content,
                onClick = onToggleFavorite,
            )
        }
    }
}

/** The seek bar, with its times beneath its thumb - the player's, and its lyrics sheet's in the same place. */
@Composable
internal fun DefaultSeekBar(
    sliderStyle: PlayerSliderStyle,
    isPlaying: Boolean,
    colors: PlayerColors,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        PlayerSlider(
            sliderStyle = sliderStyle,
            sliderPosition = sliderPosition,
            position = position,
            duration = duration,
            isPlaying = isPlaying,
            textButtonColor = colors.button,
            onValueChange = onSliderValueChange,
            onValueChangeFinished = onSliderValueChangeFinished,
        )

        // Beneath the thumb, not the touch target it is centred in.
        PlayerTimeLabel(
            sliderPosition = sliderPosition,
            position = position,
            duration = duration,
            textBackgroundColor = colors.content,
            modifier = Modifier.padding(top = (PlayerSeekBarHeight + PlayerSeekThumbSize.height) / 2 + PlayerSeekTimeGap),
        )
    }
}

/**
 * Shuffle at the start and repeat at the end, each lit while on, either side of previous, play/pause and next - which keep
 * as far apart as the row allows, up to [PlayerTransportGap].
 *
 * While the [lyrics] are open in the player, shuffle is their menu and repeat their close; with [hidesControls], the three
 * in the middle fade, leaving the two corners to the lyrics. The lyrics sheet draws it too, in the same place.
 */
@Composable
internal fun DefaultTransportControls(
    playback: PlaybackUiState,
    isLoading: Boolean,
    colors: PlayerColors,
    lyrics: PlayerLyricsState,
    hidesControls: Boolean,
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    val contentColor = colors.content
    val haptic = LocalHapticFeedback.current
    val areMiddleControlsAway = hidesControls && lyrics.isOpenInPlayer
    val middleControlsFade =
        Modifier.graphicsLayer { alpha = if (hidesControls) 1f - lyrics.phase(0f, 0.3f) else 1f }
    val skipColors = IconButtonDefaults.iconButtonColors(contentColor = contentColor, disabledContentColor = contentColor.copy(alpha = 0.38f))

    val onPlayPause = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        viewModel.onTogglePlayPause()
    }
    val onPrevious = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        viewModel.onSkipPrevious()
    }
    val onNext = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        viewModel.onSkipNext()
    }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = PlayerButtonRowPadding)
                .height(PlayerPlayButtonSize),
    ) {
        val middleWidth = PlayerSkipButtonSize * 2 + PlayerPlayButtonSize
        val gap = ((maxWidth - (PlayerBarButtonSize + TransportCornerClearance) * 2 - middleWidth) / 2).coerceIn(0.dp, PlayerTransportGap)

        LyricsCornerButton(
            lyrics = lyrics,
            colors = colors,
            playerIcon = PlayerIcons.Shuffle,
            playerContentDescription = stringResource(R.string.player_shuffle),
            isPlayerToggleOn = playback.shuffleEnabled,
            lyricsIcon = PlayerIcons.More,
            lyricsContentDescription = stringResource(R.string.player_more_options),
            turnsIn = false,
            onPlayerClick = viewModel::onToggleShuffle,
            onLyricsClick = lyrics::showMenu,
            modifier = Modifier.align(Alignment.CenterStart),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.Center),
        ) {
            IconButton(
                onClick = onPrevious,
                shapes = iconButtonPressShapes(),
                enabled = playback.canSkipPrevious && !areMiddleControlsAway,
                colors = skipColors,
                modifier = Modifier.size(PlayerSkipButtonSize).then(middleControlsFade),
            ) {
                Icon(PlayerIcons.SkipPrevious, stringResource(R.string.player_previous), Modifier.size(PlayerSkipIconSize))
            }

            FilledIconButton(
                onClick = onPlayPause,
                shapes = iconButtonPressShapes(),
                enabled = !areMiddleControlsAway,
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = colors.button,
                        contentColor = colors.onButton,
                        disabledContainerColor = colors.button,
                        disabledContentColor = colors.onButton,
                    ),
                modifier = Modifier.size(PlayerPlayButtonSize).then(middleControlsFade),
            ) {
                if (isLoading) {
                    CircularWavyProgressIndicator(modifier = Modifier.size(PlayerPlayIconSize), color = colors.onButton)
                } else {
                    Icon(
                        imageVector = playback.playPauseIcon(),
                        contentDescription = stringResource(if (playback.isPlaying) R.string.player_pause else R.string.player_play),
                        modifier = Modifier.size(PlayerPlayIconSize),
                    )
                }
            }

            IconButton(
                onClick = onNext,
                shapes = iconButtonPressShapes(),
                enabled = playback.canSkipNext && !areMiddleControlsAway,
                colors = skipColors,
                modifier = Modifier.size(PlayerSkipButtonSize).then(middleControlsFade),
            ) {
                Icon(PlayerIcons.SkipNext, stringResource(R.string.player_next), Modifier.size(PlayerSkipIconSize))
            }
        }

        LyricsCornerButton(
            lyrics = lyrics,
            colors = colors,
            playerIcon = playback.repeatMode.transportIcon(),
            playerContentDescription = stringResource(R.string.player_repeat),
            isPlayerToggleOn = playback.repeatMode != RepeatMode.OFF,
            lyricsIcon = PlayerIcons.Close,
            lyricsContentDescription = stringResource(R.string.player_close),
            turnsIn = true,
            onPlayerClick = viewModel::onCycleRepeatMode,
            onLyricsClick = lyrics::close,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

/**
 * A transport corner that is a player toggle - shuffle, repeat - lit while on, and, while the [lyrics] are open in the
 * player, one of theirs in its place: the toggle's glyph shrinks away as the lyrics' grows in. [turnsIn] turns the lyrics'
 * glyph into place as it comes - the way out of them.
 */
@Composable
private fun LyricsCornerButton(
    lyrics: PlayerLyricsState,
    colors: PlayerColors,
    playerIcon: ImageVector,
    playerContentDescription: String,
    isPlayerToggleOn: Boolean,
    lyricsIcon: ImageVector,
    lyricsContentDescription: String,
    turnsIn: Boolean,
    onPlayerClick: () -> Unit,
    onLyricsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLyricsOpen = lyrics.isOpenInPlayer
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(PlayerBarButtonSize)) {
        IconButton(
            onClick = if (isLyricsOpen) onLyricsClick else onPlayerClick,
            shapes = iconButtonPressShapes(),
            colors = IconButtonDefaults.iconButtonColors(contentColor = colors.content),
            modifier = Modifier.matchParentSize(),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = playerIcon,
                    contentDescription = if (isLyricsOpen) null else playerContentDescription,
                    tint = if (isPlayerToggleOn) colors.button else colors.content.copy(alpha = InactiveToggleAlpha),
                    modifier =
                        Modifier
                            .size(PlayerBarIconSize)
                            .graphicsLayer {
                                val swap = lyrics.phase(0.12f, 0.6f)
                                alpha = 1f - swap
                                scaleX = lerp(1f, 0.6f, swap)
                                scaleY = scaleX
                            },
                )
                Icon(
                    imageVector = lyricsIcon,
                    contentDescription = if (isLyricsOpen) lyricsContentDescription else null,
                    tint = colors.content,
                    modifier =
                        Modifier
                            .size(PlayerBarIconSize)
                            .graphicsLayer {
                                val swap = lyrics.phase(0.12f, 0.6f)
                                alpha = swap
                                scaleX = lerp(0.6f, 1f, swap)
                                scaleY = scaleX
                                if (turnsIn) rotationZ = lerp(-60f, 0f, swap)
                            },
                )
            }
        }
        if (isPlayerToggleOn) {
            PlayerLitDot(
                color = colors.button,
                modifier = Modifier.align(Alignment.BottomCenter).graphicsLayer { alpha = 1f - lyrics.phase(0.12f, 0.6f) },
            )
        }
    }
}
