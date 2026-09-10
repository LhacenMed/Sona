package com.lhacenmed.sona.feature.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import com.lhacenmed.sona.core.designsystem.component.SonaTabRow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
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
        val tabTitles = remember(visibleTabs) { visibleTabs.map { it.label() } }

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

        Column(modifier = modifier.fillMaxSize()) {
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
