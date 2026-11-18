@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.library.tabs

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.R as DesignR
import com.lhacenmed.sona.core.designsystem.component.LocalDragHandle
import com.lhacenmed.sona.core.designsystem.component.SonaSwitch
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.feature.library.R
import com.lhacenmed.sona.feature.library.ReorderableRows
import com.lhacenmed.sona.feature.library.rememberReorderableRows
import com.lhacenmed.sona.feature.library.reorderableRows

/** The library's tabs as one group of rows - shown or hidden by their switches, put in order by their handles. */
class LibraryTabEditor internal constructor(
    internal val rows: ReorderableRows<LibraryTabRow>,
    internal val viewModel: LibraryTabsViewModel,
    /**
     * The tab the library opens on, by the order as kept - not as a drag has it for the moment - so the row that says so
     * stays the one it is until the drop, and no row grows or shrinks under the finger.
     */
    internal val openedAtLaunch: LibraryTabRow?,
)

/**
 * The one editor of the library's tabs - Settings' Library tabs and the setup's step alike, so both show the same rows and
 * write the same settings. Its rows are laid in [listState]'s list with [libraryTabRows].
 */
@Composable
fun rememberLibraryTabEditor(listState: LazyListState): LibraryTabEditor {
    val viewModel: LibraryTabsViewModel = hiltViewModel()
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val rows = rememberReorderableRows(tabs, key = { it.tab.name }, listState, onReorder = viewModel::arrange)
    return LibraryTabEditor(rows, viewModel, openedAtLaunch = tabs.firstOrNull { it.isVisible })
}

/**
 * [editor]'s rows: one connected group, each row a tab - tapped or switched to show it, dragged by its handle - inset
 * [horizontalPadding] from the list's sides, in [colors], each arriving as [arrival] of its place in the group says.
 */
fun LazyListScope.libraryTabRows(
    editor: LibraryTabEditor,
    horizontalPadding: Dp = 16.dp,
    colors: @Composable () -> ListItemColors = { ListItemDefaults.segmentedColors() },
    arrival: @Composable (index: Int) -> Modifier = { Modifier },
) {
    reorderableRows(editor.rows, key = { it.tab.name }) { row ->
        val tabs = editor.rows.rows
        val visibleCount = tabs.count { it.isVisible }
        // Disabled rather than allowed-then-ignored, so the last visible tab's switch visibly can't be turned off.
        val canToggle = !(row.isVisible && visibleCount <= 1)
        val toggle = { editor.viewModel.setTabVisible(row.tab, !row.isVisible) }
        SegmentedListItem(
            onClick = toggle,
            enabled = canToggle,
            shapes = ListItemDefaults.segmentedShapes(index = tabs.indexOf(row), count = tabs.size),
            colors = colors(),
            modifier = Modifier
                .padding(horizontal = horizontalPadding)
                .padding(bottom = ListItemDefaults.SegmentedGap)
                .then(arrival(tabs.indexOf(row))),
            leadingContent = {
                Icon(
                    imageVector = SonaIcons.DragHandle,
                    contentDescription = stringResource(DesignR.string.list_row_reorder),
                    modifier = LocalDragHandle.current ?: Modifier,
                )
            },
            trailingContent = { SonaSwitch(checked = row.isVisible, onCheckedChange = null, enabled = canToggle) },
            supportingContent = if (row.tab == editor.openedAtLaunch?.tab) {
                { Text(stringResource(R.string.library_tabs_opened_at_launch)) }
            } else {
                null
            },
        ) {
            Text(row.tab.label)
        }
    }
}
