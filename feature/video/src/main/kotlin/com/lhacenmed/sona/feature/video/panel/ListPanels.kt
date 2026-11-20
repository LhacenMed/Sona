package com.lhacenmed.sona.feature.video.panel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.icu.text.CompactDecimalFormat
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.player.formatPlaybackTime
import com.lhacenmed.sona.core.designsystem.component.SonaPlaybackModeButtons
import com.lhacenmed.sona.core.designsystem.component.SonaTrackRow
import com.lhacenmed.sona.core.designsystem.component.copyToClipboard
import com.lhacenmed.sona.core.model.RepeatMode
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.model.VideoChapter
import com.lhacenmed.sona.core.model.VideoStats
import com.lhacenmed.sona.core.model.siteNameOf
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.lhacenmed.sona.feature.playback.QueueVideo
import com.lhacenmed.sona.feature.playback.labelRes
import com.lhacenmed.sona.feature.video.R
import com.lhacenmed.sona.feature.video.VideoPlayerTokens
import com.lhacenmed.sona.feature.video.controls.VideoAction
import com.lhacenmed.sona.feature.video.controls.VideoBarButton
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

/**
 * What the video's site counted of it as it was downloaded, or last read again - when it was uploaded, how often watched and
 * liked - each where the site said. The day is the site's own, UTC's, wherever the device is. With [onRefresh], they can be
 * read again from the video's page - and, read [isRefreshing], are being.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ColumnScope.AboutPanel(stats: VideoStats?, sourceUrl: String?, isRefreshing: Boolean, onRefresh: (() -> Unit)?, onClose: () -> Unit) {
    PanelHeader(
        title = stringResource(R.string.video_about),
        onClose = onClose,
        action = onRefresh?.let { refresh ->
            {
                if (isRefreshing) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(VideoPlayerTokens.BarButtonSize)) {
                        LoadingIndicator(color = VideoPlayerTokens.ContentColor, modifier = Modifier.size(VideoPlayerTokens.BarIconSize + 8.dp))
                    }
                } else {
                    VideoBarButton(VideoAction(SonaIcons.Refresh, stringResource(R.string.video_about_refresh), refresh))
                }
            }
        },
    )
    val count = remember { CompactDecimalFormat.getInstance(Locale.getDefault(), CompactDecimalFormat.CompactStyle.SHORT) }
    val day = remember { DateFormat.getDateInstance(DateFormat.LONG).apply { timeZone = TimeZone.getTimeZone("UTC") } }
    Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
        // Where it came from first, its link copied with a tap - the page its details are read again from.
        sourceUrl?.let { link ->
            val context = LocalContext.current
            val copied = stringResource(R.string.video_about_link_copied)
            PanelActionRow(
                icon = SonaIcons.Link,
                label = stringResource(R.string.video_about_source, siteNameOf(link) ?: link),
                onClick = { context.copyToClipboard(link, copied) },
            )
        }
        if (stats?.hasCounts != true) PanelFactRow(SonaIcons.Info, stringResource(R.string.video_about_unread))
        stats?.uploadedAtMs?.let { PanelFactRow(SonaIcons.CalendarMonth, stringResource(R.string.video_uploaded, day.format(Date(it)))) }
        stats?.viewCount?.let { PanelFactRow(SonaIcons.Visibility, pluralStringResource(R.plurals.video_views, it.toPluralCount(), count.format(it))) }
        stats?.likeCount?.let { PanelFactRow(SonaIcons.ThumbUp, pluralStringResource(R.plurals.video_likes, it.toPluralCount(), count.format(it))) }
        stats?.commentCount?.let { PanelFactRow(SonaIcons.Comment, pluralStringResource(R.plurals.video_comments, it.toPluralCount(), count.format(it))) }
        stats?.repostCount?.let { PanelFactRow(SonaIcons.Share, pluralStringResource(R.plurals.video_shares, it.toPluralCount(), count.format(it))) }
    }
}

/** [this] as the count a plural is picked by - past what an Int holds, its largest, which every language says as "many". */
private fun Long.toPluralCount(): Int = coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

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
