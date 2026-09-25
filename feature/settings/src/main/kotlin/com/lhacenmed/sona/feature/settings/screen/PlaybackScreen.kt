package com.lhacenmed.sona.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.data.shuffle.ShuffleAllSource
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.library.shuffle.ShuffleSourceKind
import com.lhacenmed.sona.feature.library.shuffle.ShuffleSourcePickerScreen
import com.lhacenmed.sona.feature.library.shuffle.shuffleSourceKind
import com.lhacenmed.sona.feature.settings.R
import com.lhacenmed.sona.feature.settings.component.SettingsChoiceItem
import com.lhacenmed.sona.feature.settings.component.SettingsList
import com.lhacenmed.sona.feature.settings.component.SettingsSection
import com.lhacenmed.sona.feature.settings.component.SettingsSectionDivider
import com.lhacenmed.sona.feature.settings.component.SettingsSliderItem
import com.lhacenmed.sona.feature.settings.component.SettingsSwitchItem
import com.lhacenmed.sona.feature.settings.component.settingsScrollTarget

/** A row [PlaybackScreen] can be opened scrolled to. */
enum class PlaybackSetting {
    /** What shuffle-all plays - where the library's shuffle button sends its Other. */
    SHUFFLE_ALL_SOURCE,
}

/**
 * How playback behaves, how shuffle works, how tracks join onto each other, and how loud they come out -
 * opened scrolled to [scrollTo], when given, as [SettingsList] does.
 */
data class PlaybackScreen(val scrollTo: PlaybackSetting? = null) : Screen {
    override val titleRes: Int get() = R.string.playback_title

    @Composable
    override fun Content() {
        val secondsFormat = stringResource(R.string.seconds_format)
        val decibelsFormat = stringResource(R.string.decibels_format)
        val viewModel: PlaybackSettingsViewModel = hiltViewModel()
        val rewindBeforeSkipBack by viewModel.rewindBeforeSkipBack.collectAsStateWithLifecycle()
        val stopAfterCurrentEnabled by viewModel.stopAfterCurrentEnabled.collectAsStateWithLifecycle()
        val keepShuffle by viewModel.keepShuffle.collectAsStateWithLifecycle()
        val reshuffleEachTime by viewModel.reshuffleEachTime.collectAsStateWithLifecycle()
        val rememberShuffleOrder by viewModel.rememberShuffleOrder.collectAsStateWithLifecycle()
        val shuffleAllButton by viewModel.shuffleAllButton.collectAsStateWithLifecycle()
        val shuffleAllSource by viewModel.shuffleAllSource.collectAsStateWithLifecycle()
        val navigator = LocalNavigator.current

        SettingsList(scrollTo = scrollTo) {
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
                    checked = rewindBeforeSkipBack,
                    onCheckedChange = viewModel::setRewindBeforeSkipBack,
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
                    title = stringResource(R.string.stop_after_current_option_title),
                    summary = stringResource(R.string.stop_after_current_option_summary),
                    checked = stopAfterCurrentEnabled,
                    onCheckedChange = viewModel::setStopAfterCurrentEnabled,
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

            SettingsSection(stringResource(R.string.playback_shuffle_section)) {
                SettingsSwitchItem(
                    title = stringResource(R.string.keep_shuffle_title),
                    summary = stringResource(R.string.keep_shuffle_summary),
                    checked = keepShuffle,
                    onCheckedChange = viewModel::setKeepShuffle,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.reshuffle_each_time_title),
                    summary = stringResource(R.string.reshuffle_each_time_summary),
                    checked = reshuffleEachTime,
                    onCheckedChange = viewModel::setReshuffleEachTime,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.remember_shuffle_order_title),
                    summary = stringResource(R.string.remember_shuffle_order_summary),
                    checked = rememberShuffleOrder,
                    onCheckedChange = viewModel::setRememberShuffleOrder,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.shuffle_all_button_title),
                    summary = stringResource(R.string.shuffle_all_button_summary),
                    checked = shuffleAllButton,
                    onCheckedChange = viewModel::setShuffleAllButton,
                )
                ShuffleAllSourceItem(
                    modifier = Modifier.settingsScrollTarget(PlaybackSetting.SHUFFLE_ALL_SOURCE),
                    source = shuffleAllSource,
                    onChooseAllTracks = viewModel::chooseAllTracksForShuffleAll,
                    onChooseKind = { kind -> navigator.go(ShuffleSourcePickerScreen(kind)) },
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

/**
 * What shuffle-all plays - the library's button and the launcher shortcut alike. Every track is chosen
 * right here; a kind of collection opens its picker, where the one to play is chosen.
 */
@Composable
private fun ShuffleAllSourceItem(
    source: ShuffleAllSource,
    onChooseAllTracks: () -> Unit,
    onChooseKind: (ShuffleSourceKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kinds = ShuffleSourceKind.entries
    val options = listOf(stringResource(R.string.shuffle_source_all_tracks)) + kinds.map { it.label() }
    val sourceKind = source.parent?.shuffleSourceKind
    SettingsChoiceItem(
        title = stringResource(R.string.shuffle_all_source_title),
        options = options,
        selectedIndex = sourceKind?.let { kinds.indexOf(it) + 1 } ?: 0,
        onSelect = { index -> if (index == 0) onChooseAllTracks() else onChooseKind(kinds[index - 1]) },
        modifier = modifier,
        summary = when (source) {
            ShuffleAllSource.AllTracks -> options.first()
            is ShuffleAllSource.Collection ->
                stringResource(R.string.shuffle_all_source_collection, sourceKind?.label().orEmpty(), source.name)
        },
    )
}

@Composable
private fun ShuffleSourceKind.label(): String =
    stringResource(
        when (this) {
            ShuffleSourceKind.PLAYLIST -> R.string.shuffle_source_playlist
            ShuffleSourceKind.ARTIST -> R.string.shuffle_source_artist
            ShuffleSourceKind.ALBUM -> R.string.shuffle_source_album
            ShuffleSourceKind.GENRE -> R.string.shuffle_source_genre
            ShuffleSourceKind.FOLDER -> R.string.shuffle_source_folder
        },
    )
