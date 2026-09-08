package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

private val TabRowHeight = 44.dp
private val TabRowPadding = 4.dp

/**
 * A rounded, pill-styled tab strip.
 *
 * [selectedPosition] is a lambda returning a *continuous* position (e.g. a pager's
 * `currentPage + currentPageOffsetFraction`) rather than a discrete index, so the selected pill and
 * the label colors track a swipe under the finger instead of jumping once it settles. Passing it as
 * a lambda keeps the state read inside this component: the caller - which typically also hosts the
 * pager itself - never reads it, so a swipe recomposes only the few small nodes below rather than
 * the paged content.
 *
 * Tabs share the width equally, which makes the indicator's position exact arithmetic rather than
 * something derived from measuring each tab - no measurement pass, nothing to drift or glitch.
 */
@Composable
fun SonaTabRow(
    tabTitles: List<String>,
    selectedPosition: () -> Float,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tabTitles.isEmpty()) return

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(TabRowHeight)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(TabRowPadding),
    ) {
        val tabWidth = maxWidth / tabTitles.size
        val tabWidthPx = with(LocalDensity.current) { tabWidth.toPx() }

        // Read in the layout phase, so sliding the pill never recomposes anything at all.
        Box(
            modifier = Modifier
                .offset { IntOffset(x = (selectedPosition() * tabWidthPx).roundToInt(), y = 0) }
                .width(tabWidth)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            tabTitles.forEachIndexed { index, title ->
                TabLabel(
                    title = title,
                    index = index,
                    selectedPosition = selectedPosition,
                    width = tabWidth,
                    onClick = { onTabClick(index) },
                )
            }
        }
    }
}

@Composable
private fun TabLabel(
    title: String,
    index: Int,
    selectedPosition: () -> Float,
    width: Dp,
    onClick: () -> Unit,
) {
    // Fades between the inactive and active color by how close the strip currently sits to this
    // tab, so labels cross-fade with the swipe. Reading the position here scopes the per-frame
    // recomposition to this one label.
    val activeFraction = (1f - abs(index - selectedPosition())).coerceIn(0f, 1f)
    val color = lerp(
        MaterialTheme.colorScheme.onSurfaceVariant,
        MaterialTheme.colorScheme.onSecondaryContainer,
        activeFraction,
    )

    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            // Clipped before `clickable` so the press ripple stays inside the pill shape.
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
