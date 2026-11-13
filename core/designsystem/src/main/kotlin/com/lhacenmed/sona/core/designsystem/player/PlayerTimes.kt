package com.lhacenmed.sona.core.designsystem.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import com.lhacenmed.sona.core.designsystem.R
import com.lhacenmed.sona.core.designsystem.theme.SonaComponentStyle
import com.lhacenmed.sona.core.designsystem.theme.roundedShape
import java.util.Locale

private const val Minus = "−"

/** "m:ss", or "h:mm:ss" from the hour on - how every player says a time. */
fun formatPlaybackTime(timeMs: Long): String {
    val totalSeconds = timeMs.coerceAtLeast(0L) / 1000
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

/**
 * The times beneath a player's seek bar, in line with its ends: where it is at the start, and at the end how much is left
 * or - [showsTotalDuration] - how long it is all told, which a press on it changes between, [onToggleEndTime].
 */
@Composable
fun PlayerTimes(
    positionMs: Long,
    durationMs: Long,
    showsTotalDuration: Boolean,
    onToggleEndTime: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        PlayerElapsedTime(positionMs, color, Modifier.align(Alignment.CenterStart))
        PlayerEndTime(positionMs, durationMs, showsTotalDuration, onToggleEndTime, color, Modifier.align(Alignment.CenterEnd))
    }
}

/** Where playback is - or a scrub has reached. */
@Composable
fun PlayerElapsedTime(positionMs: Long, color: Color, modifier: Modifier = Modifier) {
    Text(text = formatPlaybackTime(positionMs), style = playerTimeStyle(), color = color, maxLines = 1, modifier = modifier)
}

/**
 * The time at the seek bar's end - what is left from [positionMs], "−1:26", or, [showsTotalDuration], the whole of
 * [durationMs] - in [color], quieter. A press changes which, [onToggle] - its press shown [PressMargin] round the text,
 * clear of it, the text standing where it would without it.
 */
@Composable
fun PlayerEndTime(
    positionMs: Long,
    durationMs: Long,
    showsTotalDuration: Boolean,
    onToggle: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val text = when {
        durationMs <= 0L -> ""
        showsTotalDuration -> formatPlaybackTime(durationMs)
        else -> Minus + formatPlaybackTime(durationMs - positionMs)
    }
    Text(
        text = text,
        style = playerTimeStyle(),
        color = color.copy(alpha = PlayerControls.SecondaryAlpha),
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = modifier
            .outset(PressMargin)
            .clip(roundedShape(SonaComponentStyle.PressedCornerRadius))
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(
                    if (showsTotalDuration) R.string.player_control_show_remaining else R.string.player_control_show_duration,
                ),
                onClick = onToggle,
            )
            .padding(PressMargin),
    )
}

/**
 * As wide as a time of a [durationMs]-long track ever gets - [isEndTime], with the end's "−" - for a time that stands
 * beside other controls to keep them where they are as it counts: its figures are all one width, and none has more of
 * them than the length itself.
 */
@Composable
fun playbackTimeWidth(durationMs: Long, isEndTime: Boolean): Dp {
    val measurer = rememberTextMeasurer()
    val style = playerTimeStyle()
    val widest = (if (isEndTime) Minus else "") + formatPlaybackTime(durationMs)
    val widthPx = remember(measurer, widest, style) { measurer.measure(widest, style, maxLines = 1).size.width }
    return with(LocalDensity.current) { widthPx.toDp() }
}

/** How far a press on the end time shows round its text. */
private val PressMargin = 6.dp

/**
 * What follows laid out [margin] larger all round than this says it is: drawn, and pressed, past its own bounds - its
 * neighbours laid out as if the margin were not there.
 */
private fun Modifier.outset(margin: Dp): Modifier = layout { measurable, constraints ->
    val marginPx = margin.roundToPx()
    val placeable = measurable.measure(constraints.offset(marginPx * 2, marginPx * 2))
    layout(placeable.width - marginPx * 2, placeable.height - marginPx * 2) {
        placeable.place(-marginPx, -marginPx)
    }
}

@Composable
private fun playerTimeStyle(): TextStyle =
    MaterialTheme.typography.labelMedium.copy(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = "tnum",
    )
