package com.lhacenmed.sona.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.datastore.SubtitleBackdrop
import com.lhacenmed.sona.core.datastore.SubtitlePosition
import com.lhacenmed.sona.core.datastore.SubtitleTextSize
import com.lhacenmed.sona.core.datastore.VideoSettings
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.R
import com.lhacenmed.sona.feature.settings.component.SettingsChoiceItem
import com.lhacenmed.sona.feature.settings.component.SettingsList
import com.lhacenmed.sona.feature.settings.component.SettingsNavigationItem
import com.lhacenmed.sona.feature.settings.component.SettingsSection
import com.lhacenmed.sona.feature.settings.component.SettingsSectionDivider
import com.lhacenmed.sona.feature.settings.component.SettingsSwitchItem
import com.lhacenmed.sona.feature.settings.manage.ExcludedFoldersScreen

/** How the video player shows a video and answers the hand - PLAYit's Video settings. */
data object VideoScreen : Screen {
    override val titleRes: Int get() = R.string.video_title

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel: VideoSettingsViewModel = hiltViewModel()
        val keepAspect by viewModel.keepAspect.collectAsStateWithLifecycle()
        val showClock by viewModel.showClock.collectAsStateWithLifecycle()
        val resume by viewModel.resume.collectAsStateWithLifecycle()
        val gestures by viewModel.gestures.collectAsStateWithLifecycle()
        val doubleTapSeek by viewModel.doubleTapSeek.collectAsStateWithLifecycle()
        val doubleTapSeekSeconds by viewModel.doubleTapSeekSeconds.collectAsStateWithLifecycle()
        val longPressSpeedUp by viewModel.longPressSpeedUp.collectAsStateWithLifecycle()
        val longPressSpeed by viewModel.longPressSpeed.collectAsStateWithLifecycle()
        val longPressVibration by viewModel.longPressVibration.collectAsStateWithLifecycle()
        val zoomPan by viewModel.zoomPan.collectAsStateWithLifecycle()
        val subtitleCustomization by viewModel.subtitleCustomization.collectAsStateWithLifecycle()
        val subtitleTextSize by viewModel.subtitleTextSize.collectAsStateWithLifecycle()
        val subtitleBackdrop by viewModel.subtitleBackdrop.collectAsStateWithLifecycle()
        val subtitlePosition by viewModel.subtitlePosition.collectAsStateWithLifecycle()
        val secondsFormat = stringResource(R.string.seconds_format)
        val speedFormat = stringResource(R.string.video_speed_format)

        SettingsList {
            SettingsSection(stringResource(R.string.video_display_section)) {
                SettingsNavigationItem(
                    title = stringResource(R.string.video_scan_list_title),
                    summary = stringResource(R.string.video_scan_list_summary),
                    onClick = { navigator.go(ExcludedFoldersScreen) },
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.video_keep_aspect_title),
                    summary = stringResource(R.string.video_keep_aspect_summary),
                    checked = keepAspect,
                    onCheckedChange = viewModel::setKeepAspect,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.video_show_clock_title),
                    summary = stringResource(R.string.video_show_clock_summary),
                    checked = showClock,
                    onCheckedChange = viewModel::setShowClock,
                )
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.video_playback_section)) {
                SettingsSwitchItem(
                    title = stringResource(R.string.video_subtitle_customization_title),
                    summary = stringResource(R.string.video_subtitle_customization_summary),
                    checked = subtitleCustomization,
                    onCheckedChange = viewModel::setSubtitleCustomization,
                )
                SettingsChoiceItem(
                    title = stringResource(R.string.video_subtitle_text_size_title),
                    options = SubtitleTextSize.entries.map { it.label() },
                    selectedIndex = subtitleTextSize.ordinal,
                    onSelect = { viewModel.setSubtitleTextSize(SubtitleTextSize.entries[it]) },
                    enabled = subtitleCustomization,
                )
                SettingsChoiceItem(
                    title = stringResource(R.string.video_subtitle_backdrop_title),
                    options = SubtitleBackdrop.entries.map { it.label() },
                    selectedIndex = subtitleBackdrop.ordinal,
                    onSelect = { viewModel.setSubtitleBackdrop(SubtitleBackdrop.entries[it]) },
                    enabled = subtitleCustomization,
                )
                SettingsChoiceItem(
                    title = stringResource(R.string.video_subtitle_position_title),
                    options = SubtitlePosition.entries.map { it.label() },
                    selectedIndex = subtitlePosition.ordinal,
                    onSelect = { viewModel.setSubtitlePosition(SubtitlePosition.entries[it]) },
                    enabled = subtitleCustomization,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.video_resume_title),
                    summary = stringResource(R.string.video_resume_summary),
                    checked = resume,
                    onCheckedChange = viewModel::setResume,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.video_gestures_title),
                    summary = stringResource(R.string.video_gestures_summary),
                    checked = gestures,
                    onCheckedChange = viewModel::setGestures,
                )
                // Not stored yet: the floating window is still to come.
                SettingsSwitchItem(
                    title = stringResource(R.string.video_auto_popup_title),
                    summary = stringResource(R.string.video_auto_popup_summary),
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.video_double_tap_seek_title),
                    summary = stringResource(R.string.video_double_tap_seek_summary),
                    checked = doubleTapSeek,
                    onCheckedChange = viewModel::setDoubleTapSeek,
                )
                SettingsChoiceItem(
                    title = stringResource(R.string.video_double_tap_seek_time_title),
                    options = VideoSettings.DoubleTapSeekSecondsChoices.map { secondsFormat.format(it) },
                    selectedIndex = VideoSettings.DoubleTapSeekSecondsChoices.indexOf(doubleTapSeekSeconds).coerceAtLeast(0),
                    onSelect = { viewModel.setDoubleTapSeekSeconds(VideoSettings.DoubleTapSeekSecondsChoices[it]) },
                    enabled = doubleTapSeek,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.video_long_press_speed_up_title),
                    summary = stringResource(R.string.video_long_press_speed_up_summary),
                    checked = longPressSpeedUp,
                    onCheckedChange = viewModel::setLongPressSpeedUp,
                )
                SettingsChoiceItem(
                    title = stringResource(R.string.video_long_press_speed_title),
                    options = VideoSettings.LongPressSpeedChoices.map { speedFormat.format(it.toBigDecimal().stripTrailingZeros().toPlainString()) },
                    selectedIndex = VideoSettings.LongPressSpeedChoices.indexOf(longPressSpeed).coerceAtLeast(0),
                    onSelect = { viewModel.setLongPressSpeed(VideoSettings.LongPressSpeedChoices[it]) },
                    enabled = longPressSpeedUp,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.video_long_press_vibration_title),
                    summary = stringResource(R.string.video_long_press_vibration_summary),
                    checked = longPressVibration,
                    onCheckedChange = viewModel::setLongPressVibration,
                    enabled = longPressSpeedUp,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.video_zoom_pan_title),
                    summary = stringResource(R.string.video_zoom_pan_summary),
                    checked = zoomPan,
                    onCheckedChange = viewModel::setZoomPan,
                )
            }
        }
    }
}

@Composable
private fun SubtitleTextSize.label(): String =
    stringResource(
        when (this) {
            SubtitleTextSize.SMALL -> R.string.video_subtitle_text_size_small
            SubtitleTextSize.MEDIUM -> R.string.video_subtitle_text_size_medium
            SubtitleTextSize.LARGE -> R.string.video_subtitle_text_size_large
        },
    )

@Composable
private fun SubtitleBackdrop.label(): String =
    stringResource(
        when (this) {
            SubtitleBackdrop.OUTLINE -> R.string.video_subtitle_backdrop_outline
            SubtitleBackdrop.SHADOW -> R.string.video_subtitle_backdrop_shadow
            SubtitleBackdrop.BOX -> R.string.video_subtitle_backdrop_box
        },
    )

@Composable
private fun SubtitlePosition.label(): String =
    stringResource(
        when (this) {
            SubtitlePosition.LOW -> R.string.video_subtitle_position_low
            SubtitlePosition.MIDDLE -> R.string.video_subtitle_position_middle
            SubtitlePosition.HIGH -> R.string.video_subtitle_position_high
        },
    )
