package com.lhacenmed.sona.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.R
import com.lhacenmed.sona.feature.settings.component.SettingsInfoItem
import com.lhacenmed.sona.feature.settings.component.SettingsList
import com.lhacenmed.sona.feature.settings.component.SettingsNavigationItem
import com.lhacenmed.sona.feature.settings.component.SettingsSection
import com.lhacenmed.sona.feature.settings.component.SettingsSectionDivider

/** What this app is, and where the rest of it can be found. */
object AboutScreen : Screen {
    override val titleRes: Int get() = R.string.about_title

    @Composable
    override fun Content() {
        SettingsList {
            SettingsSection(stringResource(R.string.about_app_section)) {
                SettingsInfoItem(
                    title = stringResource(R.string.about_version_title),
                    value = stringResource(R.string.updates_placeholder_version),
                )
                SettingsNavigationItem(
                    title = stringResource(R.string.changelog_title),
                    summary = stringResource(R.string.changelog_summary),
                    onClick = {},
                )
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.about_project_section)) {
                SettingsNavigationItem(
                    title = stringResource(R.string.about_source_title),
                    summary = stringResource(R.string.about_source_summary),
                    onClick = {},
                )
                SettingsNavigationItem(
                    title = stringResource(R.string.about_licenses_title),
                    summary = stringResource(R.string.about_licenses_summary),
                    onClick = {},
                )
            }
        }
    }
}
