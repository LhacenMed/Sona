package com.lhacenmed.sona.feature.download

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.SonaConnectedChoice
import com.lhacenmed.sona.core.designsystem.component.SonaGroupRow
import com.lhacenmed.sona.core.designsystem.component.SonaRowGroup
import com.lhacenmed.sona.core.designsystem.component.SonaValueRow
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaBottomSheet
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.core.downloader.AudioTrack
import com.lhacenmed.sona.core.downloader.AudioTrackKind
import com.lhacenmed.sona.core.downloader.MediaInfo
import com.lhacenmed.sona.core.downloader.captionLanguageOf
import com.lhacenmed.sona.core.downloader.translatedCaptionCode
import com.lhacenmed.sona.core.downloader.captionGroups
import com.lhacenmed.sona.core.downloader.estimatedSizeBytes
import com.lhacenmed.sona.core.downloader.suggestedCaptions
import com.lhacenmed.sona.core.downloader.videoQualities
import com.lhacenmed.sona.core.model.AudioFormat
import com.lhacenmed.sona.core.model.DownloadChoices
import com.lhacenmed.sona.core.model.DownloadKind
import com.lhacenmed.sona.core.model.VideoQuality
import java.util.Locale

/** A picker's line under its title. */
@Composable
private fun PickerHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
    )
}

/** A row picked as one of several - its radio leading it - the picker closing with the pick. */
@Composable
private fun RadioRow(label: String, value: String?, selected: Boolean, shapes: ListItemShapes, onClick: () -> Unit) {
    SonaValueRow(
        label = label,
        shapes = shapes,
        value = value,
        leading = { RadioButton(selected = selected, onClick = null) },
        trailing = null,
        onClick = onClick,
    )
}

/**
 * How good the download is, for [info]: a video's qualities, each with what it comes to for this video - or a song's formats -
 * kept for every download while "Use for every download" is on; and, for the few who want one, a format of the video's own.
 */
@Composable
internal fun QualitySheet(viewModel: DownloadViewModel, info: MediaInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val choices by viewModel.sheetChoices.collectAsStateWithLifecycle()
    var forEveryDownload by remember { mutableStateOf(true) }
    var isPickingFormat by remember { mutableStateOf(false) }
    fun sizeOf(bytes: Long?) = bytes?.let { Formatter.formatShortFileSize(context, it) }
    val isVideo = choices.kind == DownloadKind.VIDEO
    SonaBottomSheet(
        title = stringResource(if (isVideo) R.string.download_quality else R.string.download_format),
        onDismissRequest = onDismiss,
    ) {
        PickerHint(stringResource(R.string.download_quality_sizes))
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            val pick = { change: (DownloadChoices) -> DownloadChoices ->
                viewModel.onPickFormat(null)
                viewModel.onSheetChoicesChange(forEveryDownload, change)
                dismiss()
            }
            SonaRowGroup(
                if (isVideo) {
                    // A playlist's videos are read as they download: every quality is offered for them.
                    info.videoQualities.ifEmpty { VideoQuality.entries }.map<VideoQuality, SonaGroupRow> { quality ->
                        { shapes ->
                            RadioRow(
                                label = stringResource(quality.labelRes),
                                value = sizeOf(info.estimatedSizeBytes(choices.copy(videoQuality = quality))),
                                selected = quality == choices.videoQuality,
                                shapes = shapes,
                                onClick = { pick { it.copy(videoQuality = quality) } },
                            )
                        }
                    }
                } else {
                    AudioFormat.entries.map<AudioFormat, SonaGroupRow> { format ->
                        { shapes ->
                            RadioRow(
                                label = stringResource(format.labelRes),
                                value = sizeOf(info.estimatedSizeBytes(choices.copy(audioFormat = format))),
                                selected = format == choices.audioFormat,
                                shapes = shapes,
                                onClick = { pick { it.copy(audioFormat = format) } },
                            )
                        }
                    }
                },
            )
            SonaRowGroup(
                listOf(
                    { shapes ->
                        SwitchRow(
                            label = stringResource(R.string.download_for_every_download),
                            icon = Icons.Filled.Bookmark,
                            shapes = shapes,
                            checked = forEveryDownload,
                            value = stringResource(R.string.download_for_every_download_off).takeIf { !forEveryDownload },
                        ) { forEveryDownload = it }
                    },
                    { shapes ->
                        SonaValueRow(
                            label = stringResource(R.string.download_option_format),
                            shapes = shapes,
                            icon = Icons.Filled.Tune,
                            enabled = info.formats.isNotEmpty(),
                            onClick = { isPickingFormat = true },
                        )
                    },
                ),
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
    if (isPickingFormat) ExactFormatDialog(viewModel, info, onDismiss = { isPickingFormat = false })
}

/** The videos's sounds - the one recorded first - one picked; and whether every download keeps to the one recorded. */
@Composable
internal fun AudioTrackSheet(viewModel: DownloadViewModel, info: MediaInfo, onDismiss: () -> Unit) {
    val choices by viewModel.sheetChoices.collectAsStateWithLifecycle()
    val remembered by viewModel.choices.collectAsStateWithLifecycle()
    var alwaysOriginal by remember { mutableStateOf(remembered.audioLanguage == null) }
    val tracks = info.audioTracks
    val chosen = info.audioLanguageFor(choices.audioLanguage)
    SonaBottomSheet(title = stringResource(R.string.download_audio_track), onDismissRequest = onDismiss) {
        PickerHint(pluralStringResource(R.plurals.download_audio_tracks, tracks.size, tracks.size))
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            SonaRowGroup(
                tracks.map<AudioTrack, SonaGroupRow> { track ->
                    { shapes ->
                        RadioRow(
                            label = languageName(track.language),
                            value = stringResource(track.kind.summaryRes),
                            selected = track.language == chosen,
                            shapes = shapes,
                            onClick = {
                                viewModel.onAudioTrackChange(track.language.takeUnless { track.kind == AudioTrackKind.ORIGINAL }, alwaysOriginal)
                                dismiss()
                            },
                        )
                    }
                },
            )
            SonaRowGroup(
                listOf(
                    { shapes ->
                        SwitchRow(
                            label = stringResource(R.string.download_track_always_original),
                            icon = Icons.Filled.Verified,
                            shapes = shapes,
                            checked = alwaysOriginal,
                            value = stringResource(R.string.download_track_remembered),
                        ) {
                            alwaysOriginal = it
                            viewModel.onAudioTrackChange(choices.audioLanguage, it)
                        }
                    },
                ),
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/**
 * The video's captions, any picked - those made by people, those made from the speech, those translated, each group under its
 * heading - added into the video or as files beside it; Done keeps the picks.
 */
@Composable
internal fun SubtitlesSheet(viewModel: DownloadViewModel, info: MediaInfo, onDismiss: () -> Unit) {
    val choices by viewModel.sheetChoices.collectAsStateWithLifecycle()
    val options by viewModel.options.collectAsStateWithLifecycle()
    val groups = info.captionGroups
    var picked by remember {
        mutableStateOf(options.subtitleLanguages ?: if (choices.captions) info.suggestedCaptions(Locale.getDefault().language) else emptyList())
    }
    var asFile by remember { mutableStateOf(choices.subtitlesAsFile) }
    var isAddingLanguage by remember { mutableStateOf(false) }
    fun pickRow(code: String, label: String, value: String? = null): SonaGroupRow = { shapes ->
        val isPicked = code in picked
        SonaValueRow(
            label = label,
            shapes = shapes,
            value = value,
            trailing = if (isPicked) ({ Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }) else null,
            onClick = { picked = if (isPicked) picked - code else picked + code },
        )
    }
    SonaBottomSheet(title = stringResource(R.string.download_subtitles), onDismissRequest = onDismiss) {
        PickerHint(stringResource(R.string.download_subtitles_hint))
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            if (groups.made.isNotEmpty()) {
                SectionLabel(stringResource(R.string.download_subtitles_made))
                SonaRowGroup(groups.made.map { pickRow(it, languageName(it)) })
            }
            if (groups.spoken.isNotEmpty()) {
                SectionLabel(stringResource(R.string.download_subtitles_auto))
                val autoLabel = stringResource(R.string.download_subtitles_auto_language)
                SonaRowGroup(groups.spoken.map { pickRow(it, autoLabel.format(languageName(it))) })
            }
            // Translated by Sona from the video's own - see CaptionTranslation.
            groups.translationSource?.let { source ->
                SectionLabel(stringResource(R.string.download_subtitles_translated))
                val translatedFrom = stringResource(R.string.download_subtitles_translated_from, languageName(source))
                val addLanguage: SonaGroupRow = { shapes ->
                    SonaValueRow(
                        label = stringResource(R.string.download_subtitles_add_language),
                        shapes = shapes,
                        icon = Icons.Filled.Add,
                        onClick = { isAddingLanguage = true },
                    )
                }
                SonaRowGroup(
                    picked.filter { it != captionLanguageOf(it) }.map { code -> pickRow(code, languageName(captionLanguageOf(code)), translatedFrom) } + addLanguage,
                )
            }
            SectionLabel(stringResource(R.string.download_subtitles_add))
            SonaConnectedChoice(
                options = listOf(false, true),
                selected = asFile,
                onSelect = { asFile = it },
                label = { stringResource(if (it) R.string.download_subtitles_as_file else R.string.download_subtitles_in_video) },
                icon = { if (it) Icons.Filled.Description else Icons.Filled.Movie },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    viewModel.onSubtitlesChange(picked, asFile)
                    dismiss()
                },
                shapes = buttonPressShapes(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(top = 16.dp),
            ) {
                Text(stringResource(R.string.download_done))
            }
        }
    }
    val source = groups.translationSource
    if (isAddingLanguage && source != null) {
        val pickedLanguages = picked.map(::captionLanguageOf)
        OptionDialog<String>(
            title = stringResource(R.string.download_subtitles_add_language),
            options = translatableLanguages().filter { it != captionLanguageOf(source) && it !in pickedLanguages },
            selected = null,
            label = { languageName(it) },
            onSelect = { language -> picked = picked + translatedCaptionCode(language, source) },
            onDismiss = { isAddingLanguage = false },
        )
    }
}

/** The quality row's value: the quality chosen - a song's format - and what it comes to, where that is known. */
@Composable
internal fun qualityValue(info: MediaInfo?, choices: DownloadChoices): String {
    val context = LocalContext.current
    val label = stringResource(if (choices.kind == DownloadKind.VIDEO) choices.videoQuality.labelRes else choices.audioFormat.labelRes)
    val size = info?.estimatedSizeBytes(choices)?.let { Formatter.formatShortFileSize(context, it) }
    return listOfNotNull(label, size).joinToString(" · ")
}
