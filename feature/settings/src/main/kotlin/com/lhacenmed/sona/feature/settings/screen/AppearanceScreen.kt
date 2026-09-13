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

/** How the app looks: its theme, the colours it draws itself in, and the shape of the player. */
object AppearanceScreen : Screen {
    override val titleRes: Int get() = R.string.appearance_title

    @Composable
    override fun Content() {
        SettingsList {
            SettingsSection(stringResource(R.string.appearance_theme_section)) {
                SettingsChoiceItem(
                    title = stringResource(R.string.theme_title),
                    options = listOf(
                        stringResource(R.string.theme_automatic),
                        stringResource(R.string.theme_light),
                        stringResource(R.string.theme_dark),
                    ),
                )
                SettingsChoiceItem(
                    title = stringResource(R.string.color_scheme_title),
                    options = listOf(
                        stringResource(R.string.color_scheme_dynamic),
                        stringResource(R.string.color_scheme_from_cover),
                        stringResource(R.string.color_scheme_custom),
                    ),
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.black_theme_title),
                    summary = stringResource(R.string.black_theme_summary),
                )
                SettingsChoiceItem(
                    title = stringResource(R.string.app_icon_title),
                    options = listOf(
                        stringResource(R.string.app_icon_default),
                        stringResource(R.string.app_icon_monochrome),
                    ),
                )
                SettingsChoiceItem(
                    title = stringResource(R.string.font_title),
                    options = listOf(
                        stringResource(R.string.font_system),
                        stringResource(R.string.font_rounded),
                    ),
                )
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.appearance_player_section)) {
                SettingsChoiceItem(
                    title = stringResource(R.string.player_style_title),
                    options = listOf(
                        stringResource(R.string.player_style_classic),
                        stringResource(R.string.player_style_expressive),
                    ),
                )
                SettingsChoiceItem(
                    title = stringResource(R.string.player_background_title),
                    options = listOf(
                        stringResource(R.string.player_background_solid),
                        stringResource(R.string.player_background_gradient),
                        stringResource(R.string.player_background_blurred_cover),
                    ),
                )
                SettingsSliderItem(
                    title = stringResource(R.string.blur_intensity_title),
                    valueRange = 0f..100f,
                    initialValue = 50f,
                    formatValue = { "${it.toInt()}%" },
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.show_volume_bar_title),
                    summary = stringResource(R.string.show_volume_bar_summary),
                    initialValue = true,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.swipe_thumbnail_title),
                    summary = stringResource(R.string.swipe_thumbnail_summary),
                    initialValue = true,
                )
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.appearance_motion_section)) {
                SettingsSwitchItem(
                    title = stringResource(R.string.round_mode_title),
                    summary = stringResource(R.string.round_mode_summary),
                    initialValue = true,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.disable_animations_title),
                    summary = stringResource(R.string.disable_animations_summary),
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.high_refresh_rate_title),
                    summary = stringResource(R.string.high_refresh_rate_summary),
                )
            }
        }
    }
}
