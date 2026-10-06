package com.lhacenmed.sona.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.datastore.VideoOrientation
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
        val orientation by viewModel.orientation.collectAsStateWithLifecycle()
        val keepOrientation by viewModel.keepOrientation.collectAsStateWithLifecycle()
        val keepAspect by viewModel.keepAspect.collectAsStateWithLifecycle()
        val showClock by viewModel.showClock.collectAsStateWithLifecycle()
        val resume by viewModel.resume.collectAsStateWithLifecycle()
        val gestures by viewModel.gestures.collectAsStateWithLifecycle()
        val continuousPlay by viewModel.continuousPlay.collectAsStateWithLifecycle()
        val doubleTapSeek by viewModel.doubleTapSeek.collectAsStateWithLifecycle()
        val doubleTapSeekSeconds by viewModel.doubleTapSeekSeconds.collectAsStateWithLifecycle()
        val longPressSpeedUp by viewModel.longPressSpeedUp.collectAsStateWithLifecycle()
        val longPressSpeed by viewModel.longPressSpeed.collectAsStateWithLifecycle()
        val longPressVibration by viewModel.longPressVibration.collectAsStateWithLifecycle()
        val zoomPan by viewModel.zoomPan.collectAsStateWithLifecycle()
        val secondsFormat = stringResource(R.string.seconds_format)
        val speedFormat = stringResource(R.string.video_speed_format)

        SettingsList {
            SettingsSection(stringResource(R.string.video_display_section)) {
                SettingsChoiceItem(
                    title = stringResource(R.string.video_orientation_title),
                    options = VideoOrientation.entries.map { it.label() },
                    selectedIndex = orientation.ordinal,
                    onSelect = { viewModel.setOrientation(VideoOrientation.entries[it]) },
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.video_keep_orientation_title),
                    summary = stringResource(R.string.video_keep_orientation_summary),
                    checked = keepOrientation,
                    onCheckedChange = viewModel::setKeepOrientation,
                )
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
                // Not stored yet: subtitles are still to come.
                SettingsSwitchItem(
                    title = stringResource(R.string.video_subtitle_customization_title),
                    summary = stringResource(R.string.video_subtitle_customization_summary),
                    initialValue = true,
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
                SettingsSwitchItem(
                    title = stringResource(R.string.video_continuous_play_title),
                    summary = stringResource(R.string.video_continuous_play_summary),
                    checked = continuousPlay,
                    onCheckedChange = viewModel::setContinuousPlay,
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
private fun VideoOrientation.label(): String =
    stringResource(
        when (this) {
            VideoOrientation.AUTO -> R.string.video_orientation_auto
            VideoOrientation.PORTRAIT -> R.string.video_orientation_portrait
            VideoOrientation.LANDSCAPE -> R.string.video_orientation_landscape
        },
    )
