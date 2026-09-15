package com.lhacenmed.sona.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.datastore.PlayerStyle
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.R
import com.lhacenmed.sona.feature.settings.component.SettingsChoiceItem
import com.lhacenmed.sona.feature.settings.component.SettingsList
import com.lhacenmed.sona.feature.settings.component.SettingsNavigationItem
import com.lhacenmed.sona.feature.settings.component.SettingsSection
import com.lhacenmed.sona.feature.settings.component.SettingsSectionDivider
import com.lhacenmed.sona.feature.settings.component.SettingsSliderItem
import com.lhacenmed.sona.feature.settings.component.SettingsSwitchItem

/** How the app looks: its theme, the colours it draws itself in, and the shape of the player. */
object AppearanceScreen : Screen {
    override val titleRes: Int get() = R.string.appearance_title

    @Composable
    override fun Content() {
        val viewModel: AppearanceSettingsViewModel = hiltViewModel()
        val roundMode by viewModel.roundMode.collectAsStateWithLifecycle()
        val playerStyle by viewModel.playerStyle.collectAsStateWithLifecycle()
        val sliderStyle by viewModel.sliderStyle.collectAsStateWithLifecycle()
        var showSeekBarStyleDialog by rememberSaveable { mutableStateOf(false) }

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
                // Options in PlayerStyle's order, so an option's index is the style it names.
                SettingsChoiceItem(
                    title = stringResource(R.string.player_style_title),
                    options = listOf(
                        stringResource(R.string.player_style_classic),
                        stringResource(R.string.player_style_minimal),
                        stringResource(R.string.player_style_cinematic),
                        stringResource(R.string.player_style_immersive),
                        stringResource(R.string.player_style_immersive_extended),
                        stringResource(R.string.player_style_editorial),
                    ),
                    selectedIndex = playerStyle.ordinal,
                    onSelect = { viewModel.setPlayerStyle(PlayerStyle.entries[it]) },
                )
                SettingsNavigationItem(
                    title = stringResource(R.string.player_slider_style_title),
                    summary = seekBarStyleLabel(sliderStyle),
                    onClick = { showSeekBarStyleDialog = true },
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
                    checked = roundMode,
                    onCheckedChange = viewModel::setRoundMode,
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

        if (showSeekBarStyleDialog) {
            SeekBarStyleDialog(
                selectedStyle = sliderStyle,
                onSelect = viewModel::setSliderStyle,
                onDismiss = { showSeekBarStyleDialog = false },
            )
        }
    }
}
