package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The pages under a [SonaTabRow] - the library's tabs, a picker sheet's - swiped between as one, the same wherever
 * they are.
 *
 * No band past the first or last page: the pager simply stops there. A stretch would spring back once the swipe has
 * settled, moving the page on its own after the pill above has stopped - with few pages, every swipe ends at an edge.
 *
 * Every page is kept, so none is ever disposed while the pages are shown. Disposing one cancels whatever its list
 * was doing: leave a page mid-fling and it would freeze where it stood, then reappear stopped dead when you came
 * back. Kept alive, it keeps flinging and settles exactly where it would have. [key] keeps each page's scroll and
 * composition when the pages are unchanged but the pager recomposes.
 */
@Composable
fun SonaTabPager(
    state: PagerState,
    modifier: Modifier = Modifier,
    key: ((page: Int) -> Any)? = null,
    pageContent: @Composable PagerScope.(page: Int) -> Unit,
) {
    HorizontalPager(
        state = state,
        modifier = modifier,
        overscrollEffect = null,
        beyondViewportPageCount = (state.pageCount - 1).coerceAtLeast(0),
        key = key,
        pageContent = pageContent,
    )
}
