package com.lhacenmed.sona.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.R
import com.lhacenmed.sona.feature.settings.component.SettingsActionItem
import com.lhacenmed.sona.feature.settings.component.SettingsChoiceItem
import com.lhacenmed.sona.feature.settings.component.SettingsList
import com.lhacenmed.sona.feature.settings.component.SettingsNavigationItem
import com.lhacenmed.sona.feature.settings.component.SettingsSection
import com.lhacenmed.sona.feature.settings.component.SettingsSectionDivider
import com.lhacenmed.sona.feature.settings.component.SettingsSwitchItem
import com.lhacenmed.sona.feature.settings.manage.TabVisibilityScreen

/** What the app shows, what it remembers, and what it does when the user reaches for it. */
object BehaviorScreen : Screen {
    override val titleRes: Int get() = R.string.behavior_title

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        SettingsList {
            SettingsSection(stringResource(R.string.behavior_display_section)) {
                SettingsNavigationItem(
                    title = stringResource(R.string.library_tabs_title),
                    summary = stringResource(R.string.library_tabs_summary),
                    onClick = { navigator.go(TabVisibilityScreen) },
                )
                SettingsChoiceItem(
                    title = stringResource(R.string.default_tab_title),
                    options = listOf(
                        stringResource(R.string.default_tab_tracks),
                        stringResource(R.string.default_tab_artists),
                        stringResource(R.string.default_tab_albums),
                        stringResource(R.string.default_tab_genres),
                        stringResource(R.string.default_tab_folders),
                    ),
                )
                SettingsChoiceItem(
                    title = stringResource(R.string.playback_bar_action_title),
                    options = listOf(
                        stringResource(R.string.playback_bar_action_skip_next),
                        stringResource(R.string.playback_bar_action_favorite),
                        stringResource(R.string.playback_bar_action_none),
                    ),
                )
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.behavior_playing_section)) {
                SettingsChoiceItem(
                    title = stringResource(R.string.play_from_library_title),
                    options = listOf(
                        stringResource(R.string.play_from_all_tracks),
                        stringResource(R.string.play_from_shown_list),
                    ),
                )
                SettingsChoiceItem(
                    title = stringResource(R.string.play_from_details_title),
                    options = listOf(
                        stringResource(R.string.play_from_shown_item),
                        stringResource(R.string.play_from_all_tracks),
                    ),
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.exit_on_close_title),
                    summary = stringResource(R.string.exit_on_close_summary),
                )
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.behavior_history_section)) {
                SettingsSwitchItem(
                    title = stringResource(R.string.pause_listen_history_title),
                    summary = stringResource(R.string.pause_listen_history_summary),
                )
                SettingsActionItem(
                    title = stringResource(R.string.clear_listen_history_title),
                    summary = stringResource(R.string.clear_listen_history_summary),
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.pause_search_history_title),
                    summary = stringResource(R.string.pause_search_history_summary),
                )
                SettingsActionItem(
                    title = stringResource(R.string.clear_search_history_title),
                    summary = stringResource(R.string.clear_search_history_summary),
                )
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.behavior_privacy_section)) {
                SettingsSwitchItem(
                    title = stringResource(R.string.haptics_title),
                    summary = stringResource(R.string.haptics_summary),
                    initialValue = true,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.block_screenshots_title),
                    summary = stringResource(R.string.block_screenshots_summary),
                )
            }
        }
    }
}
