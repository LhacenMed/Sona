package com.lhacenmed.sona.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.R
import com.lhacenmed.sona.feature.settings.component.SettingsActionItem
import com.lhacenmed.sona.feature.settings.component.SettingsChoiceItem
import com.lhacenmed.sona.feature.settings.component.SettingsList
import com.lhacenmed.sona.feature.settings.component.SettingsNote
import com.lhacenmed.sona.feature.settings.component.SettingsSection
import com.lhacenmed.sona.feature.settings.component.SettingsSectionDivider
import com.lhacenmed.sona.feature.settings.component.SettingsSliderItem
import com.lhacenmed.sona.feature.settings.component.SettingsSwitchItem

/** Where lyrics come from, and how they read once they arrive. */
object LyricsScreen : Screen {
    override val titleRes: Int get() = R.string.lyrics_title

    @Composable
    override fun Content() {
        val spFormat = stringResource(R.string.sp_format)
        val dpFormat = stringResource(R.string.dp_format)

        SettingsList {
            SettingsSection(stringResource(R.string.lyrics_display_section)) {
                SettingsChoiceItem(
                    title = stringResource(R.string.lyrics_mode_title),
                    options = listOf(
                        stringResource(R.string.lyrics_mode_synced),
                        stringResource(R.string.lyrics_mode_plain),
                    ),
                )
                SettingsChoiceItem(
                    title = stringResource(R.string.lyrics_animation_title),
                    options = listOf(
                        stringResource(R.string.lyrics_animation_fade),
                        stringResource(R.string.lyrics_animation_karaoke),
                        stringResource(R.string.lyrics_animation_none),
                    ),
                )
                SettingsSliderItem(
                    title = stringResource(R.string.lyrics_text_size_title),
                    valueRange = 12f..32f,
                    initialValue = 20f,
                    steps = 19,
                    formatValue = { spFormat.format(it.toInt()) },
                )
                SettingsSliderItem(
                    title = stringResource(R.string.lyrics_line_spacing_title),
                    valueRange = 0f..24f,
                    initialValue = 8f,
                    steps = 23,
                    formatValue = { dpFormat.format(it.toInt()) },
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.lyrics_blur_title),
                    summary = stringResource(R.string.lyrics_blur_summary),
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.lyrics_auto_scroll_title),
                    summary = stringResource(R.string.lyrics_auto_scroll_summary),
                    initialValue = true,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.lyrics_tap_to_seek_title),
                    summary = stringResource(R.string.lyrics_tap_to_seek_summary),
                    initialValue = true,
                )
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.lyrics_providers_section)) {
                SettingsSwitchItem(
                    title = stringResource(R.string.lyrics_provider_embedded_title),
                    summary = stringResource(R.string.lyrics_provider_embedded_summary),
                    initialValue = true,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.lyrics_provider_lrclib_title),
                    summary = stringResource(R.string.lyrics_provider_lrclib_summary),
                    initialValue = true,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.lyrics_provider_netease_title),
                    summary = stringResource(R.string.lyrics_provider_netease_summary),
                )
                SettingsNote(stringResource(R.string.lyrics_providers_note))
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.lyrics_romanization_section)) {
                SettingsSwitchItem(
                    title = stringResource(R.string.romanize_japanese_title),
                    summary = stringResource(R.string.romanize_japanese_summary),
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.romanize_korean_title),
                    summary = stringResource(R.string.romanize_korean_summary),
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.romanize_chinese_title),
                    summary = stringResource(R.string.romanize_chinese_summary),
                )
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.lyrics_cache_section)) {
                SettingsSwitchItem(
                    title = stringResource(R.string.lyrics_preload_title),
                    summary = stringResource(R.string.lyrics_preload_summary),
                )
                SettingsActionItem(
                    title = stringResource(R.string.lyrics_clear_cache_title),
                    summary = stringResource(R.string.lyrics_clear_cache_summary),
                )
            }
        }
    }
}
