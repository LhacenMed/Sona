package com.lhacenmed.sona.feature.download.settings

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaConfirmationDialog
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsActionItem
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsDownload
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsDownloadItem
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsList
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsSecondaryAction
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsSection
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsSectionDivider
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsSliderItem
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsSwitchItem
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsSwitchSplitItem
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.downloader.RuntimeState
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.download.R
import kotlin.math.roundToInt

/** The most downloads run at once: past it, yt-dlp's processes only slow each other down on a phone. */
private const val MaxDownloadsAtOnce = 10

/**
 * Seal's General download settings: the downloader it needs - Python, FFmpeg and yt-dlp, downloaded here once and removed
 * here - yt-dlp, kept up to date by itself on the channel chosen and brought up to date here at once, and how downloads run;
 * what of them is kept; and the archive and SponsorBlock.
 */
data object GeneralDownloadSettingsScreen : Screen {
    override val titleRes: Int get() = R.string.downloads_settings_general_title

    @Composable
    override fun Content() {
        val viewModel: DownloadsSettingsViewModel = hiltViewModel()
        val downloader by viewModel.downloaderState.collectAsStateWithLifecycle()
        val ytDlpVersion by viewModel.ytDlpVersion.collectAsStateWithLifecycle()
        val isUpdatingYtDlp by viewModel.isUpdatingYtDlp.collectAsStateWithLifecycle()
        val preferences by viewModel.preferences.collectAsStateWithLifecycle()
        val choices by viewModel.choices.collectAsStateWithLifecycle()
        var isSettingUpUpdates by remember { mutableStateOf(false) }
        var isRemovingDownloader by remember { mutableStateOf(false) }
        var isClearingHistory by remember { mutableStateOf(false) }
        var isPickingSegments by remember { mutableStateOf(false) }
        var archive by remember { mutableStateOf<String?>(null) }

        SettingsList {
            SettingsSection(stringResource(R.string.downloads_settings_general_section)) {
                SettingsDownloadItem(
                    title = stringResource(R.string.downloads_settings_downloader_title),
                    summary = downloaderSummary(downloader),
                    download = downloader.asSettingsDownload(),
                    checked = null,
                    onCheckedChange = {},
                    onDownload = viewModel::downloadDownloader,
                    onCancel = viewModel::cancelDownloader,
                    onRemove = { isRemovingDownloader = true },
                    icon = SonaIcons.Download,
                )
                SettingsActionItem(
                    title = stringResource(R.string.downloads_settings_ytdlp_title),
                    summary = ytDlpVersion ?: stringResource(R.string.downloads_settings_ytdlp_unavailable),
                    onClick = viewModel::updateYtDlp,
                    icon = SonaIcons.Update,
                    enabled = downloader == RuntimeState.Installed,
                    isBusy = isUpdatingYtDlp,
                    secondaryAction = SettingsSecondaryAction(
                        label = stringResource(R.string.downloads_settings_ytdlp_settings),
                        icon = SonaIcons.Settings,
                        onClick = { isSettingUpUpdates = true },
                    ),
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.downloads_settings_thumbnail_title),
                    summary = stringResource(R.string.downloads_settings_thumbnail_summary),
                    icon = SonaIcons.Image,
                    checked = preferences.savesThumbnail,
                    onCheckedChange = { saves -> viewModel.updatePreferences { it.copy(savesThumbnail = saves) } },
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.downloads_settings_library_button_title),
                    summary = stringResource(R.string.downloads_settings_library_button_summary),
                    icon = if (preferences.showsLibraryButton) SonaIcons.Visibility else SonaIcons.VisibilityOff,
                    checked = preferences.showsLibraryButton,
                    onCheckedChange = { shows -> viewModel.updatePreferences { it.copy(showsLibraryButton = shows) } },
                )
                SettingsSliderItem(
                    title = stringResource(R.string.downloads_settings_at_once_title),
                    value = preferences.downloadsAtOnce.toFloat(),
                    onValueChangeFinished = { value -> viewModel.updatePreferences { it.copy(downloadsAtOnce = value.roundToInt()) } },
                    valueRange = 1f..MaxDownloadsAtOnce.toFloat(),
                    formatValue = { it.roundToInt().toString() },
                    icon = SonaIcons.Downloading,
                    steps = MaxDownloadsAtOnce - 2,
                )
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.downloads_settings_privacy_section)) {
                SettingsSwitchItem(
                    title = stringResource(R.string.downloads_settings_incognito_title),
                    summary = stringResource(R.string.downloads_settings_incognito_summary),
                    icon = if (preferences.isIncognito) SonaIcons.HistoryToggleOff else SonaIcons.History,
                    checked = preferences.isIncognito,
                    onCheckedChange = { incognito -> viewModel.updatePreferences { it.copy(isIncognito = incognito) } },
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.downloads_settings_preview_title),
                    summary = stringResource(R.string.downloads_settings_preview_summary),
                    icon = if (preferences.hidesPreview) SonaIcons.VisibilityOff else SonaIcons.Visibility,
                    checked = preferences.hidesPreview,
                    onCheckedChange = { hides -> viewModel.updatePreferences { it.copy(hidesPreview = hides) } },
                )
                SettingsActionItem(
                    title = stringResource(R.string.downloads_settings_clear_history_title),
                    summary = stringResource(R.string.downloads_settings_clear_history_summary),
                    icon = SonaIcons.DeleteSweep,
                    onClick = { isClearingHistory = true },
                )
            }

            SettingsSectionDivider()

            SettingsSection(stringResource(R.string.downloads_settings_advanced_section)) {
                SettingsSwitchSplitItem(
                    title = stringResource(R.string.downloads_settings_archive_title),
                    summary = stringResource(R.string.downloads_settings_archive_summary),
                    icon = SonaIcons.Archive,
                    checked = preferences.usesArchive,
                    onCheckedChange = { uses -> viewModel.updatePreferences { it.copy(usesArchive = uses) } },
                    onClick = { viewModel.readArchive { archive = it } },
                )
                SettingsSwitchSplitItem(
                    title = stringResource(R.string.downloads_settings_sponsorblock_title),
                    summary = stringResource(R.string.downloads_settings_sponsorblock_summary),
                    icon = SonaIcons.MoneyOff,
                    checked = choices.skipSponsors,
                    onCheckedChange = { skips -> viewModel.updateChoices { it.copy(skipSponsors = skips) } },
                    onClick = { isPickingSegments = true },
                )
            }
        }

        if (isSettingUpUpdates) {
            YtDlpUpdateDialog(
                channel = preferences.ytDlpChannel,
                autoUpdate = preferences.ytDlpAutoUpdate,
                onSave = { channel, interval ->
                    viewModel.updatePreferences { it.copy(ytDlpChannel = channel, ytDlpAutoUpdate = interval) }
                    isSettingUpUpdates = false
                },
                onDismiss = { isSettingUpUpdates = false },
            )
        }

        if (isRemovingDownloader) {
            SonaConfirmationDialog(
                title = stringResource(R.string.downloads_settings_remove_downloader_title),
                message = stringResource(R.string.downloads_settings_remove_downloader_message),
                confirmLabel = stringResource(R.string.downloads_settings_remove_downloader_confirm),
                successMessage = stringResource(R.string.downloads_settings_downloader_removed),
                failureMessage = stringResource(R.string.downloads_settings_downloader_not_removed),
                onDismiss = { isRemovingDownloader = false },
                operation = { onFinished ->
                    viewModel.removeDownloader()
                    onFinished(true)
                },
            )
        }

        if (isClearingHistory) {
            SonaConfirmationDialog(
                title = stringResource(R.string.downloads_settings_clear_history_title),
                message = stringResource(R.string.downloads_settings_clear_history_message),
                confirmLabel = stringResource(R.string.downloads_settings_clear_history_confirm),
                successMessage = stringResource(R.string.downloads_settings_history_cleared),
                failureMessage = stringResource(R.string.downloads_settings_history_not_cleared),
                onDismiss = { isClearingHistory = false },
                operation = { onFinished -> viewModel.clearHistory { onFinished(true) } },
            )
        }

        if (isPickingSegments) {
            SponsorBlockDialog(
                picked = preferences.sponsorSegments,
                onSave = { segments ->
                    viewModel.updatePreferences { it.copy(sponsorSegments = segments) }
                    isPickingSegments = false
                },
                onDismiss = { isPickingSegments = false },
            )
        }

        archive?.let { content ->
            ArchiveDialog(
                content = content,
                onSave = { edited ->
                    viewModel.writeArchive(edited)
                    archive = null
                },
                onDismiss = { archive = null },
            )
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
