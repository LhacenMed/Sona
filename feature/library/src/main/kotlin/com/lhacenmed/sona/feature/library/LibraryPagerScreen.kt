package com.lhacenmed.sona.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
 */
@Composable
fun LibraryPagerScreen(
    modifier: Modifier = Modifier,
    tabsViewModel: LibraryTabsViewModel = hiltViewModel(),
) {
    val visibleTabs by tabsViewModel.visibleTabs.collectAsStateWithLifecycle()
    if (visibleTabs.isEmpty()) return

    // Every tab's view model is created up front, not on arrival. Their data flows are eagerly
    // collected (see the view models' `SharingStarted.Eagerly`), so a tab already holds its rows
    // before it is ever swiped to and paints them on its first frame.
    val tracksViewModel: TracksViewModel = hiltViewModel()
    val artistsViewModel: ArtistsViewModel = hiltViewModel()
    val albumsViewModel: AlbumsViewModel = hiltViewModel()
    val genresViewModel: GenresViewModel = hiltViewModel()
    val foldersViewModel: FoldersViewModel = hiltViewModel()

    // Keying on the tab set (not just its size) fully resets the pager whenever it changes - a
    // settings change mid-session is rare enough that snapping back to the first tab is the
    // simplest correct behavior, rather than trying to preserve a page index that may no longer
    // point at the same tab.
    key(visibleTabs) {
        val pagerState = rememberPagerState(pageCount = { visibleTabs.size })
        val scope = rememberCoroutineScope()

        Column(modifier = modifier.fillMaxSize()) {
            if (visibleTabs.size > 1) {
                SonaTabRow(
                    tabTitles = visibleTabs.map { it.label() },
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
            ) { page ->
                when (visibleTabs[page]) {
                    LibraryTab.TRACKS -> TracksScreen(viewModel = tracksViewModel)
                    LibraryTab.ARTISTS -> ArtistsScreen(viewModel = artistsViewModel)
                    LibraryTab.ALBUMS -> AlbumsScreen(viewModel = albumsViewModel)
                    LibraryTab.GENRES -> GenresScreen(viewModel = genresViewModel)
                    LibraryTab.FOLDERS -> FoldersScreen(viewModel = foldersViewModel)
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
