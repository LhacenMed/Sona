package com.lhacenmed.sona.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.R
import com.lhacenmed.sona.feature.settings.component.SettingsChoiceItem
import com.lhacenmed.sona.feature.settings.component.SettingsList
import com.lhacenmed.sona.feature.settings.component.SettingsSection
import com.lhacenmed.sona.feature.settings.component.SettingsSectionDivider
import com.lhacenmed.sona.feature.settings.component.SettingsSliderItem
import com.lhacenmed.sona.feature.settings.component.SettingsSwitchItem

/** How playback behaves, how tracks join onto each other, and how loud they come out. */
object PlaybackScreen : Screen {
    override val titleRes: Int get() = R.string.playback_title

    @Composable
    override fun Content() {
        val secondsFormat = stringResource(R.string.seconds_format)
        val decibelsFormat = stringResource(R.string.decibels_format)

        SettingsList {
            SettingsSection(stringResource(R.string.playback_controls_section)) {
                SettingsSwitchItem(
                    title = stringResource(R.string.headset_autoplay_title),
                    summary = stringResource(R.string.headset_autoplay_summary),
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.bluetooth_autoplay_title),
                    summary = stringResource(R.string.bluetooth_autoplay_summary),
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.swap_headphone_buttons_title),
                    summary = stringResource(R.string.swap_headphone_buttons_summary),
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.rewind_before_skip_title),
                    summary = stringResource(R.string.rewind_before_skip_summary),
                    initialValue = true,
                )
                SettingsSliderItem(
                    title = stringResource(R.string.seek_amount_title),
                    valueRange = 5f..60f,
                    initialValue = 10f,
                    steps = 10,
                    formatValue = { secondsFormat.format(it.toInt()) },
                )
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.playback_transitions_section)) {
                SettingsSliderItem(
                    title = stringResource(R.string.crossfade_title),
                    valueRange = 0f..12f,
                    initialValue = 0f,
                    steps = 11,
                    formatValue = { secondsFormat.format(it.toInt()) },
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.gapless_title),
                    summary = stringResource(R.string.gapless_summary),
                    initialValue = true,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.skip_silence_title),
                    summary = stringResource(R.string.skip_silence_summary),
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.pause_on_repeat_title),
                    summary = stringResource(R.string.pause_on_repeat_summary),
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.remember_pause_title),
                    summary = stringResource(R.string.remember_pause_summary),
                )
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.playback_audio_section)) {
                SettingsSwitchItem(
                    title = stringResource(R.string.audio_offload_title),
                    summary = stringResource(R.string.audio_offload_summary),
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.pause_on_mute_title),
                    summary = stringResource(R.string.pause_on_mute_summary),
                )
                SettingsChoiceItem(
                    title = stringResource(R.string.replaygain_strategy_title),
                    options = listOf(
                        stringResource(R.string.replaygain_prefer_album),
                        stringResource(R.string.replaygain_prefer_track),
                        stringResource(R.string.replaygain_off),
                    ),
                )
                SettingsSliderItem(
                    title = stringResource(R.string.replaygain_preamp_title),
                    valueRange = -15f..15f,
                    initialValue = 0f,
                    steps = 29,
                    formatValue = { decibelsFormat.format(it.toInt()) },
                )
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.playback_queue_section)) {
                SettingsSwitchItem(
                    title = stringResource(R.string.persistent_queue_title),
                    summary = stringResource(R.string.persistent_queue_summary),
                    initialValue = true,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.remember_shuffle_title),
                    summary = stringResource(R.string.remember_shuffle_summary),
                    initialValue = true,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.skip_on_error_title),
                    summary = stringResource(R.string.skip_on_error_summary),
                    initialValue = true,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.stop_on_task_clear_title),
                    summary = stringResource(R.string.stop_on_task_clear_summary),
                )
            }
        }
    }
}
