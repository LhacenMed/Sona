package com.lhacenmed.sona.feature.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.datastore.LibraryTab
import com.lhacenmed.sona.core.designsystem.component.SelectionState
import com.lhacenmed.sona.core.designsystem.component.SonaTabRow
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.rememberSelectionState
import com.lhacenmed.sona.core.designsystem.component.toTopBarSelection
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
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
    if (visibleTabs.isEmpty()) return

    // Keying on the tab set (not just its size) fully resets the pager whenever it changes - a
    // settings change mid-session is rare enough that snapping back to the first tab is the
    // simplest correct behavior, rather than trying to preserve a page index that may no longer
    // point at the same tab.
    key(visibleTabs) {
        val pagerState = rememberPagerState(pageCount = { visibleTabs.size })
        val tabTitles = remember(visibleTabs) { visibleTabs.map { it.label() } }
        val selection = rememberSelectionState()

        // The tab a tap asked for, held until the pager has actually come to rest on it. A swipe
        // leaves this null: the pager is then its own authority and the strip simply follows it,
        // so the two can never fight over which tab is selected.
        var requestedPage by remember { mutableStateOf<Int?>(null) }

        // Where the pill sits while a tap is being carried out. The pager cannot be asked to slide
        // the whole way across a long move - it snaps a page first - so the pill travels that
        // distance itself, and is only consulted while it is actually moving. A swipe therefore
        // still reads the pager directly, with nothing in between to lag behind the finger.
        val pillPosition = remember { Animatable(0f) }

        // Every suspending move lives here, in one place, which is what makes a tap reliable.
        // The move itself is left to `animateScrollToPage`: it already jumps to within three pages
        // of a distant target before animating the rest, so the slide stays smooth and short
        // without ever composing every tab in between.
        //
        // It can still be interrupted - a list fling coming to rest re-snaps the pager, cancelling
        // the move rather than letting it finish somewhere wrong. Watching `settledPage` alongside
        // the request turns that into an ordinary observation ("we came to rest on a page nobody
        // asked for") and simply issues the move again, without needing to know what interrupted
        // it. `collectLatest` drops a move the moment a newer tab is tapped, so the last tap wins.
        LaunchedEffect(pagerState) {
            snapshotFlow { requestedPage to pagerState.settledPage }
                .collectLatest { (requested, settled) ->
                    when {
                        requested == null -> Unit
                        requested == settled -> requestedPage = null
                        else -> coroutineScope {
                            launch {
                                pillPosition.snapTo(pagerState.currentPage + pagerState.currentPageOffsetFraction)
                                pillPosition.animateTo(requested.toFloat(), TabMotion)
                            }
                            pagerState.animateScrollToPage(requested, animationSpec = TabMotion)
                        }
                    }
                }
        }

        // A selection belongs to the list that made it, so leaving that list ends it. Without this
        // the bar would go on offering to play one tab's rows while another tab is on screen.
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }.collect { selection.clear() }
        }

        val selectedTab = visibleTabs[pagerState.settledPage]

        Column(modifier = modifier.fillMaxSize()) {
            SonaTopAppBar(
                title = "Sona",
                actions = actions,
                selection = selection.toTopBarSelection(
                    actions = contextActions(selectedTab, selection, viewModel),
                ),
            )

            if (visibleTabs.size > 1) {
                SonaTabRow(
                    tabTitles = tabTitles,
                    // Passed as a lambda so the swipe position is read inside the tab row, not
                    // here - otherwise every frame of a swipe would recompose the pager below.
                    selectedPosition = {
                        if (pillPosition.isRunning) {
                            pillPosition.value
                        } else {
                            pagerState.currentPage + pagerState.currentPageOffsetFraction
                        }
                    },
                    onTabClick = { page -> requestedPage = page },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
                when (visibleTabs[page]) {
                    LibraryTab.TRACKS -> TracksScreen(viewModel = viewModel, selection = selection)
                    LibraryTab.ARTISTS -> ArtistsScreen(viewModel = viewModel, selection = selection)
                    LibraryTab.ALBUMS -> AlbumsScreen(viewModel = viewModel, selection = selection)
                    LibraryTab.GENRES -> GenresScreen(viewModel = viewModel, selection = selection)
                    LibraryTab.FOLDERS -> FoldersScreen(viewModel = viewModel, selection = selection)
                }
            }
        }
    }
}

/**
 * What the context bar offers for [tab].
 *
 * Every tab can play what it has selected; only folders can also be kept out of the library. Adding
 * an action here is all it takes - the bar works out for itself which ones fit as icons and which
 * fold into the overflow menu, so a tab growing a fourth action needs no layout work.
 */
private fun contextActions(
    tab: LibraryTab,
    selection: SelectionState,
    viewModel: LibraryViewModel,
): List<TopBarAction> = buildList {
    add(
        TopBarAction(label = "Play", icon = Icons.Filled.PlayArrow) {
            viewModel.playSelection(tab, selection.selectedKeys)
            selection.clear()
        },
    )
    if (tab == LibraryTab.FOLDERS) {
        add(
            TopBarAction(label = "Exclude folder", icon = Icons.Filled.Block) {
                viewModel.excludeSelectedFolders(selection.selectedKeys)
                selection.clear()
            },
        )
    }
    add(
        TopBarAction(label = "Select all", icon = Icons.Filled.SelectAll) {
            selection.selectAll(viewModel.selectableKeys(tab))
        },
    )
}

/**
 * The motion a tap gives the pager and the pill. They share one spec because they cover different
 * distances - the pager snaps a page before animating a long move, the pill glides the whole way -
 * and must still arrive together, or the pill would jump at the handover.
 */
private val TabMotion = spring<Float>()

private fun LibraryTab.label(): String = when (this) {
    LibraryTab.TRACKS -> "Tracks"
    LibraryTab.ARTISTS -> "Artists"
    LibraryTab.ALBUMS -> "Albums"
    LibraryTab.GENRES -> "Genres"
    LibraryTab.FOLDERS -> "Folders"
}
