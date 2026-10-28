package com.lhacenmed.sona.core.designsystem.component.sheet

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.R
import com.lhacenmed.sona.core.designsystem.component.SelectionState
import com.lhacenmed.sona.core.designsystem.component.SonaActionButtonGroup
import com.lhacenmed.sona.core.designsystem.component.SonaFolderCover
import com.lhacenmed.sona.core.designsystem.component.SonaListRow
import com.lhacenmed.sona.core.designsystem.component.SonaTabPager
import com.lhacenmed.sona.core.designsystem.component.SonaTabRow
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.selectAllAction
import com.lhacenmed.sona.core.model.Folder
import kotlinx.coroutines.launch

/** One folder a [SonaFolderPickerSheet] offers, with what it holds - "12 tracks". */
@Immutable
class FolderChoice(val folder: Folder, val contents: String)

/** One kind of folder a [SonaFolderPickerSheet] offers, under its own tab - "Music", "Videos" - and its folders. */
@Immutable
class FolderPickerTab(val title: String, val choices: List<FolderChoice>)

/**
 * Picks a set of folders - which to exclude from the library, which to auto-tag - out of [tabs], one for each kind of
 * folder the choice is among, laid out as the library's own tabs are: a pill over their names that follows the pages
 * as they are swiped. One kind alone is one list, with no tabs over it. Every folder is one row, picked and unpicked
 * with a press as a list's rows are selected, starting from [initiallySelected]; a folder is picked by its path, so
 * one listed under two kinds is picked under both at once.
 *
 * Nothing is taken until the choice is confirmed: under the list, however long it runs, stand Select all - or
 * Deselect all, once every folder of the tab on screen is picked - then Cancel and the confirm, which [confirmLabel]
 * names for how many folders are picked, as a dialog's buttons stand. Confirming hands [onConfirm] the folders picked;
 * it waits for the choice to differ from where it started, as there is nothing to take before.
 */
@Composable
fun SonaFolderPickerSheet(
    title: String,
    tabs: List<FolderPickerTab>,
    initiallySelected: Set<String>,
    confirmLabel: (selectedCount: Int) -> String,
    onConfirm: (selected: Set<String>) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val selection = remember { SelectionState().apply { selectAll(initiallySelected) } }
    val pagerState = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()
    val paths = tabs.flatMapTo(mutableSetOf()) { tab -> tab.choices.map { it.folder.path } }
    val selected = paths.filterTo(mutableSetOf()) { selection.isSelected(it) }
    val cancelLabel = stringResource(R.string.confirmation_dialog_cancel)

    SheetFrame(onDismissRequest, Modifier) { sheetScope ->
        SheetTitle(title)
        if (tabs.size > 1) {
            SonaTabRow(
                tabTitles = tabs.map { it.title },
                selectedPosition = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
                onTabClick = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            )
        }
        // Tabs keep the sheet at its full height, so swiping between kinds of different lengths never moves its top.
        SonaTabPager(state = pagerState, modifier = Modifier.weight(1f, fill = tabs.size > 1), key = { page -> tabs[page].title }) { page ->
            LazyColumn {
                items(tabs[page].choices, key = { it.folder.path }) { choice ->
                    val path = choice.folder.path
                    SonaListRow(
                        title = choice.folder.name,
                        subtitle = choice.contents,
                        selection = selection,
                        selectionKey = path,
                        onClick = { selection.toggle(path) },
                        onOpenOptions = null,
                        // The sheet already paints its own background; a row painting its own would seam against it.
                        containerColor = Color.Transparent,
                        cover = { isSelected ->
                            SonaFolderCover(coverArtUris = choice.folder.coverArtUris, seed = path.hashCode(), isSelected = isSelected)
                        },
                    )
                }
            }
        }
        val selectAll = selection.selectAllAction(tabs[pagerState.currentPage].choices.map { it.folder.path })
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SonaActionButtonGroup { actionButton(label = selectAll.label, onClick = selectAll.onClick) }
            Spacer(modifier = Modifier.weight(1f))
            SonaActionButtonGroup {
                actionButton(label = cancelLabel, onClick = sheetScope::dismiss)
                actionButton(
                    label = confirmLabel(selected.size),
                    onClick = {
                        onConfirm(selected)
                        sheetScope.dismiss()
                    },
                    enabled = selected != initiallySelected.intersect(paths),
                )
            }
        }
    }
}
