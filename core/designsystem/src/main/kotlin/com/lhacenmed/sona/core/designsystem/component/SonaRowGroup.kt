package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.theme.connectedLeadingButtonPressShapes
import com.lhacenmed.sona.core.designsystem.theme.connectedTrailingButtonPressShapes
import com.lhacenmed.sona.core.designsystem.theme.togglePressShapes

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
    trailing: (@Composable () -> Unit)? = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
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
 * its [icon]. [isCompact] for a group that sits inside a row.
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
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
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
                    .then(if (isCompact) Modifier else Modifier.weight(1f).heightIn(min = 48.dp))
                    .semantics { role = Role.RadioButton },
            ) {
                (if (isSelected) Icons.Filled.Check else icon(option))?.let {
                    Icon(it, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Text(label(option), modifier = Modifier.padding(start = if (isSelected || icon(option) != null) 8.dp else 0.dp))
            }
        }
    }
}
