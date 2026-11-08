@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.player

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhacenmed.sona.core.datastore.PlayerSliderStyle
import com.lhacenmed.sona.core.designsystem.component.SonaMarquee
import com.lhacenmed.sona.core.designsystem.icon.PlayerIcons
import com.lhacenmed.sona.core.designsystem.theme.iconButtonPressShapes
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

/** The expanded player's play button glyph - the mini player's is [playPauseIconRes]. */
internal fun PlaybackUiState.playPauseIcon(): ImageVector =
    when {
        hasEnded -> PlayerIcons.Replay
        isPlaying -> PlayerIcons.Pause
        else -> PlayerIcons.Play
    }

/**
 * How faint a toggle that is off - shuffle, repeat - is drawn beside the controls, in the player and its widgets: as quiet
 * as the player's secondary text. On, it is drawn whole: the glyph stays the same, so the toggle never changes size as it
 * is turned on.
 */
const val InactiveToggleAlpha = PlayerSecondaryAlpha

/** The repeat glyph of a transport row, with Sona's stop-after-current mode drawn as the notification draws it. */
@DrawableRes
fun RepeatMode.transportIconRes(): Int =
    when (this) {
        RepeatMode.OFF, RepeatMode.ALL -> PlaybackR.drawable.repeat
        RepeatMode.ONE -> R.drawable.repeat_one
        RepeatMode.STOP_AFTER_CURRENT -> PlaybackR.drawable.repeat_one_stop
    }

/** [transportIconRes] in the players' own icon set, for the expanded player. */
internal fun RepeatMode.transportIcon(): ImageVector =
    when (this) {
        RepeatMode.OFF, RepeatMode.ALL -> PlayerIcons.Repeat
        RepeatMode.ONE -> PlayerIcons.RepeatOne
        RepeatMode.STOP_AFTER_CURRENT -> PlayerIcons.RepeatStop
    }

@DrawableRes
internal fun favoriteIconRes(isFavorite: Boolean): Int =
    if (isFavorite) PlaybackR.drawable.favorite else PlaybackR.drawable.favorite_border

internal fun favoriteIcon(isFavorite: Boolean): ImageVector =
    if (isFavorite) PlayerIcons.Favorite else PlayerIcons.FavoriteBorder

/**
 * A control of the player's bars: Material's icon button, pressing as every press in the app does, its glyph
 * [PlayerBarIconSize] within a [PlayerBarButtonSize] target. [isLit] marks one that is on - a toggle, or the view it
 * opens - with a dot beneath its glyph, in its [color].
 */
@Composable
internal fun PlayerBarButton(
    icon: ImageVector,
    contentDescription: String?,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLit: Boolean = false,
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(PlayerBarButtonSize)) {
        IconButton(
            onClick = onClick,
            shapes = iconButtonPressShapes(),
            enabled = enabled,
            colors = IconButtonDefaults.iconButtonColors(contentColor = color, disabledContentColor = color.copy(alpha = 0.38f)),
            modifier = Modifier.matchParentSize(),
        ) {
            Icon(imageVector = icon, contentDescription = contentDescription, modifier = Modifier.size(PlayerBarIconSize))
        }
        if (isLit) PlayerLitDot(color, Modifier.align(Alignment.BottomCenter))
    }
}

/** The dot beneath a control that is on. */
@Composable
internal fun PlayerLitDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(bottom = PlayerLitDotInset)
            .size(PlayerLitDotSize)
            .background(color, CircleShape),
    )
}

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

/** The title over the artist line, the artist as quiet as the player's secondary text. */
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
        SonaMarquee(
            modifier =
                Modifier.combinedClickable(
                    enabled = true,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = titleActions.onTitleClick,
                    onLongClick = titleActions.onCopyTitle,
                ),
        ) {
            PlayerTitleText(
                title = title,
                color = textBackgroundColor,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }

    Spacer(Modifier.height(2.dp))

    ClickableArtist(
        artist = track.artist,
        creditedArtists = titleActions.creditedArtists,
        onClick = { titleActions.onArtistClick(track.artistId) },
        onCreditedArtistClick = titleActions.onArtistClick,
        style =
            MaterialTheme.typography.bodyLarge.copy(
                color = textBackgroundColor.copy(alpha = PlayerSecondaryAlpha),
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
            ),
        onLongClick = titleActions.onCopyArtists,
        modifier = Modifier.fillMaxWidth(),
    )
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
        modifier =
            Modifier
                .padding(horizontal = PlayerHorizontalPadding)
                .height(PlayerSeekBarHeight),
    )
}

/**
 * A seek bar in [sliderStyle] - the player's own, the mini player's, and each preview choosing between the styles: Material's
 * slider with its upright thumb, the track kept clear of it either side, flat or waving along the part played while it plays.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val colors = playerSliderColors(activeColor)
    val interactionSource = remember { MutableInteractionSource() }
    when (sliderStyle) {
        PlayerSliderStyle.STANDARD -> {
            Slider(
                value = value,
                onValueChange = onValueChange,
                modifier = modifier,
                onValueChangeFinished = onValueChangeFinished,
                colors = colors,
                interactionSource = interactionSource,
                valueRange = valueRange,
                thumb = { SliderDefaults.Thumb(interactionSource = interactionSource, colors = colors, thumbSize = PlayerSeekThumbSize) },
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        modifier = Modifier.height(PlayerSeekTrackHeight),
                        colors = colors,
                        drawStopIndicator = null,
                        thumbTrackGapSize = PlayerSeekThumbGap,
                    )
                },
            )
        }

        PlayerSliderStyle.CIRCULAR -> {
            SquigglySlider(
                value = value,
                valueRange = valueRange,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                colors = colors,
                interactionSource = interactionSource,
                modifier = modifier,
                squigglesSpec =
                    SquigglesSpec(
                        strokeWidth = PlayerSeekTrackHeight,
                        wavelength = PlayerSeekWavelength,
                        amplitude = if (isPlaying) PlayerSeekAmplitude else 0.dp,
                    ),
            )
        }
    }
}

/** Where playback is, and how much is left of the track from there - in line with the seek bar's ends. */
@Composable
internal fun PlayerTimeLabel(
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    textBackgroundColor: Color,
    modifier: Modifier = Modifier,
) {
    val shownPosition = sliderPosition ?: position
    val style =
        MaterialTheme.typography.labelMedium.copy(
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            fontFeatureSettings = "tnum",
        )
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = PlayerHorizontalPadding),
    ) {
        Text(
            text = makeTimeString(shownPosition),
            style = style,
            color = textBackgroundColor,
            maxLines = 1,
            modifier = Modifier.align(Alignment.CenterStart),
        )

        Text(
            text = if (duration > 0L) makeRemainingTimeString(duration - shownPosition) else "",
            style = style,
            color = textBackgroundColor.copy(alpha = PlayerSecondaryAlpha),
            maxLines = 1,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}
