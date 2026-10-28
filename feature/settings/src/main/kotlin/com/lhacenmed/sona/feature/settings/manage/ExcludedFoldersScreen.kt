package com.lhacenmed.sona.feature.settings.manage

import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.annotation.PluralsRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Restore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.SonaEmptyState
import com.lhacenmed.sona.core.designsystem.component.SonaFolderCover
import com.lhacenmed.sona.core.designsystem.component.SonaListRow
import com.lhacenmed.sona.core.designsystem.component.fab.ExtendedFloatingActionButtonContent
import com.lhacenmed.sona.core.designsystem.component.fab.SonaExtendedFloatingActionButtons
import com.lhacenmed.sona.core.designsystem.component.rememberSelectionState
import com.lhacenmed.sona.core.designsystem.component.screen.screenList
import com.lhacenmed.sona.core.designsystem.component.sheet.FolderChoice
import com.lhacenmed.sona.core.designsystem.component.sheet.FolderPickerTab
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaFolderPickerSheet
import com.lhacenmed.sona.core.designsystem.component.toast
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.R

/**
 * "Excluded Folders" screen: the folders the media scanner skips - their music and videos alike - and the way to
 * change them, many at a time.
 *
 * Its one button excludes more: a sheet of the folders the library found media in, never the device's whole
 * storage - its music's and its videos' under tabs of their own, as the library keeps them - picked together and
 * excluded in one go. Pressing excluded folders picks
 * them, as a list's rows are selected, and the button then lets the picked ones back in instead.
 */
data object ExcludedFoldersScreen : Screen {
    override val titleRes: Int get() = R.string.excluded_folders_title

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val viewModel: ExcludedFoldersViewModel = hiltViewModel()
        val excludedFolders by viewModel.excludedFolders.collectAsStateWithLifecycle()
        val excludableMusicFolders by viewModel.excludableMusicFolders.collectAsStateWithLifecycle()
        val excludableVideoFolders by viewModel.excludableVideoFolders.collectAsStateWithLifecycle()
        val picks = rememberSelectionState()
        var isPickingFolders by rememberSaveable { mutableStateOf(false) }

        BackHandler(enabled = picks.isActive) { picks.clear() }

        SonaExtendedFloatingActionButtons(
            listOf(
                if (picks.isActive) {
                    ExtendedFloatingActionButtonContent(
                        label = pluralStringResource(R.plurals.excluded_folders_include_count, picks.count, picks.count),
                        icon = Icons.Filled.Restore,
                        onClick = {
                            viewModel.include(picks.selectedKeys.filterIsInstance<String>().toSet())
                            picks.clear()
                        },
                        isPrimary = true,
                    )
                } else {
                    ExtendedFloatingActionButtonContent(
                        label = stringResource(R.string.excluded_folders_add),
                        icon = Icons.Filled.Block,
                        onClick = {
                            if (excludableMusicFolders.isEmpty() && excludableVideoFolders.isEmpty()) context.toast(R.string.excluded_folders_none_left) else isPickingFolders = true
                        },
                        isPrimary = true,
                    )
                },
            ),
        )

        // Box keeps the screen's root layout shape identical whether showing the empty state or
        // the list, so switching between the two never reflows surrounding chrome.
        Box(modifier = Modifier.fillMaxSize()) {
            if (excludedFolders.isEmpty()) {
                SonaEmptyState(
                    title = stringResource(R.string.excluded_folders_empty_title),
                    message = stringResource(R.string.excluded_folders_empty_body),
                )
            } else {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().screenList(listState),
                    contentPadding = PaddingValues(bottom = LocalBottomContentPadding.current),
                ) {
                    items(excludedFolders, key = { it }) { path ->
                        SonaListRow(
                            title = path.substringAfterLast('/'),
                            subtitle = path.substringBeforeLast('/'),
                            selection = picks,
                            selectionKey = path,
                            onClick = { picks.toggle(path) },
                            onOpenOptions = null,
                            // An excluded folder is out of the library, its covers with it: the folder glyph stands for it.
                            cover = { isSelected -> SonaFolderCover(coverArtUris = emptyList(), seed = path.hashCode(), isSelected = isSelected) },
                        )
                    }
                }
            }
        }

        if (isPickingFolders) {
            SonaFolderPickerSheet(
                title = stringResource(R.string.excluded_folders_add),
                // A kind with no folder left to exclude has no tab: there is nothing to pick under it.
                tabs = listOf(
                    FolderPickerTab(
                        stringResource(R.string.excluded_folders_music),
                        excludableMusicFolders.map { FolderChoice(it, context.resources.countOf(R.plurals.excluded_folders_tracks, it.trackCount)) },
                    ),
                    FolderPickerTab(
                        stringResource(R.string.excluded_folders_videos_tab),
                        excludableVideoFolders.map { FolderChoice(it, context.resources.countOf(R.plurals.excluded_folders_videos, it.trackCount)) },
                    ),
                ).filter { it.choices.isNotEmpty() },
                initiallySelected = emptySet(),
                confirmLabel = { count ->
                    if (count == 0) {
                        context.getString(R.string.excluded_folders_confirm)
                    } else {
                        context.resources.getQuantityString(R.plurals.excluded_folders_confirm_count, count, count)
                    }
                },
                onConfirm = viewModel::exclude,
                onDismissRequest = { isPickingFolders = false },
            )
        }
    }
}

/** [count] as [plurals] words it: "12 tracks". */
private fun Resources.countOf(@PluralsRes plurals: Int, count: Int): String = getQuantityString(plurals, count, count)
