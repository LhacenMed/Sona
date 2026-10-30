@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.player.style

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.lhacenmed.sona.core.designsystem.theme.roundedShape
import com.lhacenmed.sona.core.datastore.PlayerAppearance
import com.lhacenmed.sona.core.datastore.PlayerSliderStyle
import com.lhacenmed.sona.core.model.RepeatMode
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.PlaybackUiState
import com.lhacenmed.sona.feature.player.InactiveToggleAlpha
import com.lhacenmed.sona.feature.player.LyricsPillSlot
import com.lhacenmed.sona.feature.player.PlayerHorizontalPadding
import com.lhacenmed.sona.feature.player.PlayerLyricsState
import com.lhacenmed.sona.feature.player.PlayerSlider
import com.lhacenmed.sona.feature.player.PlayerTimeLabel
import com.lhacenmed.sona.feature.player.PlayerTitleActions
import com.lhacenmed.sona.feature.player.PlayerTitleSection
import com.lhacenmed.sona.feature.player.PlayerUiState
import com.lhacenmed.sona.feature.player.PlayerViewModel
import com.lhacenmed.sona.feature.player.R
import com.lhacenmed.sona.feature.player.Thumbnail
import com.lhacenmed.sona.feature.player.background.PlayerColors
import com.lhacenmed.sona.feature.player.favoriteIconRes
import com.lhacenmed.sona.feature.player.lyricsPane
import com.lhacenmed.sona.feature.player.playPauseIconRes
import com.lhacenmed.sona.feature.player.transportIconRes
import com.lhacenmed.sona.feature.playback.R as PlaybackR

/** The sheet the default player is drawn on: the app theme's own surface, black under a black theme. */
internal object DefaultPlayerColors {
    val sheet: Color
        @Composable get() = MaterialTheme.colorScheme.surface
}

/**
 * The default player: the artwork above its title, seek bar and transport - side by side in landscape. Ported
 * from ArchiveTune's Cinematic player. Both `PlayerStyle.DEFAULT` and `IMMERSIVE` are drawn with it,
 * told apart by where their [lyrics] open.
 *
 * Lyrics that open in a sheet of their own - the default style's - leave the player as it is under them.
 *
 * Lyrics that open in the player - the immersive style's - show their line in a pill above the seek bar, and
 * grow from it over the artwork, which recedes: in portrait the title goes with it, and the seek bar and
 * transport glide down into the room the queue bar leaves; in landscape they stay beside the lyrics. Shuffle
 * becomes the lyrics' menu and repeat their close, in place. With the player controls hidden under the lyrics,
 * the seek bar and the middle of the transport fade, and the lyrics take their room too.
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

    val titleRow: @Composable (Modifier) -> Unit = { modifier ->
        DefaultTitleRow(
            track = track,
            isFavorite = uiState.isCurrentTrackFavorite,
            canFavorite = uiState.canFavorite,
            contentColor = contentColor,
            titleActions = titleActions,
            onMenuClick = onMenuClick,
            viewModel = viewModel,
            modifier = modifier,
        )
    }
    val seekBar: @Composable () -> Unit = {
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
    }
    val transport: @Composable (Modifier) -> Unit = { modifier ->
        DefaultTransportControls(
            playback = uiState.playback,
            isLoading = isLoading,
            colors = colors,
            lyrics = lyrics,
            hidesControls = hidesControls,
            viewModel = viewModel,
            modifier = modifier,
        )
    }
    // Only lyrics that open in the player have a pill; their sheet is opened from the queue bar.
    val lyricsPill: @Composable () -> Unit = {
        if (lyrics.opensInPlayer) {
            LyricsPillSlot(lyrics, Modifier.padding(horizontal = PlayerHorizontalPadding))
            Spacer(Modifier.height(12.dp))
        }
    }
    val thumbnail: @Composable (Modifier) -> Unit = { modifier ->
        Thumbnail(
            uiState = uiState,
            durationMs = duration,
            textBackgroundColor = contentColor,
            hideThumbnail = appearance.hideThumbnail,
            swipeToChangeTrack = appearance.swipeToChangeTrack,
            isPlayerExpanded = isPlayerExpanded,
            onCollapse = onCollapse,
            onOpenEqualizer = onOpenEqualizer,
            viewModel = viewModel,
            modifier = modifier,
        )
    }

    if (isLandscape) {
        Row(
            modifier =
                Modifier
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                    .padding(bottom = queueBarHeight + 48.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .lyricsPane(lyrics),
            ) {
                val thumbnailSize = (LocalConfiguration.current.screenWidthDp * 0.4).dp
                thumbnail(Modifier.size(thumbnailSize).then(coverRecede))
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .weight(1f)
                        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
            ) {
                Spacer(Modifier.weight(1f))

                titleRow(Modifier)
                Spacer(Modifier.height(12.dp))
                lyricsPill()
                seekBar()
                transport(Modifier)

                Spacer(Modifier.weight(1f))
            }
        }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                    .padding(bottom = queueBarHeight),
        ) {
            // Everything the open lyrics are drawn over: the artwork and its header, the title and the pill.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .weight(1f)
                        .lyricsPane(lyrics),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.weight(1f).then(coverRecede),
                ) {
                    thumbnail(Modifier)
                }
                titleRow(titleRecede)
                Spacer(Modifier.height(12.dp))
                lyricsPill()
            }

            seekBar()
            transport(glide)

            Spacer(Modifier.height(30.dp))
        }
    }
}

/** The title and artist, with the track's actions beside them. */
@Composable
private fun DefaultTitleRow(
    track: Track,
    isFavorite: Boolean,
    canFavorite: Boolean,
    contentColor: Color,
    titleActions: PlayerTitleActions,
    onMenuClick: () -> Unit,
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = PlayerHorizontalPadding),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            PlayerTitleSection(
                track = track,
                textBackgroundColor = contentColor,
                titleActions = titleActions,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        DefaultTrackActions(
            contentColor = contentColor,
            isFavorite = isFavorite,
            onToggleFavorite = if (canFavorite) viewModel::onToggleFavorite else null,
            onMenuClick = onMenuClick,
        )
    }
}

/** The seek bar and its times, with the gap down to the transport. */
@Composable
private fun DefaultSeekBar(
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
    Column(modifier = modifier) {
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

        Spacer(Modifier.height(4.dp))

        PlayerTimeLabel(
            sliderPosition = sliderPosition,
            position = position,
            duration = duration,
            textBackgroundColor = colors.content,
        )

        Spacer(Modifier.height(12.dp))
    }
}

/**
 * Favourite and more, as glass cards beside the title. Sharing is in the track's options sheet, which
 * more opens, so the title keeps the room a third card would take.
 */
@Composable
private fun DefaultTrackActions(
    contentColor: Color,
    isFavorite: Boolean,
    /** Null for a track that cannot be a favourite, which leaves its card out. */
    onToggleFavorite: (() -> Unit)?,
    onMenuClick: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onToggleFavorite != null) Surface(
            onClick = onToggleFavorite,
            shape = roundedShape(14.dp),
            color =
                if (isFavorite) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                } else {
                    contentColor.copy(alpha = 0.12f)
                },
            modifier =
                Modifier
                    .height(44.dp)
                    .width(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    painter = painterResource(favoriteIconRes(isFavorite)),
                    contentDescription = null,
                    // The theme's own colour, as the queue's favourite button marks a favourite.
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else contentColor,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Surface(
            onClick = onMenuClick,
            shape = roundedShape(14.dp),
            color = contentColor.copy(alpha = 0.12f),
            modifier =
                Modifier
                    .height(44.dp)
                    .width(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    painter = painterResource(R.drawable.more_horiz),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/**
 * Shuffle, previous, play/pause, next and repeat, scaled down together to fit a narrow screen. The
 * play/pause button squares off while playing.
 *
 * While the [lyrics] are open in the player, shuffle is their menu and repeat their close; with [hidesControls],
 * the three in the middle fade, leaving the two corners to the lyrics.
 */
@Composable
private fun DefaultTransportControls(
    playback: PlaybackUiState,
    isLoading: Boolean,
    colors: PlayerColors,
    lyrics: PlayerLyricsState,
    hidesControls: Boolean,
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    val contentColor = colors.content
    val onButtonColor = colors.onButton
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val shuffleEnabled = playback.shuffleEnabled
    val repeatMode = playback.repeatMode
    val canSkipPrevious = playback.canSkipPrevious
    val canSkipNext = playback.canSkipNext
    val areMiddleControlsAway = hidesControls && lyrics.isOpenInPlayer
    val middleControlsFade =
        Modifier.graphicsLayer { alpha = if (hidesControls) 1f - lyrics.phase(0f, 0.3f) else 1f }

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
    val onShuffle = {
        view.performContextClick()
        viewModel.onToggleShuffle()
    }
    val onRepeat = {
        view.performContextClick()
        viewModel.onCycleRepeatMode()
    }

    val playPauseCorner by animateDpAsState(
        targetValue = if (playback.isPlaying) 28.dp else 44.dp,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "playPauseCorner",
    )

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = PlayerHorizontalPadding),
    ) {
        val baseLarge = 56.dp
        val baseSmall = 46.dp
        val baseGap = 12.dp
        val baseLargeIcon = 28.dp
        val baseSmallIcon = 22.dp
        val baseLargeRadius = 18.dp
        val baseSmallRadius = 16.dp
        val centerSize = 88.dp
        val centerPadding = 40.dp
        val sideTotal = (maxWidth - centerSize - centerPadding) / 2f
        val scale =
            ((sideTotal - baseGap) / (baseLarge + baseSmall)).coerceAtMost(1f).coerceAtLeast(0.6f)
        val large = baseLarge * scale
        val small = baseSmall * scale
        val gap = baseGap * scale
        val largeIcon = baseLargeIcon * scale
        val smallIcon = baseSmallIcon * scale
        val largeRadius = baseLargeRadius * scale
        val smallRadius = baseSmallRadius * scale

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LyricsCornerButton(
                    lyrics = lyrics,
                    size = small,
                    radius = smallRadius,
                    iconSize = smallIcon,
                    contentColor = contentColor,
                    playerIconRes = PlaybackR.drawable.shuffle,
                    isPlayerToggleOn = shuffleEnabled,
                    lyricsIconRes = R.drawable.more_horiz,
                    lyricsContentDescription = stringResource(R.string.player_more_options),
                    isLyricsAccent = false,
                    onPlayerClick = onShuffle,
                    onLyricsClick = lyrics::showMenu,
                )

                Spacer(modifier = Modifier.width(gap))

                Surface(
                    onClick = onPrevious,
                    enabled = canSkipPrevious && !areMiddleControlsAway,
                    shape = roundedShape(largeRadius),
                    color = contentColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(large).then(middleControlsFade),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.skip_previous),
                            contentDescription = null,
                            tint = contentColor.copy(alpha = if (canSkipPrevious) 1f else 0.4f),
                            modifier = Modifier.size(largeIcon),
                        )
                    }
                }
            }

            Surface(
                onClick = onPlayPause,
                enabled = !areMiddleControlsAway,
                shape = roundedShape(playPauseCorner),
                color = colors.button,
                modifier =
                    Modifier
                        .padding(horizontal = 20.dp)
                        .size(88.dp)
                        .then(middleControlsFade),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoading) {
                        CircularWavyProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            color = onButtonColor,
                        )
                    } else {
                        Icon(
                            painter = painterResource(playback.playPauseIconRes()),
                            contentDescription = null,
                            tint = onButtonColor,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = onNext,
                    enabled = canSkipNext && !areMiddleControlsAway,
                    shape = roundedShape(largeRadius),
                    color = contentColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(large).then(middleControlsFade),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.skip_next),
                            contentDescription = null,
                            tint = contentColor.copy(alpha = if (canSkipNext) 1f else 0.4f),
                            modifier = Modifier.size(largeIcon),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(gap))

                LyricsCornerButton(
                    lyrics = lyrics,
                    size = small,
                    radius = smallRadius,
                    iconSize = smallIcon,
                    contentColor = contentColor,
                    playerIconRes = repeatMode.transportIconRes(),
                    isPlayerToggleOn = repeatMode != RepeatMode.OFF,
                    lyricsIconRes = R.drawable.close,
                    lyricsContentDescription = stringResource(R.string.player_close),
                    isLyricsAccent = true,
                    onPlayerClick = onRepeat,
                    onLyricsClick = lyrics::close,
                )
            }
        }
    }
}

/**
 * A transport corner that is a player toggle - shuffle, repeat - and, while the [lyrics] are open in the player, one of
 * theirs in its place: the toggle's glyph shrinks away as the lyrics' grows in. [isLyricsAccent] lights it
 * for the way out of the lyrics, which turns into place as it comes.
 */
@Composable
private fun LyricsCornerButton(
    lyrics: PlayerLyricsState,
    size: Dp,
    radius: Dp,
    iconSize: Dp,
    contentColor: Color,
    @DrawableRes playerIconRes: Int,
    isPlayerToggleOn: Boolean,
    @DrawableRes lyricsIconRes: Int,
    lyricsContentDescription: String,
    isLyricsAccent: Boolean,
    onPlayerClick: () -> Unit,
    onLyricsClick: () -> Unit,
) {
    val isLyricsOpen = lyrics.isOpenInPlayer
    Surface(
        onClick = if (isLyricsOpen) onLyricsClick else onPlayerClick,
        shape = roundedShape(radius),
        color = contentColor.copy(alpha = if (isPlayerToggleOn) 0.2f else 0.08f),
        modifier = Modifier.size(size),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (isLyricsAccent) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = lyrics.phase(0.12f, 0.6f) }
                        .background(contentColor.copy(alpha = 0.2f)),
                )
            }
            Icon(
                painter = painterResource(playerIconRes),
                contentDescription = null,
                tint = contentColor.copy(alpha = if (isPlayerToggleOn) 1f else InactiveToggleAlpha),
                modifier =
                    Modifier
                        .size(iconSize)
                        .graphicsLayer {
                            val swap = lyrics.phase(0.12f, 0.6f)
                            alpha = 1f - swap
                            scaleX = lerp(1f, 0.6f, swap)
                            scaleY = scaleX
                        },
            )
            Icon(
                painter = painterResource(lyricsIconRes),
                contentDescription = if (isLyricsOpen) lyricsContentDescription else null,
                tint = contentColor,
                modifier =
                    Modifier
                        .size(iconSize)
                        .graphicsLayer {
                            val swap = lyrics.phase(0.12f, 0.6f)
                            alpha = swap
                            scaleX = lerp(0.6f, 1f, swap)
                            scaleY = scaleX
                            if (isLyricsAccent) rotationZ = lerp(-60f, 0f, swap)
                        },
            )
        }
    }
}
