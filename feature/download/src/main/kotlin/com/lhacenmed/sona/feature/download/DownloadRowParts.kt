package com.lhacenmed.sona.feature.download

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.component.CoverArtDefaults
import com.lhacenmed.sona.core.designsystem.component.InfoSeparator
import com.lhacenmed.sona.core.designsystem.component.SonaCoverArt
import com.lhacenmed.sona.core.designsystem.component.SonaIconButton
import com.lhacenmed.sona.core.designsystem.component.SonaVideoCover
import com.lhacenmed.sona.core.designsystem.component.shape
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.designsystem.theme.LocalCoverStyle
import com.lhacenmed.sona.core.designsystem.theme.iconButtonPressShapes
import com.lhacenmed.sona.core.downloader.fractionOf
import com.lhacenmed.sona.core.model.Download
import com.lhacenmed.sona.core.model.DownloadFailure
import com.lhacenmed.sona.core.model.DownloadKind
import com.lhacenmed.sona.core.model.DownloadStatus
import com.lhacenmed.sona.core.model.DownloaderPreferences
import com.lhacenmed.sona.feature.tageditor.SongSummary
import com.lhacenmed.sona.feature.tageditor.SongTagging

/** Whether a download is saved as the song it is - drawn as a song, square, rather than as an audio file. */
private val Download.isSong: Boolean get() = choices.kind == DownloadKind.AUDIO && choices.savesAsSong

/** The song [this] is saved as - picked, or found as it began - as its row shows it; null for any other. */
internal val Download.song: SongSummary? get() = options.song?.takeIf { isSong }?.let(SongTagging::summaryOf)

/**
 * What stands for a download in its row: a song's cover, square - the song's own once it is found; an audio file's tile; a
 * video's frame at 16:9 with its length.
 */
@Composable
internal fun DownloadCover(download: Download, isSelected: Boolean) {
    val song = remember(download.options.song) { download.song }
    when {
        download.choices.kind == DownloadKind.VIDEO -> SonaVideoCover(
            coverArtUri = download.thumbnailUrl,
            durationMs = (download.durationSeconds ?: 0) * 1000,
            watchedFraction = { 0f },
            isSelected = isSelected,
        )

        download.isSong -> SonaCoverArt(coverArtUri = song?.coverUri ?: download.thumbnailUrl, contentDescription = null, isSelected = isSelected)

        else -> Box(
            modifier = Modifier
                .size(CoverArtDefaults.ListSize)
                .clip(LocalCoverStyle.current.shape(CoverArtDefaults.ListCornerRadius))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (download.choices.kind.isMedia) SonaIcons.GraphicEq else download.choices.kind.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

/**
 * What a row says of its download under its title: who made it, and what it is kept as - "Mara Vell · 1080p · MP4"; a song's
 * artist and album.
 */
internal fun rowMeta(context: Context, download: Download): String = download.song?.let { listOf(it.artist, it.album).filter(String::isNotBlank).joinToString(InfoSeparator) }
    ?.takeIf(String::isNotEmpty) ?: listOfNotNull(
    download.uploader,
    when {
        download.choices.kind == DownloadKind.VIDEO -> context.getString(download.choices.videoQuality.labelRes)
        download.isSong || !download.choices.kind.isMedia -> null
        else -> context.getString(download.choices.audioFormat.labelRes)
    },
    download.savedPaths.firstOrNull()?.substringAfterLast('.', "")?.uppercase()?.takeIf(String::isNotEmpty),
).joinToString(InfoSeparator).ifEmpty { hostOf(download.url) }

/** How a row's status line reads: as it goes, quietly, or as a failure - each with the icon it starts with, where it has one. */
internal enum class StatusTone { ACTIVE, QUIET, ERROR }

internal class RowStatus(val text: String, val tone: StatusTone, val icon: ImageVector? = null)

/**
 * Where a row's download stands, in a few words: how far and how long left while it runs, what it waits for, how big once
 * saved, or why it failed - never the machinery.
 */
internal fun rowStatus(
    context: Context,
    download: Download,
    running: RunningDownload?,
    held: HeldProgress?,
    preferences: DownloaderPreferences,
    isMissing: Boolean = false,
): RowStatus {
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
    fun sizes(done: Long?, total: Long?) = done?.let { if (total != null) context.getString(R.string.download_size_done_of, size(it), size(total)) else size(it) }
    return when {
        running != null && running.isFinishingUp ->
            RowStatus(context.getString(if (download.isSong) R.string.download_finishing_song else R.string.download_finishing), StatusTone.ACTIVE)

        running != null -> RowStatus(
            listOfNotNull(
                if (running.item != null && running.itemCount != null) context.getString(R.string.download_item_of, running.item, running.itemCount) else null,
                sizes(running.downloadedBytes, running.totalBytes),
                timeLeft(context, running),
            ).joinToString(InfoSeparator).ifEmpty { context.getString(R.string.download_starting) },
            StatusTone.ACTIVE,
        )

        download.status == DownloadStatus.PAUSED ->
            RowStatus(listOfNotNull(context.getString(R.string.download_paused), held?.let { sizes(it.first, it.second) }).joinToString(InfoSeparator), StatusTone.QUIET)

        download.status == DownloadStatus.QUEUED || download.status == DownloadStatus.RUNNING -> {
            val startAt = download.startAt
            when {
                download.failure == DownloadFailure.NO_NETWORK -> RowStatus(context.getString(R.string.download_waiting_network), StatusTone.QUIET, SonaIcons.WifiOff)
                startAt != null && startAt > System.currentTimeMillis() -> {
                    val time = DateFormat.getTimeFormat(context).format(startAt)
                    RowStatus(context.getString(if (DateUtils.isToday(startAt)) R.string.download_starts_at else R.string.download_starts_tomorrow_at, time), StatusTone.QUIET)
                }
                preferences.wifiOnly -> RowStatus(context.getString(R.string.download_waiting_wifi), StatusTone.QUIET, SonaIcons.Wifi)
                else -> RowStatus(context.getString(R.string.download_waiting), StatusTone.QUIET)
            }
        }

        download.status == DownloadStatus.DONE && isMissing -> RowStatus(context.getString(R.string.download_status_missing), StatusTone.QUIET, SonaIcons.Warning)

        download.status == DownloadStatus.DONE ->
            RowStatus(listOfNotNull(context.getString(R.string.download_status_saved), download.sizeBytes.takeIf { it > 0 }?.let(::size)).joinToString(InfoSeparator), StatusTone.QUIET)

        download.status == DownloadStatus.CANCELED -> RowStatus(context.getString(R.string.download_status_canceled), StatusTone.QUIET)
        else -> RowStatus(context.getString((download.failure ?: DownloadFailure.FAILED).presentation.rowRes), StatusTone.ERROR, SonaIcons.Error)
    }
}

/** How long [running] has left at the speed it goes - "1 min left" - null while it cannot tell. */
private fun timeLeft(context: Context, running: RunningDownload): String? {
    val speed = running.bytesPerSecond?.takeIf { it > 0 } ?: return null
    val left = ((running.totalBytes ?: return null) - (running.downloadedBytes ?: return null)).coerceAtLeast(0) / speed
    return if (left < 60) {
        context.resources.getQuantityString(R.plurals.download_seconds_left, left.toInt(), left.toInt())
    } else {
        val minutes = (left / 60).toInt()
        context.resources.getQuantityString(R.plurals.download_minutes_left, minutes, minutes)
    }
}

/** A row's status line, under its title and what it is - coloured by how it reads. */
@Composable
internal fun RowStatusLine(status: RowStatus) {
    val color = when (status.tone) {
        StatusTone.ACTIVE -> MaterialTheme.colorScheme.primary
        StatusTone.QUIET -> MaterialTheme.colorScheme.onSurfaceVariant
        StatusTone.ERROR -> MaterialTheme.colorScheme.error
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        status.icon?.let { Icon(it, contentDescription = null, tint = color, modifier = Modifier.padding(end = 4.dp).size(16.dp)) }
        Text(status.text, style = MaterialTheme.typography.bodyMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * How far a download is, filling its row from behind - YTDLnis's queue card: Material's linear progress indicator laid over the
 * whole row, its indicator in the theme's primary colour at [FillAlpha], and no track - the rest of the row is as every other row. Each figure
 * is moved to as Material's own `setProgressCompat(progress, true)` moves - see [FillSpring] - and while there is none - [fraction]
 * null - it runs Material's indeterminate animation across the row.
 */
@Composable
internal fun BoxScope.RowProgressFill(fraction: Float?) {
    val modifier = Modifier.matchParentSize().alpha(FillAlpha)
    val color = MaterialTheme.colorScheme.primary
    if (fraction == null) {
        LinearProgressIndicator(modifier = modifier, color = color, trackColor = Color.Transparent, strokeCap = StrokeCap.Butt, gapSize = 0.dp)
    } else {
        val shown by animateFloatAsState(targetValue = fraction, animationSpec = FillSpring, label = "rowFill")
        LinearProgressIndicator(
            progress = { shown },
            modifier = modifier,
            color = color,
            trackColor = Color.Transparent,
            strokeCap = StrokeCap.Butt,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

/** How strongly the fill lies over its row: YTDLnis's `android:alpha="0.3"`. */
private const val FillAlpha = 0.3f

/** How the fill moves to each new figure: Material's own determinate drawable's spring - no bounce, stiffness 50. */
private val FillSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 50f)

/** How far [download] is as its row fills - null where it does not fill: neither running nor paused. See [RowProgressFill]. */
internal class RowFill(val fraction: Float?)

internal fun rowFillOf(download: Download, running: RunningDownload?, held: HeldProgress?): RowFill? = when {
    // None yet, or whole and made ready - merged, converted, tagged: Material's indeterminate run, as YTDLnis's `progress <= 0`.
    running != null -> RowFill(running.fraction?.takeUnless { running.isFinishingUp || it <= 0f })
    download.status == DownloadStatus.PAUSED -> RowFill(fractionOf(held?.first, held?.second) ?: 0f)
    else -> null
}

/**
 * A row's one button, as its download stands: Pause while it runs, Resume while paused, Start now where it waits for a time,
 * then Play once saved, its fix once failed, and Download again once cancelled. How far it is fills the row - see
 * [RowProgressFill].
 */
@Composable
internal fun DownloadRowButton(download: Download, running: RunningDownload?, isMissing: Boolean, viewModel: DownloadViewModel, onFix: () -> Unit) {
    val ids = listOf(download.id)
    when {
        running != null && running.isFinishingUp ->
            SonaIconButton(onClick = {}, icon = SonaIcons.AutoAwesome, contentDescription = stringResource(R.string.download_finishing), enabled = false)

        running != null -> SonaIconButton(onClick = { viewModel.onPause(ids) }, icon = SonaIcons.Pause, contentDescription = stringResource(R.string.download_pause))

        download.status == DownloadStatus.PAUSED ->
            SonaIconButton(onClick = { viewModel.onResume(ids) }, icon = SonaIcons.Play, contentDescription = stringResource(R.string.download_resume))

        download.status == DownloadStatus.QUEUED || download.status == DownloadStatus.RUNNING -> {
            val waitsForTime = (download.startAt ?: 0) > System.currentTimeMillis()
            SonaIconButton(
                onClick = { viewModel.onResume(ids) },
                icon = SonaIcons.Download,
                contentDescription = stringResource(R.string.download_start_now),
                enabled = waitsForTime,
            )
        }

        download.status == DownloadStatus.CANCELED || (download.status == DownloadStatus.DONE && isMissing) ->
            FilledActionButton(SonaIcons.Download, stringResource(R.string.download_again), MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurface) {
                viewModel.onRetry(download)
            }

        download.status == DownloadStatus.DONE ->
            FilledActionButton(download.choices.kind.openIcon, stringResource(download.choices.kind.openLabelRes), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) {
                viewModel.onPlay(download)
            }

        else -> {
            val fix = (download.failure ?: DownloadFailure.FAILED).presentation.fix
            FilledActionButton(fix.icon, stringResource(fix.labelRes), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, onFix)
        }
    }
}

/** A filled round button - Play, a fix, Download again - in [containerColor]. */
@Composable
private fun FilledActionButton(icon: ImageVector, label: String, containerColor: Color, contentColor: Color, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        shapes = iconButtonPressShapes(),
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Icon(icon, contentDescription = label)
    }
}
