package com.lhacenmed.sona.feature.download

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.InfoSeparator
import com.lhacenmed.sona.core.designsystem.component.SelectionState
import com.lhacenmed.sona.core.designsystem.component.SonaIconButton
import com.lhacenmed.sona.core.designsystem.component.SonaListRow
import com.lhacenmed.sona.core.designsystem.component.rememberSelectionState
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaColumnBottomSheet
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.core.designsystem.theme.iconButtonPressShapes
import com.lhacenmed.sona.core.downloader.fractionOf
import com.lhacenmed.sona.core.model.Download
import com.lhacenmed.sona.core.model.DownloadBatch
import com.lhacenmed.sona.core.model.DownloadStatus

/** What a list shows of its downloads: each alone, or a [DownloadBatch]'s as one - a playlist's, a watched channel's. */
internal sealed interface ListedDownload {
    val key: Any

    class One(val download: Download) : ListedDownload {
        override val key: Any get() = download.id
    }

    class Batch(val batch: DownloadBatch) : ListedDownload {
        override val key: Any get() = "batch:${batch.key}"
    }
}

/**
 * Each of [sections]' downloads as a list shows them - a batch's as one, in the place of its first, in the first section that
 * holds any of it: a batch still downloading is listed with what downloads, one over with when it ended.
 */
internal fun listedOf(sections: List<List<Download>>): List<List<ListedDownload>> {
    val shown = mutableSetOf<String>()
    return sections.map { downloads ->
        downloads.mapNotNull { download ->
            val batch = download.batch ?: return@mapNotNull ListedDownload.One(download)
            if (shown.add(batch.key)) ListedDownload.Batch(batch) else null
        }
    }
}

/** Every download of [listed] the [selection] holds - each of a batch's, where the batch is selected. */
internal fun selectedOf(listed: List<Download>, selection: SelectionState): List<Download> =
    listed.filter { selection.isSelected(it.id) || it.batch?.let { batch -> selection.isSelected(ListedDownload.Batch(batch).key) } == true }

/** Whether [this] still downloads, or may: waiting, running, or paused. */
private val Download.isActive: Boolean
    get() = status == DownloadStatus.QUEUED || status == DownloadStatus.RUNNING || status == DownloadStatus.PAUSED

/** A batch's videos, as they stand - every one it was queued with, saved or not. */
@Composable
private fun rememberBatch(batch: DownloadBatch, viewModel: DownloadViewModel): List<Download> {
    val all by viewModel.downloads.collectAsStateWithLifecycle()
    return all.orEmpty().filter { it.batch?.key == batch.key }
}

/**
 * A batch, as one row: its name, what it is and how many, and how far it is as a whole - every video saved counted whole, each
 * running as far as it is - filling it as a download's fills its own; Pause or Resume for all of it, Play once it is over.
 * Tapped, its videos open; selected as a download is, it is all of its videos.
 */
@Composable
internal fun BatchRow(batch: DownloadBatch, selection: SelectionState, viewModel: DownloadViewModel, onOpen: () -> Unit) {
    val context = LocalContext.current
    val videos = rememberBatch(batch, viewModel)
    val running by viewModel.running.collectAsStateWithLifecycle()
    val first = videos.firstOrNull() ?: return
    val held by viewModel.held.collectAsStateWithLifecycle()
    val saved = videos.count { it.status == DownloadStatus.DONE }
    val isRunning = videos.any { it.id in running }
    val isPaused = !isRunning && videos.any { it.status == DownloadStatus.PAUSED } && videos.none { it.status == DownloadStatus.QUEUED }
    // Each video at the furthest it has got: one between running and saved - the two told apart a moment apart - never falls
    // back, so the whole only ever fills.
    val reached = remember(batch.key) { mutableMapOf<Long, Float>() }
    videos.forEach { video ->
        val now = when {
            video.status == DownloadStatus.DONE -> 1f
            else -> running[video.id]?.fraction ?: held[video.id]?.let { fractionOf(it.first, it.second) } ?: 0f
        }
        reached[video.id] = maxOf(reached[video.id] ?: 0f, now)
    }
    val fraction = videos.sumOf { reached[it.id]?.toDouble() ?: 0.0 }.toFloat() / videos.size
    val ids = videos.filter { it.isActive }.map(Download::id)
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
        // Filling for as long as any of it is still to come - not only while one runs, which leaves gaps between them.
        if (ids.isNotEmpty()) RowProgressFill(fraction = fraction)
        SonaListRow(
            title = batch.title,
            subtitle = listOf(
                stringResource(if (batch.key.startsWith(WatchBatchPrefix)) R.string.download_batch_channel else R.string.download_playlist),
                context.resources.getQuantityString(R.plurals.download_videos, videos.size, videos.size),
            ).joinToString(InfoSeparator),
            selection = selection,
            selectionKey = ListedDownload.Batch(batch).key,
            onClick = onOpen,
            onOpenOptions = null,
            containerColor = Color.Transparent,
            supporting = {
                RowStatusLine(
                    RowStatus(
                        listOfNotNull(
                            context.getString(R.string.download_batch_saved, saved, videos.size),
                            videos.count { it.status == DownloadStatus.FAILED }.takeIf { it > 0 }?.let { context.resources.getQuantityString(R.plurals.download_batch_failed, it, it) },
                        ).joinToString(InfoSeparator),
                        if (isRunning) StatusTone.ACTIVE else StatusTone.QUIET,
                    ),
                )
            },
            trailing = when {
                selection.isActive -> null
                ids.isEmpty() -> if (saved == 0) null else ({
                    FilledTonalIconButton(
                        onClick = { viewModel.onPlayAll(videos) },
                        shapes = iconButtonPressShapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    ) {
                        Icon(SonaIcons.Play, contentDescription = stringResource(R.string.download_play))
                    }
                })
                else -> ({
                    if (isPaused) {
                        SonaIconButton(onClick = { viewModel.onResume(ids) }, icon = SonaIcons.Play, contentDescription = stringResource(R.string.download_resume))
                    } else {
                        SonaIconButton(onClick = { viewModel.onPause(ids) }, icon = SonaIcons.Pause, contentDescription = stringResource(R.string.download_pause))
                    }
                })
            },
            cover = { DownloadCover(first, isSelected = false) },
        )
    }
}

/** What a watched channel's batches are known by - see [WatchChecker]. */
internal const val WatchBatchPrefix = "watch:"

/**
 * A batch opened: every one of its videos as a download's own row - paused, resumed, played and opened each as any other - then
 * Pause or Resume and Cancel for all that are not over yet.
 */
@Composable
internal fun BatchSheet(batch: DownloadBatch, viewModel: DownloadViewModel, onOpenDownload: (Download) -> Unit, onDismiss: () -> Unit) {
    val videos = rememberBatch(batch, viewModel)
    val running by viewModel.running.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    // A batch's videos are opened, not selected, here: they are selected from the history.
    val selection = rememberSelectionState()
    val active = videos.filter { it.isActive }.map(Download::id)
    val isPaused = videos.none { it.id in running || it.status == DownloadStatus.QUEUED }
    SonaColumnBottomSheet(onDismissRequest = onDismiss, title = batch.title) { sheet ->
        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(videos, key = Download::id) { download ->
                DownloadRow(download, running[download.id], preferences, selection, viewModel, onOpen = { onOpenDownload(download) })
            }
        }
        if (active.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { if (isPaused) viewModel.onResume(active) else viewModel.onPause(active) },
                    shapes = buttonPressShapes(),
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) {
                    Icon(if (isPaused) SonaIcons.Play else SonaIcons.Pause, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Text(stringResource(if (isPaused) R.string.download_resume_all else R.string.download_pause_all), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing), maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        viewModel.onCancel(active)
                        sheet.dismiss()
                    },
                    shapes = buttonPressShapes(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) {
                    Icon(SonaIcons.Close, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Text(stringResource(R.string.download_cancel_all), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing), maxLines = 1)
                }
            }
        }
    }
}
