package com.lhacenmed.sona.feature.download

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.LocalDragSelection
import com.lhacenmed.sona.core.designsystem.component.SelectionState
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
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.model.Download
import com.lhacenmed.sona.core.model.DownloadBatch
import com.lhacenmed.sona.core.model.DownloaderPreferences
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.feature.download.tools.CookiesScreen
import kotlinx.coroutines.launch

/** Downloads listed under one heading - [actions] beside it - each batch's as one: see [listedOf]. */
internal class DownloadGroup(val key: String, val title: String, val downloads: List<Download>, val actions: List<TopBarAction> = emptyList())

/** What a list of downloads has open over it, and which of them are selected - the one state Downloads and History share. */
@Stable
internal class DownloadListState(val selection: SelectionState) {
    /** The download opened in its sheet, followed by its id as it goes, from downloading to saved. */
    var openedId by mutableStateOf<Long?>(null)

    /** The batch opened in its sheet - a playlist's or a watched channel's videos. */
    var openedBatch by mutableStateOf<DownloadBatch?>(null)
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
        selection.selectAllAction(listedOf(listOf(listed)).flatten().map(ListedDownload::key)),
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
    val listed = remember(groups) { listedOf(groups.map(DownloadGroup::downloads)) }
    val keys = remember(listed) { listed.flatten().map(ListedDownload::key) }
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
                groups.zip(listed).filter { (_, rows) -> rows.isNotEmpty() }.forEach { (group, rows) ->
                    section(key = group.key, title = group.title, state = sectionState, actions = group.actions, hasDividerAbove = !isFirst) {
                        items(rows, key = ListedDownload::key) { row ->
                            when (row) {
                                is ListedDownload.One -> DownloadRow(row.download, running[row.download.id], preferences, state.selection, viewModel, onOpen = { state.openedId = row.download.id })
                                is ListedDownload.Batch -> BatchRow(row.batch, state.selection, viewModel, onOpen = { state.openedBatch = row.batch })
                            }
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
    val selected = selectedOf(listed, selection)

    val all by viewModel.downloads.collectAsStateWithLifecycle()
    // Found among every download, not only those listed: a batch's videos are opened from its sheet.
    all.orEmpty().firstOrNull { it.id == state.openedId }?.let { download ->
        DownloadSheet(download, viewModel, onDismiss = { state.openedId = null })
    }
    state.openedBatch?.let { batch ->
        BatchSheet(batch, viewModel, onOpenDownload = { state.openedId = it.id }, onDismiss = { state.openedBatch = null })
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

/**
 * One download: what it is, who made it and what it is kept as, then where it stands - filled from behind as far as it has got
 * - and its one button, as it stands: pause, resume, play, its fix. Tapped, it opens; its button hides while the list is being
 * selected from.
 */
@Composable
internal fun DownloadRow(
    download: Download,
    running: RunningDownload?,
    preferences: DownloaderPreferences,
    selection: SelectionState,
    viewModel: DownloadViewModel,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val missingFiles by viewModel.missingFiles.collectAsStateWithLifecycle()
    val isMissing = download.id in missingFiles
    val heldAll by viewModel.held.collectAsStateWithLifecycle()
    val held = heldAll[download.id]
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
        rowFillOf(download, running, held)?.let { RowProgressFill(it.fraction) }
        SonaListRow(
            title = download.song?.title ?: running?.title ?: download.title ?: hostOf(download.url),
            subtitle = rowMeta(context, download),
            selection = selection,
            selectionKey = download.id,
            onClick = onOpen,
            onOpenOptions = null,
            containerColor = Color.Transparent,
            supporting = { RowStatusLine(rowStatus(context, download, running, held, preferences, isMissing)) },
            trailing = if (selection.isActive) null else ({
                DownloadRowButton(download, running, isMissing, viewModel, onFix = {
                    applyFix(context, viewModel, download, onSignIn = { navigator.go(CookiesScreen(download.url)) })
                })
            }),
            cover = { isSelected -> DownloadCover(download, isSelected) },
        )
    }
}
