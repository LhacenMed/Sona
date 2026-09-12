package com.lhacenmed.sona.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * The body every settings screen has: its rows, in order, scrolling as one.
 *
 * A plain scrolling [Column] rather than a `LazyColumn` - a settings screen's rows are a fixed,
 * short, hand-written list, and a lazy container would allocate scroll state and defer composition
 * for rows that are all going to exist anyway.
 */
@Composable
internal fun SettingsList(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        content = content,
    )
}

/**
 * A named group of related settings.
 *
 * The rule the screens follow is one a reader can check at a glance: a screen either has no sections
 * at all, or every row belongs to one. Half-sectioned screens are what make a settings page feel
 * arbitrary.
 */
@Composable
internal fun ColumnScope.SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
    content()
}

/** The line between two [SettingsSection]s. */
@Composable
internal fun SettingsSectionDivider() {
    HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
}

/** A row that opens a screen of its own. */
@Composable
internal fun SettingsNavigationItem(
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        modifier = modifier.clickable(onClick = onClick),
    )
}

/**
 * A row whose value is picked from a set of choices, showing the one in force.
 *
 * Clickable, but opens nothing yet. The row takes its final shape now for the same reason the rest
 * of the screen does: a chooser added later should drop into a row that is already the right height
 * and already in the right place, rather than moving every row beneath it.
 */
@Composable
internal fun SettingsChoiceItem(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        modifier = modifier.clickable {},
    )
}

/**
 * A row that turns something on or off.
 *
 * [initialValue] is where the switch starts and, for now, all it knows: the state lives in the row
 * and lasts as long as the screen is open. The settings tree is being laid out before any of it is
 * connected, and a switch that cannot move reads as broken rather than as unfinished. Connecting one
 * means taking its value and its callback from a ViewModel instead - the row around it does not
 * change.
 */
@Composable
internal fun SettingsSwitchItem(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    initialValue: Boolean = false,
) {
    var checked by remember { mutableStateOf(initialValue) }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = { checked = it })
        },
        // The whole row, not just the switch: a settings row is one target, and hitting the text
        // expecting it to toggle is the commonest way to miss.
        modifier = modifier.clickable { checked = !checked },
    )
}
