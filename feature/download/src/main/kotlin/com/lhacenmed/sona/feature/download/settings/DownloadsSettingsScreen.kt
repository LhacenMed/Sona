package com.lhacenmed.sona.feature.download.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsList
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsNavigationItem
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.download.R
import com.lhacenmed.sona.feature.download.tools.CommandsScreen
import com.lhacenmed.sona.feature.download.tools.TerminalScreen
import com.lhacenmed.sona.feature.download.tools.WatchesScreen

/**
 * Downloading from links, as Seal lays its settings out: a page for each part of it - the downloader and how downloads run,
 * where they are saved, how they reach their sites, their subtitles - then the downloader's tools. The
 * downloads themselves are on their own screen.
 */
data object DownloadsSettingsScreen : Screen {
    override val titleRes: Int get() = R.string.downloads_settings_screen_title

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        SettingsList {
            SettingsNavigationItem(
                title = stringResource(R.string.downloads_settings_general_title),
                summary = stringResource(R.string.downloads_settings_general_summary),
                icon = SonaIcons.SettingsApplications,
                onClick = { navigator.go(GeneralDownloadSettingsScreen) },
            )
            SettingsNavigationItem(
                title = stringResource(R.string.downloads_settings_directory_title),
                summary = stringResource(R.string.downloads_settings_directory_summary),
                icon = SonaIcons.Folder,
                onClick = { navigator.go(DownloadDirectorySettingsScreen) },
            )
            SettingsNavigationItem(
                title = stringResource(R.string.downloads_settings_network_title),
                summary = stringResource(R.string.downloads_settings_network_summary),
                icon = SonaIcons.SignalWifi,
                onClick = { navigator.go(NetworkSettingsScreen) },
            )
            SettingsNavigationItem(
                title = stringResource(R.string.downloads_settings_browser_title),
                summary = stringResource(R.string.downloads_settings_browser_summary),
                icon = SonaIcons.Language,
                onClick = { navigator.go(BrowserSettingsScreen) },
            )
            SettingsNavigationItem(
                title = stringResource(R.string.downloads_settings_subtitles_title),
                summary = stringResource(R.string.downloads_settings_subtitles_summary),
                icon = SonaIcons.Subtitles,
                onClick = { navigator.go(SubtitleSettingsScreen) },
            )
            SettingsNavigationItem(
                title = stringResource(R.string.downloads_settings_commands_title),
                summary = stringResource(R.string.downloads_settings_commands_summary),
                icon = SonaIcons.Terminal,
                onClick = { navigator.go(CommandsScreen) },
            )
            SettingsNavigationItem(
                title = stringResource(R.string.downloads_settings_watches_title),
                summary = stringResource(R.string.downloads_settings_watches_summary),
                icon = SonaIcons.Subscriptions,
                onClick = { navigator.go(WatchesScreen) },
            )
            SettingsNavigationItem(
                title = stringResource(R.string.downloads_settings_terminal_title),
                summary = stringResource(R.string.downloads_settings_terminal_summary),
                icon = SonaIcons.Code,
                onClick = { navigator.go(TerminalScreen) },
            )
        }
    }
}
