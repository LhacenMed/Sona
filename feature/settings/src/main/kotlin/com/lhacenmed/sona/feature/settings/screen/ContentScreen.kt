package com.lhacenmed.sona.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.R
import com.lhacenmed.sona.feature.settings.component.SettingsChoiceItem
import com.lhacenmed.sona.feature.settings.component.SettingsList
import com.lhacenmed.sona.feature.settings.component.SettingsNavigationItem
import com.lhacenmed.sona.feature.settings.component.SettingsSection
import com.lhacenmed.sona.feature.settings.component.SettingsSectionDivider
import com.lhacenmed.sona.feature.settings.component.SettingsSwitchItem
import com.lhacenmed.sona.feature.settings.component.SettingsTextFieldItem
import com.lhacenmed.sona.feature.settings.manage.ExcludedFoldersScreen

/** Where the library comes from, what it is called, and what it is allowed to look like. */
object ContentScreen : Screen {
    override val titleRes: Int get() = R.string.content_title

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        SettingsList {
            SettingsSection(stringResource(R.string.content_library_section)) {
                SettingsSwitchItem(
                    title = stringResource(R.string.automatic_reloading_title),
                    summary = stringResource(R.string.automatic_reloading_summary),
                )
                SettingsNavigationItem(
                    title = stringResource(R.string.excluded_folders_title),
                    summary = stringResource(R.string.excluded_folders_summary),
                    onClick = { navigator.go(ExcludedFoldersScreen) },
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.filename_as_title_title),
                    summary = stringResource(R.string.filename_as_title_summary),
                )
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.content_tags_section)) {
                SettingsTextFieldItem(
                    title = stringResource(R.string.separators_title),
                    summary = stringResource(R.string.separators_summary),
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.intelligent_sorting_title),
                    summary = stringResource(R.string.intelligent_sorting_summary),
                    initialValue = true,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.hide_collaborators_title),
                    summary = stringResource(R.string.hide_collaborators_summary),
                )
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.content_images_section)) {
                SettingsChoiceItem(
                    title = stringResource(R.string.album_covers_title),
                    options = listOf(
                        stringResource(R.string.album_covers_as_is),
                        stringResource(R.string.album_covers_high),
                        stringResource(R.string.album_covers_balanced),
                        stringResource(R.string.album_covers_save_space),
                        stringResource(R.string.album_covers_off),
                    ),
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.force_square_covers_title),
                    summary = stringResource(R.string.force_square_covers_summary),
                    initialValue = true,
                )
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.content_language_section)) {
                SettingsSwitchItem(
                    title = stringResource(R.string.use_system_language_title),
                    summary = stringResource(R.string.use_system_language_summary),
                    initialValue = true,
                )
                SettingsChoiceItem(
                    title = stringResource(R.string.app_language_title),
                    options = listOf(
                        stringResource(R.string.app_language_english),
                        stringResource(R.string.app_language_french),
                        stringResource(R.string.app_language_arabic),
                    ),
                )
            }
        }
    }
}
