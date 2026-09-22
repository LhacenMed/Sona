package com.lhacenmed.sona.feature.player

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhacenmed.sona.core.datastore.PlayerSliderStyle
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
