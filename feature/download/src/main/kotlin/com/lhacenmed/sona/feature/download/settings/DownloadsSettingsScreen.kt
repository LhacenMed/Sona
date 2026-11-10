package com.lhacenmed.sona.feature.download.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.SonaSettingsRow
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaConfirmationDialog
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialog
import com.lhacenmed.sona.core.downloader.RuntimeState
import com.lhacenmed.sona.core.model.DEFAULT_FILE_NAME_TEMPLATE
import com.lhacenmed.sona.core.model.SponsorSegment
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.download.labelRes
import com.lhacenmed.sona.feature.download.tools.CommandsScreen
import com.lhacenmed.sona.feature.download.tools.CookiesScreen
import com.lhacenmed.sona.feature.download.tools.TerminalScreen
import com.lhacenmed.sona.feature.download.tools.WatchesScreen
import com.lhacenmed.sona.feature.download.R
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsActionItem
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsDownload
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsDownloadItem
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsList
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsNavigationItem
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsSection
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsSliderItem
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsSwitchItem
import com.lhacenmed.sona.core.designsystem.component.settings.SettingsTextFieldDialog
import kotlin.math.roundToInt

/** The most downloads run at once: past it, yt-dlp's processes only slow each other down on a phone. */
private const val MaxDownloadsAtOnce = 4

/**
 * Downloading from links: the downloader it needs - Python, FFmpeg and yt-dlp, downloaded here once and removed here - and
 * yt-dlp, kept up to date by itself and brought up to date here at once; how downloads run, where they are saved and named,
 * how they reach their sites; and the downloader's tools. The downloads themselves are on their own screen.
 */
data object DownloadsSettingsScreen : Screen {
    override val titleRes: Int get() = R.string.downloads_settings_screen_title

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel: DownloadsSettingsViewModel = hiltViewModel()
        val downloader by viewModel.downloaderState.collectAsStateWithLifecycle()
        val ytDlpVersion by viewModel.ytDlpVersion.collectAsStateWithLifecycle()
        val isUpdatingYtDlp by viewModel.isUpdatingYtDlp.collectAsStateWithLifecycle()
        val preferences by viewModel.preferences.collectAsStateWithLifecycle()
        val none = stringResource(R.string.downloads_settings_none)
        var isPickingSegments by remember { mutableStateOf(false) }
        var isClearingHistory by remember { mutableStateOf(false) }

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
            }
            SettingsSection(stringResource(R.string.downloads_settings_queue_section)) {
                SettingsSliderItem(
                    title = stringResource(R.string.downloads_settings_at_once_title),
                    value = preferences.downloadsAtOnce.toFloat(),
                    onValueChangeFinished = { value -> viewModel.updatePreferences { it.copy(downloadsAtOnce = value.roundToInt()) } },
                    valueRange = 1f..MaxDownloadsAtOnce.toFloat(),
                    formatValue = { it.roundToInt().toString() },
                    steps = MaxDownloadsAtOnce - 2,
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.downloads_settings_wifi_only_title),
                    summary = stringResource(R.string.downloads_settings_wifi_only_summary),
                    checked = preferences.wifiOnly,
                    onCheckedChange = { checked -> viewModel.updatePreferences { it.copy(wifiOnly = checked) } },
                )
            }
            SettingsSection(stringResource(R.string.downloads_settings_files_section)) {
                TextSettingRow(
                    title = stringResource(R.string.downloads_settings_folder_title),
                    value = preferences.folderName,
                    summary = stringResource(R.string.downloads_settings_folder_summary, preferences.folderName),
                    onChange = { name -> viewModel.updatePreferences { it.copy(folderName = name.trim('/', ' ').ifEmpty { "Sona" }) } },
                )
                TextSettingRow(
                    title = stringResource(R.string.downloads_settings_file_names_title),
                    value = preferences.fileNameTemplate,
                    summary = preferences.fileNameTemplate,
                    onChange = { template -> viewModel.updatePreferences { it.copy(fileNameTemplate = template.ifBlank { DEFAULT_FILE_NAME_TEMPLATE }) } },
                )
                val segmentNames = SponsorSegment.entries.filter { it in preferences.sponsorSegments }.map { stringResource(it.labelRes) }
                SonaSettingsRow(
                    title = stringResource(R.string.downloads_settings_segments_title),
                    summary = segmentNames.joinToString(", ").ifEmpty { none },
                    onClick = { isPickingSegments = true },
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.downloads_settings_archive_title),
                    summary = stringResource(R.string.downloads_settings_archive_summary),
                    checked = preferences.usesArchive,
                    onCheckedChange = { checked -> viewModel.updatePreferences { it.copy(usesArchive = checked) } },
                )
                SettingsActionItem(
                    title = stringResource(R.string.downloads_settings_clear_archive_title),
                    summary = stringResource(R.string.downloads_settings_clear_archive_summary),
                    onClick = viewModel::clearArchive,
                )
                SettingsActionItem(
                    title = stringResource(R.string.downloads_settings_clear_history_title),
                    summary = stringResource(R.string.downloads_settings_clear_history_summary),
                    onClick = { isClearingHistory = true },
                )
            }
            SettingsSection(stringResource(R.string.downloads_settings_network_section)) {
                TextSettingRow(
                    title = stringResource(R.string.downloads_settings_proxy_title),
                    value = preferences.proxy,
                    summary = preferences.proxy.ifBlank { none },
                    onChange = { proxy -> viewModel.updatePreferences { it.copy(proxy = proxy.trim()) } },
                )
                TextSettingRow(
                    title = stringResource(R.string.downloads_settings_rate_title),
                    value = preferences.rateLimit,
                    summary = preferences.rateLimit.ifBlank { none },
                    onChange = { rate -> viewModel.updatePreferences { it.copy(rateLimit = rate.trim()) } },
                )
                SettingsNavigationItem(
                    title = stringResource(R.string.downloads_settings_cookies_title),
                    summary = stringResource(R.string.downloads_settings_cookies_summary),
                    onClick = { navigator.go(CookiesScreen()) },
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.downloads_settings_use_cookies_title),
                    checked = preferences.usesCookies,
                    onCheckedChange = { checked -> viewModel.updatePreferences { it.copy(usesCookies = checked) } },
                )
            }
            SettingsSection(stringResource(R.string.downloads_settings_tools_section)) {
                SettingsNavigationItem(
                    title = stringResource(R.string.downloads_settings_commands_title),
                    summary = stringResource(R.string.downloads_settings_commands_summary),
                    onClick = { navigator.go(CommandsScreen) },
                )
                SettingsNavigationItem(
                    title = stringResource(R.string.downloads_settings_watches_title),
                    summary = stringResource(R.string.downloads_settings_watches_summary),
                    onClick = { navigator.go(WatchesScreen) },
                )
                SettingsNavigationItem(
                    title = stringResource(R.string.downloads_settings_terminal_title),
                    summary = stringResource(R.string.downloads_settings_terminal_summary),
                    onClick = { navigator.go(TerminalScreen) },
                )
            }
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
            SegmentsDialog(
                picked = preferences.sponsorSegments,
                onChange = { segments -> viewModel.updatePreferences { it.copy(sponsorSegments = segments) } },
                onDismiss = { isPickingSegments = false },
            )
        }
    }
}

/** A row holding a stored piece of text, edited in a dialog - [summary] saying what it is set to. */
@Composable
private fun TextSettingRow(title: String, value: String, summary: String, onChange: (String) -> Unit) {
    var isEditing by remember { mutableStateOf(false) }
    SonaSettingsRow(title = title, summary = summary, onClick = { isEditing = true })
    if (isEditing) {
        SettingsTextFieldDialog(
            title = title,
            initialValue = value,
            onConfirm = {
                onChange(it)
                isEditing = false
            },
            onDismiss = { isEditing = false },
        )
    }
}

/** Which parts SponsorBlock marks Skip sponsors cuts - each turned on and off as it is pressed. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SegmentsDialog(picked: Set<SponsorSegment>, onChange: (Set<SponsorSegment>) -> Unit, onDismiss: () -> Unit) {
    val doneLabel = stringResource(R.string.downloads_settings_segments_done)
    SonaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.downloads_settings_segments_title),
        buttons = { actionButton(label = doneLabel, onClick = onDismiss) },
    ) {
        Column {
            SponsorSegment.entries.forEach { segment ->
                val isPicked = segment in picked
                val toggle = { onChange(if (isPicked) picked - segment else picked + segment) }
                SonaSettingsRow(
                    title = stringResource(segment.labelRes),
                    onClick = toggle,
                    trailing = { Switch(checked = isPicked, onCheckedChange = { toggle() }) },
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
