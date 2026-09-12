package com.lhacenmed.sona.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen

/** Where the library comes from, and what it is allowed to look like once it arrives. */
object ContentScreen : Screen {
    override val titleRes: Int get() = R.string.content_title

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        SettingsList {
            SettingsSection(stringResource(R.string.content_music_section)) {
                SettingsSwitchItem(
                    title = stringResource(R.string.automatic_reloading_title),
                    summary = stringResource(R.string.automatic_reloading_summary),
                )
                SettingsChoiceItem(
                    title = stringResource(R.string.separators_title),
                    value = stringResource(R.string.separators_summary),
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

            SettingsSection(stringResource(R.string.content_images_section)) {
                SettingsChoiceItem(
                    title = stringResource(R.string.album_covers_title),
                    value = stringResource(R.string.album_covers_value_as_is),
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.force_square_covers_title),
                    summary = stringResource(R.string.force_square_covers_summary),
                    initialValue = true,
                )
            }
        }
    }
}
