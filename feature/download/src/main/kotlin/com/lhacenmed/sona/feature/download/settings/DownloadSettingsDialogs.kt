@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.lhacenmed.sona.feature.download.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialog
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialogOption
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.core.model.DEFAULT_FILE_NAME_TEMPLATE
import com.lhacenmed.sona.core.model.DownloaderPreferences
import com.lhacenmed.sona.core.model.FILE_NAME_BASENAME
import com.lhacenmed.sona.core.model.FILE_NAME_EXTENSION
import com.lhacenmed.sona.core.model.SponsorSegment
import com.lhacenmed.sona.core.model.TITLE_FILE_NAME_TEMPLATE
import com.lhacenmed.sona.core.model.UpdateInterval
import com.lhacenmed.sona.core.model.YtDlpChannel
import com.lhacenmed.sona.feature.download.R
import com.lhacenmed.sona.feature.download.rememberDownloadFolderPicker
import kotlin.math.roundToInt

/** Where yt-dlp's SponsorBlock options are described - what Seal's dialog links to. */
private const val SponsorBlockReference = "https://github.com/yt-dlp/yt-dlp#sponsorblock-options"

/** What each way yt-dlp updates itself is called. */
private val UpdateInterval?.labelRes: Int
    get() = when (this) {
        null -> R.string.downloads_settings_auto_update_disabled
        UpdateInterval.DAY -> R.string.downloads_settings_every_day
        UpdateInterval.WEEK -> R.string.downloads_settings_every_week
        UpdateInterval.MONTH -> R.string.downloads_settings_every_month
    }

/** A part of a dialog's content, named as Seal names one: in the theme's colour, over what it gathers. */
@Composable
private fun DialogSubtitle(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
    )
}

/**
 * How yt-dlp is kept up to date - Seal's Update dialog: the channel it is brought up to date from, and how often it does so by
 * itself. Nothing is kept until it is saved.
 */
@Composable
internal fun YtDlpUpdateDialog(
    channel: YtDlpChannel,
    autoUpdate: UpdateInterval?,
    onSave: (YtDlpChannel, UpdateInterval?) -> Unit,
    onDismiss: () -> Unit,
) {
    var pickedChannel by remember { mutableStateOf(channel) }
    var pickedInterval by remember { mutableStateOf(autoUpdate) }
    var isChoosingInterval by remember { mutableStateOf(false) }
    val cancelLabel = stringResource(R.string.download_cancel)
    val saveLabel = stringResource(R.string.download_save)

    SonaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.downloads_settings_update_title),
        icon = { Icon(SonaIcons.SyncAlt, contentDescription = null) },
        buttons = {
            actionButton(label = cancelLabel, onClick = onDismiss)
            actionButton(label = saveLabel, onClick = { onSave(pickedChannel, pickedInterval) })
        },
    ) {
        DialogSubtitle(stringResource(R.string.downloads_settings_update_channel))
        Column(modifier = Modifier.selectableGroup()) {
            YtDlpChannel.entries.forEach { option ->
                SonaDialogOption(
                    label = option.repository,
                    selected = option == pickedChannel,
                    onClick = { pickedChannel = option },
                    tag = stringResource(option.labelRes),
                )
            }
        }
        DialogSubtitle(stringResource(R.string.downloads_settings_additional_settings))
        ExposedDropdownMenuBox(expanded = isChoosingInterval, onExpandedChange = { isChoosingInterval = it }) {
            OutlinedTextField(
                value = stringResource(pickedInterval.labelRes),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.downloads_settings_auto_update)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isChoosingInterval) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = isChoosingInterval, onDismissRequest = { isChoosingInterval = false }) {
                (listOf(null) + UpdateInterval.entries).forEach { interval ->
                    DropdownMenuItem(
                        text = { Text(stringResource(interval.labelRes)) },
                        onClick = {
                            pickedInterval = interval
                            isChoosingInterval = false
                        },
                    )
                }
            }
        }
    }
}

/** The project each channel's builds are published by, as Seal names it. */
private val YtDlpChannel.repository: String
    get() = when (this) {
        YtDlpChannel.STABLE -> "yt-dlp"
        YtDlpChannel.NIGHTLY -> "yt-dlp-nightly-builds"
    }

private val YtDlpChannel.labelRes: Int
    get() = when (this) {
        YtDlpChannel.STABLE -> R.string.downloads_settings_channel_stable
        YtDlpChannel.NIGHTLY -> R.string.downloads_settings_channel_nightly
    }

/**
 * Which parts SponsorBlock cuts - Seal's dialog: the categories by yt-dlp's names, typed or added from the chips under them,
 * `all` for every one and `default` for Sona's own. What is not a category is left out as it is saved.
 */
@Composable
internal fun SponsorBlockDialog(picked: Set<SponsorSegment>, onSave: (Set<SponsorSegment>) -> Unit, onDismiss: () -> Unit) {
    var categories by remember { mutableStateOf(SponsorSegment.entries.filter { it in picked }.joinToString(",") { it.id }) }
    val uriHandler = LocalUriHandler.current
    val cancelLabel = stringResource(R.string.download_cancel)
    val saveLabel = stringResource(R.string.download_save)
    val named = categories.split(',').map(String::trim)

    SonaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.downloads_settings_sponsorblock_title),
        icon = { Icon(SonaIcons.MoneyOff, contentDescription = null) },
        buttons = {
            actionButton(label = cancelLabel, onClick = onDismiss)
            actionButton(label = saveLabel, onClick = { onSave(segmentsNamed(named)) })
        },
    ) {
        Text(stringResource(R.string.downloads_settings_sponsorblock_categories_summary), style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = categories,
            onValueChange = { categories = it },
            label = { Text(stringResource(R.string.downloads_settings_sponsorblock_categories)) },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { SuggestionChip(onClick = { categories = DEFAULT_SEGMENTS }, label = { Text(DEFAULT_SEGMENTS) }) }
            item { SuggestionChip(onClick = { categories = ALL_SEGMENTS }, label = { Text(ALL_SEGMENTS) }) }
            SponsorSegment.entries.filter { it.id !in named }.forEach { segment ->
                item {
                    SuggestionChip(
                        onClick = {
                            val kept = named.filter { it.isNotEmpty() && it != ALL_SEGMENTS && it != DEFAULT_SEGMENTS }
                            categories = (kept + segment.id).joinToString(",")
                        },
                        label = { Text(segment.id) },
                    )
                }
            }
        }
        TextButton(onClick = { runCatching { uriHandler.openUri(SponsorBlockReference) } }, shapes = buttonPressShapes()) {
            Text(stringResource(R.string.downloads_settings_learn_more))
        }
    }
}

/** yt-dlp's name for every category at once. */
private const val ALL_SEGMENTS = "all"

/** Seal's name for the categories a download cuts until others are chosen - Sona's own. */
private const val DEFAULT_SEGMENTS = "default"

/** The categories [names] - yt-dlp's ids, [ALL_SEGMENTS] or [DEFAULT_SEGMENTS] - stand for; any other name for none. */
private fun segmentsNamed(names: List<String>): Set<SponsorSegment> = names.flatMapTo(mutableSetOf()) { name ->
    when (name) {
        ALL_SEGMENTS -> SponsorSegment.entries
        DEFAULT_SEGMENTS -> DownloaderPreferences().sponsorSegments
        else -> SponsorSegment.entries.filter { it.id == name }
    }
}

/** The archive - Seal's Edit file: what it holds, a site and a video's id to a line, edited and saved in place of it. */
@Composable
internal fun ArchiveDialog(content: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var edited by remember { mutableStateOf(content) }
    val cancelLabel = stringResource(R.string.download_cancel)
    val saveLabel = stringResource(R.string.download_save)

    SonaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.downloads_settings_edit_file),
        icon = { Icon(SonaIcons.Edit, contentDescription = null) },
        buttons = {
            actionButton(label = cancelLabel, onClick = onDismiss)
            actionButton(label = saveLabel, onClick = { onSave(edited) })
        },
    ) {
        OutlinedTextField(
            value = edited,
            onValueChange = { edited = it },
            label = { Text(stringResource(R.string.downloads_settings_archive_file)) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            minLines = 10,
            maxLines = 10,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The fragment counts Seal's slider stops at, in order. */
private val FragmentCounts = listOf(1, 8, 16, 24)

/** How many parts of a stream in fragments are fetched at once - Seal's Multi-threaded download, on a slider of its four. */
@Composable
internal fun ConcurrentFragmentsDialog(count: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var position by remember { mutableFloatStateOf(FragmentCounts.indexOf(count).coerceAtLeast(0).toFloat()) }
    val picked = FragmentCounts[position.roundToInt()]
    val cancelLabel = stringResource(R.string.download_cancel)
    val saveLabel = stringResource(R.string.download_save)

    SonaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.downloads_settings_fragments_title),
        icon = { Icon(SonaIcons.OfflineBolt, contentDescription = null) },
        buttons = {
            actionButton(label = cancelLabel, onClick = onDismiss)
            actionButton(label = saveLabel, onClick = { onSave(picked) })
        },
    ) {
        Text(pluralStringResource(R.plurals.downloads_settings_fragments_count, picked, picked))
        Slider(
            value = position,
            onValueChange = { position = it },
            valueRange = 0f..(FragmentCounts.size - 1).toFloat(),
            steps = FragmentCounts.size - 2,
            modifier = Modifier.padding(top = 8.dp).height(48.dp),
        )
    }
}

/** Where yt-dlp's file names are described - what Seal's output template dialog links to. */
private const val OutputTemplateReference = "https://github.com/yt-dlp/yt-dlp#output-template"

/** Where yt-dlp's folders are described - what Seal's custom command directory dialog links to. */
private const val FilesystemReference = "https://github.com/yt-dlp/yt-dlp#filesystem-options"

/** Where yt-dlp's subtitle options are described - what Seal's subtitle language dialog links to. */
private const val SubtitleReference = "https://github.com/yt-dlp/yt-dlp#subtitle-options"

/** One of Seal's outlined chips under a dialog's field: [label], led by [icon], doing [onClick]. */
@Composable
private fun DialogChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(label) }, leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) })
}

/**
 * The languages subtitles are downloaded in - Seal's subtitle languages dialog: the codes, comma-separated, in a field of their
 * own; Reset leaves them to the device's language and English.
 */
@Composable
internal fun SubtitleLanguagesDialog(languages: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var edited by remember { mutableStateOf(languages) }
    val uriHandler = LocalUriHandler.current
    val cancelLabel = stringResource(R.string.download_cancel)
    val saveLabel = stringResource(R.string.download_save)

    SonaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.downloads_settings_subtitle_languages),
        icon = { Icon(SonaIcons.Language, contentDescription = null) },
        buttons = {
            actionButton(label = cancelLabel, onClick = onDismiss)
            actionButton(label = saveLabel, onClick = { onSave(edited) })
        },
    ) {
        Text(stringResource(R.string.downloads_settings_subtitle_languages_summary))
        OutlinedTextField(
            value = edited,
            onValueChange = { edited = it },
            label = { Text(stringResource(R.string.downloads_settings_subtitle_languages)) },
            placeholder = { Text(stringResource(R.string.downloads_settings_subtitle_languages_default)) },
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            DialogChip(stringResource(R.string.downloads_settings_reset), SonaIcons.Sync) { edited = "" }
            DialogChip(stringResource(R.string.downloads_settings_ytdlp_docs), SonaIcons.OpenInNew) { runCatching { uriHandler.openUri(SubtitleReference) } }
        }
    }
}

/** The templates Seal's dialog offers before a custom one, in its order. */
private val FileNameTemplates = listOf(TITLE_FILE_NAME_TEMPLATE, DEFAULT_FILE_NAME_TEMPLATE)

/**
 * How downloads' files are named - Seal's output template dialog: by title alone, by title and id, or by a custom template,
 * which has to keep the title and the extension.
 */
@Composable
internal fun OutputTemplateDialog(template: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var picked by remember { mutableIntStateOf(FileNameTemplates.indexOf(template).takeIf { it >= 0 } ?: FileNameTemplates.size) }
    var custom by remember { mutableStateOf(template.takeIf { it !in FileNameTemplates } ?: DEFAULT_FILE_NAME_TEMPLATE) }
    val isCustomValid = FILE_NAME_BASENAME in custom && custom.endsWith(FILE_NAME_EXTENSION)
    val uriHandler = LocalUriHandler.current
    val cancelLabel = stringResource(R.string.download_cancel)
    val saveLabel = stringResource(R.string.download_save)
    val monospace = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)

    SonaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.downloads_settings_output_template_title),
        icon = { Icon(SonaIcons.FolderSpecial, contentDescription = null) },
        buttons = {
            actionButton(label = cancelLabel, onClick = onDismiss)
            actionButton(
                label = saveLabel,
                enabled = picked < FileNameTemplates.size || isCustomValid,
                onClick = { onSave(FileNameTemplates.getOrNull(picked) ?: custom) },
            )
        },
    ) {
        Text(stringResource(R.string.downloads_settings_output_template_summary), style = MaterialTheme.typography.bodyLarge)
        Column(modifier = Modifier.selectableGroup().padding(top = 12.dp)) {
            CompositionLocalProvider(LocalTextStyle provides monospace) {
                FileNameTemplates.forEachIndexed { index, option ->
                    SonaDialogOption(label = option, selected = picked == index, onClick = { picked = index })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
                RadioButton(selected = picked == FileNameTemplates.size, onClick = { picked = FileNameTemplates.size })
                OutlinedTextField(
                    value = custom,
                    onValueChange = {
                        custom = it
                        picked = FileNameTemplates.size
                    },
                    label = { Text(stringResource(R.string.downloads_settings_custom_template)) },
                    textStyle = monospace,
                    isError = !isCustomValid,
                    supportingText = {
                        Text(
                            stringResource(R.string.downloads_settings_template_required, FILE_NAME_BASENAME, FILE_NAME_EXTENSION),
                            fontFamily = FontFamily.Monospace,
                        )
                    },
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                )
            }
        }
        TextButton(onClick = { runCatching { uriHandler.openUri(OutputTemplateReference) } }, shapes = buttonPressShapes()) {
            Text(stringResource(R.string.downloads_settings_learn_more))
        }
    }
}

/**
 * Where what downloads with a command is saved - Seal's custom command directory dialog: the folder as yt-dlp's `-P` takes it,
 * typed or picked; left empty, each kind's own folder.
 */
@Composable
internal fun CommandFolderDialog(folder: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var edited by remember { mutableStateOf(folder) }
    val pickFolder = rememberDownloadFolderPicker { edited = it }
    val uriHandler = LocalUriHandler.current
    val cancelLabel = stringResource(R.string.download_cancel)
    val saveLabel = stringResource(R.string.download_save)

    SonaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.downloads_settings_command_folder_title),
        icon = { Icon(SonaIcons.Folder, contentDescription = null) },
        buttons = {
            actionButton(label = cancelLabel, onClick = onDismiss)
            actionButton(label = saveLabel, onClick = { onSave(edited.trim().trimEnd('/')) })
        },
    ) {
        Text(stringResource(R.string.downloads_settings_command_folder_summary), style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = edited,
            onValueChange = { edited = it },
            leadingIcon = { Text(stringResource(R.string.downloads_settings_command_folder_flag), fontFamily = FontFamily.Monospace) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            DialogChip(stringResource(R.string.downloads_settings_folder_picker), SonaIcons.FolderOpen, pickFolder)
            DialogChip(stringResource(R.string.downloads_settings_ytdlp_docs), SonaIcons.OpenInNew) { runCatching { uriHandler.openUri(FilesystemReference) } }
        }
    }
}
