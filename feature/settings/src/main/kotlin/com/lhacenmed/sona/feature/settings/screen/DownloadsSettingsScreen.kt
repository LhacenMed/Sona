package com.lhacenmed.sona.feature.settings.screen

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.downloader.RuntimeState
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.download.DownloadScreen
import com.lhacenmed.sona.feature.settings.R
import com.lhacenmed.sona.feature.settings.component.SettingsActionItem
import com.lhacenmed.sona.feature.settings.component.SettingsDownload
import com.lhacenmed.sona.feature.settings.component.SettingsDownloadItem
import com.lhacenmed.sona.feature.settings.component.SettingsList
import com.lhacenmed.sona.feature.settings.component.SettingsNavigationItem
import com.lhacenmed.sona.feature.settings.component.SettingsSection

/**
 * Downloading from links: the downloader it needs - Python, FFmpeg and yt-dlp, downloaded here once and removed here - and
 * yt-dlp, kept up to date by itself and brought up to date here at once. The downloads themselves are on their own screen.
 */
data object DownloadsSettingsScreen : Screen {
    override val titleRes: Int get() = R.string.downloads_settings_title

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel: DownloadsSettingsViewModel = hiltViewModel()
        val downloader by viewModel.downloaderState.collectAsStateWithLifecycle()
        val ytDlpVersion by viewModel.ytDlpVersion.collectAsStateWithLifecycle()
        val isUpdatingYtDlp by viewModel.isUpdatingYtDlp.collectAsStateWithLifecycle()

        SettingsList {
            SettingsSection(stringResource(R.string.downloads_settings_downloader_section)) {
                SettingsDownloadItem(
                    title = stringResource(R.string.downloads_settings_downloader_title),
                    summary = downloaderSummary(downloader),
                    download = downloader.asSettingsDownload(),
                    checked = null,
                    onCheckedChange = {},
                    onDownload = viewModel::downloadDownloader,
                    onCancel = viewModel::cancelDownloader,
                    onRemove = viewModel::removeDownloader,
                )
                SettingsActionItem(
                    title = stringResource(R.string.downloads_settings_ytdlp_title),
                    summary = ytDlpVersion?.let { stringResource(R.string.downloads_settings_ytdlp_summary, it) }
                        ?: stringResource(R.string.downloads_settings_ytdlp_unavailable),
                    onClick = viewModel::updateYtDlp,
                    enabled = downloader == RuntimeState.Installed,
                    isBusy = isUpdatingYtDlp,
                )
                SettingsNavigationItem(
                    title = stringResource(R.string.downloads_settings_open_title),
                    summary = stringResource(R.string.downloads_settings_open_summary),
                    onClick = { navigator.go(DownloadScreen()) },
                )
            }
        }
    }
}

@Composable
private fun downloaderSummary(state: RuntimeState): String {
    val context = LocalContext.current
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
    return when (state) {
        RuntimeState.Missing -> stringResource(R.string.downloads_settings_downloader_download_summary)
        is RuntimeState.Downloading ->
            stringResource(R.string.downloads_settings_downloader_downloading_summary, size(state.receivedBytes), size(state.totalBytes))
        RuntimeState.Installed -> stringResource(R.string.downloads_settings_downloader_summary)
        RuntimeState.Failed -> stringResource(R.string.downloads_settings_downloader_failed_summary)
    }
}

private fun RuntimeState.asSettingsDownload(): SettingsDownload = when (this) {
    RuntimeState.Missing -> SettingsDownload.Available
    is RuntimeState.Downloading -> SettingsDownload.InProgress(progress)
    RuntimeState.Installed -> SettingsDownload.Done
    RuntimeState.Failed -> SettingsDownload.Failed
}
