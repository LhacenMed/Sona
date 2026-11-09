package com.lhacenmed.sona.feature.download

import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.SonaIconButton
import com.lhacenmed.sona.core.designsystem.component.SonaSettingsRow
import com.lhacenmed.sona.core.designsystem.component.SonaSettingsValue
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaConfirmationDialog
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialog
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialogOption
import com.lhacenmed.sona.core.designsystem.component.section.rememberSectionListState
import com.lhacenmed.sona.core.designsystem.component.section.section
import com.lhacenmed.sona.core.designsystem.player.formatPlaybackTime
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.core.downloader.RuntimeState
import com.lhacenmed.sona.core.downloader.MediaInfo
import com.lhacenmed.sona.core.model.AudioFormat
import com.lhacenmed.sona.core.model.Download
import com.lhacenmed.sona.core.model.DownloadChoices
import com.lhacenmed.sona.core.model.DownloadKind
import com.lhacenmed.sona.core.model.DownloadStatus
import com.lhacenmed.sona.core.model.VideoQuality
import com.lhacenmed.sona.core.navigation.Screen

/**
 * Where links are downloaded: one typed, pasted or shared - [link] - looked up as it is given, downloaded as the choices
 * below it say, which every Quick download takes too. Beneath, every download asked for, as it stands.
 *
 * Downloading needs the downloader, which this offers to download while it is not here - as the Downloads settings do.
 */
data class DownloadScreen(val link: String? = null) : Screen {
    override val titleRes: Int get() = R.string.download_title

    @Composable
    override fun Content() {
        val viewModel: DownloadViewModel = hiltViewModel()
        val downloaderState by viewModel.downloaderState.collectAsStateWithLifecycle()
        val typedLink by viewModel.link.collectAsStateWithLifecycle()
        val lookup by viewModel.lookup.collectAsStateWithLifecycle()
        val choices by viewModel.choices.collectAsStateWithLifecycle()
        val downloads by viewModel.downloads.collectAsStateWithLifecycle()
        val running by viewModel.running.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val sections = rememberSectionListState()
        var isClearing by rememberSaveable { mutableStateOf(false) }

        // The link this was opened with, given once - not again as the screen comes back.
        var hasTakenLink by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(link) {
            if (!hasTakenLink && link != null) viewModel.onLinkChange(link)
            hasTakenLink = true
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalBottomContentPadding.current)) {
            if (downloaderState != RuntimeState.Installed) {
                item(key = "downloader") { DownloaderPanel(downloaderState, viewModel::onDownloadDownloader, viewModel::onCancelDownloader) }
            }
            item(key = "link") {
                LinkPanel(
                    link = typedLink,
                    lookup = lookup,
                    canDownload = downloaderState == RuntimeState.Installed && linkIn(typedLink) != null,
                    onLinkChange = viewModel::onLinkChange,
                    onPaste = {
                        val pasted = context.getSystemService(ClipboardManager::class.java)?.primaryClip?.getItemAt(0)?.coerceToText(context)
                        pasted?.toString()?.let(viewModel::onLinkChange)
                    },
                    onDownload = viewModel::onDownload,
                )
            }
            section(key = "choices", title = context.getString(R.string.download_as), state = sections) {
                item(key = "choices-rows") { ChoiceRows(choices, viewModel::onChoicesChange) }
            }
            if (downloads.isNotEmpty()) {
                section(
                    key = "downloads",
                    title = context.getString(R.string.download_list),
                    state = sections,
                    hasDividerAbove = true,
                    actions = if (downloads.any { it.status.isFinished }) {
                        listOf(TopBarAction(context.getString(R.string.download_clear_finished), Icons.Filled.DeleteSweep) { isClearing = true })
                    } else {
                        emptyList()
                    },
                ) {
                    items(downloads, key = { "download-${it.id}" }) { download ->
                        DownloadRow(download, running?.takeIf { it.id == download.id }, viewModel::onCancel, viewModel::onRetry)
                    }
                }
            }
        }

        if (isClearing) {
            SonaConfirmationDialog(
                title = stringResource(R.string.download_clear_finished),
                message = stringResource(R.string.download_clear_finished_message),
                confirmLabel = stringResource(R.string.download_clear),
                successMessage = stringResource(R.string.download_cleared),
                failureMessage = stringResource(R.string.download_failed),
                onDismiss = { isClearing = false },
                operation = { onFinished ->
                    viewModel.onClearFinished()
                    onFinished(true)
                },
            )
        }
    }
}

/**
 * What downloading is waiting on: the downloader, to be downloaded - its size said, its progress shown as it comes, and a
 * failed download offered again. Managed, and removed, in the Downloads settings.
 */
@Composable
private fun DownloaderPanel(state: RuntimeState, onDownload: () -> Unit, onCancel: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.download_downloader), style = MaterialTheme.typography.titleMedium)
        Text(
            text = when (state) {
                is RuntimeState.Downloading -> stringResource(R.string.download_downloader_downloading, (state.progress * 100).toInt())
                RuntimeState.Failed -> stringResource(R.string.download_downloader_failed)
                else -> stringResource(R.string.download_downloader_needed)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (state == RuntimeState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state is RuntimeState.Downloading) {
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = onCancel, shapes = buttonPressShapes()) { Text(stringResource(R.string.download_cancel)) }
        } else {
            Button(onClick = onDownload, shapes = buttonPressShapes()) {
                Text(stringResource(if (state == RuntimeState.Failed) R.string.download_retry else R.string.download_downloader_download))
            }
        }
    }
}

@Composable
private fun LinkPanel(
    link: String,
    lookup: LinkLookup,
    canDownload: Boolean,
    onLinkChange: (String) -> Unit,
    onPaste: () -> Unit,
    onDownload: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = link,
            onValueChange = onLinkChange,
            label = { Text(stringResource(R.string.download_link)) },
            singleLine = true,
            trailingIcon = { SonaIconButton(onClick = onPaste, icon = Icons.Filled.ContentPaste, contentDescription = stringResource(R.string.download_paste)) },
            modifier = Modifier.fillMaxWidth(),
        )
        when (lookup) {
            LinkLookup.None -> Unit
            LinkLookup.Looking -> Text(stringResource(R.string.download_looking_up), style = MaterialTheme.typography.bodyMedium)
            is LinkLookup.Failed -> Text(lookup.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            is LinkLookup.Found -> FoundMedia(lookup.info)
        }
        Button(onClick = onDownload, shapes = buttonPressShapes(), enabled = canDownload, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Download, contentDescription = null)
            Text(stringResource(R.string.download_action), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** What the link leads to: its picture, its title, and who published it, with how long it is or how many it holds. */
@Composable
private fun FoundMedia(info: MediaInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        info.thumbnailUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
            )
        }
        Text(info.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val details = listOfNotNull(
            info.uploader,
            info.playlistCount?.let { stringResource(R.string.download_videos, it) } ?: info.durationSeconds?.let { formatPlaybackTime(it * 1_000) },
        ).joinToString(" · ")
        if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Every choice of how to download - the user's own, kept as they change them. */
@Composable
private fun ChoiceRows(choices: DownloadChoices, onChange: (DownloadChoices) -> Unit) {
    var picking by remember { mutableStateOf<ChoicePicker?>(null) }
    Column {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
            DownloadKind.entries.forEachIndexed { index, kind ->
                SegmentedButton(
                    selected = choices.kind == kind,
                    onClick = { onChange(choices.copy(kind = kind)) },
                    shape = SegmentedButtonDefaults.itemShape(index, DownloadKind.entries.size),
                    icon = {},
                ) {
                    Text(stringResource(kind.labelRes))
                }
            }
        }
        when (choices.kind) {
            DownloadKind.VIDEO -> {
                SonaSettingsRow(
                    title = stringResource(R.string.download_quality),
                    onClick = { picking = ChoicePicker.QUALITY },
                    trailing = { SonaSettingsValue(stringResource(choices.videoQuality.labelRes)) },
                )
                SonaSettingsRow(
                    title = stringResource(R.string.download_captions),
                    summary = stringResource(R.string.download_captions_summary),
                    onClick = { onChange(choices.copy(captions = !choices.captions)) },
                    trailing = { Switch(checked = choices.captions, onCheckedChange = { onChange(choices.copy(captions = it)) }) },
                )
            }

            DownloadKind.AUDIO -> SonaSettingsRow(
                title = stringResource(R.string.download_format),
                onClick = { picking = ChoicePicker.FORMAT },
                trailing = { SonaSettingsValue(stringResource(choices.audioFormat.labelRes)) },
            )
        }
        SonaSettingsRow(
            title = stringResource(R.string.download_skip_sponsors),
            summary = stringResource(R.string.download_skip_sponsors_summary),
            onClick = { onChange(choices.copy(skipSponsors = !choices.skipSponsors)) },
            trailing = { Switch(checked = choices.skipSponsors, onCheckedChange = { onChange(choices.copy(skipSponsors = it)) }) },
        )
    }

    when (picking) {
        ChoicePicker.QUALITY -> OptionDialog(
            title = stringResource(R.string.download_quality),
            options = VideoQuality.entries,
            selected = choices.videoQuality,
            label = { stringResource(it.labelRes) },
            onSelect = { onChange(choices.copy(videoQuality = it)) },
            onDismiss = { picking = null },
        )
        ChoicePicker.FORMAT -> OptionDialog(
            title = stringResource(R.string.download_format),
            options = AudioFormat.entries,
            selected = choices.audioFormat,
            label = { stringResource(it.labelRes) },
            onSelect = { onChange(choices.copy(audioFormat = it)) },
            onDismiss = { picking = null },
        )
        null -> Unit
    }
}

private enum class ChoicePicker { QUALITY, FORMAT }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> OptionDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val cancelLabel = stringResource(R.string.download_cancel)
    SonaDialog(
        onDismissRequest = onDismiss,
        title = title,
        buttons = { actionButton(label = cancelLabel, onClick = onDismiss) },
    ) {
        Column(modifier = Modifier.selectableGroup()) {
            options.forEach { option ->
                SonaDialogOption(
                    label = label(option),
                    selected = option == selected,
                    onClick = {
                        onSelect(option)
                        onDismiss()
                    },
                )
            }
        }
    }
}

/** One download, as it stands - stopped from here while it waits or runs, tried again once it failed. */
@Composable
private fun DownloadRow(download: Download, running: RunningDownload?, onCancel: (Download) -> Unit, onRetry: (Download) -> Unit) {
    val summary = when {
        running != null -> listOfNotNull(
            running.progress?.let { stringResource(R.string.download_percent, (it * 100).toInt()) } ?: stringResource(R.string.download_starting),
            if (running.item != null && running.itemCount != null) stringResource(R.string.download_item_of, running.item, running.itemCount) else null,
        ).joinToString(" · ")
        download.status == DownloadStatus.FAILED -> download.error ?: stringResource(R.string.download_failed)
        else -> stringResource(download.status.labelRes)
    }
    val action = when (download.status) {
        DownloadStatus.QUEUED, DownloadStatus.RUNNING -> Triple(Icons.Filled.Close, R.string.download_cancel, onCancel)
        DownloadStatus.FAILED, DownloadStatus.CANCELED -> Triple(Icons.Filled.Refresh, R.string.download_retry, onRetry)
        DownloadStatus.DONE -> null
    }
    SonaSettingsRow(
        title = running?.title ?: download.title ?: download.url,
        summary = summary,
        titleMaxLines = 1,
        summaryMaxLines = 3,
        trailing = action?.let { (icon, labelRes, act) ->
            @Composable { SonaIconButton(onClick = { act(download) }, icon = icon, contentDescription = stringResource(labelRes)) }
        },
    )
}

private val DownloadKind.labelRes: Int
    get() = when (this) {
        DownloadKind.VIDEO -> R.string.download_kind_video
        DownloadKind.AUDIO -> R.string.download_kind_audio
    }

private val VideoQuality.labelRes: Int
    get() = when (this) {
        VideoQuality.BEST -> R.string.download_quality_best
        VideoQuality.P2160 -> R.string.download_quality_2160
        VideoQuality.P1440 -> R.string.download_quality_1440
        VideoQuality.P1080 -> R.string.download_quality_1080
        VideoQuality.P720 -> R.string.download_quality_720
        VideoQuality.P480 -> R.string.download_quality_480
        VideoQuality.P360 -> R.string.download_quality_360
    }

private val AudioFormat.labelRes: Int
    get() = when (this) {
        AudioFormat.ORIGINAL -> R.string.download_format_original
        AudioFormat.M4A -> R.string.download_format_m4a
        AudioFormat.MP3 -> R.string.download_format_mp3
        AudioFormat.OPUS -> R.string.download_format_opus
    }

private val DownloadStatus.labelRes: Int
    get() = when (this) {
        DownloadStatus.QUEUED -> R.string.download_status_queued
        DownloadStatus.RUNNING -> R.string.download_starting
        DownloadStatus.DONE -> R.string.download_saved
        DownloadStatus.FAILED -> R.string.download_failed
        DownloadStatus.CANCELED -> R.string.download_status_canceled
    }
