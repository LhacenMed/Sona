package com.lhacenmed.sona.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.R
import com.lhacenmed.sona.feature.settings.component.SettingsChoiceItem
import com.lhacenmed.sona.feature.settings.component.SettingsInfoItem
import com.lhacenmed.sona.feature.settings.component.SettingsList
import com.lhacenmed.sona.feature.settings.component.SettingsNote
import com.lhacenmed.sona.feature.settings.component.SettingsSection
import com.lhacenmed.sona.feature.settings.component.SettingsSectionDivider

/**
 * What has actually been listened to.
 *
 * Every figure reads as a row rather than as a card or a chart, so the screen keeps the same shape
 * whether a library has been played for a year or was added this morning - and so an empty
 * statistic is a zero in its place rather than a hole in the layout.
 */
object ListeningStatsScreen : Screen {
    override val titleRes: Int get() = R.string.listening_stats_title

    @Composable
    override fun Content() {
        SettingsList {
            SettingsSection(stringResource(R.string.stats_period_section)) {
                SettingsChoiceItem(
                    title = stringResource(R.string.stats_period_title),
                    options = listOf(
                        stringResource(R.string.stats_period_week),
                        stringResource(R.string.stats_period_month),
                        stringResource(R.string.stats_period_year),
                        stringResource(R.string.stats_period_all_time),
                    ),
                    selectedIndex = 3,
                )
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.stats_summary_section)) {
                SettingsInfoItem(
                    title = stringResource(R.string.stats_listening_time_title),
                    value = stringResource(R.string.stats_placeholder_duration),
                )
                SettingsInfoItem(
                    title = stringResource(R.string.stats_tracks_played_title),
                    value = stringResource(R.string.stats_placeholder_count),
                )
                SettingsInfoItem(
                    title = stringResource(R.string.stats_different_artists_title),
                    value = stringResource(R.string.stats_placeholder_count),
                )
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.stats_top_section)) {
                SettingsInfoItem(
                    title = stringResource(R.string.stats_top_track_title),
                    value = stringResource(R.string.stats_placeholder_empty),
                )
                SettingsInfoItem(
                    title = stringResource(R.string.stats_top_artist_title),
                    value = stringResource(R.string.stats_placeholder_empty),
                )
                SettingsInfoItem(
                    title = stringResource(R.string.stats_top_album_title),
                    value = stringResource(R.string.stats_placeholder_empty),
                )
                SettingsNote(stringResource(R.string.stats_note))
            }
        }
    }
}
