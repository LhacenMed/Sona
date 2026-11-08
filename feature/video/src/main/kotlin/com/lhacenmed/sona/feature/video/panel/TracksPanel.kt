@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.video.panel

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.datastore.SubtitleTextSize
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.feature.playback.MediaTrack
import com.lhacenmed.sona.feature.video.PlayerText
import com.lhacenmed.sona.feature.video.R
import com.lhacenmed.sona.feature.video.VideoPlayerTokens
import com.lhacenmed.sona.feature.video.VideoPlayerType
import com.lhacenmed.sona.core.designsystem.icon.PlayerIcons
import com.lhacenmed.sona.feature.video.displayName
import com.lhacenmed.sona.feature.video.formatSubtitleOffset
import com.lhacenmed.sona.feature.video.subtitle.SubtitleSource
import com.lhacenmed.sona.feature.video.subtitle.SubtitleUiState
import kotlinx.coroutines.delay

/**
 * What the video is heard and read in, side by side, and how its subtitles look and line up - each change seen at once
 * on the video beside it, which is why the panel stays open as a choice is made.
 *
 * Only a file's lines can be moved in time: the player plays a file's own tracks at their own times.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.TracksPanel(
    audioTracks: List<MediaTrack>,
    subtitles: SubtitleUiState,
    textSize: SubtitleTextSize,
    onSelectAudio: (MediaTrack) -> Unit,
    onSelectSubtitle: (SubtitleSource) -> Unit,
    onAddFile: () -> Unit,
    onTextSizeChange: (SubtitleTextSize) -> Unit,
    onOffsetChange: (Long) -> Unit,
    onClose: () -> Unit,
) {
    PanelHeader(stringResource(R.string.video_tracks), onClose)
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            PanelSectionTitle(stringResource(R.string.video_tracks_audio))
            audioTracks.forEachIndexed { index, track ->
                PanelChoiceRow(track.displayName(index), track.isSelected, { onSelectAudio(track) })
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            PanelSectionTitle(stringResource(R.string.video_tracks_subtitles))
            SubtitleChoices(subtitles, onSelectSubtitle)
            PanelActionRow(PlayerIcons.Add, stringResource(R.string.video_tracks_add_file), onAddFile, color = VideoPlayerTokens.TertiaryContent)
        }
    }
    PanelDivider()
    FlowRow(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 10.dp),
    ) {
        // Small on purpose, as the row is laid out: Material would otherwise widen each to its touch target.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            SizeControl(textSize, onTextSizeChange)
            SyncControl(subtitles.offsetMs, isEnabled = subtitles.source is SubtitleSource.FromFile, onOffsetChange)
        }
    }
}

@Composable
private fun SubtitleChoices(subtitles: SubtitleUiState, onSelect: (SubtitleSource) -> Unit) {
    val source = subtitles.source
    val isDefaultShowingNothing = source == SubtitleSource.PlayerDefault && subtitles.tracks.none { it.isSelected }
    PanelChoiceRow(
        label = stringResource(R.string.video_tracks_off),
        isChosen = source == SubtitleSource.Off || isDefaultShowingNothing,
        onClick = { onSelect(SubtitleSource.Off) },
    )
    (listOfNotNull(subtitles.attached) + subtitles.sideFiles).forEach { file ->
        PanelChoiceRow(file.name, (source as? SubtitleSource.FromFile)?.subtitle == file, { onSelect(SubtitleSource.FromFile(file)) })
    }
    subtitles.tracks.forEachIndexed { index, track ->
        val isShowing = when (source) {
            is SubtitleSource.Embedded -> source.track.groupIndex == track.groupIndex && source.track.trackIndex == track.trackIndex
            SubtitleSource.PlayerDefault -> track.isSelected
            else -> false
        }
        PanelChoiceRow(track.displayName(index), isShowing, { onSelect(SubtitleSource.Embedded(track)) })
    }
}

@Composable
private fun SizeControl(textSize: SubtitleTextSize, onChange: (SubtitleTextSize) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PlayerText(stringResource(R.string.video_subtitle_size), VideoPlayerType.PanelLabel, color = VideoPlayerTokens.SecondaryContent)
        PanelSegmentGroup {
            SubtitleTextSize.entries.forEach { size ->
                PanelSegmentButton(
                    label = stringResource(size.shortLabel),
                    isChosen = size == textSize,
                    onClick = { onChange(size) },
                )
            }
        }
    }
}

private val SubtitleTextSize.shortLabel: Int
    get() = when (this) {
        SubtitleTextSize.SMALL -> R.string.video_subtitle_size_small
        SubtitleTextSize.MEDIUM -> R.string.video_subtitle_size_medium
        SubtitleTextSize.LARGE -> R.string.video_subtitle_size_large
    }

@Composable
private fun SyncControl(offsetMs: Long, isEnabled: Boolean, onChange: (Long) -> Unit) {
    val latestOffsetMs by rememberUpdatedState(offsetMs)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PlayerText(stringResource(R.string.video_tracks_sync), VideoPlayerType.PanelLabel, color = VideoPlayerTokens.SecondaryContent)
        PanelSegmentGroup {
            PanelSegmentButton(
                label = stringResource(R.string.video_subtitle_sync_earlier_symbol),
                isChosen = false,
                isEnabled = isEnabled,
                onHeldStep = { onChange(latestOffsetMs - VideoPlayerTokens.SyncStepMs) },
            )
            PlayerText(
                text = formatSubtitleOffset(offsetMs),
                style = VideoPlayerType.PanelSegment,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 44.dp),
            )
            PanelSegmentButton(
                label = stringResource(R.string.video_subtitle_sync_later_symbol),
                isChosen = false,
                isEnabled = isEnabled,
                onHeldStep = { onChange(latestOffsetMs + VideoPlayerTokens.SyncStepMs) },
            )
        }
    }
}

/**
 * One small button of a [PanelSegmentGroup]. It acts on a tap through [onClick], or - with [onHeldStep] - on every step
 * of a press: once at once and repeatedly while held, so a long way can be covered without tapping it all out.
 */
@Composable
private fun PanelSegmentButton(
    label: String,
    isChosen: Boolean,
    isEnabled: Boolean = true,
    onClick: () -> Unit = {},
    onHeldStep: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    // A press has already taken its step by the time its click arrives; a click with no press - a screen reader's - has not.
    val steppedByPress = remember { booleanArrayOf(false) }
    if (onHeldStep != null) {
        val isPressed by interactionSource.collectIsPressedAsState()
        val latestStep by rememberUpdatedState(onHeldStep)
        LaunchedEffect(isPressed, isEnabled) {
            if (!isPressed || !isEnabled) return@LaunchedEffect
            steppedByPress[0] = true
            latestStep()
            delay(VideoPlayerTokens.SyncRepeatDelayMs)
            while (true) {
                latestStep()
                delay(VideoPlayerTokens.SyncRepeatMs)
            }
        }
    }
    TextButton(
        onClick = {
            onClick()
            if (!steppedByPress[0]) onHeldStep?.invoke()
            steppedByPress[0] = false
        },
        enabled = isEnabled,
        shapes = buttonPressShapes(),
        interactionSource = interactionSource,
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (isChosen) VideoPlayerTokens.SegmentChosen else Color.Transparent,
            contentColor = if (isChosen) VideoPlayerTokens.ContentColor else VideoPlayerTokens.SecondaryContent,
            disabledContentColor = VideoPlayerTokens.DimmedContent,
        ),
        contentPadding = PaddingValues(horizontal = 10.dp),
        modifier = Modifier.height(32.dp).widthIn(min = 36.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium.merge(VideoPlayerType.PanelSegment))
    }
}
