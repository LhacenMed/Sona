@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.designsystem.theme.SonaComponentStyle
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * How far a detail screen's header has collapsed into its bar - Auxio's `AppBarLayout` offset.
 *
 * The header is not part of the list: it collapses by taking the scroll before the list does
 * ([nestedScrollConnection]), as an `exitUntilCollapsed` app bar does. Scrolling up collapses it
 * first and scrolls the list after; scrolling down scrolls the list back to its top first and opens
 * the header after. So it collapses the same over one row as over a thousand, and nothing about the
 * list's length or contents can leave it stuck part-way. Every value here is read while laying out
 * or drawing, never in composition, so a collapse recomposes nothing.
 */
@Stable
class DetailHeaderState internal constructor(
    val listState: LazyListState,
    private val scope: CoroutineScope,
) {

    internal var headerHeightPx by mutableIntStateOf(0)
    internal var barHeightPx by mutableIntStateOf(0)

    /** How far the header has collapsed, 0 when open. Kept within [collapseRangePx] when read. */
    private var collapsedPx by mutableFloatStateOf(0f)

    /** The header's height less the bar's: how far it collapses before the list moves. */
    internal val collapseRangePx: Float
        get() = (headerHeightPx - barHeightPx).coerceAtLeast(0).toFloat()

    internal val collapsedOffsetPx: Float
        get() = collapsedPx.coerceIn(0f, collapseRangePx)

    /** 0 with the header open, 1 once it has collapsed into the bar. */
    val collapse: Float
        get() = if (collapseRangePx == 0f) 0f else collapsedOffsetPx / collapseRangePx

    /** Whether the list has scrolled on under the bar once the header was out of the way. */
    val isLifted: Boolean
        get() = collapse >= 1f && listState.canScrollBackward

    /** Whether the header is held collapsed out of the way - see [DetailScaffold]'s `isHeaderAside`. */
    internal var isHeaderAside = false
        private set

    private var settleJob: Job? = null

    /**
     * Collapses the header by [delta] of a scroll - negative up, positive down - as far as it goes,
     * and returns how much of [delta] that took. Any settle in flight gives way: the finger has it.
     */
    internal fun collapseBy(delta: Float): Float {
        settleJob?.cancel()
        val before = collapsedOffsetPx
        collapsedPx = (before - delta).coerceIn(0f, collapseRangePx)
        return before - collapsedPx
    }

    /**
     * Settles a header let go part-way open or collapsed, whichever is nearer - Auxio's `snap`.
     *
     * In a job of its own, which the next touch cancels ([collapseBy]); whatever watches the scroll
     * never runs the settle itself, so interrupting one can never stop the next.
     */
    internal fun settle() {
        val from = collapsedOffsetPx
        val range = collapseRangePx
        if (from <= 0f || from >= range) return
        val target = if (from < range / 2) 0f else range
        settleJob = scope.launch { animate(from, target) { value, _ -> collapsedPx = value } }
    }

    internal val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (available.y < 0f && !isHeaderAside) Offset(0f, collapseBy(available.y)) else Offset.Zero

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            if (available.y > 0f && !isHeaderAside) Offset(0f, collapseBy(available.y)) else Offset.Zero

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            settle()
            return Velocity.Zero
        }
    }

    /** Where the header and the list stood when the header was set aside, to be put back after. */
    private var positionBeforeAside: Triple<Float, Int, Int>? = null

    internal fun updateHeaderAside(isAside: Boolean) {
        if (isAside == isHeaderAside) return
        isHeaderAside = isAside
        settleJob?.cancel()
        if (isAside) {
            positionBeforeAside =
                Triple(collapsedOffsetPx, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            collapsedPx = collapseRangePx
            listState.requestScrollToItem(0)
        } else {
            val (collapsed, index, offset) = positionBeforeAside ?: return
            positionBeforeAside = null
            collapsedPx = collapsed
            listState.requestScrollToItem(index, offset)
        }
    }
}

/** A [DetailHeaderState] for a screen opening with its header open and its list at the top. */
@Composable
fun rememberDetailHeaderState(listState: LazyListState = rememberLazyListState()): DetailHeaderState {
    val scope = rememberCoroutineScope()
    return remember(listState, scope) { DetailHeaderState(listState, scope) }
}

/** Auxio's `layout_collapseParallaxMultiplier`: how much of the collapse the header is held back by. */
private const val HeaderParallax = 0.85f

/** How much smaller the header gets on its way out - Auxio's `0.2 / (5 / 3)`. */
private const val HeaderShrink = 0.12f

/**
 * A detail screen: a header that collapses into the [bar] as its [content] scrolls up - Auxio's
 * `fragment_detail`, a `CollapsingToolbarLayout` in an `AppBarLayout` over a list.
 *
 * Laid out as Auxio's is. The list is as tall as the screen below the collapsed bar and starts where
 * the header ends, so a collapse only moves things - nothing is measured again. The header is held
 * back by [HeaderParallax] as it goes and cut off at its bottom edge, so it slides out from under
 * itself; over the first half of the collapse it shrinks and fades away (Auxio's `onOffsetChanged`),
 * and a divider stays pinned where it ends. Let go part-way, and it settles open or collapsed. The
 * header can be dragged itself, and a drag that collapses it all the way carries on into the list,
 * as Auxio's `ContinuousAppBarLayoutBehavior` makes it.
 *
 * While [isHeaderAside] - a screen being searched - the header is collapsed out of the way and stays
 * so, with the list from its top straight under the bar. When it comes back, so do the header and the
 * list, exactly where they were.
 *
 * [contentKey] is what the list shows. A screen opens at its top, and a list at its top stays there as
 * its rows arrive or change - see the note in the body.
 *
 * [dragSelection], where the list's rows can be selected, lets a long press drag across them.
 */
@Composable
fun DetailScaffold(
    state: DetailHeaderState,
    bar: @Composable () -> Unit,
    header: @Composable () -> Unit,
    isHeaderAside: Boolean,
    contentKey: Any?,
    modifier: Modifier = Modifier,
    dragSelection: DragSelection? = null,
    content: LazyListScope.() -> Unit,
) {
    val listState = state.listState
    val barHeight = with(LocalDensity.current) { state.barHeightPx.toDp() }

    // A list follows its first visible row wherever that row moves, so rows arriving above it - an
    // artist's albums, loaded after its tracks - would open the screen scrolled to its tracks. At the
    // top there is no place to keep, so a list that was at its top when [contentKey] changed stays at
    // its top; one scrolled anywhere else is left where it is. Where it stood is read as the change
    // arrives, before the new rows are laid out. The frame the header is set aside or brought back
    // puts the list where it belongs itself, and is left to it.
    val wasAtTop = remember(contentKey) {
        Snapshot.withoutReadObservation {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }
    DisposableEffect(contentKey) {
        if (wasAtTop && state.isHeaderAside == isHeaderAside) listState.requestScrollToItem(0)
        onDispose {}
    }

    DisposableEffect(state, isHeaderAside) {
        state.updateHeaderAside(isHeaderAside)
        onDispose {}
    }

    val headerDrag = rememberDraggableState { delta ->
        val remaining = delta - state.collapseBy(delta)
        if (remaining != 0f) listState.dispatchRawDelta(-remaining)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Layout(
            contents = listOf(
                {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { state.headerHeightPx = it.height }
                            .draggable(
                                state = headerDrag,
                                orientation = Orientation.Vertical,
                                enabled = !isHeaderAside,
                                onDragStopped = { state.settle() },
                            )
                            // Beneath the bar: the header starts where the bar ends, as Auxio's pads
                            // itself by the toolbar's height.
                            .padding(top = barHeight)
                            .graphicsLayer {
                                val out = min(state.collapse * 2, 1f)
                                scaleX = 1 - HeaderShrink * out
                                scaleY = 1 - HeaderShrink * out
                                alpha = 1 - out
                            },
                    ) {
                        header()
                    }
                },
                { HorizontalDivider() },
                {
                    // Fast scrolling only once the header has collapsed, as Auxio's detail list allows it:
                    // the thumb moves the list directly, past the header's collapse, which would leave
                    // the header standing open over a list scrolled somewhere else.
                    val isCollapsed by remember(state) { derivedStateOf { state.collapse == 1f } }
                    FastScroller(listState = listState, enabled = isCollapsed) {
                        CompositionLocalProvider(LocalDragSelection provides dragSelection) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .then(dragSelection?.let(Modifier::dragSelection) ?: Modifier)
                                    .nestedScroll(state.nestedScrollConnection),
                                contentPadding = PaddingValues(bottom = LocalBottomContentPadding.current),
                                content = content,
                            )
                        }
                    }
                },
            ),
        ) { (headerMeasurables, dividerMeasurables, listMeasurables), constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val looseWidth = Constraints.fixedWidth(width)
            val headerPlaceable = headerMeasurables.single().measure(looseWidth)
            val dividerPlaceable = dividerMeasurables.single().measure(looseWidth)
            // The room left below the collapsed bar - the most the list can ever be shown in.
            val listHeight = (height - state.barHeightPx).coerceAtLeast(0)
            val listPlaceable = listMeasurables.single().measure(Constraints.fixed(width, listHeight))

            layout(width, height) {
                val collapsed = state.collapsedOffsetPx
                val headerBottom = (headerPlaceable.height - collapsed).roundToInt()
                headerPlaceable.placeWithLayer(0, 0) {
                    translationY = -collapsed * (1 - HeaderParallax)
                    // Cut off where the collapsing app bar would end: the header's own bottom, moved up
                    // by the collapse and back down by the parallax.
                    shape = GenericShape { size, _ ->
                        addRect(Rect(0f, 0f, size.width, size.height - collapsed * HeaderParallax))
                    }
                    clip = true
                }
                dividerPlaceable.place(0, headerBottom - dividerPlaceable.height)
                // Last, so a touch below where the header is cut off is the list's, not the header's.
                listPlaceable.place(0, headerBottom)
            }
        }

        Box(modifier = Modifier.onSizeChanged { state.barHeightPx = it.height }) {
            bar()
        }
    }
}

/**
 * A detail screen's header: its cover, what kind of collection it is, its name, a line or two about
 * it, then Play and Shuffle - Auxio's `detail_header` on a tall screen. [subhead] is left out when
 * there is nothing to say there, as Auxio hides its line.
 */
@Composable
fun DetailHeader(
    cover: @Composable () -> Unit,
    type: String,
    name: String,
    subhead: String?,
    info: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    playLabel: String,
    shuffleLabel: String,
    modifier: Modifier = Modifier,
    isPlayable: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { cover() }
        Spacer(Modifier.size(16.dp))
        Text(
            text = type,
            style = MaterialTheme.typography.labelMediumEmphasized,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            text = name,
            style = MaterialTheme.typography.titleLargeEmphasized,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subhead != null) {
            Text(
                text = subhead,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = info,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.size(12.dp))
        DetailPlaybackButtons(
            onPlay = onPlay,
            onShuffle = onShuffle,
            playLabel = playLabel,
            shuffleLabel = shuffleLabel,
            isPlayable = isPlayable,
        )
    }
}

/**
 * The header's Play and Shuffle, sharing its width - Auxio's `MaterialButtonGroup` of a tonal and a
 * filled button. They press as every labelled button does ([buttonPressShapes]) and hand width to one
 * another as a bar's buttons do ([SonaComponentStyle.PressedExpandedRatio]).
 */
@Composable
private fun DetailPlaybackButtons(
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    playLabel: String,
    shuffleLabel: String,
    isPlayable: Boolean,
) {
    val playInteraction = remember { MutableInteractionSource() }
    val shuffleInteraction = remember { MutableInteractionSource() }
    ButtonGroup(
        overflowIndicator = {},
        modifier = Modifier.fillMaxWidth(),
        expandedRatio = SonaComponentStyle.PressedExpandedRatio,
        horizontalArrangement = Arrangement.spacedBy(SonaComponentStyle.ItemSpacing),
    ) {
        customItem(
            buttonGroupContent = {
                FilledTonalButton(
                    onClick = onPlay,
                    shapes = buttonPressShapes(),
                    enabled = isPlayable,
                    interactionSource = playInteraction,
                    modifier = Modifier
                        .weight(1f)
                        .animateWidth(playInteraction),
                ) {
                    ButtonContent(icon = { Icon(SonaIcons.Play, contentDescription = null) }, label = playLabel)
                }
            },
            menuContent = {},
        )
        customItem(
            buttonGroupContent = {
                Button(
                    onClick = onShuffle,
                    shapes = buttonPressShapes(),
                    enabled = isPlayable,
                    interactionSource = shuffleInteraction,
                    modifier = Modifier
                        .weight(1f)
                        .animateWidth(shuffleInteraction),
                ) {
                    ButtonContent(icon = { Icon(SonaIcons.Shuffle, contentDescription = null) }, label = shuffleLabel)
                }
            },
            menuContent = {},
        )
    }
}

@Composable
private fun ButtonContent(icon: @Composable () -> Unit, label: String) {
    Box(modifier = Modifier.size(ButtonDefaults.IconSize)) { icon() }
    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
    Text(label)
}

/**
 * The heading of one section of a detail screen's list - "Albums", "Tracks" - with, on the section
 * that can be sorted, its sort button at the end: Auxio's `item_header` and `item_sort_header`.
 */
@Composable
fun DetailSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
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
        trailing?.invoke()
    }
}
