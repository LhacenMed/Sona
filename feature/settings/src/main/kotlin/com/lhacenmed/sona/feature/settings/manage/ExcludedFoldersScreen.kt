package com.lhacenmed.sona.feature.settings.manage

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.SonaEmptyState
import com.lhacenmed.sona.core.designsystem.component.SonaFolderCover
import com.lhacenmed.sona.core.designsystem.component.SonaListRow
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.fab.ExtendedFloatingActionButtonContent
import com.lhacenmed.sona.core.designsystem.component.fab.SonaExtendedFloatingActionButtons
import com.lhacenmed.sona.core.designsystem.component.rememberSelectionState
import com.lhacenmed.sona.core.designsystem.component.screen.screenList
import com.lhacenmed.sona.core.designsystem.component.selectAllAction
import com.lhacenmed.sona.core.designsystem.component.toTopBarSelection
import com.lhacenmed.sona.core.designsystem.component.toast
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.feature.explorer.FolderExplorerScreen
import com.lhacenmed.sona.feature.explorer.FolderPickerSheet
import com.lhacenmed.sona.feature.explorer.FolderPickerTab
import java.io.File
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.R

/**
 * "Excluded Folders" screen: the folders and files the media scanner skips - a folder's music and videos alike -
 * and the way to change them, many at a time.
 *
 * Its button excludes more: an explorer of the library's folders - its music's and its videos' under tabs of their
 * own, as the library keeps them - where folders and files are picked together and excluded in one go. A press
 * opens what is excluded, to see what was left out and listen to it; excluded ones are selected as any list's rows
 * are - a long press, then presses - and the selection bar lets the selected ones back in.
 */
data object ExcludedFoldersScreen : Screen {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.current
        val viewModel: ExcludedFoldersViewModel = hiltViewModel()
        val excludedFolders by viewModel.excludedFolders.collectAsStateWithLifecycle()
        val music by viewModel.music.collectAsStateWithLifecycle()
        val videos by viewModel.videos.collectAsStateWithLifecycle()
        val picks = rememberSelectionState()
        var isPickingFolders by rememberSaveable { mutableStateOf(false) }

        SonaExtendedFloatingActionButtons(
            listOf(
                ExtendedFloatingActionButtonContent(
                    label = stringResource(R.string.excluded_folders_add),
                    icon = Icons.Filled.Block,
                    onClick = {
                        if (music.isEmpty() && videos.isEmpty()) context.toast(R.string.excluded_folders_none_left) else isPickingFolders = true
                    },
                    isPrimary = true,
                ),
            ),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            SonaTopAppBar(
                title = stringResource(R.string.excluded_folders_title),
                onNavigateBack = navigator::back,
                selection = picks.toTopBarSelection(
                    actions = listOf(
                        picks.selectAllAction(excludedFolders),
                        TopBarAction(label = stringResource(R.string.excluded_folders_include), icon = Icons.Filled.Restore) {
                            viewModel.include(picks.selectedKeys.filterIsInstance<String>().toSet())
                            picks.clear()
                        },
                    ),
                    onMoreOptions = null,
                ),
            )
            if (excludedFolders.isEmpty()) {
                SonaEmptyState(
                    title = stringResource(R.string.excluded_folders_empty_title),
                    message = stringResource(R.string.excluded_folders_empty_body),
                )
                return@Column
            }
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
                        // Its files are out of the library: they are read from the device, a file's with its folder's.
                        onClick = {
                            val folder = if (File(path).isFile) path.substringBeforeLast('/') else path
                            navigator.go(FolderExplorerScreen(folder, showsExcluded = true))
                        },
                        onOpenOptions = null,
                        // An excluded folder is out of the library, its covers with it: the folder glyph stands for it.
                        cover = { isSelected -> SonaFolderCover(coverArtUris = emptyList(), seed = path.hashCode(), isSelected = isSelected) },
                    )
                }
            }
        }

        if (isPickingFolders) {
            FolderPickerSheet(
                title = stringResource(R.string.excluded_folders_add),
                // A kind with nothing to exclude has no tab: there is nothing to pick under it.
                tabs = listOf(
                    FolderPickerTab(stringResource(R.string.excluded_folders_music), music),
                    FolderPickerTab(stringResource(R.string.excluded_folders_videos_tab), videos),
                ).filter { it.files.isNotEmpty() },
                initiallyPicked = emptySet(),
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
