package com.lhacenmed.sona.feature.settings.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The body every settings screen has: its rows, in order, scrolling as one.
 *
 * A plain scrolling [Column] rather than a `LazyColumn` - a settings screen's rows are a fixed,
 * short, hand-written list, and a lazy container would allocate scroll state and defer composition
 * for rows that are all going to exist anyway.
 */
@Composable
fun SettingsList(content: @Composable ColumnScope.() -> Unit) {
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
fun ColumnScope.SettingsSection(
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
fun SettingsSectionDivider() {
    HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
}
