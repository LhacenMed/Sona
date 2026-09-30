@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.settings.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.feature.settings.R

/** A list a settings screen loads: on its way, found empty, failed, or [Loaded]. */
sealed interface SettingsLoad<out T> {
    data object Loading : SettingsLoad<Nothing>
    data object Empty : SettingsLoad<Nothing>
    data object Failed : SettingsLoad<Nothing>
    data class Loaded<T>(val items: List<T>) : SettingsLoad<T>
}

/** [items] as a [SettingsLoad]: [SettingsLoad.Empty] for none. */
fun <T> settingsLoadOf(items: List<T>): SettingsLoad<T> =
    if (items.isEmpty()) SettingsLoad.Empty else SettingsLoad.Loaded(items)

/**
 * Where [load] stands, in the one row its list's rows would start at: loading, or found empty or failed and
 * pressed to [onRetry]. Nothing once it has loaded - its rows are the list's to draw.
 */
@Composable
fun SettingsLoadStatus(load: SettingsLoad<*>, onRetry: () -> Unit) {
    when (load) {
        SettingsLoad.Loading -> ListItem(
            headlineContent = { Text(stringResource(R.string.load_loading)) },
            trailingContent = { LoadingIndicator(modifier = Modifier.size(24.dp)) },
        )
        SettingsLoad.Empty -> RetryItem(stringResource(R.string.load_empty), onRetry)
        SettingsLoad.Failed -> RetryItem(stringResource(R.string.load_failed), onRetry)
        is SettingsLoad.Loaded -> Unit
    }
}

/**
 * A lazy list's rows for [load]: [loadedRows] once it has loaded, and until then the one row of its
 * [SettingsLoadStatus] in their place. Never a status row that draws nothing - a list whose first row is
 * empty takes itself to be scrolled past it, and lifts its top bar and swallows a pull at its top.
 */
fun <T> LazyListScope.settingsLoad(
    load: SettingsLoad<T>,
    onRetry: () -> Unit,
    loadedRows: LazyListScope.(items: List<T>) -> Unit,
) {
    if (load is SettingsLoad.Loaded) {
        loadedRows(load.items)
    } else {
        item(key = "status") { SettingsLoadStatus(load, onRetry) }
    }
}

@Composable
private fun RetryItem(message: String, onRetry: () -> Unit) {
    ListItem(
        headlineContent = { Text(message) },
        supportingContent = { Text(stringResource(R.string.load_retry)) },
        modifier = Modifier.clickable(onClick = onRetry),
    )
}
