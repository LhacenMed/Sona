package com.lhacenmed.sona.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen

/** What the app shows, and what it does when the user reaches for it. */
object PersonalizeScreen : Screen {
    override val titleRes: Int get() = R.string.personalize_title

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        SettingsList {
            SettingsSection(stringResource(R.string.personalize_display_section)) {
                SettingsNavigationItem(
                    title = stringResource(R.string.library_tabs_title),
                    summary = stringResource(R.string.library_tabs_summary),
                    onClick = { navigator.go(TabVisibilityScreen) },
                )
                SettingsChoiceItem(
                    title = stringResource(R.string.playback_bar_action_title),
                    value = stringResource(R.string.playback_bar_action_value_skip_next),
                )
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.personalize_behavior_section)) {
                SettingsChoiceItem(
                    title = stringResource(R.string.play_from_library_title),
                    value = stringResource(R.string.play_from_library_value_all_songs),
                )
                SettingsChoiceItem(
                    title = stringResource(R.string.play_from_details_title),
                    value = stringResource(R.string.play_from_details_value_shown_item),
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.remember_shuffle_title),
                    summary = stringResource(R.string.remember_shuffle_summary),
                    initialValue = true,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.exit_on_close_title),
                    summary = stringResource(R.string.exit_on_close_summary),
                )
            }
        }
    }
}
