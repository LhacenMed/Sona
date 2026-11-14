package com.lhacenmed.sona.feature.video.panel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.player.formatPlaybackTime
import com.lhacenmed.sona.core.designsystem.component.SonaPlaybackModeButtons
import com.lhacenmed.sona.core.designsystem.component.SonaTrackRow
import com.lhacenmed.sona.core.model.RepeatMode
import com.lhacenmed.sona.core.model.VideoChapter
import com.lhacenmed.sona.feature.playback.QueueVideo
import com.lhacenmed.sona.feature.playback.labelRes
import com.lhacenmed.sona.feature.video.R
import com.lhacenmed.sona.feature.video.VideoPlayerTokens
import com.lhacenmed.sona.feature.video.controls.VideoAction
import com.lhacenmed.sona.feature.video.speedLabel

/** The speeds the speed button offers. */
private val PlaybackSpeeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)

/** How fast the video plays. Picking one closes it. */
@Composable
internal fun ColumnScope.SpeedPanel(selected: Float, onSelect: (Float) -> Unit, onClose: () -> Unit) {
    PanelHeader(stringResource(R.string.video_speed), onClose)
    Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
        PlaybackSpeeds.forEach { speed ->
            PanelChoiceRow(speedLabel(speed), speed == selected, {
                onSelect(speed)
                onClose()
            })
        }
    }
}

/**
 * The queue, each video playing when tapped - opened at the one playing now, as the library's own rows, on a lazy list
 * as it can run long - under how it plays: shuffled or not, and how it repeats, the videos' own, as the music's queue
 * shows the music's.
 */
@Composable
internal fun ColumnScope.QueuePanel(
    queue: List<QueueVideo>,
    currentIndex: Int,
    isPlaying: Boolean,
    isShuffled: Boolean,
    repeatMode: RepeatMode,
    onPlay: (QueueVideo) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onClose: () -> Unit,
) {
    PanelHeader(stringResource(R.string.video_queue), onClose)
    SonaPlaybackModeButtons(
        isShuffled = isShuffled,
        repeatMode = repeatMode,
        shuffleLabel = stringResource(R.string.video_shuffle),
        repeatLabel = stringResource(repeatMode.labelRes),
        onShuffleClick = onToggleShuffle,
        onRepeatClick = onCycleRepeatMode,
        contentColor = VideoPlayerTokens.ContentColor,
        modifier = Modifier.padding(end = 6.dp, bottom = 4.dp),
    )
    LazyColumn(
        // The row before the current one shows above it, so it is plain where in the queue it sits.
        state = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 1).coerceAtLeast(0)),
        modifier = Modifier.weight(1f, fill = false),
    ) {
        itemsIndexed(queue, key = { _, item -> item.entry.key }) { index, item ->
            SonaTrackRow(
                track = item.video,
                isCurrent = { index == currentIndex },
                isPlaying = { isPlaying },
                selection = null,
                selectionKey = null,
                onClick = {
                    onPlay(item)
                    onClose()
                },
                onOpenOptions = null,
                subtitle = formatPlaybackTime(item.video.durationMs),
                // The panel paints its own ground; a row painting another would seam against it.
                containerColor = Color.Transparent,
            )
        }
    }
}

/** The video's chapters, each with where it starts, the one playing chosen - picking one goes to it, and closes it. */
@Composable
internal fun ColumnScope.ChaptersPanel(chapters: List<VideoChapter>, currentIndex: Int?, onSelect: (VideoChapter) -> Unit, onClose: () -> Unit) {
    PanelHeader(stringResource(R.string.video_chapters), onClose)
    LazyColumn(
        state = rememberLazyListState(initialFirstVisibleItemIndex = ((currentIndex ?: 0) - 1).coerceAtLeast(0)),
        modifier = Modifier.weight(1f, fill = false),
    ) {
        itemsIndexed(chapters) { index, chapter ->
            PanelChoiceRow("${formatPlaybackTime(chapter.startMs)}  ${chapter.title}", index == currentIndex, {
                onSelect(chapter)
                onClose()
            })
        }
    }
}

/** Whatever the player does that does not earn a place on the screen itself. Picking one closes it. */
@Composable
internal fun ColumnScope.MorePanel(actions: List<VideoAction>, onClose: () -> Unit) {
    PanelHeader(stringResource(R.string.video_more), onClose)
    Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
        actions.forEach { action ->
            PanelActionRow(
                icon = action.icon,
                label = action.label,
                onClick = action.onClick?.let { act ->
                    {
                        // Closed first, as what it does may be to open another panel.
                        onClose()
                        act()
                    }
                },
                color = action.tint,
            )
        }
    }
}
