package com.lhacenmed.sona.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.component.SettingsList
import com.lhacenmed.sona.feature.settings.component.SettingsNavigationItem
import com.lhacenmed.sona.feature.settings.component.SettingsSection
import com.lhacenmed.sona.feature.settings.component.SettingsSectionDivider

/**
 * Settings home: every category, grouped.
 *
 * The list is [SettingsCategory] rendered, not a list written out by hand - the categories are the
 * data, and this only decides what a category looks like. Categories rather than one long list of
 * settings, because the list is long enough that finding anything in it would mean reading all of it.
 */
object SettingsScreen : Screen {
    override val titleRes: Int get() = R.string.settings_title

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        SettingsList {
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
        }
    }
}
