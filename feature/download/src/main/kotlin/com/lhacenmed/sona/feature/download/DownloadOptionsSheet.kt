package com.lhacenmed.sona.feature.download

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.SonaGroupRow
import com.lhacenmed.sona.core.designsystem.component.SonaRowGroup
import com.lhacenmed.sona.core.designsystem.component.SonaValueRow
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialog
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialogOption
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaBottomSheet
import com.lhacenmed.sona.core.designsystem.player.formatPlaybackTime
import com.lhacenmed.sona.core.downloader.MediaFormat
import com.lhacenmed.sona.core.downloader.MediaInfo
import com.lhacenmed.sona.core.downloader.RuntimeState
import com.lhacenmed.sona.core.model.ClipRange
import com.lhacenmed.sona.core.model.DownloadKind
import com.lhacenmed.sona.core.model.VideoFormat
import java.util.Calendar

/** Which of the sheet's dialogs is open. */
private enum class OptionsDialog { VIDEO_FORMAT, EXACT_FORMAT, CLIP, COMMAND, START_AT }

/**
 * Every choice the download sheet does not show, for [info]: what this download alone asks - a part of it, a file per chapter,
 * a time to start - then the user's own, kept for every download after it, then a format of the link's own and a saved
 * command (offered only once one is saved). What needs the downloader says so while it is not here.
 */
@Composable
internal fun DownloadOptionsSheet(viewModel: DownloadViewModel, info: MediaInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val choices by viewModel.sheetChoices.collectAsStateWithLifecycle()
    val options by viewModel.options.collectAsStateWithLifecycle()
    val pickedFormat by viewModel.pickedFormat.collectAsStateWithLifecycle()
    val startAt by viewModel.startAt.collectAsStateWithLifecycle()
    val commands by viewModel.commands.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val downloaderState by viewModel.downloaderState.collectAsStateWithLifecycle()
    val hasDownloader = downloaderState == RuntimeState.Installed
    var dialog by remember { mutableStateOf<OptionsDialog?>(null) }
    val pickFolder = rememberDownloadFolderPicker { folder -> viewModel.onFolderChange(choices.kind, folder) }

    SonaBottomSheet(title = stringResource(R.string.download_more_options), onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            SectionLabel(stringResource(R.string.download_options_this_one))
            SonaRowGroup(
                listOf(
                    { shapes ->
                        SonaValueRow(
                            label = stringResource(R.string.download_option_clip),
                            shapes = shapes,
                            icon = Icons.Filled.ContentCut,
                            value = options.clip?.let { "${formatPlaybackTime(it.startSeconds * 1000)} – ${formatPlaybackTime(it.endSeconds * 1000)}" }
                                ?: stringResource(R.string.download_option_whole),
                            onClick = { dialog = OptionsDialog.CLIP },
                        )
                    },
                    { shapes ->
                        val chapters = info.chapterCount
                        SwitchRow(
                            label = stringResource(R.string.download_option_split_chapters),
                            icon = Icons.Filled.FormatListNumbered,
                            shapes = shapes,
                            checked = options.splitChapters && chapters > 0,
                            value = if (chapters > 0) pluralStringResource(R.plurals.download_chapters, chapters, chapters) else stringResource(R.string.download_no_chapters),
                            enabled = chapters > 0,
                        ) { viewModel.onSplitChaptersChange(it) }
                    },
                    { shapes ->
                        SonaValueRow(
                            label = stringResource(R.string.download_option_start_at),
                            shapes = shapes,
                            icon = Icons.Filled.Schedule,
                            value = startAt?.let { DateFormat.getTimeFormat(context).format(it) } ?: stringResource(R.string.download_option_now),
                            onClick = { dialog = OptionsDialog.START_AT },
                        )
                    },
                ),
            )

            SectionLabel(stringResource(R.string.download_options_every))
            SonaRowGroup(
                listOf(
                    { shapes ->
                        SwitchRow(
                            label = stringResource(R.string.download_cover_art),
                            icon = Icons.Filled.Image,
                            shapes = shapes,
                            checked = choices.savesCover,
                        ) { saves -> viewModel.onSheetChoicesChange(forEveryDownload = true) { it.copy(savesCover = saves) } }
                    },
                    { shapes ->
                        SonaValueRow(
                            label = stringResource(R.string.download_save_to),
                            shapes = shapes,
                            icon = Icons.Filled.Folder,
                            value = preferences.folderFor(choices.kind),
                            onClick = pickFolder,
                        )
                    },
                    { shapes ->
                        SonaValueRow(
                            label = stringResource(R.string.download_video_format),
                            shapes = shapes,
                            icon = Icons.Filled.VideoFile,
                            value = stringResource(choices.videoFormat.labelRes),
                            onClick = { dialog = OptionsDialog.VIDEO_FORMAT },
                        )
                    },
                    { shapes ->
                        SwitchRow(
                            label = stringResource(R.string.download_skip_sponsors),
                            icon = Icons.Filled.FastForward,
                            shapes = shapes,
                            checked = choices.skipSponsors && hasDownloader,
                            value = stringResource(R.string.download_needs_downloader).takeIf { !hasDownloader },
                            enabled = hasDownloader,
                        ) { skips -> viewModel.onSheetChoicesChange(forEveryDownload = true) { it.copy(skipSponsors = skips) } }
                    },
                ),
            )

            SectionLabel(stringResource(R.string.download_options_advanced))
            SonaRowGroup(
                listOfNotNull<SonaGroupRow>(
                    { shapes ->
                        SonaValueRow(
                            label = stringResource(R.string.download_option_format),
                            shapes = shapes,
                            icon = Icons.Filled.Tune,
                            value = pickedFormat?.kindLabel(context) ?: stringResource(R.string.download_option_format_summary),
                            enabled = info.formats.isNotEmpty(),
                            onClick = { dialog = OptionsDialog.EXACT_FORMAT },
                        )
                    },
                    // Offered only to who saved one: everyone else never needs to know it is there.
                    if (commands.isEmpty()) null else { shapes ->
                        SonaValueRow(
                            label = stringResource(R.string.download_option_command),
                            shapes = shapes,
                            icon = Icons.Filled.Terminal,
                            value = commands.firstOrNull { it.arguments == options.commandArguments }?.name ?: stringResource(R.string.download_option_none),
                            onClick = { dialog = OptionsDialog.COMMAND },
                        )
                    },
                ),
            )
        }
    }

    val close = { dialog = null }
    when (dialog) {
        OptionsDialog.VIDEO_FORMAT -> OptionDialog(
            title = stringResource(R.string.download_video_format),
            options = VideoFormat.entries,
            selected = choices.videoFormat,
            label = { stringResource(it.labelRes) },
            summary = { stringResource(it.summaryRes) },
            onSelect = { format -> viewModel.onSheetChoicesChange(forEveryDownload = true) { it.copy(videoFormat = format) } },
            onDismiss = close,
        )
        OptionsDialog.EXACT_FORMAT -> ExactFormatDialog(viewModel, info, onDismiss = close)
        OptionsDialog.CLIP -> ClipDialog(clip = options.clip, durationSeconds = info.durationSeconds, onConfirm = viewModel::onClipChange, onDismiss = close)
        OptionsDialog.COMMAND -> OptionDialog(
            title = stringResource(R.string.download_option_command),
            options = listOf(null) + commands,
            selected = commands.firstOrNull { it.arguments == options.commandArguments },
            label = { it?.name ?: stringResource(R.string.download_option_none) },
            onSelect = viewModel::onCommandChange,
            onDismiss = close,
        )
        OptionsDialog.START_AT -> StartAtDialog(onConfirm = viewModel::onStartAtChange, onDismiss = close)

        null -> Unit
    }
}

/** A format of [info]'s own, picked in place of what the choices would pick - the pictures tallest first, then the sounds richest first. */
@Composable
internal fun ExactFormatDialog(viewModel: DownloadViewModel, info: MediaInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val pickedFormat by viewModel.pickedFormat.collectAsStateWithLifecycle()
    OptionDialog(
        title = stringResource(R.string.download_option_format),
        options = listOf<MediaFormat?>(null) + info.formats.sortedWith(
            compareByDescending<MediaFormat> { it.hasVideo }.thenByDescending { it.height ?: 0 }.thenByDescending { it.bitrateKbps ?: 0 },
        ),
        selected = pickedFormat,
        label = { it?.kindLabel(context) ?: stringResource(R.string.download_option_automatic) },
        onSelect = viewModel::onPickFormat,
        onDismiss = onDismiss,
    )
}

/** "Video · 1080p60 · avc1 · 120 MB" - which kind a picked format downloads, then what tells it from the others. */
private fun MediaFormat.kindLabel(context: Context): String =
    context.getString(if (hasVideo) DownloadKind.VIDEO.labelRes else DownloadKind.AUDIO.labelRes) + " · " + facts(context)

/** A group's heading, in the sheet's colour. */
@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
    )
}

/** A row of a group turned on and off - the whole row the target, as a settings switch row is. */
@Composable
internal fun SwitchRow(
    label: String,
    icon: ImageVector?,
    shapes: ListItemShapes,
    checked: Boolean,
    value: String? = null,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    SonaValueRow(
        label = label,
        shapes = shapes,
        icon = icon,
        value = value,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) },
    )
}

/** One of [options], picked - each over its [summary] where it has one - the dialog closing with the pick. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun <T> OptionDialog(
    title: String,
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    summary: @Composable (T) -> String? = { null },
) {
    val cancelLabel = stringResource(R.string.download_cancel)
    SonaDialog(
        onDismissRequest = onDismiss,
        title = title,
        buttons = { actionButton(label = cancelLabel, onClick = onDismiss) },
    ) {
        // The dialog scrolls a long list itself.
        Column(modifier = Modifier.selectableGroup()) {
            options.forEach { option ->
                SonaDialogOption(
                    label = label(option),
                    summary = summary(option),
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

/** A part of the video to download alone, as "1:30" to "2:45" - or the whole of it again. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ClipDialog(clip: ClipRange?, durationSeconds: Long?, onConfirm: (ClipRange?) -> Unit, onDismiss: () -> Unit) {
    var start by remember { mutableStateOf(clip?.let { formatPlaybackTime(it.startSeconds * 1000) } ?: "0:00") }
    var end by remember { mutableStateOf(clip?.let { formatPlaybackTime(it.endSeconds * 1000) } ?: durationSeconds?.let { formatPlaybackTime(it * 1000) }.orEmpty()) }
    val range = secondsOf(start)?.let { from -> secondsOf(end)?.takeIf { it > from }?.let { ClipRange(from, it) } }
    val cancelLabel = stringResource(R.string.download_cancel)
    val wholeLabel = stringResource(R.string.download_option_whole)
    val confirmLabel = stringResource(R.string.download_option_clip_confirm)
    SonaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.download_option_clip),
        buttons = {
            actionButton(label = cancelLabel, onClick = onDismiss)
            actionButton(label = wholeLabel, onClick = { onConfirm(null); onDismiss() })
            actionButton(label = confirmLabel, enabled = range != null, onClick = { onConfirm(range); onDismiss() })
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val time = KeyboardOptions(keyboardType = KeyboardType.Number)
            OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text(stringResource(R.string.download_option_clip_from)) }, singleLine = true, keyboardOptions = time, modifier = Modifier.weight(1f))
            OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text(stringResource(R.string.download_option_clip_to)) }, singleLine = true, keyboardOptions = time, modifier = Modifier.weight(1f))
        }
    }
}

/** "1:02:03", "2:45" or "90" as seconds - null for what is no time. */
private fun secondsOf(text: String): Long? {
    val parts = text.trim().split(':').map { it.toLongOrNull() ?: return null }
    if (parts.isEmpty() || parts.size > 3) return null
    return parts.fold(0L) { total, part -> total * 60 + part }
}

/** The time of day the download starts at - today, or tomorrow once that time has gone - or now again. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun StartAtDialog(onConfirm: (Long?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(is24Hour = DateFormat.is24HourFormat(context))
    val cancelLabel = stringResource(R.string.download_cancel)
    val nowLabel = stringResource(R.string.download_option_now)
    val confirmLabel = stringResource(R.string.download_option_start_at_confirm)
    SonaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.download_option_start_at),
        buttons = {
            actionButton(label = cancelLabel, onClick = onDismiss)
            actionButton(label = nowLabel, onClick = { onConfirm(null); onDismiss() })
            actionButton(
                label = confirmLabel,
                onClick = {
                    val at = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, state.hour)
                        set(Calendar.MINUTE, state.minute)
                        set(Calendar.SECOND, 0)
                        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
                    }.timeInMillis
                    onConfirm(at)
                    onDismiss()
                },
            )
        },
    ) {
        TimePicker(state = state)
    }
}
