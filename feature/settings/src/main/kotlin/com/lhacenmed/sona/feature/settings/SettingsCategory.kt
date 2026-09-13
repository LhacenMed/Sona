package com.lhacenmed.sona.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.ui.graphics.vector.ImageVector
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.screen.AboutScreen
import com.lhacenmed.sona.feature.settings.screen.AppearanceScreen
import com.lhacenmed.sona.feature.settings.screen.BehaviorScreen
import com.lhacenmed.sona.feature.settings.screen.ContentScreen
import com.lhacenmed.sona.feature.settings.screen.ListeningStatsScreen
import com.lhacenmed.sona.feature.settings.screen.LyricsScreen
import com.lhacenmed.sona.feature.settings.screen.PlaybackScreen
import com.lhacenmed.sona.feature.settings.screen.StorageScreen
import com.lhacenmed.sona.feature.settings.screen.UpdatesScreen

/** The bands the settings home groups its categories into. */
enum class SettingsGroup(val titleRes: Int) {
    LibraryAndPlayback(R.string.settings_group_library_and_playback),
    YourMusic(R.string.settings_group_your_music),
    App(R.string.settings_group_app),
}

/**
 * Every category the settings home offers, in the order it offers them.
 *
 * Declared as data rather than written out as rows, so a category is added in one place instead of
 * two and cannot end up in the list without a destination or with a title that disagrees with its
 * screen's. The home reads this; nothing else needs to know the order.
 */
enum class SettingsCategory(
    val group: SettingsGroup,
    val titleRes: Int,
    val summaryRes: Int,
    val icon: ImageVector,
    val screen: Screen,
) {
    Appearance(
        group = SettingsGroup.LibraryAndPlayback,
        titleRes = R.string.appearance_title,
        summaryRes = R.string.appearance_summary,
        icon = Icons.Filled.Palette,
        screen = AppearanceScreen,
    ),
    Playback(
        group = SettingsGroup.LibraryAndPlayback,
        titleRes = R.string.playback_title,
        summaryRes = R.string.playback_summary,
        icon = Icons.Filled.MusicNote,
        screen = PlaybackScreen,
    ),
    Lyrics(
        group = SettingsGroup.LibraryAndPlayback,
        titleRes = R.string.lyrics_title,
        summaryRes = R.string.lyrics_summary,
        icon = Icons.Filled.Lyrics,
        screen = LyricsScreen,
    ),
    Content(
        group = SettingsGroup.LibraryAndPlayback,
        titleRes = R.string.content_title,
        summaryRes = R.string.content_summary,
        icon = Icons.Filled.Language,
        screen = ContentScreen,
    ),
    Behavior(
        group = SettingsGroup.LibraryAndPlayback,
        titleRes = R.string.behavior_title,
        summaryRes = R.string.behavior_summary,
        icon = Icons.Filled.TouchApp,
        screen = BehaviorScreen,
    ),
    ListeningStats(
        group = SettingsGroup.YourMusic,
        titleRes = R.string.listening_stats_title,
        summaryRes = R.string.listening_stats_summary,
        icon = Icons.AutoMirrored.Filled.ShowChart,
        screen = ListeningStatsScreen,
    ),
    Storage(
        group = SettingsGroup.App,
        titleRes = R.string.storage_title,
        summaryRes = R.string.storage_summary,
        icon = Icons.Filled.Storage,
        screen = StorageScreen,
    ),
    Updates(
        group = SettingsGroup.App,
        titleRes = R.string.updates_title,
        summaryRes = R.string.updates_summary,
        icon = Icons.Filled.SystemUpdate,
        screen = UpdatesScreen,
    ),
    About(
        group = SettingsGroup.App,
        titleRes = R.string.about_title,
        summaryRes = R.string.about_summary,
        icon = Icons.Filled.Info,
        screen = AboutScreen,
    ),
}
