package com.lhacenmed.sona.feature.download

import android.text.format.Formatter
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.InfoSeparator
import com.lhacenmed.sona.core.designsystem.component.SonaEmptyState
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.TopBarSearch
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaConfirmationDialog
import com.lhacenmed.sona.core.model.DownloadStatus
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import kotlinx.coroutines.launch

/**
 * Every download that is over - Seal's download history: how many and how much, searched from the bar, narrowed to audio,
 * video or what failed, today's and before - each played with a tap, put right where it failed, its sheet a tap away, and
 * deleted many at a time.
 */
data object HistoryScreen : Screen {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.current
        val scope = rememberCoroutineScope()
        val viewModel: DownloadViewModel = hiltViewModel()
        val sections by viewModel.sections.collectAsStateWithLifecycle()
        val query by viewModel.query.collectAsStateWithLifecycle()
        val filter by viewModel.filter.collectAsStateWithLifecycle()
        val state = rememberDownloadListState()
        var isClearing by remember { mutableStateOf(false) }

        val today = sections?.today.orEmpty()
        val earlier = sections?.earlier.orEmpty()
        val listed = today + earlier
        val savedBytes = listed.filter { it.status == DownloadStatus.DONE }.sumOf { it.sizeBytes }
        val summary = listOfNotNull(
            pluralStringResource(R.plurals.download_history_count, listed.size, listed.size),
            savedBytes.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) },
        ).joinToString(InfoSeparator)
        val isNarrowed = !query.isNullOrBlank() || filter != null
        val title = stringResource(R.string.download_history)

        Column(modifier = Modifier.fillMaxSize()) {
            SonaTopAppBar(
                // The title is the list's own, large, over the filters.
                title = "",
                onNavigateBack = navigator::back,
                actions = listOf(
                    TopBarAction(stringResource(R.string.download_search), Icons.Filled.Search) { viewModel.onQueryChange("") },
                    TopBarAction(stringResource(R.string.downloads_settings_clear_history_title), Icons.Filled.DeleteSweep, enabled = listed.isNotEmpty() && !isNarrowed) {
                        isClearing = true
                    },
                ),
                search = query?.let { TopBarSearch(query = it, onQueryChange = viewModel::onQueryChange, onClose = { viewModel.onQueryChange(null) }) },
                selection = state.topBarSelection(listed),
            )
            DownloadList(
                groups = listOf(
                    DownloadGroup("today", stringResource(R.string.download_section_today), today),
                    DownloadGroup("earlier", stringResource(R.string.download_section_earlier), earlier),
                ),
                viewModel = viewModel,
                state = state,
                header = {
                    screenTitle(title, summary)
                    item(key = "filters") { Filters(filter, viewModel::onFilterChange) }
                },
                footer = {
                    // Still being read: nothing yet, rather than an empty state for a moment.
                    if (sections != null && listed.isEmpty()) {
                        item(key = "empty") {
                            when {
                                !query.isNullOrBlank() -> SonaEmptyState(
                                    title = stringResource(R.string.download_search_empty, query.orEmpty()),
                                    message = stringResource(R.string.download_search_empty_message),
                                )
                                filter != null -> SonaEmptyState(title = stringResource(R.string.download_filter_empty), message = stringResource(R.string.download_filter_empty_message))
                                else -> SonaEmptyState(title = stringResource(R.string.download_history_empty), message = stringResource(R.string.download_history_empty_message))
                            }
                        }
                    }
                },
            )
        }

        DownloadListSheets(state, listed, viewModel)

        if (isClearing) {
            SonaConfirmationDialog(
                title = stringResource(R.string.downloads_settings_clear_history_title),
                message = stringResource(R.string.downloads_settings_clear_history_message),
                confirmLabel = stringResource(R.string.downloads_settings_clear_history_confirm),
                successMessage = stringResource(R.string.downloads_settings_history_cleared),
                failureMessage = stringResource(R.string.downloads_settings_history_not_cleared),
                onDismiss = { isClearing = false },
                operation = { onFinished ->
                    scope.launch {
                        viewModel.onClearHistory()
                        onFinished(true)
                    }
                },
            )
        }
    }
}

/** Audio, Video or Failed - one picked at a time, picked again to show everything. */
@Composable
private fun Filters(selected: HistoryFilter?, onSelect: (HistoryFilter) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HistoryFilter.entries.forEach { filter ->
            val isSelected = filter == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(filter) },
                label = { Text(stringResource(filter.labelRes)) },
                leadingIcon = if (isSelected) ({ Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }) else null,
            )
        }
    }
}
