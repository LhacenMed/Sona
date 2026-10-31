package com.lhacenmed.sona.feature.explorer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.R as DesignR
import com.lhacenmed.sona.core.designsystem.component.SonaActionButtonGroup
import com.lhacenmed.sona.core.designsystem.component.SonaTabPager
import com.lhacenmed.sona.core.designsystem.component.SonaTabRow
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaColumnBottomSheet
import com.lhacenmed.sona.core.model.FolderPicks
import com.lhacenmed.sona.core.model.Track
import kotlinx.coroutines.launch

/**
 * One kind of file a [FolderPickerSheet] offers, under its own tab - "Music", "Videos" - [files], picked by key:
 * [keyPrefix] and a path, see [FolderPicks]. Tabs of the same prefix pick together: a folder picked whole under
 * one is the same key under the other.
 */
@Immutable
class FolderPickerTab(val title: String, val files: List<Track>, val keyPrefix: String = "")

/**
 * Picks folders and files - which to exclude from the library, which to auto-tag, which to back up - out of
 * [tabs], laid out as the library's own tabs are: a pill over their names that follows the pages as they are
 * swiped. One kind alone is one list, with no tabs over it.
 *
 * Each tab is an explorer - see [FolderExplorerList] - its folders opened in place, its files listened to with a
 * press, and each folder or file picked with its box; over it, the path to the folder open, kept clear of the
 * pages' swipes. Back goes to the folder before it closes the sheet.
 *
 * Nothing is taken until the choice is confirmed: under the list stand Select all - or Deselect all, once every
 * file of the tab on screen is picked - then Cancel and the confirm, which [confirmLabel] names for how many files
 * are picked. Confirming hands [onConfirm] the keys picked; it waits for the choice to differ from
 * [initiallyPicked], as there is nothing to take before.
 */
@Composable
fun FolderPickerSheet(
    title: String,
    tabs: List<FolderPickerTab>,
    initiallyPicked: Set<String>,
    confirmLabel: (pickedFileCount: Int) -> String,
    onConfirm: (picked: Set<String>) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val previewViewModel: ExplorerPreviewViewModel = hiltViewModel()
    val preview by previewViewModel.preview.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()
    val explored = remember(tabs) { tabs.map { ExploredFiles(it.files) } }
    val picksByPrefix = remember(tabs) {
        tabs.groupBy { it.keyPrefix }.mapValues { (prefix, sharing) -> FolderPicks(sharing.flatMap { it.files }, prefix) }
    }
    // One a tab, each kept where it was left as the tabs are swiped between.
    val navigations = tabs.map { rememberExplorerNavigation(start = null) }
    var keys by remember { mutableStateOf(initiallyPicked) }
    val currentPage = pagerState.currentPage
    val currentTab = tabs[currentPage]
    val currentPicks = picksByPrefix.getValue(currentTab.keyPrefix)
    val pickedFileCount = tabs.sumOf { tab -> tab.files.count { picksByPrefix.getValue(tab.keyPrefix).isPicked(it, keys) } }
    val isTabPicked = currentTab.files.isNotEmpty() && currentTab.files.all { currentPicks.isPicked(it, keys) }
    val selectAllLabel = stringResource(if (isTabPicked) R.string.explorer_deselect_all else R.string.explorer_select_all)
    val cancelLabel = stringResource(DesignR.string.confirmation_dialog_cancel)

    // Closed, what it played stops with it.
    DisposableEffect(previewViewModel) { onDispose(previewViewModel::stop) }

    SonaColumnBottomSheet(title = title, onDismissRequest = onDismissRequest) { sheetScope ->
        val navigation = navigations[currentPage]
        // Back leaves the folder open before it closes the sheet.
        BackHandler(enabled = navigation.canGoBack, onBack = navigation::back)
        if (tabs.size > 1) {
            SonaTabRow(
                tabTitles = tabs.map { it.title },
                selectedPosition = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
                onTabClick = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            )
        }
        FolderPathBar(folders = explored[currentPage].pathTo(navigation.open), onOpen = navigation::open)
        // Tabs keep the sheet at its full height, so swiping between kinds of different lengths never moves its top.
        SonaTabPager(state = pagerState, modifier = Modifier.weight(1f), key = { page -> tabs[page].title }) { page ->
            val tab = tabs[page]
            FolderExplorerList(
                explored = explored[page],
                navigation = navigations[page],
                preview = preview,
                onPlay = previewViewModel::play,
                picking = ExplorerPicking(picksByPrefix.getValue(tab.keyPrefix), keys) { keys = it },
                // The sheet already paints its own background; a row painting its own would seam against it.
                rowColor = Color.Transparent,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SonaActionButtonGroup {
                actionButton(
                    label = selectAllLabel,
                    onClick = { keys = currentPicks.toggled(keys, currentTab.files) },
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            SonaActionButtonGroup {
                actionButton(label = cancelLabel, onClick = sheetScope::dismiss)
                actionButton(
                    label = confirmLabel(pickedFileCount),
                    onClick = {
                        onConfirm(keys)
                        sheetScope.dismiss()
                    },
                    enabled = keys != initiallyPicked,
                )
            }
        }
    }
}
