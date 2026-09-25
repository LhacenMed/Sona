package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.theme.LocalIsRounded
import com.lhacenmed.sona.core.designsystem.theme.SonaComponentStyle
import com.lhacenmed.sona.core.designsystem.theme.pressedCornerRadius
import com.lhacenmed.sona.core.designsystem.theme.rememberPressFraction
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

private val TabRowHeight = 44.dp
private val TabRowPadding = 4.dp

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
 */
@Composable
fun SonaTabRow(
    tabTitles: List<String>,
    selectedPosition: () -> Float,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(TabRowHeight)
            // Vertical only: the outer tabs' edges are the row's own, so the row lines up with
            // whatever the caller aligns it to.
            .padding(vertical = TabRowPadding),
    ) {
        val restingTabWidth = (maxWidth - SonaComponentStyle.ItemSpacing * (tabCount - 1)) / tabCount

        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                Box(
                    modifier = Modifier.drawBehind {
                        val cornerRadius = if (!isRounded) 0f else lerpAtPosition(selectedPosition(), tabCount) { tab ->
                            pressedCornerRadius(pressFractions[tab].value).toPx()
                        }
                        drawRoundRect(color = thumbColor, cornerRadius = CornerRadius(cornerRadius))
                    },
                )
                tabTitles.forEachIndexed { index, title ->
                    TabLabel(
                        title = title,
                        index = index,
                        selectedPosition = selectedPosition,
                        restingWidth = restingTabWidth,
                        interactionSource = interactionSources[index],
                        pressFraction = pressFractions[index],
                        onClick = { onTabClick(index) },
                    )
                }
            },
        ) { measurables, constraints ->
            val spacing = SonaComponentStyle.ItemSpacing.toPx()
            val restingWidth = (constraints.maxWidth - spacing * (tabCount - 1)) / tabCount
            val height = constraints.maxHeight

            // Every tab starts at its equal share; a held tab then takes its growth from its neighbours.
            val widths = FloatArray(tabCount) { restingWidth }
            for (tab in 0 until tabCount) {
                val neighbours = listOf(tab - 1, tab + 1).filter { it in 0 until tabCount }
                if (neighbours.isEmpty()) continue
                val growth = restingWidth * SonaComponentStyle.PressedExpandedRatio * pressFractions[tab].value
                widths[tab] += growth
                neighbours.forEach { widths[it] -= growth / neighbours.size }
            }
            val lefts = FloatArray(tabCount)
            for (tab in 1 until tabCount) lefts[tab] = lefts[tab - 1] + widths[tab - 1] + spacing

            // Edges are rounded rather than widths, so the gaps stay even and the pill at rest covers
            // its tab exactly.
            val position = selectedPosition()
            val thumbLeft = lerpAtPosition(position, tabCount) { lefts[it] }.roundToInt()
            val thumbRight = lerpAtPosition(position, tabCount) { lefts[it] + widths[it] }.roundToInt()
            val thumb = measurables.first().measure(Constraints.fixed(thumbRight - thumbLeft, height))

            val tabs = measurables.drop(1).mapIndexed { tab, measurable ->
                val left = lefts[tab].roundToInt()
                val right = (lefts[tab] + widths[tab]).roundToInt()
                left to measurable.measure(Constraints.fixed(right - left, height))
            }

            layout(constraints.maxWidth, height) {
                thumb.place(x = thumbLeft, y = 0)
                tabs.forEach { (left, placeable) -> placeable.place(x = left, y = 0) }
            }
        }
    }
}

@Composable
private fun TabLabel(
    title: String,
    index: Int,
    selectedPosition: () -> Float,
    restingWidth: Dp,
    interactionSource: MutableInteractionSource,
    pressFraction: State<Float>,
    onClick: () -> Unit,
) {
    // How close the strip currently sits to this tab, as an alpha rather than a color: the active
    // label is laid over the inactive one and faded in, so a swipe changes one number in the draw
    // phase. Lerping a color instead would have to be read during composition, which meant every
    // label re-laying out its text on every frame of a swipe - the one per-frame cost in the strip.
    val activeFraction = { (1f - abs(index - selectedPosition())).coerceIn(0f, 1f) }
    val isRounded = LocalIsRounded.current

    Box(
        modifier = Modifier
            // Clipped before `clickable` so the press ripple stays inside the tab's animated shape.
            .graphicsLayer {
                shape = RoundedCornerShape(if (isRounded) pressedCornerRadius(pressFraction.value) else 0.dp)
                clip = true
            }
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
