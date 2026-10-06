package com.lhacenmed.sona.feature.video.sheet

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.designsystem.component.SonaLazyBottomSheet
import com.lhacenmed.sona.core.designsystem.component.SonaTrackRow
import com.lhacenmed.sona.feature.video.QueueVideo
import com.lhacenmed.sona.feature.video.R
import com.lhacenmed.sona.feature.video.formatVideoTime

/**
 * The queue, each video playing when tapped - opened at the one playing now, on the app's sheet for a long list,
 * as the library's own rows.
 */
@Composable
internal fun VideoQueueSheet(
    queue: List<QueueVideo>,
    currentIndex: Int,
    isPlaying: Boolean,
    onPlay: (QueueVideo) -> Unit,
    onDismissRequest: () -> Unit,
) {
    SonaLazyBottomSheet(
        title = stringResource(R.string.video_queue),
        onDismissRequest = onDismissRequest,
        // The row before the current one shows above it, so it is plain where in the queue it sits.
        listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 1).coerceAtLeast(0)),
    ) {
        itemsIndexed(queue, key = { _, item -> item.entry.key }) { index, item ->
            SonaTrackRow(
                track = item.video,
                isCurrent = { index == currentIndex },
                isPlaying = { isPlaying },
                selection = null,
                selectionKey = null,
                onClick = { onPlay(item) },
                onOpenOptions = null,
                subtitle = formatVideoTime(item.video.durationMs),
                // The sheet paints its own ground; a row painting another would seam against it.
                containerColor = Color.Transparent,
            )
        }
    }
}
