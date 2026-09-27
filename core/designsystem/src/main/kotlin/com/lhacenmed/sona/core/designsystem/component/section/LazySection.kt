package com.lhacenmed.sona.core.designsystem.component.section

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lhacenmed.sona.core.designsystem.component.TopBarAction

/**
 * The sections of one lazy list: which are collapsed, by their keys - kept across recreation, so a section
 * folded away stays folded when the screen comes back - and whether a heading is pinned over its rows. Every
 * section starts open.
 */
@Stable
class SectionListState internal constructor(
    collapsedKeys: Set<String>,
    private val listState: LazyListState,
) {
    /** The keys of the sections folded down to their headings. */
    var collapsedKeys by mutableStateOf(collapsedKeys)
        private set

    fun isCollapsed(key: String): Boolean = key in collapsedKeys

    /** Opens the section [key], if it was collapsed - a search, say, that has to show what it finds. */
    fun expand(key: String) {
        if (key in collapsedKeys) collapsedKeys = collapsedKeys - key
    }

    /**
     * Collapses or expands the section [key], whose heading is item [headerIndex]. A heading collapsed while
     * pinned over its own rows takes the list back to it: the rows the list kept its place by are going, and
     * the section under it is what the reader collapsed it to reach.
     */
    internal fun toggle(key: String, headerIndex: Int) {
        val isCollapsing = key !in collapsedKeys
        collapsedKeys = if (isCollapsing) collapsedKeys + key else collapsedKeys - key
        if (isCollapsing && isPinned(headerIndex)) listState.requestScrollToItem(headerIndex)
    }

    /** Whether the heading at [headerIndex] is held at the top while its section scrolls under it. */
    internal fun isPinned(headerIndex: Int): Boolean {
        val first = listState.firstVisibleItemIndex
        return first > headerIndex || first == headerIndex && listState.firstVisibleItemScrollOffset > 0
    }
}

/** A [SectionListState] for the list [listState] scrolls. */
@Composable
fun rememberSectionListState(listState: LazyListState): SectionListState =
    rememberSaveable(
        listState,
        saver = Saver(
            save = { ArrayList(it.collapsedKeys) },
            restore = { SectionListState(it.toSet(), listState) },
        ),
    ) { SectionListState(emptySet(), listState) }

/**
 * One section of a lazy list: the line parting it from the section before - [hasDividerAbove] - its
 * [SectionHeader], pinned at the top of the list while its rows scroll under it, then its [content], left out
 * while [state] has it collapsed. A press on the heading folds it and back.
 *
 * Folding is the list's own: its rows leave or join the list, and every row the section lays out - its
 * heading and divider too - animates as a lazy list animates an item that comes, goes or moves, so the rows
 * fade while those after them glide into place, and only rows on screen animate. The list's length changes
 * at once, so its scroll, its fast scroller and whatever follows it answer to the new length from the first
 * frame. A section inside another animates as one level, never twice.
 *
 * Every item is keyed, and [key] must be unique in the list. A row that animates itself - a reorderable
 * row - is laid out in [withoutItemAnimation] instead.
 */
fun LazyListScope.section(
    key: String,
    title: String,
    state: SectionListState,
    actions: List<TopBarAction> = emptyList(),
    hasDividerAbove: Boolean = false,
    content: LazyListScope.() -> Unit,
) {
    val scope = AnimatedItemsScope.of(this)
    val isCollapsed = state.isCollapsed(key)
    if (hasDividerAbove) {
        scope.item(key = "section-divider-$key", contentType = SectionDividerContentType) { HorizontalDivider() }
    }
    scope.stickyHeader(key = "section-header-$key", contentType = SectionHeaderContentType) { headerIndex ->
        SectionHeader(
            title = title,
            isCollapsed = isCollapsed,
            onToggle = { state.toggle(key, headerIndex) },
            isPinned = { state.isPinned(headerIndex) },
            actions = actions,
        )
    }
    if (!isCollapsed) scope.content()
}

/**
 * This list without the item animation a [section] gives what it lays out - for rows that animate
 * themselves, which a second animation would only fight. Give them [sectionItemAnimation] to move as the
 * rest do.
 */
fun LazyListScope.withoutItemAnimation(): LazyListScope = (this as? AnimatedItemsScope)?.base ?: this

/** How a [section]'s items come, go and move - see [section]. Applied to an item's outermost layout. */
@Composable
fun LazyItemScope.sectionItemAnimation(): Modifier {
    val fade = SectionMotion.fade()
    return Modifier.animateItem(fadeInSpec = fade, placementSpec = SectionMotion.placement, fadeOutSpec = fade)
}

private const val SectionHeaderContentType = "section-header"
private const val SectionDividerContentType = "section-divider"

/**
 * [base] with every item laid out in [sectionItemAnimation] - a wrapper of its own, as the animation has to
 * sit on an item's outermost layout and a section cannot reach inside the rows it is given.
 */
private class AnimatedItemsScope(val base: LazyListScope) : LazyListScope {
    override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) =
        base.item(key, contentType) { Animated { content() } }

    override fun items(
        count: Int,
        key: ((index: Int) -> Any)?,
        contentType: (index: Int) -> Any?,
        itemContent: @Composable LazyItemScope.(index: Int) -> Unit,
    ) = base.items(count, key, contentType) { index -> Animated { itemContent(index) } }

    override fun stickyHeader(key: Any?, contentType: Any?, content: @Composable LazyItemScope.(Int) -> Unit) =
        base.stickyHeader(key, contentType) { index -> Animated { content(index) } }

    companion object {
        fun of(scope: LazyListScope): AnimatedItemsScope = scope as? AnimatedItemsScope ?: AnimatedItemsScope(scope)
    }
}

@Composable
private fun LazyItemScope.Animated(content: @Composable LazyItemScope.() -> Unit) {
    Box(modifier = sectionItemAnimation()) { this@Animated.content() }
}
