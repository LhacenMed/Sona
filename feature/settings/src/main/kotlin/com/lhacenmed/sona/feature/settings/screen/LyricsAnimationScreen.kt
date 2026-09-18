package com.lhacenmed.sona.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.R
import com.lhacenmed.sona.feature.settings.component.SettingsList
import com.lhacenmed.sona.feature.settings.component.SettingsSection
import com.lhacenmed.sona.feature.settings.component.SettingsSliderItem
import com.lhacenmed.sona.feature.settings.component.SettingsSwitchItem

/** How sung words and lines move: ArchiveTune's `LyricsAnimationSettings`. */
object LyricsAnimationScreen : Screen {
    override val titleRes: Int get() = R.string.lyrics_animation_style_title

    @Composable
    override fun Content() {
        val viewModel: LyricsSettingsViewModel = hiltViewModel()
        val lrcBounceEnabled by viewModel.lrcBounceEnabled.collectAsStateWithLifecycle()
        val bounceFactor by viewModel.bounceFactor.collectAsStateWithLifecycle()
        val glowFactor by viewModel.glowFactor.collectAsStateWithLifecycle()
        val fillTransitionWidth by viewModel.fillTransitionWidth.collectAsStateWithLifecycle()

        val percentFormat = stringResource(R.string.percent_format)
        val dpFormat = stringResource(R.string.dp_format)

        SettingsList {
            SettingsSection(stringResource(R.string.lyrics_animation_tuning_section)) {
                SettingsSwitchItem(
                    title = stringResource(R.string.lyrics_line_bounce_title),
                    summary = stringResource(R.string.lyrics_line_bounce_summary),
                    checked = lrcBounceEnabled,
                    onCheckedChange = viewModel::setLrcBounceEnabled,
                )
                SettingsSliderItem(
                    title = stringResource(R.string.lyrics_bounce_amplitude_title),
                    value = bounceFactor,
                    onValueChangeFinished = viewModel::setBounceFactor,
                    valueRange = 0f..2f,
                    formatValue = { percentFormat.format((it * 100).toInt()) },
                )
                SettingsSliderItem(
                    title = stringResource(R.string.lyrics_glow_intensity_title),
                    value = glowFactor,
                    onValueChangeFinished = viewModel::setGlowFactor,
                    valueRange = 0f..2f,
                    formatValue = { percentFormat.format((it * 100).toInt()) },
                )
                SettingsSliderItem(
                    title = stringResource(R.string.lyrics_fill_smoothness_title),
                    value = fillTransitionWidth,
                    onValueChangeFinished = viewModel::setFillTransitionWidth,
                    valueRange = 2f..24f,
                    formatValue = { dpFormat.format(it.toInt()) },
                )
            }
        }
    }
}
