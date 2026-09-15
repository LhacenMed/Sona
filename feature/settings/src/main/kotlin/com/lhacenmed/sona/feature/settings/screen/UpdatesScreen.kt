package com.lhacenmed.sona.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.R
import com.lhacenmed.sona.feature.settings.component.SettingsActionItem
import com.lhacenmed.sona.feature.settings.component.SettingsInfoItem
import com.lhacenmed.sona.feature.settings.component.SettingsList
import com.lhacenmed.sona.feature.settings.component.SettingsNavigationItem
import com.lhacenmed.sona.feature.settings.component.SettingsSection
import com.lhacenmed.sona.feature.settings.component.SettingsSwitchItem

/** Whether the app looks for new versions of itself, and what it found last time it did. */
object UpdatesScreen : Screen {
    override val titleRes: Int get() = R.string.updates_title

    @Composable
    override fun Content() {
        SettingsList {
            SettingsSection(stringResource(R.string.updates_section)) {
                SettingsSwitchItem(
                    title = stringResource(R.string.updates_automatic_check_title),
                    summary = stringResource(R.string.updates_automatic_check_summary),
                    initialValue = true,
                )
                SettingsActionItem(
                    title = stringResource(R.string.updates_check_now_title),
                    summary = stringResource(R.string.updates_check_now_summary),
                )
                SettingsInfoItem(
                    title = stringResource(R.string.updates_installed_version_title),
                    value = stringResource(R.string.updates_placeholder_version),
                )
                SettingsNavigationItem(
                    title = stringResource(R.string.changelog_title),
                    summary = stringResource(R.string.changelog_summary),
                    onClick = {},
                )
            }
        }
    }
}
