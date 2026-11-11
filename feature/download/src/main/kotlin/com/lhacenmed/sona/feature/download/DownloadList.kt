package com.lhacenmed.sona.feature.download

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.TopBarSelection
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaConfirmationDialog
import com.lhacenmed.sona.core.designsystem.component.dragSelection
import com.lhacenmed.sona.core.designsystem.component.fastscroll.FastScroller
import com.lhacenmed.sona.core.designsystem.component.rememberDragSelection
import com.lhacenmed.sona.core.designsystem.component.rememberSelectionState
import com.lhacenmed.sona.core.designsystem.component.section.rememberSectionListState
import com.lhacenmed.sona.core.designsystem.component.section.section
import com.lhacenmed.sona.core.designsystem.component.selectAllAction
import com.lhacenmed.sona.core.designsystem.component.toTopBarSelection
import com.lhacenmed.sona.core.model.Download
import com.lhacenmed.sona.core.model.DownloadFailure
import com.lhacenmed.sona.core.model.DownloadStatus
import com.lhacenmed.sona.core.model.DownloaderPreferences
import kotlinx.coroutines.launch
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons

/** Downloads listed under one heading - [actions] beside it. */
internal class DownloadGroup(val key: String, val title: String, val downloads: List<Download>, val actions: List<TopBarAction> = emptyList())

/** What a list of downloads has open over it, and which of them are selected - the one state Downloads and History share. */
@Stable
internal class DownloadListState(val selection: SelectionState) {
    /** The download opened in its sheet, followed by its id as it goes, from downloading to saved. */
    var openedId by mutableStateOf<Long?>(null)
    var isChoosingSelectionOptions by mutableStateOf(false)
    var isDeleting by mutableStateOf(false)
}

@Composable
internal fun rememberDownloadListState(): DownloadListState {
    val selection = rememberSelectionState()
    return remember(selection) { DownloadListState(selection) }
}

/** The bar's selection mode over [listed]: Select all and Delete, everything else in the selection's options. */
@Composable
internal fun DownloadListState.topBarSelection(listed: List<Download>): TopBarSelection? = selection.toTopBarSelection(
    actions = listOf(
        selection.selectAllAction(listed.map(Download::id)),
        TopBarAction(stringResource(R.string.download_delete), SonaIcons.Delete) { isDeleting = true },
    ),
    onMoreOptions = { isChoosingSelectionOptions = true },
)

/**
 * [groups] in one list, beneath [header] - each download a row: played with a tap once saved, opened otherwise, selected by a
 * long press and a drag across its neighbours.
 */
@Composable
internal fun DownloadList(
    groups: List<DownloadGroup>,
    viewModel: DownloadViewModel,
    state: DownloadListState,
    header: LazyListScope.() -> Unit = {},
    footer: LazyListScope.() -> Unit = {},
) {
    val running by viewModel.running.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val sectionState = rememberSectionListState()
    val keys = remember(groups) { groups.flatMap { group -> group.downloads.map(Download::id) } }
    val dragSelection = rememberDragSelection(state.selection, listState, keys)

    CompositionLocalProvider(LocalDragSelection provides dragSelection) {
        FastScroller(listState, Modifier.fillMaxSize()) { overscrollEffect ->
            LazyColumn(
                state = listState,
                overscrollEffect = overscrollEffect,
                modifier = Modifier.fillMaxSize().dragSelection(dragSelection),
                contentPadding = PaddingValues(bottom = LocalBottomContentPadding.current),
            ) {
                header()
                var isFirst = true
                groups.filter { it.downloads.isNotEmpty() }.forEach { group ->
                    section(key = group.key, title = group.title, state = sectionState, actions = group.actions, hasDividerAbove = !isFirst) {
                        items(group.downloads, key = Download::id) { download ->
                            DownloadRow(download, running[download.id], preferences, state.selection, onOpen = {
                                if (download.status == DownloadStatus.DONE) viewModel.onPlay(download) else state.openedId = download.id
                            }, onOpenOptions = { state.openedId = download.id })
                        }
                    }
                    isFirst = false
                }
                footer()
            }
        }
    }
}

/** A large title over a list, and under it a line of where things stand - the screen's name, as Seal's home writes it. */
internal fun LazyListScope.screenTitle(title: String, summary: String) {
    item(key = "screen-title") {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp)) {
            Text(title, style = MaterialTheme.typography.displaySmall)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** The sheets and the dialog a list of downloads opens over itself: one download, the selection's options, deleting the selection. */
@Composable
internal fun DownloadListSheets(state: DownloadListState, listed: List<Download>, viewModel: DownloadViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selection = state.selection
    val selected = listed.filter { selection.isSelected(it.id) }

    listed.firstOrNull { it.id == state.openedId }?.let { download ->
        DownloadSheet(download, viewModel, onDismiss = { state.openedId = null })
    }

    if (state.isChoosingSelectionOptions) {
        SelectionOptionsSheet(
            selected = selected,
            viewModel = viewModel,
            onShareLinks = {
                val links = selected.joinToString("\n") { it.url }
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, links), null))
            },
            onDone = selection::clear,
            onDismiss = { state.isChoosingSelectionOptions = false },
        )
    }

    if (state.isDeleting) {
        val ids = selected.map(Download::id)
        SonaConfirmationDialog(
            title = context.resources.getQuantityString(R.plurals.download_delete_title, ids.size, ids.size),
            message = stringResource(R.string.download_delete_message),
            confirmLabel = stringResource(R.string.download_delete),
            successMessage = context.resources.getQuantityString(R.plurals.download_deleted_count, ids.size, ids.size),
            failureMessage = stringResource(R.string.download_delete_failed),
            onDismiss = { state.isDeleting = false },
            operation = { onFinished ->
                scope.launch {
                    val isDeleted = viewModel.onDelete(ids)
                    selection.clear()
                    onFinished(isDeleted)
                }
            },
        )
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
            download.uploader,
            context.getString(download.choices.kind.labelRes),
            download.sizeBytes.takeIf { it > 0 }?.let(::size),
        ).joinToString(InfoSeparator)

        download.status == DownloadStatus.CANCELED -> listOfNotNull(download.uploader, context.getString(R.string.download_status_canceled)).joinToString(InfoSeparator)
        else -> context.getString((download.failure ?: DownloadFailure.FAILED).presentation.rowRes)
    }
}
