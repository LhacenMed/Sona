package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.theme.LocalIsRounded
import com.lhacenmed.sona.core.designsystem.theme.SonaComponentStyle
import com.lhacenmed.sona.core.designsystem.theme.pressedCornerRadius
import com.lhacenmed.sona.core.designsystem.theme.rememberPressFraction
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val TabRowHeight = 44.dp
private val TabRowPadding = 4.dp

/** How the tabs a drag passes make way, and how the one let go settles into its place. */
private val TabMoveMotion = spring<Float>()

/** How long a tab let go takes to set down its lift, once it is in its place. */
private const val TabSetDownMillis = 150

/**
 * A tab held and being dragged along the row, from the place [from] - see [SonaTabRow]. [selected] is where the strip's
 * selection stood as it was lifted, which it keeps until the row takes its new order: nothing chooses a tab while one is held.
 */
private class TabDrag(val from: Int, val selected: Float) {
    /**
     * How lifted it is, 0..1: lifted, it wears the selected tab's pill and label over whatever it passes, and keeps the
     * look of its press - wider, its corners tighter - for as long as it is held, however the press itself ends.
     */
    val lift = Animatable(1f)

    /**
     * How far the row has gone, 0..1, from the group a press makes - the held tab widened, its neighbours giving way - to
     * the drag's: every other tab at rest in the place it steps to.
     */
    val spread = Animatable(0f)

    /** How far the finger has carried it from its place, in pixels. */
    var offset by mutableFloatStateOf(0f)

    /** The place it would be let go into. */
    var target by mutableIntStateOf(from)

    /** Whether it has been let go, and is settling into [target]. */
    var isDropped = false
}

/**
 * A tab strip whose selected tab wears a rounded pill.
 *
 * [selectedPosition] is a lambda returning a *continuous* position (e.g. a pager's
 * `currentPage + currentPageOffsetFraction`) rather than a discrete index, so the selected pill and
 * the label colors track a swipe under the finger instead of jumping once it settles. Passing it as
 * a lambda keeps the state read inside this component: the caller - which typically also hosts the
 * pager itself - never reads it, so a swipe recomposes only the few small nodes below rather than
 * the paged content.
 *
 * Holding a tab widens it and tightens its corners, the way an expressive button group answers a
 * press. The tabs and the pill are laid out by one layout from the same widths, so the pill stretches
 * and squares off with the tab beneath it in the very same frame - never a frame behind. The row's
 * own width and the gaps between tabs never change: a held tab's neighbours give up the width it takes.
 *
 * Given [onMoveTab], a tab held long is lifted and dragged along the row into another place: the others make way as it
 * passes, and the move is told once, as it is let go. The row stays as the drag left it until [tabTitles] arrive in
 * their new order, so nothing goes back for a moment in between.
 */
@Composable
fun SonaTabRow(
    tabTitles: List<String>,
    selectedPosition: () -> Float,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onMoveTab: ((from: Int, to: Int) -> Unit)? = null,
) {
    if (tabTitles.isEmpty()) return

    val tabCount = tabTitles.size
    val interactionSources = remember(tabCount) { List(tabCount) { MutableInteractionSource() } }
    // Each tab's press as an animated 0..1 fraction, read only in the layout and draw phases below,
    // so a press animates without recomposing anything.
    val pressFractions = interactionSources.map { rememberPressFraction(it) }
    val thumbColor = MaterialTheme.colorScheme.secondaryContainer
    // Read here for the draw phase below, which cannot read the composition: square while round mode is off.
    val isRounded = LocalIsRounded.current

    // Started afresh with each order of tabs, so the one a drag leaves gives way in the very frame the titles arrive in it.
    var drag by remember(tabTitles) { mutableStateOf<TabDrag?>(null) }
    // How many places each tab has stepped aside for the one being dragged.
    val shifts = remember(tabTitles) { List(tabCount) { Animatable(0f) } }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val currentTitles by rememberUpdatedState(tabTitles)
    val currentOnMoveTab by rememberUpdatedState(onMoveTab)
    // Where the selection stands - held, while a tab is dragged.
    val position = { drag?.selected ?: selectedPosition() }
    // How pressed each tab looks: by its press, or - lifted - by its lift, which outlasts the press a drag ends.
    val pressOf = { tab: Int -> maxOf(pressFractions[tab].value, drag?.takeIf { it.from == tab }?.lift?.value ?: 0f) }
    LaunchedEffect(drag) {
        val moving = drag ?: return@LaunchedEffect
        launch { moving.spread.animateTo(1f, TabMoveMotion) }
        snapshotFlow { moving.target }.collect { target ->
            shifts.forEachIndexed { tab, shift ->
                val steps = when {
                    tab in (moving.from + 1)..target -> -1f
                    tab in target until moving.from -> 1f
                    else -> 0f
                }
                if (tab != moving.from) launch { shift.animateTo(steps, TabMoveMotion) }
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(TabRowHeight)
            // Vertical only: the outer tabs' edges are the row's own, so the row lines up with
            // whatever the caller aligns it to.
            .padding(vertical = TabRowPadding),
    ) {
        val restingTabWidth = ((maxWidth - SonaComponentStyle.ItemSpacing * (tabCount - 1)) / tabCount).coerceAtLeast(0.dp)
        // From one tab's place to the next's.
        val placeWidth = with(LocalDensity.current) { (restingTabWidth + SonaComponentStyle.ItemSpacing).toPx() }

        fun letGo(moving: TabDrag) {
            if (moving.isDropped) return
            moving.isDropped = true
            val titles = tabTitles
            scope.launch {
                animate(moving.offset, (moving.target - moving.from) * placeWidth, animationSpec = TabMoveMotion) { value, _ ->
                    moving.offset = value
                }
                if (moving.target == moving.from) {
                    // Back where it was: the row gathers into its pressed group again as the tab is set down, and rests.
                    launch { moving.spread.animateTo(0f, TabMoveMotion) }
                    moving.lift.animateTo(0f, tween(TabSetDownMillis))
                } else {
                    // Set down only once it is in its place, where it no longer lies over another tab.
                    moving.lift.animateTo(0f, tween(TabSetDownMillis))
                }
                // Another order arrived while it settled: what it was let go over is gone.
                if (currentTitles !== titles) return@launch
                if (moving.target == moving.from) drag = null else currentOnMoveTab?.invoke(moving.from, moving.target)
            }
        }

        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                Box(
                    modifier = Modifier.drawBehind {
                        val cornerRadius = if (!isRounded) 0f else lerpAtPosition(position(), tabCount) { tab ->
                            pressedCornerRadius(pressOf(tab)).toPx()
                        }
                        drawRoundRect(color = thumbColor, cornerRadius = CornerRadius(cornerRadius))
                    },
                )
                tabTitles.forEachIndexed { index, title ->
                    TabLabel(
                        title = title,
                        index = index,
                        selectedPosition = position,
                        lift = { drag?.takeIf { it.from == index }?.lift?.value ?: 0f },
                        liftColor = thumbColor,
                        restingWidth = restingTabWidth,
                        interactionSource = interactionSources[index],
                        pressFraction = { pressOf(index) },
                        onClick = { onTabClick(index) },
                        modifier = if (onMoveTab == null) {
                            Modifier
                        } else {
                            Modifier.pointerInput(tabTitles, placeWidth) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        drag = TabDrag(index, selected = selectedPosition().roundToInt().toFloat())
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        drag?.takeUnless { it.isDropped }?.let { moving ->
                                            moving.offset = (moving.offset + amount.x)
                                                .coerceIn(-index * placeWidth, (tabCount - 1 - index) * placeWidth)
                                            moving.target = (index + (moving.offset / placeWidth).roundToInt()).coerceIn(0, tabCount - 1)
                                        }
                                    },
                                    onDragEnd = { drag?.let(::letGo) },
                                    onDragCancel = { drag?.let(::letGo) },
                                )
                            }
                        },
                    )
                }
            },
        ) { measurables, constraints ->
            val spacing = SonaComponentStyle.ItemSpacing.toPx()
            // Never below nothing: narrower than its gaps - as it can be measured while its window is resized - the
            // row is no wider than they are, rather than every tab and the pill a negative width.
            val restingWidth = ((constraints.maxWidth - spacing * (tabCount - 1)) / tabCount).coerceAtLeast(0f)
            val height = constraints.maxHeight

            // Every tab starts at its equal share; a held tab then takes its growth from its neighbours.
            val widths = FloatArray(tabCount) { restingWidth }
            for (tab in 0 until tabCount) {
                val neighbours = listOf(tab - 1, tab + 1).filter { it in 0 until tabCount }
                if (neighbours.isEmpty()) continue
                val growth = restingWidth * SonaComponentStyle.PressedExpandedRatio * pressOf(tab)
                widths[tab] += growth
                neighbours.forEach { widths[it] -= growth / neighbours.size }
            }
            val lefts = FloatArray(tabCount)
            for (tab in 1 until tabCount) lefts[tab] = lefts[tab - 1] + widths[tab - 1] + spacing
            // While a tab is dragged the rest keep their resting widths, each in the place it has stepped to, and the
            // lifted tab - still wide from its press - rides the finger, centred where its place would be. The row moves
            // there from its pressed group as the drag starts, rather than leaping to it.
            val moving = drag
            if (moving != null) {
                val spread = moving.spread.value
                for (tab in 0 until tabCount) {
                    val growth = if (tab == moving.from) restingWidth * SonaComponentStyle.PressedExpandedRatio * pressOf(tab) else 0f
                    val place = if (tab == moving.from) tab + moving.offset / (restingWidth + spacing) else tab + shifts[tab].value
                    val dragLeft = place * (restingWidth + spacing) - growth / 2
                    lefts[tab] += (dragLeft - lefts[tab]) * spread
                    widths[tab] += (restingWidth + growth - widths[tab]) * spread
                }
            }

            // Edges are rounded rather than widths, so the gaps stay even and the pill at rest covers
            // its tab exactly.
            val selected = position()
            val thumbLeft = lerpAtPosition(selected, tabCount) { lefts[it] }.roundToInt()
            val thumbRight = lerpAtPosition(selected, tabCount) { lefts[it] + widths[it] }.roundToInt()
            val thumb = measurables.first().measure(Constraints.fixed(thumbRight - thumbLeft, height))

            val tabs = measurables.drop(1).mapIndexed { tab, measurable ->
                val left = lefts[tab].roundToInt()
                val right = (lefts[tab] + widths[tab]).roundToInt()
                left to measurable.measure(Constraints.fixed(right - left, height))
            }

            layout(constraints.maxWidth, height) {
                thumb.place(x = thumbLeft, y = 0)
                tabs.forEachIndexed { tab, (left, placeable) ->
                    // The lifted tab passes over the others rather than under them.
                    placeable.place(x = left, y = 0, zIndex = if (tab == moving?.from) 1f else 0f)
                }
            }
        }
    }
}

@Composable
private fun TabLabel(
    title: String,
    index: Int,
    selectedPosition: () -> Float,
    lift: () -> Float,
    liftColor: Color,
    restingWidth: Dp,
    interactionSource: MutableInteractionSource,
    pressFraction: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // How close the strip currently sits to this tab, as an alpha rather than a color: the active
    // label is laid over the inactive one and faded in, so a swipe changes one number in the draw
    // phase. Lerping a color instead would have to be read during composition, which meant every
    // label re-laying out its text on every frame of a swipe - the one per-frame cost in the strip.
    // Lifted, it reads as the selected tab, whichever it is.
    val activeFraction = { maxOf((1f - abs(index - selectedPosition())).coerceIn(0f, 1f), lift()) }
    val isRounded = LocalIsRounded.current

    Box(
        modifier = modifier
            // Clipped before `clickable` so the press ripple stays inside the tab's animated shape.
            .graphicsLayer {
                shape = RoundedCornerShape(if (isRounded) pressedCornerRadius(pressFraction()) else 0.dp)
                clip = true
            }
            // Within the clip, so lifted it wears the selected pill's very colour and shape - opaque over what it passes.
            .drawBehind { lift().takeIf { it > 0f }?.let { drawRect(liftColor, alpha = it) } }
            .clickable(interactionSource = interactionSource, indication = ripple(), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // The title keeps the width it has at rest while its tab grows or gives way, so a press never
        // re-lays out or ellipsizes it - it only stays centred in the tab.
        Box(
            modifier = Modifier
                .requiredWidth(restingWidth)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            TabLabelText(title = title, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TabLabelText(
                title = title,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                // The pair is one label wearing two colors, so only the layer underneath is described.
                modifier = Modifier
                    .clearAndSetSemantics { }
                    .graphicsLayer { alpha = activeFraction() },
            )
        }
    }
}

/** One rendering of a tab's title. Two of these stacked are what let the pair cross-fade. */
@Composable
private fun TabLabelText(
    title: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = modifier.padding(horizontal = 4.dp),
    )
}

/**
 * A value of the pill between the two tabs [position] falls between - so while a swipe or a tap
 * carries it across, it takes on its neighbour's width and corners as smoothly as it moves.
 */
private inline fun lerpAtPosition(position: Float, tabCount: Int, valueAt: (tab: Int) -> Float): Float {
    val from = floor(position).toInt().coerceIn(0, tabCount - 1)
    val to = (from + 1).coerceAtMost(tabCount - 1)
    val fraction = (position - from).coerceIn(0f, 1f)
    return valueAt(from) + (valueAt(to) - valueAt(from)) * fraction
}
