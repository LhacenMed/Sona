package com.lhacenmed.sona.feature.video.panel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.designsystem.component.SonaTrackRow
import com.lhacenmed.sona.feature.video.QueueVideo
import com.lhacenmed.sona.feature.video.R
import com.lhacenmed.sona.feature.video.controls.VideoAction
import com.lhacenmed.sona.feature.video.formatVideoTime
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
 * as it can run long.
 */
@Composable
internal fun ColumnScope.QueuePanel(
    queue: List<QueueVideo>,
    currentIndex: Int,
    isPlaying: Boolean,
    onPlay: (QueueVideo) -> Unit,
    onClose: () -> Unit,
) {
    PanelHeader(stringResource(R.string.video_queue), onClose)
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
                subtitle = formatVideoTime(item.video.durationMs),
                // The panel paints its own ground; a row painting another would seam against it.
                containerColor = Color.Transparent,
            )
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
                        act()
                        onClose()
                    }
                },
                color = action.tint,
            )
        }
    }
}
