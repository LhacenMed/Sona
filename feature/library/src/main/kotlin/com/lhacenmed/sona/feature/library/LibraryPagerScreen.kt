package com.lhacenmed.sona.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.datastore.LibraryTab
import com.lhacenmed.sona.core.designsystem.component.SonaTabRow
import kotlinx.coroutines.launch

/**
 * The library's whole tab experience: a rounded, swipeable tab strip that sits directly under the
 * host's top app bar, plus its paged content. This is the single entry point `:app` needs - it
 * owns tab selection and visibility internally, leaving the bottom of the screen entirely free
 * for the expandable player.
 *
 * All five tabs share one [LibraryViewModel], which in turn reads lists that
 * [com.lhacenmed.sona.core.data.LibraryRepository] has already computed once for the whole process.
 * "Opening" a tab therefore costs a composition and nothing else - no query, no sort - which is why
 * they can all exist at once without competing for the launch frame.
 */
@Composable
fun LibraryPagerScreen(
    modifier: Modifier = Modifier,
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
        val scope = rememberCoroutineScope()
        val tabTitles = remember(visibleTabs) { visibleTabs.map { it.label() } }

        Column(modifier = modifier.fillMaxSize()) {
            if (visibleTabs.size > 1) {
                SonaTabRow(
                    tabTitles = tabTitles,
                    // Passed as a lambda so the swipe position is read inside the tab row, not
                    // here - otherwise every frame of a swipe would recompose the pager below.
                    selectedPosition = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
                    onTabClick = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                // Stable page keys, so a tab keeps its scroll position and composition when the
                // visible set is unchanged but the pager recomposes.
                key = { page -> visibleTabs[page].name },
            ) { page ->
                when (visibleTabs[page]) {
                    LibraryTab.TRACKS -> TracksScreen(viewModel = viewModel)
                    LibraryTab.ARTISTS -> ArtistsScreen(viewModel = viewModel)
                    LibraryTab.ALBUMS -> AlbumsScreen(viewModel = viewModel)
                    LibraryTab.GENRES -> GenresScreen(viewModel = viewModel)
                    LibraryTab.FOLDERS -> FoldersScreen(viewModel = viewModel)
                }
            }
        }
    }
}

private fun LibraryTab.label(): String = when (this) {
    LibraryTab.TRACKS -> "Tracks"
    LibraryTab.ARTISTS -> "Artists"
    LibraryTab.ALBUMS -> "Albums"
    LibraryTab.GENRES -> "Genres"
    LibraryTab.FOLDERS -> "Folders"
}
