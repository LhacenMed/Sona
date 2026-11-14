@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.PlaybackUiState

/** The play button's glyph, the expanded player's and the mini player's alike. */
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

internal fun favoriteIcon(isFavorite: Boolean): ImageVector =
    if (isFavorite) PlayerIcons.Favorite else PlayerIcons.FavoriteBorder

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
            text = title,
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
