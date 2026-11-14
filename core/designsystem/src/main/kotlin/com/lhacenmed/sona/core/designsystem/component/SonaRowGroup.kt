package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.theme.connectedLeadingButtonPressShapes
import com.lhacenmed.sona.core.designsystem.theme.connectedTrailingButtonPressShapes
import com.lhacenmed.sona.core.designsystem.theme.togglePressShapes
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons

/** A row of a [SonaRowGroup], laid out in the shapes of its place in the group. */
typealias SonaGroupRow = @Composable (ListItemShapes) -> Unit

/**
 * Rows that belong together, grouped as Material 3 Expressive groups a list: apart by a hairline, the group's own corners
 * round and each row's inner ones tight. Each of [rows] is given the shapes of its place in the group.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SonaRowGroup(rows: List<SonaGroupRow>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        rows.forEachIndexed { index, row -> row(ListItemDefaults.segmentedShapes(index = index, count = rows.size)) }
    }
}

/**
 * A row of a [SonaRowGroup]: what it sets, and under it the [value] it has - opening what changes it, as its chevron says,
 * unless [trailing] says otherwise. [leading] stands where [icon] would, for a picture of its own.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SonaValueRow(
    label: String,
    shapes: ListItemShapes,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = icon?.let { { Icon(it, contentDescription = null) } },
    value: String? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = { Icon(SonaIcons.KeyboardArrowRight, contentDescription = null) },
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        modifier = modifier,
        enabled = enabled,
        leadingContent = leading,
        trailingContent = trailing,
        supportingContent = value?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * One of [options] picked in a connected button group - Material 3 Expressive's - the picked one filled, a check in place of
 * its [icon]. [isCompact] for a group that sits inside a row; otherwise the group fills its width, laid out as
 * [fittedWidths] says, so a label is always on one line, whatever the font size - never broken inside a word.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> SonaConnectedChoice(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    icon: (T) -> ImageVector? = { null },
    enabled: Boolean = true,
    isCompact: Boolean = false,
) {
    val buttons = @Composable {
        options.forEachIndexed { index, option ->
            val isSelected = option == selected
            ToggleButton(
                checked = isSelected,
                onCheckedChange = { onSelect(option) },
                enabled = enabled,
                shapes = when (index) {
                    0 -> connectedLeadingButtonPressShapes()
                    options.lastIndex -> connectedTrailingButtonPressShapes()
                    else -> togglePressShapes()
                },
                modifier = Modifier
                    .then(if (isCompact) Modifier else Modifier.heightIn(min = 48.dp))
                    .semantics { role = Role.RadioButton },
            ) {
                (if (isSelected) SonaIcons.Check else icon(option))?.let {
                    Icon(it, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Text(
                    label(option),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = if (isSelected || icon(option) != null) 8.dp else 0.dp),
                )
            }
        }
    }
    val spacing = ButtonGroupDefaults.ConnectedSpaceBetween
    if (isCompact) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(spacing)) { buttons() }
    } else {
        Layout(content = buttons, modifier = modifier) { measurables, constraints ->
            val gap = spacing.roundToPx()
            val widths = fittedWidths(
                natural = measurables.map { it.maxIntrinsicWidth(constraints.maxHeight) },
                available = constraints.maxWidth - gap * measurables.lastIndex,
            )
            val placeables = measurables.mapIndexed { index, measurable ->
                measurable.measure(constraints.copy(minWidth = widths[index], maxWidth = widths[index]))
            }
            val height = placeables.maxOf { it.height }
            layout(constraints.maxWidth, height) {
                var x = 0
                placeables.forEach { placeable ->
                    placeable.placeRelative(x, (height - placeable.height) / 2)
                    x += placeable.width + gap
                }
            }
        }
    }
}

/**
 * How [available] is shared among buttons whose labels need [natural] widths: equally where every label fits its share, as a
 * group looks best; a label too long for its share keeps the width it needs, the rest sharing what is left. Where even that
 * cannot fit - a large font in a narrow window - each gives up room in proportion, and its label ends in an ellipsis.
 */
private fun fittedWidths(natural: List<Int>, available: Int): List<Int> {
    val needed = natural.sum()
    val widths = if (needed >= available) {
        natural.map { (it.toLong() * available / needed).toInt() }.toMutableList()
    } else {
        val kept = mutableSetOf<Int>()
        var share = 0
        do {
            share = (available - kept.sumOf { natural[it] }) / (natural.size - kept.size)
            val tooLong = natural.indices.filter { it !in kept && natural[it] > share }
            kept += tooLong
        } while (tooLong.isNotEmpty())
        natural.indices.map { if (it in kept) natural[it] else share }.toMutableList()
    }
    // What division leaves over, to the last, so the group spans its width exactly.
    widths[widths.lastIndex] += available - widths.sum()
    return widths
}
