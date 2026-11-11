package com.lhacenmed.sona.feature.settings.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.lhacenmed.sona.core.data.search.SearchIndex
import com.lhacenmed.sona.core.designsystem.component.SonaEmptyState
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.SettingsCategory
import com.lhacenmed.sona.feature.settings.SettingsRowScreen
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsList
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsNavigationItem
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsRecentSearchItem
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsSection
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons

/** Something a settings search can find, as it is listed: [title] where it is - its [location] - and opened at [destination]. */
private class SettingsSearchResult(
    val title: String,
    val summary: String?,
    val location: String,
    val icon: ImageVector,
    val destination: Screen,
)

/**
 * Every category and every setting a search finds, in the app's language - read once, and again only as the language
 * changes. A category stands where the settings home lists it; a setting in its category - and in the screen of its
 * category's that holds it, where that is not the category's own - and in its section there.
 */
@Composable
private fun rememberSettingsSearchResults(): List<SettingsSearchResult> {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) {
        val categories = SettingsCategory.entries.map { category ->
            SettingsSearchResult(
                title = context.getString(category.titleRes),
                summary = context.getString(category.summaryRes),
                location = context.getString(category.group.titleRes),
                icon = category.icon,
                destination = category.screen,
            )
        }
        val settings = SettingsSearchEntries.map { entry ->
            val screenTitle = entry.screen.title(context).takeIf { entry.screen != entry.category.screen }
            SettingsSearchResult(
                title = context.getString(entry.titleRes),
                summary = entry.summaryRes?.let(context::getString),
                location = listOfNotNull(context.getString(entry.category.titleRes), screenTitle, context.getString(entry.sectionRes))
                    .joinToString(" › "),
                icon = entry.category.icon,
                destination = SettingsRowScreen(entry.screen, entry.titleRes),
            )
        }
        categories + settings
    }
}

/**
 * What [query] finds among the settings, best first - by title, then by what it does and where it is, through the
 * library's own search, typos and all: see [SearchIndex]. Opening one keeps the search - [onResultOpened].
 */
@Composable
internal fun SettingsSearchResults(query: String, onResultOpened: () -> Unit) {
    val navigator = LocalNavigator.current
    val results = rememberSettingsSearchResults()
    val index = remember(results) { SearchIndex(results) { listOf(it.title) + listOfNotNull(it.summary) + it.location } }
    val found = remember(index, query) { index.search(query).items }
    if (found.isEmpty()) {
        SonaEmptyState(title = "No settings found", message = "Nothing matched \"$query\".")
        return
    }
    SettingsList {
        found.forEach { result ->
            SettingsNavigationItem(
                title = result.title,
                summary = result.location,
                icon = result.icon,
                onClick = {
                    onResultOpened()
                    navigator.go(result.destination)
                },
            )
        }
    }
}

/** What the settings were searched for lately, the latest first, any of which is searched again at a press. */
@Composable
internal fun SettingsRecentSearches(
    searches: List<String>,
    onSearch: (String) -> Unit,
    onForget: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (searches.isEmpty()) {
        SonaEmptyState(title = "Search settings", message = "Find any setting by its name, by what it does, or by where it is.")
        return
    }
    SettingsList {
        SettingsSection(
            title = "Recent searches",
            actions = listOf(TopBarAction(label = "Clear", icon = SonaIcons.ClearAll, onClick = onClear)),
        ) {
            searches.forEach { search ->
                SettingsRecentSearchItem(query = search, onClick = { onSearch(search) }, onForget = { onForget(search) })
            }
        }
    }
}
