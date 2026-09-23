package com.lhacenmed.sona.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.datastore.LibraryTab
import com.lhacenmed.sona.core.designsystem.component.SelectionState
import com.lhacenmed.sona.core.designsystem.component.SonaTabRow
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.TopBarSearch
import com.lhacenmed.sona.core.designsystem.component.rememberSelectionState
import com.lhacenmed.sona.core.designsystem.theme.SonaComponentStyle
import com.lhacenmed.sona.feature.library.operation.ExcludeFoldersDialog
import com.lhacenmed.sona.feature.library.selection.SelectionKey
import com.lhacenmed.sona.feature.library.selection.SelectionOptionsHost
import com.lhacenmed.sona.feature.library.selection.toLibraryTopBarSelection
import com.lhacenmed.sona.feature.library.sort.SortSheet
import com.lhacenmed.sona.feature.library.sort.sortAction
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch

/**
 * The library's whole tab experience: the top app bar, a rounded swipeable tab strip beneath it, and
 * the paged content. This is the single entry point `:app` needs - it owns tab selection, tab
 * visibility and row selection internally, leaving the bottom of the screen entirely free for the
 * expandable player.
 *
 * The bar lives here rather than in the host's scaffold because it has to turn into a context bar
 * when a tab has rows selected, and only this screen knows which tab that is. [actions] is what the
 * shell contributes to the ordinary bar.
 *
 * All five tabs share one [LibraryViewModel], which in turn reads lists that
 * [com.lhacenmed.sona.core.data.LibraryRepository] has already computed once for the whole process.
 * "Opening" a tab therefore costs a composition and nothing else - no query, no sort - which is why
 * they can all exist at once without competing for the launch frame.
 */
@Composable
fun LibraryPagerScreen(
    modifier: Modifier = Modifier,
    actions: List<TopBarAction> = emptyList(),
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val visibleTabs by viewModel.visibleTabs.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    if (visibleTabs.isEmpty()) return

    // Keying on the tab set (not just its size) fully resets the pager whenever it changes - a
    // settings change mid-session is rare enough that snapping back to the first tab is the
    // simplest correct behavior, rather than trying to preserve a page index that may no longer
    // point at the same tab.
    key(visibleTabs) {
        val pagerState = rememberPagerState(pageCount = { visibleTabs.size })
        val tabTitles = remember(visibleTabs) { visibleTabs.map { it.label() } }
        val selection = rememberSelectionState()
        val scope = rememberCoroutineScope()

        // Each tab's list position, held here rather than inside the list so a tap on the tab can
        // reach it. Saved, so a list keeps its place across a configuration change as before.
        val listStates = visibleTabs.associateWith { rememberLazyListState() }

        // The tab a tap asked for, held until the pager has actually come to rest on it. A swipe
        // leaves this null: the pager is then its own authority and the strip simply follows it,
        // so the two can never fight over which tab is selected.
        var requestedPage by remember { mutableStateOf<Int?>(null) }

        // The tab whose sort sheet is open. Held as the tab rather than a flag, so the sheet keeps
        // sorting the tab it was opened over.
        var sortingTab by remember { mutableStateOf<LibraryTab?>(null) }

        // The selected folders waiting on the user to confirm excluding them.
        var excludingFolders by remember { mutableStateOf<List<String>?>(null) }

        // Where the pill sits while a tap is being carried out. The pager cannot be asked to slide
        // the whole way across a long move - it teleports to a page near the target first - so the
        // pill travels that distance itself, and the strip follows it rather than the pager for as
        // long as [pillLeads]. A swipe therefore still reads the pager directly, with nothing in
        // between to lag behind the finger.
        val pillPosition = remember { Animatable(0f) }
        var pillLeads by remember { mutableStateOf(false) }

        // Every suspending move lives here, in one place and in one order, which is what makes a tap
        // reliable. `collectLatest` drops a move the moment a newer tab is tapped, so the last tap wins.
        //
        // The pill's starting point is read first, before the pager is touched: a long move teleports
        // the pager to a page near the target on its very first step, and a pill that read the pager
        // after that would leap straight there. A tap that interrupts a slide starts from the pill
        // itself, so nothing the pager does can ever move the pill.
        //
        // The move itself is left to `animateScrollToPage`: it already jumps to within three pages of
        // a distant target before animating the rest, so the slide stays smooth and short without
        // ever composing every tab in between. It can still be interrupted - a list fling coming to
        // rest re-snaps the pager, cancelling the move rather than letting it finish somewhere wrong.
        // Watching `settledPage` turns that into an ordinary observation ("we came to rest on a page
        // nobody asked for") and simply issues the move again, without needing to know what
        // interrupted it.
        //
        // The pill keeps the lead until it has arrived and the pager has come to rest on the same tab,
        // so handing the strip back to the pager changes nothing on screen.
        LaunchedEffect(pagerState) {
            snapshotFlow { requestedPage }
                .filterNotNull()
                .collectLatest { requested ->
                    if (!pillLeads) {
                        pillPosition.snapTo(pagerState.currentPage + pagerState.currentPageOffsetFraction)
                        pillLeads = true
                    }
                    coroutineScope {
                        launch { pillPosition.animateTo(requested.toFloat(), TabMotion) }
                        snapshotFlow { pagerState.settledPage }
                            .takeWhile { settled -> settled != requested && requestedPage == requested }
                            .collectLatest { pagerState.animateScrollToPage(requested, animationSpec = TabMotion) }
                    }
                    requestedPage = null
                    pillLeads = false
                }
        }

        // A swipe outranks a tap still being carried out: the finger takes the pager, so the strip
        // follows the finger at once and the tap's move is not issued again once the swipe settles.
        LaunchedEffect(pagerState) {
            pagerState.interactionSource.interactions
                .filterIsInstance<DragInteraction.Start>()
                .collect {
                    requestedPage = null
                    pillLeads = false
                }
        }

        val selectedTab = visibleTabs[pagerState.settledPage]

        // The tab the strip is on, or already sliding to.
        val destinationPage = requestedPage ?: pagerState.settledPage

        // Back returns to the first tab - Tracks, unless hidden - the way it would to a home screen,
        // and only from there leaves the app. Composed before the bar, so a selection or a search,
        // whose back handler the bar composes later, is still what a back press closes first.
        BackHandler(enabled = destinationPage != 0) { requestedPage = 0 }

        // One selection for every tab, as Auxio's is: rows picked on one tab stay picked on the next,
        // so albums, artists and single tracks can be gathered into a single set of tracks.
        SelectionOptionsHost(selection) { openSelectionOptions ->
            // Painted here rather than left to whatever hosts the screen: the bar and every row are
            // `surface`, while a Scaffold fills with `background` - the same colour on most devices, but
            // not on all, where the shortcuts and the tab strip would sit on a band of a different shade.
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                val sortTabAction = sortAction { sortingTab = selectedTab }
                SonaTopAppBar(
                    // The app's own label, so the bar reads exactly what the launcher does - "Sona Debug"
                    // on a debug build, which :app sets per build type.
                    title = stringResource(LocalContext.current.applicationInfo.labelRes),
                    // The library's own two first, so they are the ones always drawn as icons: both act
                    // on the tab on screen, while the shell's actions are the ones that can fold into
                    // the menu.
                    actions = listOf(
                        TopBarAction(label = "Search", icon = Icons.Filled.Search) {
                            viewModel.onSearchQueryChange("")
                        },
                        sortAction { sortingTab = selectedTab },
                    ) + actions,
                    // Sorting stays beside the field: a search narrows a tab, it does not reorder it.
                    search = searchQuery?.let { query ->
                        TopBarSearch(
                            query = query,
                            onQueryChange = { viewModel.onSearchQueryChange(it) },
                            onClose = { viewModel.onSearchQueryChange(null) },
                            actions = listOf(sortTabAction),
                        )
                    },
                    selection = selection.toLibraryTopBarSelection(
                        listKeys = { viewModel.selectableKeys(selectedTab) },
                        actions = excludeFolderActions(selection) { excludingFolders = it },
                        onMoreOptions = openSelectionOptions,
                    ),
                )

                LibraryShortcuts(
                    modifier = Modifier.padding(horizontal = SonaComponentStyle.ContentHorizontalPadding),
                )

                if (visibleTabs.size > 1) {
                    SonaTabRow(
                        tabTitles = tabTitles,
                        // Passed as a lambda so the swipe position is read inside the tab row, not
                        // here - otherwise every frame of a swipe would recompose the pager below.
                        selectedPosition = {
                            if (pillLeads) {
                                pillPosition.value
                            } else {
                                pagerState.currentPage + pagerState.currentPageOffsetFraction
                            }
                        },
                        // Tapping the tab already chosen takes its list back to the top instead.
                        onTabClick = { page ->
                            if (page == destinationPage) {
                                scope.launch { listStates.getValue(visibleTabs[page]).glideToTop() }
                            } else {
                                requestedPage = page
                            }
                        },
                        modifier = Modifier.padding(
                            horizontal = SonaComponentStyle.ContentHorizontalPadding,
                            vertical = 8.dp,
                        ),
                    )
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    // Enough to hold every tab, so none is ever disposed while the library is open.
                    // Disposing one cancels whatever its list was doing: leave a tab mid-fling and it
                    // would freeze where it stood, then reappear stopped dead when you came back.
                    // Kept alive, it keeps flinging and settles exactly where it would have. The whole
                    // library is already in memory, so an off-screen tab costs composition and nothing
                    // else - the same reason all five can be built on the launch frame.
                    beyondViewportPageCount = visibleTabs.size - 1,
                    // Stable page keys, so a tab keeps its scroll position and composition when the
                    // visible set is unchanged but the pager recomposes.
                    key = { page -> visibleTabs[page].name },
                ) { page ->
                    val tab = visibleTabs[page]
                    val listState = listStates.getValue(tab)
                    when (tab) {
                        LibraryTab.TRACKS -> TracksScreen(viewModel, selection, listState)
                        LibraryTab.ARTISTS -> ArtistsScreen(viewModel, selection, listState)
                        LibraryTab.ALBUMS -> AlbumsScreen(viewModel, selection, listState)
                        LibraryTab.GENRES -> GenresScreen(viewModel, selection, listState)
                        LibraryTab.FOLDERS -> FoldersScreen(viewModel, selection, listState)
                    }
                }
            }
        }

        sortingTab?.let { tab ->
            SortSheet(sort = viewModel.sort(tab), onDismiss = { sortingTab = null })
        }

        // The selection only ends once the exclusion is confirmed: cancelling leaves every row picked.
        excludingFolders?.let { folderPaths ->
            ExcludeFoldersDialog(
                folderPaths = folderPaths,
                onDismiss = { excludingFolders = null },
                onConfirmed = { selection.clear() },
            )
        }
    }
}

/**
 * Excluding folders, offered while the selection holds any, whichever tab is on screen - the one thing
 * the library's own bar adds to every list's. It asks [onExclude] to confirm excluding the selected
 * folders alone.
 */
private fun excludeFolderActions(selection: SelectionState, onExclude: (folderPaths: List<String>) -> Unit): List<TopBarAction> {
    val folderPaths = selection.selectedKeys.filterIsInstance<SelectionKey.Folder>().map { it.folderPath }
    if (folderPaths.isEmpty()) return emptyList()
    return listOf(
        TopBarAction(label = "Exclude folder", icon = Icons.Filled.Block) { onExclude(folderPaths) },
    )
}

/**
 * The motion a tap gives the pager and the pill. They share one spec because they cover different
 * distances - the pager snaps a page before animating a long move, the pill glides the whole way -
 * and should still arrive together, so the pill never waits long over a pager that is still moving.
 * A tap on the chosen tab moves its list back to the top with the same motion.
 */
private val TabMotion = spring<Float>()

/**
 * Takes the list to its first row in one short glide, however far down it is.
 *
 * A list more than a screen down first jumps to a screen from the top: scrolling the whole way would
 * compose every row in between, stuttering exactly where it should be fastest. What is left is always
 * about a screen, so every return to the top takes the same brief, even time. Rows in a list share
 * one shape, so a row's height gives that distance exactly and the glide lands on the first row.
 */
private suspend fun LazyListState.glideToTop() {
    val rowsOnScreen = layoutInfo.visibleItemsInfo.size
    if (firstVisibleItemIndex > rowsOnScreen) scrollToItem(rowsOnScreen)
    val rowHeight = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: return
    animateScrollBy(-(firstVisibleItemIndex * rowHeight + firstVisibleItemScrollOffset).toFloat(), TabMotion)
}

private fun LibraryTab.label(): String = when (this) {
    LibraryTab.TRACKS -> "Tracks"
    LibraryTab.ARTISTS -> "Artists"
    LibraryTab.ALBUMS -> "Albums"
    LibraryTab.GENRES -> "Genres"
    LibraryTab.FOLDERS -> "Folders"
}
