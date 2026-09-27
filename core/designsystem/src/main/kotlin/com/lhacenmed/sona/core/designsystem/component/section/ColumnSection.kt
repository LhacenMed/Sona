package com.lhacenmed.sona.core.designsystem.component.section

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * One section of a column scrolled by [scrollState] - [section], for a list short enough to be one column:
 * its [SectionHeader] pinned at the top of the column while its rows scroll under it, then its [content],
 * which a press on the heading folds away and back. It opens expanded, and stays as it was left when the
 * screen comes back.
 *
 * Folding closes the section from its bottom up with the motion a lazy section's rows move by, so the
 * column's length - and its scroll, and whatever follows it - follows frame by frame. Collapsed while pinned,
 * the column goes back to the heading first, as a lazy list's does.
 *
 * Pinned only as far as its own section goes, so the next heading pushes it on as it arrives. Moved as the
 * column is placed, never recomposed, so a scroll costs nothing more for it.
 */
@Composable
fun ColumnScope.ColumnSection(
    title: String,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    actions: List<TopBarAction> = emptyList(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isCollapsed by rememberSaveable(title) { mutableStateOf(false) }
    // Where the section starts in the column, how tall it is and how tall its heading is, as last laid out.
    var sectionTop by remember { mutableIntStateOf(0) }
    var sectionHeight by remember { mutableIntStateOf(0) }
    var headerHeight by remember { mutableIntStateOf(0) }
    fun pinnedOffset() = (scrollState.value - sectionTop).coerceIn(0, (sectionHeight - headerHeight).coerceAtLeast(0))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onPlaced {
                sectionTop = it.positionInParent().y.roundToInt()
                sectionHeight = it.size.height
            },
    ) {
        SectionHeader(
            title = title,
            isCollapsed = isCollapsed,
            onToggle = {
                if (!isCollapsed && pinnedOffset() > 0) scope.launch { scrollState.scrollTo(sectionTop) }
                isCollapsed = !isCollapsed
            },
            isPinned = { pinnedOffset() > 0 },
            actions = actions,
            modifier = Modifier
                .onSizeChanged { headerHeight = it.height }
                .offset { IntOffset(0, pinnedOffset()) }
                // Over its own rows, which follow it in the column and would otherwise be drawn over it.
                .zIndex(1f),
        )
        val fade = SectionMotion.fade()
        AnimatedVisibility(
            visible = !isCollapsed,
            enter = expandVertically(SectionMotion.size, expandFrom = Alignment.Top) + fadeIn(fade),
            exit = shrinkVertically(SectionMotion.size, shrinkTowards = Alignment.Top) + fadeOut(fade),
        ) {
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
    }
}
