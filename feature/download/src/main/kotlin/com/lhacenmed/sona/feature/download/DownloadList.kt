package com.lhacenmed.sona.feature.download

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.InfoSeparator
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.LocalDragSelection
import com.lhacenmed.sona.core.designsystem.component.SelectionState
import com.lhacenmed.sona.core.designsystem.component.SonaCoverArt
import com.lhacenmed.sona.core.designsystem.component.SonaListRow
import com.lhacenmed.sona.core.designsystem.component.dragSelection
import com.lhacenmed.sona.core.designsystem.component.fastscroll.FastScroller
import com.lhacenmed.sona.core.designsystem.component.rememberDragSelection
import com.lhacenmed.sona.core.designsystem.component.section.rememberSectionListState
import com.lhacenmed.sona.core.designsystem.component.section.section
import com.lhacenmed.sona.core.model.Download
import com.lhacenmed.sona.core.model.DownloadFailure
import com.lhacenmed.sona.core.model.DownloadStatus
import com.lhacenmed.sona.core.model.DownloaderPreferences

/**
 * Every download in one list - what is downloading first, then what was saved today, then before - each a row: played with
 * a tap once saved, opened otherwise, selected by a long press and a drag across its neighbours.
 */
@Composable
internal fun DownloadList(sections: DownloadSections, viewModel: DownloadViewModel, selection: SelectionState, onOpen: (Download) -> Unit) {
    val running by viewModel.running.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val sectionState = rememberSectionListState()
    val keys = remember(sections) { sections.all.map(Download::id) }
    val dragSelection = rememberDragSelection(selection, listState, keys)
    val downloadingTitle = stringResource(R.string.download_section_downloading)
    val todayTitle = stringResource(R.string.download_section_today)
    val earlierTitle = stringResource(R.string.download_section_earlier)

    CompositionLocalProvider(LocalDragSelection provides dragSelection) {
        FastScroller(listState, Modifier.fillMaxSize()) { overscrollEffect ->
            LazyColumn(
                state = listState,
                overscrollEffect = overscrollEffect,
                modifier = Modifier.fillMaxSize().dragSelection(dragSelection),
                contentPadding = PaddingValues(bottom = LocalBottomContentPadding.current),
            ) {
                fun LazyListScope.rows(key: String, title: String, downloads: List<Download>, hasDividerAbove: Boolean) {
                    if (downloads.isEmpty()) return
                    section(key = key, title = title, state = sectionState, hasDividerAbove = hasDividerAbove) {
                        items(downloads, key = Download::id) { download ->
                            DownloadRow(download, running[download.id], preferences, selection, onOpen = {
                                if (download.status == DownloadStatus.DONE) viewModel.onPlay(download) else onOpen(download)
                            }, onOpenOptions = { onOpen(download) })
                        }
                    }
                }
                rows("downloading", downloadingTitle, sections.downloading, hasDividerAbove = false)
                rows("today", todayTitle, sections.today, hasDividerAbove = sections.downloading.isNotEmpty())
                rows("earlier", earlierTitle, sections.earlier, hasDividerAbove = sections.downloading.isNotEmpty() || sections.today.isNotEmpty())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadRow(
    download: Download,
    running: RunningDownload?,
    preferences: DownloaderPreferences,
    selection: SelectionState,
    onOpen: () -> Unit,
    onOpenOptions: () -> Unit,
) {
    val context = LocalContext.current
    val isFailed = download.status == DownloadStatus.FAILED
    SonaListRow(
        title = running?.title ?: download.title ?: hostOf(download.url),
        subtitle = rowSummary(context, download, running, preferences),
        selection = selection,
        selectionKey = download.id,
        onClick = onOpen,
        onOpenOptions = onOpenOptions,
        subtitleColor = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        supporting = running?.let { progress ->
            {
                val fraction = progress.fraction
                val modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                // Wavy as the player's own: it moves as the download does, and waits in a wave while its size is unknown.
                if (fraction == null) LinearWavyProgressIndicator(modifier = modifier) else LinearWavyProgressIndicator(progress = { fraction }, modifier = modifier)
            }
        },
        cover = { isSelected -> SonaCoverArt(coverArtUri = download.thumbnailUrl, contentDescription = null, isSelected = isSelected) },
    )
}

/** What a row says of where its download stands - in a few words, never the machinery. */
private fun rowSummary(context: Context, download: Download, running: RunningDownload?, preferences: DownloaderPreferences): String {
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
    return when {
        running != null -> listOfNotNull(
            if (running.item != null && running.itemCount != null) context.getString(R.string.download_item_of, running.item, running.itemCount) else null,
            running.fraction?.let { context.getString(R.string.download_percent, (it * 100).toInt()) },
            running.downloadedBytes?.let { done -> running.totalBytes?.let { total -> context.getString(R.string.download_size_of, size(done), size(total)) } },
        ).joinToString(InfoSeparator).ifEmpty { context.getString(R.string.download_starting) }

        download.status == DownloadStatus.QUEUED || download.status == DownloadStatus.RUNNING -> {
            val startAt = download.startAt
            when {
                startAt != null && startAt > System.currentTimeMillis() -> {
                    val time = DateFormat.getTimeFormat(context).format(startAt)
                    context.getString(if (DateUtils.isToday(startAt)) R.string.download_starts_at else R.string.download_starts_tomorrow_at, time)
                }
                preferences.wifiOnly -> context.getString(R.string.download_waiting_wifi)
                else -> context.getString(R.string.download_waiting)
            }
        }

        download.status == DownloadStatus.DONE -> listOfNotNull(
            context.getString(download.choices.kind.labelRes),
            download.sizeBytes.takeIf { it > 0 }?.let(::size),
        ).joinToString(InfoSeparator)

        download.status == DownloadStatus.CANCELED -> context.getString(R.string.download_status_canceled)
        else -> context.getString((download.failure ?: DownloadFailure.FAILED).presentation.rowRes)
    }
}
