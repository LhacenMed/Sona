@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.player

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhacenmed.sona.core.datastore.PlayerSliderStyle
import com.lhacenmed.sona.core.datastore.PlayerStyle
import com.lhacenmed.sona.core.designsystem.component.SonaCoverImage
import com.lhacenmed.sona.core.model.RepeatMode
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.PlaybackUiState
import com.lhacenmed.sona.feature.playback.R as PlaybackR

@DrawableRes
internal fun PlaybackUiState.playPauseIconRes(): Int =
    when {
        hasEnded -> R.drawable.replay
        isPlaying -> R.drawable.pause
        else -> R.drawable.play
    }

/** The repeat glyph of a transport row, with Sona's stop-after-current mode drawn as the notification draws it. */
@DrawableRes
internal fun RepeatMode.transportIconRes(): Int =
    when (this) {
        RepeatMode.OFF, RepeatMode.ALL -> PlaybackR.drawable.repeat
        RepeatMode.ONE -> R.drawable.repeat_one
        RepeatMode.STOP_AFTER_CURRENT -> PlaybackR.drawable.repeat_one_stop
    }

@DrawableRes
internal fun favoriteIconRes(isFavorite: Boolean): Int =
    if (isFavorite) PlaybackR.drawable.favorite else PlaybackR.drawable.favorite_border

@Composable
internal fun PlayerTitleText(
    title: String,
    color: Color,
    style: TextStyle,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
) {
    Text(
        text = title,
        color = color,
        style = style,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
internal fun PlayerTitleSection(
    track: Track,
    textBackgroundColor: Color,
    titleActions: PlayerTitleActions,
) {
    AnimatedContent(
        targetState = track.title,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "",
    ) { title ->
        PlayerTitleText(
            title = title,
            color = textBackgroundColor,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier =
                Modifier
                    .basicMarquee()
                    .combinedClickable(
                        enabled = true,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = titleActions.onTitleClick,
                        onLongClick = titleActions.onCopyTitle,
                    ),
        )
    }

    Spacer(Modifier.height(6.dp))

    ClickableArtist(
        artist = track.artist,
        onArtistClick = titleActions.onArtistClick,
        style = MaterialTheme.typography.titleMedium.copy(color = textBackgroundColor, fontSize = 16.sp),
        onLongClick = titleActions.onCopyArtists,
        modifier =
            Modifier
                .fillMaxWidth()
                .basicMarquee()
                .padding(end = 12.dp),
    )
}

@Composable
internal fun PlayerTopActions(
    track: Track,
    playerStyle: PlayerStyle,
    textButtonColor: Color,
    iconButtonColor: Color,
    textBackgroundColor: Color,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onMenuClick: () -> Unit,
) {
    val context = LocalContext.current

    when (playerStyle) {
        PlayerStyle.MINIMAL -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { context.shareTrack(track) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.share),
                        contentDescription = null,
                        tint = textBackgroundColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp),
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onToggleFavorite),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(favoriteIconRes(isFavorite)),
                        contentDescription = null,
                        tint =
                            if (isFavorite) {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                            } else {
                                textBackgroundColor.copy(alpha = 0.7f)
                            },
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        PlayerStyle.CINEMATIC -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = { context.shareTrack(track) },
                    shape = RoundedCornerShape(14.dp),
                    color = textBackgroundColor.copy(alpha = 0.12f),
                    modifier =
                        Modifier
                            .height(44.dp)
                            .width(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            painter = painterResource(R.drawable.share),
                            contentDescription = null,
                            tint = textBackgroundColor,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Surface(
                    onClick = onToggleFavorite,
                    shape = RoundedCornerShape(14.dp),
                    color =
                        if (isFavorite) {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.25f)
                        } else {
                            textBackgroundColor.copy(alpha = 0.12f)
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
                            tint = if (isFavorite) MaterialTheme.colorScheme.error else textBackgroundColor,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                // More menu button - cinematic glass card
                Surface(
                    onClick = onMenuClick,
                    shape = RoundedCornerShape(14.dp),
                    color = textBackgroundColor.copy(alpha = 0.12f),
                    modifier =
                        Modifier
                            .height(44.dp)
                            .width(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            painter = painterResource(R.drawable.more_horiz),
                            contentDescription = null,
                            tint = textBackgroundColor,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }

        PlayerStyle.CLASSIC -> {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(textButtonColor)
                        .clickable { context.shareTrack(track) },
            ) {
                Image(
                    painter = painterResource(R.drawable.share),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(iconButtonColor),
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .size(24.dp),
                )
            }

            Spacer(modifier = Modifier.size(12.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(textButtonColor)
                        .clickable(onClick = onMenuClick),
            ) {
                Image(
                    painter = painterResource(R.drawable.more_horiz),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(iconButtonColor),
                )
            }
        }

        PlayerStyle.IMMERSIVE, PlayerStyle.IMMERSIVE_EXTENDED, PlayerStyle.EDITORIAL -> {
            Unit
        }
    }
}

@Composable
internal fun PlayerSlider(
    sliderStyle: PlayerSliderStyle,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    textButtonColor: Color,
    onValueChange: (Long) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    val safeDuration = if (duration <= 0L) 0f else duration.toFloat()
    val safeValue = (sliderPosition ?: position).toFloat().coerceIn(0f, maxOf(0f, safeDuration))

    StyledPlaybackSlider(
        sliderStyle = sliderStyle,
        value = safeValue,
        valueRange = 0f..maxOf(1f, safeDuration),
        onValueChange = { onValueChange(it.toLong()) },
        onValueChangeFinished = onValueChangeFinished,
        activeColor = textButtonColor,
        isPlaying = isPlaying,
        modifier = Modifier.padding(horizontal = PlayerHorizontalPadding),
    )
}

/** A seek bar in [sliderStyle]: the player's own, and each preview choosing between the styles. Ported from ArchiveTune. */
@Composable
fun StyledPlaybackSlider(
    sliderStyle: PlayerSliderStyle,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    activeColor: Color,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    when (sliderStyle) {
        PlayerSliderStyle.STANDARD -> {
            Slider(
                value = value,
                valueRange = valueRange,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                colors = playerSliderColors(activeColor),
                modifier = modifier,
            )
        }

        PlayerSliderStyle.CIRCULAR -> {
            SquigglySlider(
                value = value,
                valueRange = valueRange,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                colors = playerSliderColors(activeColor),
                modifier = modifier,
                squigglesSpec =
                    SquigglesSpec(
                        amplitude = if (isPlaying) 2.dp else 0.dp,
                        strokeWidth = 6.dp,
                    ),
            )
        }
    }
}

@Composable
internal fun PlayerTimeLabel(
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    textBackgroundColor: Color,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = PlayerHorizontalPadding + 4.dp),
    ) {
        Text(
            text = makeTimeString(sliderPosition ?: position),
            style = MaterialTheme.typography.labelMedium,
            color = textBackgroundColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.CenterStart),
        )

        Text(
            text = makeTimeString(duration),
            style = MaterialTheme.typography.labelMedium,
            color = textBackgroundColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun ResizableIconButton(
    @DrawableRes icon: Int,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Image(
        painter = painterResource(icon),
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier =
            modifier
                .clickable(
                    indication = ripple(bounded = false),
                    interactionSource = remember { MutableInteractionSource() },
                    enabled = enabled,
                    onClick = onClick,
                ).alpha(if (enabled) 1f else 0.5f),
    )
}

@Composable
internal fun PlayerPlaybackControls(
    playerStyle: PlayerStyle,
    playback: PlaybackUiState,
    isLoading: Boolean,
    textButtonColor: Color,
    iconButtonColor: Color,
    textBackgroundColor: Color,
    icBackgroundColor: Color,
    playPauseRoundness: Dp,
    isFavorite: Boolean,
    viewModel: PlayerViewModel,
) {
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val shuffleEnabled = playback.shuffleEnabled
    val repeatMode = playback.repeatMode
    val canSkipPrevious = playback.canSkipPrevious
    val canSkipNext = playback.canSkipNext

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

    val cinematicPlayPauseCorner by animateDpAsState(
        targetValue = if (playback.isPlaying) 28.dp else 44.dp,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "cinematicPlayPauseCorner",
    )

    when (playerStyle) {
        PlayerStyle.MINIMAL -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PlayerHorizontalPadding),
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onShuffle),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(PlaybackR.drawable.shuffle),
                            contentDescription = null,
                            tint = textBackgroundColor.copy(alpha = if (shuffleEnabled) 1f else 0.4f),
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    Box(
                        modifier =
                            Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(textBackgroundColor.copy(alpha = 0.08f))
                                .clickable(enabled = canSkipPrevious, onClick = onPrevious),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.skip_previous),
                            contentDescription = null,
                            tint = textBackgroundColor.copy(alpha = if (canSkipPrevious) 0.9f else 0.4f),
                            modifier = Modifier.size(26.dp),
                        )
                    }

                    Box(
                        modifier =
                            Modifier
                                .size(70.dp)
                                .clip(RoundedCornerShape(50))
                                .background(textBackgroundColor)
                                .clickable(onClick = onPlayPause),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isLoading) {
                            CircularWavyProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = icBackgroundColor,
                            )
                        } else {
                            Icon(
                                painter = painterResource(playback.playPauseIconRes()),
                                contentDescription = null,
                                tint = icBackgroundColor,
                                modifier = Modifier.size(34.dp),
                            )
                        }
                    }

                    Box(
                        modifier =
                            Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(textBackgroundColor.copy(alpha = 0.08f))
                                .clickable(enabled = canSkipNext, onClick = onNext),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.skip_next),
                            contentDescription = null,
                            tint = textBackgroundColor.copy(alpha = if (canSkipNext) 0.9f else 0.4f),
                            modifier = Modifier.size(26.dp),
                        )
                    }

                    Box(
                        modifier =
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onRepeat),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(repeatMode.transportIconRes()),
                            contentDescription = null,
                            tint = textBackgroundColor.copy(alpha = if (repeatMode == RepeatMode.OFF) 0.4f else 1f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }

        PlayerStyle.CINEMATIC -> {
            BoxWithConstraints(
                modifier =
                    Modifier
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
                        Surface(
                            onClick = onShuffle,
                            shape = RoundedCornerShape(smallRadius),
                            color = textBackgroundColor.copy(alpha = if (shuffleEnabled) 0.2f else 0.08f),
                            modifier = Modifier.size(small),
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(PlaybackR.drawable.shuffle),
                                    contentDescription = null,
                                    tint = textBackgroundColor.copy(alpha = if (shuffleEnabled) 1f else 0.6f),
                                    modifier = Modifier.size(smallIcon),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(gap))

                        Surface(
                            onClick = onPrevious,
                            enabled = canSkipPrevious,
                            shape = RoundedCornerShape(largeRadius),
                            color = textBackgroundColor.copy(alpha = 0.15f),
                            modifier = Modifier.size(large),
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.skip_previous),
                                    contentDescription = null,
                                    tint = textBackgroundColor.copy(alpha = if (canSkipPrevious) 1f else 0.4f),
                                    modifier = Modifier.size(largeIcon),
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = onPlayPause,
                        shape = RoundedCornerShape(cinematicPlayPauseCorner),
                        color = textButtonColor,
                        modifier =
                            Modifier
                                .padding(horizontal = 20.dp)
                                .size(88.dp),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isLoading) {
                                CircularWavyProgressIndicator(
                                    modifier = Modifier.size(40.dp),
                                    color = icBackgroundColor,
                                )
                            } else {
                                Icon(
                                    painter = painterResource(playback.playPauseIconRes()),
                                    contentDescription = null,
                                    tint = icBackgroundColor,
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
                            enabled = canSkipNext,
                            shape = RoundedCornerShape(largeRadius),
                            color = textBackgroundColor.copy(alpha = 0.15f),
                            modifier = Modifier.size(large),
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.skip_next),
                                    contentDescription = null,
                                    tint = textBackgroundColor.copy(alpha = if (canSkipNext) 1f else 0.4f),
                                    modifier = Modifier.size(largeIcon),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(gap))

                        Surface(
                            onClick = onRepeat,
                            shape = RoundedCornerShape(smallRadius),
                            color = textBackgroundColor.copy(alpha = if (repeatMode != RepeatMode.OFF) 0.2f else 0.08f),
                            modifier = Modifier.size(small),
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(repeatMode.transportIconRes()),
                                    contentDescription = null,
                                    tint = textBackgroundColor.copy(alpha = if (repeatMode == RepeatMode.OFF) 0.6f else 1f),
                                    modifier = Modifier.size(smallIcon),
                                )
                            }
                        }
                    }
                }
            }
        }

        PlayerStyle.CLASSIC -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PlayerHorizontalPadding),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    ResizableIconButton(
                        icon = repeatMode.transportIconRes(),
                        color = textBackgroundColor,
                        modifier =
                            Modifier
                                .size(32.dp)
                                .padding(4.dp)
                                .align(Alignment.Center)
                                .alpha(if (repeatMode == RepeatMode.OFF) 0.5f else 1f),
                        onClick = onRepeat,
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    ResizableIconButton(
                        icon = R.drawable.skip_previous,
                        enabled = canSkipPrevious,
                        color = textBackgroundColor,
                        modifier =
                            Modifier
                                .size(32.dp)
                                .align(Alignment.Center),
                        onClick = onPrevious,
                    )
                }

                Spacer(Modifier.width(8.dp))

                Box(
                    modifier =
                        Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(playPauseRoundness))
                            .background(textButtonColor)
                            .clickable(onClick = onPlayPause),
                ) {
                    if (isLoading) {
                        CircularWavyProgressIndicator(
                            modifier =
                                Modifier
                                    .align(Alignment.Center)
                                    .size(36.dp),
                            color = iconButtonColor,
                        )
                    } else {
                        Image(
                            painter = painterResource(playback.playPauseIconRes()),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(iconButtonColor),
                            modifier =
                                Modifier
                                    .align(Alignment.Center)
                                    .size(36.dp),
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Box(modifier = Modifier.weight(1f)) {
                    ResizableIconButton(
                        icon = R.drawable.skip_next,
                        enabled = canSkipNext,
                        color = textBackgroundColor,
                        modifier =
                            Modifier
                                .size(32.dp)
                                .align(Alignment.Center),
                        onClick = onNext,
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    ResizableIconButton(
                        icon = favoriteIconRes(isFavorite),
                        color = if (isFavorite) MaterialTheme.colorScheme.error else textBackgroundColor,
                        modifier =
                            Modifier
                                .size(32.dp)
                                .padding(4.dp)
                                .align(Alignment.Center),
                        onClick = viewModel::onToggleFavorite,
                    )
                }
            }
        }

        PlayerStyle.IMMERSIVE, PlayerStyle.IMMERSIVE_EXTENDED, PlayerStyle.EDITORIAL -> {
            Unit
        }
    }
}

/** The Classic, Minimal and Cinematic players' title, seek bar, times and transport. Ported from ArchiveTune. */
@Composable
internal fun PlayerControlsContent(
    track: Track,
    playerStyle: PlayerStyle,
    sliderStyle: PlayerSliderStyle,
    playback: PlaybackUiState,
    isLoading: Boolean,
    isFavorite: Boolean,
    textButtonColor: Color,
    iconButtonColor: Color,
    textBackgroundColor: Color,
    icBackgroundColor: Color,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    titleActions: PlayerTitleActions,
    onMenuClick: () -> Unit,
    viewModel: PlayerViewModel,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
) {
    val playPauseRoundness by animateDpAsState(
        targetValue = if (playback.isPlaying) 24.dp else 36.dp,
        animationSpec = tween(durationMillis = 90, easing = LinearEasing),
        label = "playPauseRoundness",
    )

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = PlayerHorizontalPadding),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            PlayerTitleSection(
                track = track,
                textBackgroundColor = textBackgroundColor,
                titleActions = titleActions,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        PlayerTopActions(
            track = track,
            playerStyle = playerStyle,
            textButtonColor = textButtonColor,
            iconButtonColor = iconButtonColor,
            textBackgroundColor = textBackgroundColor,
            isFavorite = isFavorite,
            onToggleFavorite = viewModel::onToggleFavorite,
            onMenuClick = onMenuClick,
        )
    }

    Spacer(Modifier.height(12.dp))

    PlayerSlider(
        sliderStyle = sliderStyle,
        sliderPosition = sliderPosition,
        position = position,
        duration = duration,
        isPlaying = playback.isPlaying,
        textButtonColor = textButtonColor,
        onValueChange = onSliderValueChange,
        onValueChangeFinished = onSliderValueChangeFinished,
    )

    Spacer(Modifier.height(4.dp))

    PlayerTimeLabel(
        sliderPosition = sliderPosition,
        position = position,
        duration = duration,
        textBackgroundColor = textBackgroundColor,
    )

    Spacer(Modifier.height(12.dp))

    PlayerPlaybackControls(
        playerStyle = playerStyle,
        playback = playback,
        isLoading = isLoading,
        textButtonColor = textButtonColor,
        iconButtonColor = iconButtonColor,
        textBackgroundColor = textBackgroundColor,
        icBackgroundColor = icBackgroundColor,
        playPauseRoundness = playPauseRoundness,
        isFavorite = isFavorite,
        viewModel = viewModel,
    )
}

/** The Immersive player's controls, laid over its backdrop. Ported from ArchiveTune's `V8PlayerControlsContent`. */
@Composable
internal fun ImmersiveControlsContent(
    track: Track,
    playback: PlaybackUiState,
    isLoading: Boolean,
    isFavorite: Boolean,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    volume: Float,
    titleActions: PlayerTitleActions,
    onMenuClick: () -> Unit,
    viewModel: PlayerViewModel,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    landscape: Boolean,
) {
    val foreground = Color.White
    val secondaryForeground = foreground.copy(alpha = 0.72f)

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val horizontalPadding =
            if (landscape) {
                36.dp
            } else if (maxWidth < 380.dp) {
                22.dp
            } else {
                24.dp
            }
        val contentGap = if (landscape) 14.dp else 18.dp
        val progressToTransportGap = if (landscape) 12.dp else 18.dp
        val transportToVolumeGap = if (landscape) 12.dp else 18.dp

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ImmersiveMetadataActions(
                track = track,
                isFavorite = isFavorite,
                foreground = foreground,
                titleActions = titleActions,
                onMenuClick = onMenuClick,
                onToggleFavorite = viewModel::onToggleFavorite,
            )

            Spacer(Modifier.height(contentGap))

            ImmersivePlaybackProgress(
                sliderPosition = sliderPosition,
                position = position,
                duration = duration,
                foreground = foreground,
                onSliderValueChange = onSliderValueChange,
                onSliderValueChangeFinished = onSliderValueChangeFinished,
            )

            Spacer(Modifier.height(progressToTransportGap))

            ImmersiveTransportControls(
                playback = playback,
                isLoading = isLoading,
                foreground = foreground,
                viewModel = viewModel,
            )

            Spacer(Modifier.height(transportToVolumeGap))

            ImmersiveVolumeControls(
                volume = volume,
                foreground = foreground,
                secondaryForeground = secondaryForeground,
                onVolumeChange = onVolumeChange,
            )
        }
    }
}

/** The Immersive Extended player: header, artwork and controls over its backdrop. Ported from ArchiveTune's `V8PlayerContent`. */
@Composable
internal fun ImmersiveExtendedPlayerContent(
    track: Track,
    playback: PlaybackUiState,
    isLoading: Boolean,
    isFavorite: Boolean,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    volume: Float,
    titleActions: PlayerTitleActions,
    onMenuClick: () -> Unit,
    viewModel: PlayerViewModel,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    landscape: Boolean,
    modifier: Modifier = Modifier,
) {
    val foreground = Color.White
    val secondaryForeground = foreground.copy(alpha = 0.72f)

    if (landscape) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val horizontalPadding = 36.dp
            val contentGap = 36.dp
            val artworkSize =
                (maxHeight - 48.dp)
                    .coerceAtMost((maxWidth - horizontalPadding * 2 - contentGap) * 0.44f)
                    .coerceAtLeast(0.dp)

            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = horizontalPadding, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(contentGap),
            ) {
                ImmersiveArtwork(
                    coverArtUri = track.coverArtUri,
                    size = artworkSize,
                )

                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .heightIn(min = 320.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ImmersiveHeader(
                        subtitle = track.album,
                        foreground = foreground,
                        secondaryForeground = secondaryForeground,
                    )

                    Spacer(Modifier.height(22.dp))

                    ImmersiveMetadataActions(
                        track = track,
                        isFavorite = isFavorite,
                        foreground = foreground,
                        titleActions = titleActions,
                        onMenuClick = onMenuClick,
                        onToggleFavorite = viewModel::onToggleFavorite,
                    )

                    Spacer(Modifier.height(18.dp))

                    ImmersivePlaybackProgress(
                        sliderPosition = sliderPosition,
                        position = position,
                        duration = duration,
                        foreground = foreground,
                        onSliderValueChange = onSliderValueChange,
                        onSliderValueChangeFinished = onSliderValueChangeFinished,
                    )

                    Spacer(Modifier.height(18.dp))

                    ImmersiveTransportControls(
                        playback = playback,
                        isLoading = isLoading,
                        foreground = foreground,
                        viewModel = viewModel,
                    )

                    Spacer(Modifier.height(18.dp))

                    ImmersiveVolumeControls(
                        volume = volume,
                        foreground = foreground,
                        secondaryForeground = secondaryForeground,
                        onVolumeChange = onVolumeChange,
                    )
                }
            }
        }
    } else {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val contentPadding = if (maxWidth < 380.dp) 22.dp else 24.dp
            val compactHeight = maxHeight < 760.dp
            val veryCompactHeight = maxHeight < 680.dp
            val headerTop = if (compactHeight) 6.dp else 14.dp
            val headerToArtwork =
                when {
                    veryCompactHeight -> 10.dp
                    compactHeight -> 14.dp
                    else -> 28.dp
                }
            val artworkToMetadata =
                when {
                    veryCompactHeight -> 12.dp
                    compactHeight -> 16.dp
                    else -> 28.dp
                }
            val controlsGap = if (compactHeight) 10.dp else 18.dp
            val progressToTransportGap = if (compactHeight) 8.dp else 18.dp
            val transportToVolumeGap = if (compactHeight) 8.dp else 18.dp
            val bottomGap = if (compactHeight) 8.dp else 16.dp
            val reservedControlsHeight =
                headerTop +
                    56.dp +
                    headerToArtwork +
                    artworkToMetadata +
                    58.dp +
                    controlsGap +
                    62.dp +
                    progressToTransportGap +
                    72.dp +
                    transportToVolumeGap + 30.dp +
                    bottomGap
            val maxArtworkSize =
                (maxWidth - contentPadding * 2)
                    .coerceAtMost(if (compactHeight) 360.dp else 420.dp)
            val artworkSize =
                maxArtworkSize
                    .coerceAtMost(maxHeight - reservedControlsHeight)
                    .coerceAtLeast(0.dp)

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = contentPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(headerTop))

                ImmersiveHeader(
                    subtitle = track.album,
                    foreground = foreground,
                    secondaryForeground = secondaryForeground,
                )

                Spacer(Modifier.height(headerToArtwork))
                Spacer(Modifier.weight(1f))

                ImmersiveArtwork(
                    coverArtUri = track.coverArtUri,
                    size = artworkSize,
                )

                Spacer(Modifier.height(artworkToMetadata))

                ImmersiveMetadataActions(
                    track = track,
                    isFavorite = isFavorite,
                    foreground = foreground,
                    titleActions = titleActions,
                    onMenuClick = onMenuClick,
                    onToggleFavorite = viewModel::onToggleFavorite,
                )

                Spacer(Modifier.height(controlsGap))

                ImmersivePlaybackProgress(
                    sliderPosition = sliderPosition,
                    position = position,
                    duration = duration,
                    foreground = foreground,
                    onSliderValueChange = onSliderValueChange,
                    onSliderValueChangeFinished = onSliderValueChangeFinished,
                )

                Spacer(Modifier.height(progressToTransportGap))

                ImmersiveTransportControls(
                    playback = playback,
                    isLoading = isLoading,
                    foreground = foreground,
                    viewModel = viewModel,
                )

                Spacer(Modifier.height(transportToVolumeGap))

                ImmersiveVolumeControls(
                    volume = volume,
                    foreground = foreground,
                    secondaryForeground = secondaryForeground,
                    onVolumeChange = onVolumeChange,
                )

                Spacer(Modifier.height(bottomGap))
            }
        }
    }
}

@Composable
private fun ImmersiveHeader(
    subtitle: String,
    foreground: Color,
    secondaryForeground: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.player_now_playing),
            style = MaterialTheme.typography.titleLarge,
            color = foreground,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.titleMedium,
            color = secondaryForeground,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .basicMarquee(),
        )
    }
}

@Composable
private fun ImmersiveArtwork(
    coverArtUri: String?,
    size: Dp,
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.08f)),
    ) {
        SonaCoverImage(
            coverArtUri = coverArtUri,
            contentDescription = null,
            cornerRadius = 8.dp,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ImmersiveMetadataActions(
    track: Track,
    isFavorite: Boolean,
    foreground: Color,
    titleActions: PlayerTitleActions,
    onMenuClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PlayerTitleText(
                title = track.title,
                color = foreground,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier
                        .basicMarquee()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = titleActions.onTitleClick,
                        ),
            )
            ClickableArtist(
                artist = track.artist,
                onArtistClick = titleActions.onArtistClick,
                style = MaterialTheme.typography.titleMedium,
                color = foreground,
                modifier = Modifier.basicMarquee(),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ImmersiveActionButton(
                iconRes = R.drawable.more_vert,
                contentDescription = stringResource(R.string.player_more_options),
                foreground = foreground,
                containerColor = foreground.copy(alpha = 0.16f),
                iconSize = 24.dp,
                onClick = onMenuClick,
            )
            ImmersiveActionButton(
                iconRes = favoriteIconRes(isFavorite),
                contentDescription = stringResource(R.string.player_like),
                foreground = foreground,
                containerColor = foreground.copy(alpha = 0.16f),
                iconSize = 26.dp,
                onClick = onToggleFavorite,
            )
        }
    }
}

@Composable
private fun ImmersiveActionButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    foreground: Color,
    containerColor: Color,
    iconSize: Dp,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        modifier = Modifier.size(48.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = foreground,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@Composable
private fun ImmersivePlaybackProgress(
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    foreground: Color,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
) {
    val safeDuration = if (duration <= 0L) 0f else duration.toFloat()
    val safeValue = (sliderPosition ?: position).toFloat().coerceIn(0f, safeDuration)

    Column(modifier = Modifier.fillMaxWidth()) {
        ImmersiveFlatSlider(
            value = safeValue,
            valueRange = 0f..safeDuration,
            activeColor = foreground.copy(alpha = 0.88f),
            inactiveColor = foreground.copy(alpha = 0.32f),
            trackHeight = 9.dp,
            onValueChange = { onSliderValueChange(it.toLong()) },
            onValueChangeFinished = onSliderValueChangeFinished,
            enabled = safeDuration > 0f,
            modifier = Modifier.fillMaxWidth(),
        )

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
        ) {
            Text(
                text = makeTimeString(sliderPosition ?: position),
                style = MaterialTheme.typography.labelMedium,
                color = foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.CenterStart),
            )

            Text(
                text = makeTimeString(duration),
                style = MaterialTheme.typography.labelMedium,
                color = foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun ImmersiveTransportControls(
    playback: PlaybackUiState,
    isLoading: Boolean,
    foreground: Color,
    viewModel: PlayerViewModel,
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ImmersiveTransportButton(
            iconRes = R.drawable.skip_previous,
            contentDescription = stringResource(R.string.player_previous),
            foreground = foreground,
            enabled = playback.canSkipPrevious,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.onSkipPrevious()
            },
        )

        Surface(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.onTogglePlayPause()
            },
            shape = CircleShape,
            color = Color.Transparent,
            modifier = Modifier.size(72.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (isLoading) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(44.dp),
                        color = foreground,
                    )
                } else {
                    Icon(
                        painter = painterResource(playback.playPauseIconRes()),
                        contentDescription =
                            stringResource(if (playback.isPlaying) R.string.player_pause else R.string.player_play),
                        tint = foreground,
                        modifier = Modifier.size(52.dp),
                    )
                }
            }
        }

        ImmersiveTransportButton(
            iconRes = R.drawable.skip_next,
            contentDescription = stringResource(R.string.player_next),
            foreground = foreground,
            enabled = playback.canSkipNext,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.onSkipNext()
            },
        )
    }
}

@Composable
private fun ImmersiveTransportButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    foreground: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.Transparent,
        modifier = Modifier.size(64.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = foreground.copy(alpha = if (enabled) 1f else 0.4f),
                modifier = Modifier.size(44.dp),
            )
        }
    }
}

@Composable
private fun ImmersiveVolumeControls(
    volume: Float,
    foreground: Color,
    secondaryForeground: Color,
    onVolumeChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.volume_off),
            contentDescription = stringResource(R.string.player_minimum_volume),
            tint = secondaryForeground,
            modifier = Modifier.size(22.dp),
        )
        ImmersiveFlatSlider(
            value = volume.coerceIn(0f, 1f),
            valueRange = 0f..1f,
            activeColor = foreground.copy(alpha = 0.86f),
            inactiveColor = foreground.copy(alpha = 0.24f),
            trackHeight = 8.dp,
            onValueChange = { onVolumeChange(it.coerceIn(0f, 1f)) },
            onValueChangeFinished = {},
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 18.dp),
        )
        Icon(
            painter = painterResource(R.drawable.volume_up),
            contentDescription = stringResource(R.string.player_maximum_volume),
            tint = secondaryForeground,
            modifier = Modifier.size(24.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImmersiveFlatSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    activeColor: Color,
    inactiveColor: Color,
    trackHeight: Dp,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val safeEnd = valueRange.endInclusive.coerceAtLeast(valueRange.start + 1f)
    val safeRange = valueRange.start..safeEnd
    val colors =
        SliderDefaults.colors(
            activeTrackColor = activeColor,
            activeTickColor = activeColor,
            thumbColor = Color.Transparent,
            inactiveTrackColor = inactiveColor,
        )

    Slider(
        value = value.coerceIn(safeRange),
        valueRange = safeRange,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        enabled = enabled,
        colors = colors,
        thumb = { Spacer(modifier = Modifier.size(0.dp)) },
        track = { sliderState ->
            PlayerSliderTrack(
                sliderState = sliderState,
                colors = colors,
                trackHeight = trackHeight,
            )
        },
        modifier = modifier.height(30.dp),
    )
}

/** The Editorial player, coloured from the cover. Ported from ArchiveTune's `V9PlayerContent`. */
@Composable
internal fun EditorialPlayerContent(
    track: Track,
    playback: PlaybackUiState,
    isFavorite: Boolean,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    textBackgroundColor: Color,
    textButtonColor: Color,
    iconButtonColor: Color,
    titleActions: PlayerTitleActions,
    onCollapseClick: () -> Unit,
    onQueueClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onMenuClick: () -> Unit,
    viewModel: PlayerViewModel,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    landscape: Boolean,
    modifier: Modifier = Modifier,
) {
    if (landscape) {
        EditorialLandscapeContent(
            track = track,
            playback = playback,
            sliderPosition = sliderPosition,
            position = position,
            duration = duration,
            textBackgroundColor = textBackgroundColor,
            textButtonColor = textButtonColor,
            iconButtonColor = iconButtonColor,
            titleActions = titleActions,
            onCollapseClick = onCollapseClick,
            onQueueClick = onQueueClick,
            onLyricsClick = onLyricsClick,
            onMenuClick = onMenuClick,
            viewModel = viewModel,
            onSliderValueChange = onSliderValueChange,
            onSliderValueChangeFinished = onSliderValueChangeFinished,
            modifier = modifier,
        )
    } else {
        EditorialPortraitContent(
            track = track,
            playback = playback,
            isFavorite = isFavorite,
            sliderPosition = sliderPosition,
            position = position,
            duration = duration,
            textBackgroundColor = textBackgroundColor,
            textButtonColor = textButtonColor,
            iconButtonColor = iconButtonColor,
            titleActions = titleActions,
            onCollapseClick = onCollapseClick,
            onQueueClick = onQueueClick,
            onLyricsClick = onLyricsClick,
            viewModel = viewModel,
            onSliderValueChange = onSliderValueChange,
            onSliderValueChangeFinished = onSliderValueChangeFinished,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditorialPortraitContent(
    track: Track,
    playback: PlaybackUiState,
    isFavorite: Boolean,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    textBackgroundColor: Color,
    textButtonColor: Color,
    iconButtonColor: Color,
    titleActions: PlayerTitleActions,
    onCollapseClick: () -> Unit,
    onQueueClick: () -> Unit,
    onLyricsClick: () -> Unit,
    viewModel: PlayerViewModel,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val horizontalPadding = if (maxWidth < 380.dp) 16.dp else 20.dp
        val compactHeight = maxHeight < 760.dp
        val veryCompactHeight = maxHeight < 700.dp

        val headerGap =
            when {
                veryCompactHeight -> 14.dp
                compactHeight -> 18.dp
                else -> 26.dp
            }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(if (compactHeight) 8.dp else 14.dp))

            EditorialHeader(
                textColor = textBackgroundColor,
                containerColor = textButtonColor.copy(alpha = 0.16f),
                iconColor = textBackgroundColor,
                onCollapseClick = onCollapseClick,
                onLyricsClick = onLyricsClick,
                onQueueClick = onQueueClick,
            )

            Spacer(Modifier.height(headerGap))

            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                EditorialArtwork(
                    coverArtUri = track.coverArtUri,
                    placeholderColor = textButtonColor.copy(alpha = 0.12f),
                    modifier = Modifier.aspectRatio(1f),
                )
            }

            Spacer(Modifier.height(headerGap))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PlayerTitleText(
                        title = track.title,
                        color = textBackgroundColor,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .basicMarquee()
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = titleActions.onTitleClick,
                                ),
                    )
                    ClickableArtist(
                        artist = track.artist,
                        onArtistClick = titleActions.onArtistClick,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = textBackgroundColor.copy(alpha = 0.72f),
                        textAlign = TextAlign.Start,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .basicMarquee(),
                    )
                }

                Spacer(Modifier.width(16.dp))

                IconButton(
                    onClick = viewModel::onToggleFavorite,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        painter = painterResource(favoriteIconRes(isFavorite)),
                        contentDescription = stringResource(if (isFavorite) R.string.player_remove_like else R.string.player_like),
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else textBackgroundColor,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            Spacer(Modifier.height(headerGap))

            EditorialPlaybackProgress(
                sliderPosition = sliderPosition,
                position = position,
                duration = duration,
                isPlaying = playback.isPlaying,
                activeColor = textButtonColor,
                inactiveColor = textButtonColor.copy(alpha = 0.24f),
                textColor = textBackgroundColor,
                onSliderValueChange = onSliderValueChange,
                onSliderValueChangeFinished = onSliderValueChangeFinished,
            )

            Spacer(Modifier.height(if (compactHeight) 16.dp else 24.dp))

            val motionScheme = remember { MotionScheme.expressive() }
            val controlSpatialSpec = remember { motionScheme.fastSpatialSpec<Float>() }
            EditorialPlaybackControls(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                isPlayingProvider = { playback.isPlaying },
                onPrevious = viewModel::onSkipPrevious,
                onPlayPause = viewModel::onTogglePlayPause,
                onNext = viewModel::onSkipNext,
                height = 80.dp,
                pressAnimationSpec = controlSpatialSpec,
                colorOtherButtons = textButtonColor.copy(alpha = 0.16f),
                colorPlayPause = textButtonColor,
                tintPlayPauseIcon = iconButtonColor,
                tintOtherIcons = textBackgroundColor,
            )

            Spacer(Modifier.height(if (compactHeight) 16.dp else 24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditorialLandscapeContent(
    track: Track,
    playback: PlaybackUiState,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    textBackgroundColor: Color,
    textButtonColor: Color,
    iconButtonColor: Color,
    titleActions: PlayerTitleActions,
    onCollapseClick: () -> Unit,
    onQueueClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onMenuClick: () -> Unit,
    viewModel: PlayerViewModel,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val artworkSize =
            (maxHeight * 0.74f)
                .coerceAtMost(maxWidth * 0.4f)
                .coerceAtLeast(236.dp)

        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            EditorialArtwork(
                coverArtUri = track.coverArtUri,
                size = artworkSize,
                placeholderColor = textButtonColor.copy(alpha = 0.12f),
            )

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EditorialHeader(
                    textColor = textBackgroundColor,
                    containerColor = textButtonColor.copy(alpha = 0.16f),
                    iconColor = textBackgroundColor,
                    onCollapseClick = onCollapseClick,
                    onLyricsClick = onLyricsClick,
                    onQueueClick = onQueueClick,
                )

                Spacer(Modifier.height(14.dp))

                EditorialMetadata(
                    track = track,
                    textColor = textBackgroundColor,
                    titleActions = titleActions,
                )

                Spacer(Modifier.height(12.dp))

                EditorialPlaybackProgress(
                    sliderPosition = sliderPosition,
                    position = position,
                    duration = duration,
                    isPlaying = playback.isPlaying,
                    activeColor = textButtonColor,
                    inactiveColor = textButtonColor.copy(alpha = 0.24f),
                    textColor = textBackgroundColor,
                    onSliderValueChange = onSliderValueChange,
                    onSliderValueChangeFinished = onSliderValueChangeFinished,
                )

                Spacer(Modifier.height(12.dp))

                val motionScheme = remember { MotionScheme.expressive() }
                val controlSpatialSpec = remember { motionScheme.fastSpatialSpec<Float>() }
                EditorialPlaybackControls(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    isPlayingProvider = { playback.isPlaying },
                    onPrevious = viewModel::onSkipPrevious,
                    onPlayPause = viewModel::onTogglePlayPause,
                    onNext = viewModel::onSkipNext,
                    height = 64.dp,
                    pressAnimationSpec = controlSpatialSpec,
                    colorOtherButtons = textButtonColor.copy(alpha = 0.14f),
                    colorPlayPause = textButtonColor,
                    tintPlayPauseIcon = iconButtonColor,
                    tintOtherIcons = textBackgroundColor,
                )

                Spacer(Modifier.height(10.dp))

                EditorialBottomToggleRow(
                    shuffleEnabled = playback.shuffleEnabled,
                    repeatMode = playback.repeatMode,
                    onShuffleClick = viewModel::onToggleShuffle,
                    onRepeatClick = viewModel::onCycleRepeatMode,
                    onMenuClick = onMenuClick,
                    activeColor = textButtonColor,
                    inactiveColor = textButtonColor.copy(alpha = 0.16f),
                    textColor = textBackgroundColor,
                    containerColor = textButtonColor.copy(alpha = 0.08f),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                )
            }
        }
    }
}

@Composable
private fun EditorialHeader(
    textColor: Color,
    containerColor: Color,
    iconColor: Color,
    onCollapseClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onQueueClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        EditorialHeaderButton(
            iconRes = R.drawable.expand_more,
            contentDescription = null,
            containerColor = containerColor,
            iconColor = iconColor,
            shape = CircleShape,
            onClick = onCollapseClick,
        )

        Text(
            text = stringResource(R.string.player_now_playing),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .weight(1f)
                    .basicMarquee(),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EditorialHeaderButton(
                iconRes = R.drawable.lyrics,
                contentDescription = stringResource(R.string.player_lyrics),
                containerColor = containerColor,
                iconColor = iconColor,
                shape = RoundedCornerShape(22.dp),
                onClick = onLyricsClick,
            )
            EditorialHeaderButton(
                iconRes = R.drawable.queue_music,
                contentDescription = stringResource(R.string.player_queue),
                containerColor = containerColor,
                iconColor = iconColor,
                shape = RoundedCornerShape(22.dp),
                onClick = onQueueClick,
            )
        }
    }
}

@Composable
private fun EditorialHeaderButton(
    @DrawableRes iconRes: Int,
    contentDescription: String?,
    containerColor: Color,
    iconColor: Color,
    shape: Shape,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = containerColor,
        modifier = Modifier.size(56.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = iconColor,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
private fun EditorialArtwork(
    coverArtUri: String?,
    placeholderColor: Color,
    modifier: Modifier = Modifier,
    size: Dp? = null,
) {
    Box(
        modifier =
            modifier
                .then(if (size != null) Modifier.size(size) else Modifier)
                .clip(RoundedCornerShape(36.dp))
                .background(placeholderColor),
    ) {
        SonaCoverImage(
            coverArtUri = coverArtUri,
            contentDescription = null,
            cornerRadius = 36.dp,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun EditorialMetadata(
    track: Track,
    textColor: Color,
    titleActions: PlayerTitleActions,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PlayerTitleText(
            title = track.title,
            color = textColor,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .basicMarquee()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = titleActions.onTitleClick,
                    ),
        )
        ClickableArtist(
            artist = track.artist,
            onArtistClick = titleActions.onArtistClick,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = textColor.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .basicMarquee(),
        )
    }
}

@Composable
private fun EditorialPlaybackProgress(
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    textColor: Color,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
) {
    val (smoothProgressFraction, displayedPosition) =
        rememberSmoothProgress(
            isPlayingProvider = { isPlaying },
            currentPositionProvider = { sliderPosition ?: position },
            totalDuration = duration.coerceAtLeast(0L),
        )

    Column(modifier = Modifier.fillMaxWidth()) {
        WavySliderExpressive(
            value = { smoothProgressFraction.value },
            onValueChange = { fraction ->
                onSliderValueChange((fraction * duration.coerceAtLeast(0L)).toLong())
            },
            onValueCommit = { onSliderValueChangeFinished() },
            enabled = duration > 0L,
            activeTrackColor = activeColor,
            inactiveTrackColor = inactiveColor,
            thumbColor = activeColor,
            isPlaying = isPlaying,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(36.dp),
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = makeTimeString(sliderPosition ?: displayedPosition.value),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = makeTimeString(duration),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EditorialBottomToggleRow(
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onMenuClick: () -> Unit,
    activeColor: Color,
    inactiveColor: Color,
    textColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier.background(
                color = containerColor,
                shape = RoundedCornerShape(60.dp),
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val commonModifier = Modifier.weight(1f)

            ToggleSegmentButton(
                modifier = commonModifier,
                active = shuffleEnabled,
                activeColor = activeColor,
                activeCornerRadius = 60.dp,
                activeContentColor = MaterialTheme.colorScheme.onPrimary,
                inactiveColor = inactiveColor,
                inactiveContentColor = textColor.copy(alpha = 0.6f),
                onClick = onShuffleClick,
                iconId = PlaybackR.drawable.shuffle,
                contentDesc = stringResource(R.string.player_shuffle),
            )

            ToggleSegmentButton(
                modifier = commonModifier,
                active = repeatMode != RepeatMode.OFF,
                activeColor = activeColor,
                activeCornerRadius = 60.dp,
                activeContentColor = MaterialTheme.colorScheme.onPrimary,
                inactiveColor = inactiveColor,
                inactiveContentColor = textColor.copy(alpha = 0.6f),
                onClick = onRepeatClick,
                iconId = repeatMode.transportIconRes(),
                contentDesc = stringResource(R.string.player_repeat_all),
            )

            ToggleSegmentButton(
                modifier = commonModifier,
                active = false,
                activeColor = activeColor,
                activeCornerRadius = 60.dp,
                activeContentColor = MaterialTheme.colorScheme.onPrimary,
                inactiveColor = inactiveColor,
                inactiveContentColor = textColor.copy(alpha = 0.6f),
                onClick = onMenuClick,
                iconId = R.drawable.more_vert,
                contentDesc = stringResource(R.string.player_more_options),
            )
        }
    }
}
