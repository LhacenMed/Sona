package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** The thumb's column along the list's end edge: Auxio's `spacing_mid_medium`. */
private val ThumbWidth = 12.dp

/** Auxio's `size_touchable_medium`. */
private val ThumbHeight = 56.dp

/** The bar drawn in the thumb: Auxio's `ui_scroll_thumb`, inset by `spacing_tiny`. */
private val ThumbBarWidth = 4.dp
private val ThumbBarInset = 4.dp

/** The least a touch on the thumb is given, and the gap between it and the popup: Auxio's `size_touchable_small`. */
private val MinTouchTargetSize = 48.dp

/** Auxio's `size_fast_scroll_popup`, and the popup's padding around its text. */
private val PopupSize = 96.dp
private val PopupHorizontalPadding = 24.dp
private val PopupVerticalPadding = 20.dp

private const val AUTO_HIDE_DELAY_MILLIS = 500L
private const val POPUP_HIDDEN_SCALE = 0.5f

/** The popup's burst turns a full turn over the list, from this resting tilt. */
private const val POPUP_BASE_ROTATION_DEGREES = 14f

/**
 * [content] - a list on [listState] - with Auxio's fast scroller over it: `FastScrollRecyclerView`.
 *
 * A thumb slides in along the list's end edge while the list scrolls and slides out
 * [AUTO_HIDE_DELAY_MILLIS] after it stops; a relayout never shows it. Dragging it moves the list in
 * proportion, and while it is dragged a popup beside it names the section of the first row on screen -
 * [sectionAt] of that row's index, "?" where it has none - with a tick of haptics each time that
 * changes. Without [sectionAt] there is no popup.
 *
 * Touches are Auxio's: the thumb takes any touch within a touch target's width of the edge, and a
 * touch on the edge's outermost sliver takes the thumb straight to the finger. A vertical drag
 * starting anywhere in that width does the same once it passes the touch slop. The thumb stays clear
 * of [LocalBottomContentPadding], so it never slides under the mini player.
 *
 * Only while [enabled], and only for a list with somewhere to scroll.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FastScroller(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    sectionAt: ((index: Int) -> String?)? = null,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state = remember(listState, scope) { FastScrollerState(listState, scope) }
    val canFastScroll by remember(listState) {
        derivedStateOf { listState.canScrollForward || listState.canScrollBackward }
    }
    val isActive = enabled && canFastScroll
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    val bottomPaddingPx = with(density) { LocalBottomContentPadding.current.toPx() }
    val thumbHeightPx = with(density) { ThumbHeight.toPx() }

    // Shown by the list moving under a scroll of its own - a finger, a fling - never by a relayout.
    LaunchedEffect(state, isActive) {
        if (!isActive) {
            state.hide()
            return@LaunchedEffect
        }
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .drop(1)
            .collect { if (listState.isScrollInProgress) state.onScrolled() }
    }

    Box(
        modifier = modifier.pointerInput(state, isActive, isRtl, bottomPaddingPx) {
            if (!isActive) return@pointerInput
            val thumbWidthPx = ThumbWidth.toPx()
            val minTouchTargetPx = MinTouchTargetSize.toPx()
            fun thumbRangePx() = thumbRangePx(size.height, bottomPaddingPx, thumbHeightPx)
            fun thumbTopPx() = listState.scrollFraction() * thumbRangePx()
            // The thumb's column, and the touch target around it - as wide as a touch target, kept
            // within the list: Auxio's `isUnder`. The thumb is taller than a touch target already.
            val columnStart = if (isRtl) 0f else size.width - thumbWidthPx
            val targetStart = if (isRtl) 0f else size.width - max(minTouchTargetPx, thumbWidthPx)
            fun isInColumn(x: Float) = x >= columnStart && x < columnStart + thumbWidthPx
            fun isInTarget(x: Float) = x >= targetStart && x < targetStart + max(minTouchTargetPx, thumbWidthPx)
            fun isOnThumb(x: Float, y: Float) = isInTarget(x) && y >= thumbTopPx() && y < thumbTopPx() + thumbHeightPx
            // The column's outermost quarter, where a touch takes the thumb straight to the finger.
            fun isAtOuterEdge(x: Float) = if (isRtl) x < thumbWidthPx / 4 else x > size.width - thumbWidthPx / 4
            fun thumbTopUnder(y: Float) = y - thumbHeightPx / 2

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var ownsGesture = false
                var dragStartY = 0f
                var dragStartThumbTop = 0f
                fun startDragging(fromY: Float, fromThumbTop: Float) {
                    dragStartY = fromY
                    dragStartThumbTop = fromThumbTop
                    ownsGesture = true
                    state.startDragging()
                }

                val downX = down.position.x
                val downY = down.position.y
                if (isInColumn(downX)) {
                    if (isOnThumb(downX, downY)) {
                        startDragging(downY, thumbTopPx())
                    } else if (isAtOuterEdge(downX)) {
                        listState.scrollToThumbTop(thumbTopUnder(downY), thumbRangePx())
                        startDragging(downY, thumbTopUnder(downY))
                    }
                }
                if (ownsGesture) down.consume()

                var lastY = downY
                try {
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes
                            .firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (ownsGesture) change.consume()
                            break
                        }
                        val y = change.position.y
                        if (!ownsGesture && isInTarget(downX) && abs(y - downY) > viewConfiguration.touchSlop) {
                            if (isOnThumb(downX, downY)) {
                                startDragging(lastY, thumbTopPx())
                            } else {
                                listState.scrollToThumbTop(thumbTopUnder(y), thumbRangePx())
                                startDragging(y, thumbTopUnder(y))
                            }
                        }
                        if (ownsGesture) {
                            listState.scrollToThumbTop(dragStartThumbTop + (y - dragStartY), thumbRangePx())
                            change.consume()
                        }
                        lastY = y
                    }
                } finally {
                    if (ownsGesture) state.stopDragging()
                }
            }
        },
    ) {
        content()

        val hiddenFraction by animateFloatAsState(
            targetValue = if (state.isThumbShown) 0f else 1f,
            animationSpec = if (state.isThumbShown) {
                MaterialTheme.motionScheme.defaultSpatialSpec()
            } else {
                MaterialTheme.motionScheme.fastSpatialSpec()
            },
            label = "fastScrollThumb",
        )
        val thumbColor = MaterialTheme.colorScheme.secondary
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .layout { measurable, constraints ->
                    val thumb = measurable.measure(Constraints())
                    layout(thumb.width, constraints.maxHeight) {
                        val top = listState.scrollFraction() * thumbRangePx(constraints.maxHeight, bottomPaddingPx, thumbHeightPx)
                        thumb.placeRelativeWithLayer(0, top.roundToInt()) {
                            // Slid out past the end edge, whichever side that is.
                            translationX = hiddenFraction * thumb.width * if (isRtl) -1 else 1
                        }
                    }
                }
                .size(ThumbWidth, ThumbHeight)
                .padding(ThumbBarInset),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(ThumbBarWidth)
                    .fillMaxHeight()
                    .background(thumbColor, CircleShape),
            )
        }

        AnimatedVisibility(
            visible = state.isDragging && sectionAt != null,
            enter = scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec(), POPUP_HIDDEN_SCALE) +
                fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = scaleOut(MaterialTheme.motionScheme.defaultSpatialSpec(), POPUP_HIDDEN_SCALE) +
                fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = ThumbWidth + MinTouchTargetSize)
                .layout { measurable, constraints ->
                    val popup = measurable.measure(Constraints())
                    layout(popup.width, constraints.maxHeight) {
                        val thumbTop = listState.scrollFraction() *
                            thumbRangePx(constraints.maxHeight, bottomPaddingPx, thumbHeightPx)
                        // Centred on the thumb, and kept within the list above the mini player.
                        val top = (thumbTop + (thumbHeightPx - popup.height) / 2)
                            .coerceAtMost(constraints.maxHeight - bottomPaddingPx - popup.height)
                            .coerceAtLeast(0f)
                        popup.placeRelative(0, top.roundToInt())
                    }
                },
        ) {
            val latestSectionAt by rememberUpdatedState(sectionAt)
            val section by remember(listState) {
                derivedStateOf { latestSectionAt?.invoke(listState.firstVisibleItemIndex) ?: "?" }
            }
            val haptics = LocalHapticFeedback.current
            val isShowing by rememberUpdatedState(transition.targetState == EnterExitState.Visible)
            LaunchedEffect(Unit) {
                snapshotFlow { section }.drop(1).collect {
                    if (isShowing) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
            FastScrollPopup(section = section, rotationDegrees = { listState.scrollFraction() * 360f })
        }
    }
}

/**
 * The popup: [section] on Material's soft burst, which turns with the list - Auxio's
 * `SoftBurstPopupDrawable`. [rotationDegrees] is read only while drawing, so scrolling redraws the burst
 * without recomposing anything.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FastScrollPopup(section: String, rotationDegrees: () -> Float) {
    val burstShape = MaterialShapes.SoftBurst.toShape()
    Box(modifier = Modifier.size(PopupSize), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = POPUP_BASE_ROTATION_DEGREES + rotationDegrees() }
                .background(MaterialTheme.colorScheme.secondary, burstShape),
        )
        Text(
            text = section,
            color = MaterialTheme.colorScheme.onSecondary,
            style = MaterialTheme.typography.headlineMediumEmphasized,
            textAlign = TextAlign.Center,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 1.sp, maxFontSize = 32.sp),
            modifier = Modifier.padding(horizontal = PopupHorizontalPadding, vertical = PopupVerticalPadding),
        )
    }
}

/** Whether the thumb is out and whether it is being dragged - Auxio's `showingThumb` and `dragging`. */
@Stable
private class FastScrollerState(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
) {
    var isThumbShown by mutableStateOf(false)
        private set

    var isDragging by mutableStateOf(false)
        private set

    private var hideJob: Job? = null

    fun onScrolled() {
        isThumbShown = true
        hideAfterDelay()
    }

    fun startDragging() {
        isDragging = true
        isThumbShown = true
        hideJob?.cancel()
        // A fling still running would fight the thumb for the list.
        scope.launch { listState.stopScroll() }
    }

    fun stopDragging() {
        isDragging = false
        hideAfterDelay()
    }

    fun hide() {
        hideJob?.cancel()
        isDragging = false
        isThumbShown = false
    }

    private fun hideAfterDelay() {
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(AUTO_HIDE_DELAY_MILLIS)
            if (!isDragging) isThumbShown = false
        }
    }
}

/** How far the thumb can travel down a list [heightPx] tall, stopping [bottomPaddingPx] short of its end. */
private fun thumbRangePx(heightPx: Int, bottomPaddingPx: Float, thumbHeightPx: Float): Float =
    (heightPx - bottomPaddingPx - thumbHeightPx).coerceAtLeast(0f)

/**
 * How far through its content the list is scrolled, 0 at its top and 1 at its end.
 *
 * A lazy list only knows the rows it has laid out, so the rest are taken to be as tall as those on
 * screen - the estimate `RecyclerView`'s own scrollbar makes, and so Auxio's. Pinned to its ends
 * wherever the list cannot scroll further, so the thumb reaches them exactly.
 */
private fun LazyListState.scrollFraction(): Float {
    if (!canScrollBackward) return 0f
    if (!canScrollForward) return 1f
    val range = estimatedScrollRangePx()
    return if (range > 0f) (estimatedScrollOffsetPx() / range).coerceIn(0f, 1f) else 0f
}

private fun LazyListState.averageItemSizePx(): Float {
    val items = layoutInfo.visibleItemsInfo
    if (items.isEmpty()) return 0f
    return (items.last().offset + items.last().size - items.first().offset).toFloat() / items.size
}

private fun LazyListState.estimatedScrollOffsetPx(): Float =
    firstVisibleItemIndex * averageItemSizePx() + firstVisibleItemScrollOffset

private fun LazyListState.estimatedScrollRangePx(): Float {
    val info = layoutInfo
    return averageItemSizePx() * info.totalItemsCount + info.beforeContentPadding + info.afterContentPadding -
        info.viewportSize.height
}

/**
 * Puts the list where a thumb at [thumbTopPx] stands for - Auxio's `scrollToThumbOffset`, by row
 * rather than by pixels.
 *
 * Auxio scrolls by the pixels between where the thumb was and where it is, which a `RecyclerView` can
 * do cheaply. A lazy list cannot: to scroll some distance it lays out every row it passes, one at a
 * time, to learn how tall each is - so a thumb dragged down a long list had it compose thousands of
 * rows off screen in a single frame. Here the thumb's place is turned into a row and an offset into
 * it, by the same estimate the thumb is placed by, and the list is simply put there: only the rows
 * that end up on screen are laid out, however long the list, and a request made several times between
 * frames is laid out once.
 */
private fun LazyListState.scrollToThumbTop(thumbTopPx: Float, thumbRangePx: Float) {
    val averageItemSize = averageItemSizePx()
    if (thumbRangePx <= 0f || averageItemSize <= 0f) return
    val fraction = thumbTopPx.coerceIn(0f, thumbRangePx) / thumbRangePx
    val targetOffset = fraction * estimatedScrollRangePx().coerceAtLeast(0f)
    val index = (targetOffset / averageItemSize).toInt().coerceAtMost(layoutInfo.totalItemsCount - 1)
    requestScrollToItem(index, (targetOffset - index * averageItemSize).roundToInt())
}
