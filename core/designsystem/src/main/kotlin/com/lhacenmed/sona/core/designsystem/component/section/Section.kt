@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.core.designsystem.component.section

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.lhacenmed.sona.core.designsystem.R
import com.lhacenmed.sona.core.designsystem.component.SonaIconButtonGroup
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.iconButton
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * The heading of one section of a list - "Albums", "Tracks", "Contributors": Auxio's `item_header`, the one
 * heading every sectioned list in the app has, a detail screen's and a settings screen's alike.
 *
 * What acts on the section sits at its end - its [actions], as one group of buttons like a top bar's. A
 * section that can be collapsed ends that group with the button that collapses it and back - [isCollapsed]
 * non-null, flipped by [onToggleCollapsed] - and is collapsed or expanded by a press anywhere on its heading
 * too, the widest target there is.
 *
 * It stays at the top of its list while its section scrolls under it - see [section] and [ColumnSection] -
 * so it is painted in the rows' own colour, and takes every press on it: a heading that let one through
 * would press the row it is pinned over, out of sight.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actions: List<TopBarAction> = emptyList(),
    isCollapsed: Boolean? = null,
    onToggleCollapsed: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (isCollapsed != null) Modifier.clickable(onClick = onToggleCollapsed) else Modifier.pointerInput(Unit) {},
            )
            .heightIn(min = 48.dp)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f),
        )
        if (actions.isNotEmpty() || isCollapsed != null) {
            // Read here rather than inside the group: a group builds its items outside composition.
            val toggleLabel = stringResource(
                if (isCollapsed == true) R.string.section_expand else R.string.section_collapse,
            )
            // Points up while the section is open - it closes upwards - and turns down once it has.
            val chevronRotation by animateFloatAsState(if (isCollapsed == true) 180f else 0f, label = "sectionChevron")
            SonaIconButtonGroup {
                actions.forEach { action ->
                    iconButton(icon = action.icon, label = action.label, onClick = action.onClick, enabled = action.enabled)
                }
                if (isCollapsed != null) {
                    iconButton(
                        icon = Icons.Filled.ExpandLess,
                        label = toggleLabel,
                        onClick = onToggleCollapsed,
                        modifier = Modifier.rotate(chevronRotation),
                    )
                }
            }
        }
    }
}

/**
 * Which of a lazy list's sections are collapsed, by their keys - kept across recreation, so a section folded
 * away stays folded when the screen comes back. Every section starts open.
 */
@Stable
class SectionCollapseState internal constructor(
    collapsedKeys: Set<String>,
    private val listState: LazyListState,
) {
    internal var collapsedKeys by mutableStateOf(collapsedKeys)
        private set

    fun isCollapsed(key: String): Boolean = key in collapsedKeys

    /**
     * Collapses or expands the section [key], whose heading is item [headerIndex]. A heading collapsed while
     * pinned over its own rows takes the list back to it: the rows the list kept its place by are gone, and
     * the section under it is what the reader collapsed it to reach.
     */
    internal fun toggle(key: String, headerIndex: Int) {
        val isCollapsing = key !in collapsedKeys
        collapsedKeys = if (isCollapsing) collapsedKeys + key else collapsedKeys - key
        if (isCollapsing && headerIndex <= listState.firstVisibleItemIndex) listState.requestScrollToItem(headerIndex)
    }
}

/** A [SectionCollapseState] for the list [listState] scrolls. */
@Composable
fun rememberSectionCollapseState(listState: LazyListState): SectionCollapseState =
    rememberSaveable(
        listState,
        saver = Saver(
            save = { ArrayList(it.collapsedKeys) },
            restore = { SectionCollapseState(it.toSet(), listState) },
        ),
    ) { SectionCollapseState(emptySet(), listState) }

/**
 * One section of a lazy list: the line parting it from the section before - [hasDividerAbove] - its
 * [SectionHeader], pinned at the top of the list while its rows scroll under it, then its [content], left out
 * while [collapse] has it collapsed.
 *
 * Collapsing takes away only rows below the heading pressed, so the list stays where it is - or, pinned,
 * comes back to it; see [SectionCollapseState.toggle]. Every item is keyed by [key], which must be unique in
 * the list. Without [collapse] the section cannot be collapsed.
 */
fun LazyListScope.section(
    key: String,
    title: String,
    collapse: SectionCollapseState? = null,
    actions: List<TopBarAction> = emptyList(),
    hasDividerAbove: Boolean = false,
    content: LazyListScope.() -> Unit,
) {
    if (hasDividerAbove) item(key = "section-divider-$key") { HorizontalDivider() }
    val isCollapsed = collapse?.isCollapsed(key)
    stickyHeader(key = "section-header-$key") { headerIndex ->
        SectionHeader(
            title = title,
            actions = actions,
            isCollapsed = isCollapsed,
            onToggleCollapsed = { collapse?.toggle(key, headerIndex) },
        )
    }
    if (isCollapsed != true) content()
}

/**
 * One section of a column scrolled by [scrollState] - [section], for a list short enough to be one column:
 * its [SectionHeader] pinned at the top of the column while its rows scroll under it, then its [content],
 * left out while [isCollapsed].
 *
 * Pinned only as far as its own section goes, so the next heading pushes it on as it arrives. Moved as the
 * column is placed, never recomposed, so a scroll costs nothing more for it. Collapsed while pinned, the
 * column goes back to it, as a lazy list's does.
 */
@Composable
fun ColumnScope.ColumnSection(
    title: String,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    actions: List<TopBarAction> = emptyList(),
    isCollapsed: Boolean? = null,
    onToggleCollapsed: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    // Where the section starts in the column, how tall it is and how tall its heading is, as last laid out.
    var sectionTop by remember { mutableIntStateOf(0) }
    var sectionHeight by remember { mutableIntStateOf(0) }
    var headerHeight by remember { mutableIntStateOf(0) }

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
            actions = actions,
            isCollapsed = isCollapsed,
            onToggleCollapsed = {
                if (isCollapsed == false && scrollState.value > sectionTop) {
                    scope.launch { scrollState.scrollTo(sectionTop) }
                }
                onToggleCollapsed()
            },
            modifier = Modifier
                .onSizeChanged { headerHeight = it.height }
                .offset {
                    val pinned = (scrollState.value - sectionTop).coerceIn(0, (sectionHeight - headerHeight).coerceAtLeast(0))
                    IntOffset(0, pinned)
                }
                // Over its own rows, which follow it in the column and would otherwise be drawn over it.
                .zIndex(1f),
        )
        if (isCollapsed != true) content()
    }
}
