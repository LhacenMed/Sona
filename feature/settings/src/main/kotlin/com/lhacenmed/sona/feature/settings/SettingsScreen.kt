package com.lhacenmed.sona.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.TopBarSearch
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.component.SettingsList
import com.lhacenmed.sona.feature.settings.component.SettingsNavigationItem
import com.lhacenmed.sona.feature.settings.component.SettingsSection
import com.lhacenmed.sona.feature.settings.component.SettingsSectionDivider
import com.lhacenmed.sona.feature.settings.search.SettingsRecentSearches
import com.lhacenmed.sona.feature.settings.search.SettingsSearchResults
import com.lhacenmed.sona.feature.vault.rememberPrivateFolderOpener

/**
 * Settings home: every category, grouped - and, from its bar, a search of every setting there is.
 *
 * The list is [SettingsCategory] rendered, not a list written out by hand - the categories are the
 * data, and this only decides what a category looks like. Categories rather than one long list of
 * settings, because the list is long enough that finding anything in it would mean reading all of it.
 *
 * Searched, it lists what is found as it is typed, best first - or, before anything is typed, what was searched for
 * lately. A result opens its setting's screen at its row. It draws its own bar, as the search's field stands in it.
 */
data object SettingsScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val openPrivateFolder = rememberPrivateFolderOpener()
        val viewModel = hiltViewModel<SettingsHomeViewModel>()
        val debugTools = viewModel.debugTools
        val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
        // Null while the search is closed; blank while it is open with nothing typed yet.
        var searchQuery by rememberSaveable { mutableStateOf<String?>(null) }

        Column(modifier = Modifier.fillMaxSize()) {
            SonaTopAppBar(
                title = stringResource(R.string.settings_title),
                onNavigateBack = navigator::back,
                actions = listOf(TopBarAction(label = "Search", icon = Icons.Filled.Search) { searchQuery = "" }),
                search = searchQuery?.let { query ->
                    TopBarSearch(
                        query = query,
                        onQueryChange = { searchQuery = it },
                        onClose = { searchQuery = null },
                    )
                },
            )
            val query = searchQuery
            when {
                query == null -> SettingsList(onPrivateFolderRevealed = openPrivateFolder) {
                    SettingsGroup.entries.forEachIndexed { index, group ->
                        if (index > 0) SettingsSectionDivider()
                        SettingsSection(stringResource(group.titleRes)) {
                            SettingsCategory.entries
                                .filter { it.group == group }
                                .forEach { category ->
                                    SettingsNavigationItem(
                                        title = stringResource(category.titleRes),
                                        summary = stringResource(category.summaryRes),
                                        icon = category.icon,
                                        onClick = { navigator.go(category.screen) },
                                    )
                                }
                        }
                    }
                    if (debugTools != null) {
                        SettingsSectionDivider()
                        SettingsNavigationItem(
                            title = debugTools.screen.title(LocalContext.current).orEmpty(),
                            summary = debugTools.summary,
                            icon = Icons.Filled.BugReport,
                            onClick = { navigator.go(debugTools.screen) },
                        )
                    }
                }

                query.isBlank() -> SettingsRecentSearches(
                    searches = recentSearches,
                    onSearch = { searchQuery = it },
                    onForget = viewModel::forgetSearch,
                    onClear = viewModel::clearSearches,
                )

                else -> SettingsSearchResults(query = query, onResultOpened = { viewModel.recordSearch(query) })
            }
        }
    }
}
