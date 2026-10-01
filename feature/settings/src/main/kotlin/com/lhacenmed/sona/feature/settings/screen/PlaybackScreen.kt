package com.lhacenmed.sona.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.data.quickplay.QuickPlaySource
import com.lhacenmed.sona.core.datastore.QuickPlayMode
import com.lhacenmed.sona.core.model.PlaybackParent
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.library.quickplay.QuickPlaySourceKind
import com.lhacenmed.sona.feature.library.quickplay.QuickPlaySourcePickerScreen
import com.lhacenmed.sona.feature.library.quickplay.quickPlaySourceKind
import com.lhacenmed.sona.feature.settings.R
import com.lhacenmed.sona.feature.settings.component.SettingsChoiceItem
import com.lhacenmed.sona.feature.settings.component.SettingsList
import com.lhacenmed.sona.feature.settings.component.SettingsNote
import com.lhacenmed.sona.feature.settings.component.SettingsSection
import com.lhacenmed.sona.feature.settings.component.SettingsSectionDivider
import com.lhacenmed.sona.feature.settings.component.SettingsSliderItem
import com.lhacenmed.sona.feature.settings.component.SettingsSwitchItem
import com.lhacenmed.sona.feature.settings.component.settingsScrollTarget

/** A row [PlaybackScreen] can be opened scrolled to. */
enum class PlaybackSetting {
    /** What quick play plays - where the library's quick play button sends its Other. */
    QUICK_PLAY_SOURCE,
}

/**
 * How playback behaves: its controls, the queue, shuffle and quick play, how tracks join onto each other,
 * how loud they come out, and what headphones do - opened scrolled to [scrollTo], when given, as
 * [SettingsList] does.
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
        val showQuickPlayButton by viewModel.showQuickPlayButton.collectAsStateWithLifecycle()
        val quickPlayMode by viewModel.quickPlayMode.collectAsStateWithLifecycle()
        val quickPlaySource by viewModel.quickPlaySource.collectAsStateWithLifecycle()
        val navigator = LocalNavigator.current

        SettingsList(scrollTo = scrollTo) {
            SettingsSection(stringResource(R.string.playback_controls_section)) {
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
            }

            SettingsSectionDivider()

            // The action and the source stay live with the button hidden: the launcher shortcut plays them too.
            SettingsSection(stringResource(R.string.playback_quick_play_section)) {
                SettingsSwitchItem(
                    title = stringResource(R.string.quick_play_button_title),
                    summary = stringResource(R.string.quick_play_button_summary),
                    checked = showQuickPlayButton,
                    onCheckedChange = viewModel::setShowQuickPlayButton,
                )
                SettingsChoiceItem(
                    title = stringResource(R.string.quick_play_mode_title),
                    options = QuickPlayMode.entries.map { it.label() },
                    selectedIndex = quickPlayMode.ordinal,
                    onSelect = { viewModel.setQuickPlayMode(QuickPlayMode.entries[it]) },
                )
                QuickPlaySourceItem(
                    modifier = Modifier.settingsScrollTarget(PlaybackSetting.QUICK_PLAY_SOURCE),
                    source = quickPlaySource,
                    onChoose = viewModel::chooseQuickPlaySource,
                    onChooseKind = { kind -> navigator.go(QuickPlaySourcePickerScreen(kind)) },
                )
                SettingsNote(stringResource(R.string.quick_play_note))
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

            SettingsSection(stringResource(R.string.playback_headphones_section)) {
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
            }
        }
    }
}

/** The sources chosen outright, in the order the source row offers them - ahead of the kinds of collection. */
private val directSources = listOf(QuickPlaySource.AllTracks, QuickPlaySource.RecentlyPlayed, QuickPlaySource.MostPlayed)

/**
 * What quick play plays - the library's button and the launcher shortcut alike. Every track and the two
 * listening histories are chosen right here; a kind of collection opens its picker, where the one to play
 * is chosen.
 */
@Composable
private fun QuickPlaySourceItem(
    source: QuickPlaySource,
    onChoose: (PlaybackParent?) -> Unit,
    onChooseKind: (QuickPlaySourceKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kinds = QuickPlaySourceKind.entries
    SettingsChoiceItem(
        title = stringResource(R.string.quick_play_source_title),
        options = directSources.map { it.label() } + kinds.map { it.label() },
        selectedIndex = when (source) {
            is QuickPlaySource.Collection -> source.parent.quickPlaySourceKind?.let { directSources.size + kinds.indexOf(it) } ?: 0
            else -> directSources.indexOf(source)
        },
        onSelect = { index ->
            if (index < directSources.size) onChoose(directSources[index].parent) else onChooseKind(kinds[index - directSources.size])
        },
        modifier = modifier,
        summary = if (source is QuickPlaySource.Collection) source.label() else null,
    )
}

@Composable
private fun QuickPlayMode.label(): String =
    stringResource(
        when (this) {
            QuickPlayMode.SHUFFLE -> R.string.quick_play_mode_shuffle
            QuickPlayMode.PLAY -> R.string.quick_play_mode_play
        },
    )

/** A source's name - a collection's with its kind before it. */
@Composable
private fun QuickPlaySource.label(): String =
    when (this) {
        QuickPlaySource.AllTracks -> stringResource(R.string.quick_play_source_all_tracks)
        QuickPlaySource.RecentlyPlayed -> stringResource(R.string.quick_play_source_recently_played)
        QuickPlaySource.MostPlayed -> stringResource(R.string.quick_play_source_most_played)
        is QuickPlaySource.Collection ->
            stringResource(R.string.quick_play_source_collection, parent.quickPlaySourceKind?.label().orEmpty(), name)
    }

@Composable
private fun QuickPlaySourceKind.label(): String =
    stringResource(
        when (this) {
            QuickPlaySourceKind.PLAYLIST -> R.string.quick_play_source_playlist
            QuickPlaySourceKind.ARTIST -> R.string.quick_play_source_artist
            QuickPlaySourceKind.ALBUM -> R.string.quick_play_source_album
            QuickPlaySourceKind.GENRE -> R.string.quick_play_source_genre
            QuickPlaySourceKind.FOLDER -> R.string.quick_play_source_folder
        },
    )
