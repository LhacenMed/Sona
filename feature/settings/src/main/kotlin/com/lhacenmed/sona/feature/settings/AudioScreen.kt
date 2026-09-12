package com.lhacenmed.sona.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.navigation.Screen

/** How playback behaves, and how loud it comes out. */
object AudioScreen : Screen {
    override val titleRes: Int get() = R.string.audio_title

    @Composable
    override fun Content() {
        SettingsList {
            SettingsSection(stringResource(R.string.audio_playback_section)) {
                SettingsSwitchItem(
                    title = stringResource(R.string.headset_autoplay_title),
                    summary = stringResource(R.string.headset_autoplay_summary),
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.rewind_before_skip_title),
                    summary = stringResource(R.string.rewind_before_skip_summary),
                    initialValue = true,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.pause_on_repeat_title),
                    summary = stringResource(R.string.pause_on_repeat_summary),
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.remember_pause_title),
                    summary = stringResource(R.string.remember_pause_summary),
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.swap_headphone_buttons_title),
                    summary = stringResource(R.string.swap_headphone_buttons_summary),
                )
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.audio_normalization_section)) {
                SettingsChoiceItem(
                    title = stringResource(R.string.replaygain_strategy_title),
                    value = stringResource(R.string.replaygain_strategy_value_prefer_album),
                )
                SettingsChoiceItem(
                    title = stringResource(R.string.replaygain_preamp_title),
                    value = stringResource(R.string.replaygain_preamp_summary),
                )
            }
        }
    }
}
