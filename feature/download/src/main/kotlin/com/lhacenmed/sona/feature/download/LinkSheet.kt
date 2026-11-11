package com.lhacenmed.sona.feature.download

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lhacenmed.sona.core.designsystem.component.InfoSeparator
import com.lhacenmed.sona.core.designsystem.component.SelectionState
import com.lhacenmed.sona.core.designsystem.component.SonaActionButtonGroup
import com.lhacenmed.sona.core.designsystem.component.SonaConnectedChoice
import com.lhacenmed.sona.core.designsystem.component.SonaCoverArt
import com.lhacenmed.sona.core.designsystem.component.SonaGroupRow
import com.lhacenmed.sona.core.designsystem.component.SonaListRow
import com.lhacenmed.sona.core.designsystem.component.SonaRowGroup
import com.lhacenmed.sona.core.designsystem.component.SonaValueRow
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaBottomSheet
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaColumnBottomSheet
import com.lhacenmed.sona.core.designsystem.component.shimmer
import com.lhacenmed.sona.core.designsystem.player.formatPlaybackTime
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.core.downloader.LinkPreview
import com.lhacenmed.sona.core.downloader.MediaInfo
import com.lhacenmed.sona.core.downloader.PlaylistEntry
import com.lhacenmed.sona.core.downloader.RuntimeState
import com.lhacenmed.sona.core.downloader.captionLanguageOf
import com.lhacenmed.sona.core.downloader.estimatedSizeBytes
import com.lhacenmed.sona.core.downloader.suggestedCaptions
import com.lhacenmed.sona.core.model.DownloadChoices
import com.lhacenmed.sona.core.model.DownloadFailure
import com.lhacenmed.sona.core.model.DownloadKind
import com.lhacenmed.sona.core.model.DownloadOptions
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.feature.download.tools.CookiesScreen
import com.lhacenmed.sona.feature.tageditor.LyricsPick
import com.lhacenmed.sona.feature.tageditor.VideoSong
import com.lhacenmed.sona.feature.tageditor.lyrics.LyricsTiming
import com.lhacenmed.sona.feature.tageditor.tags.TagField
import java.util.Locale

/**
 * The link given, in a sheet over the list: what it leads to, enough to tell at a glance it is the right thing, then Audio or
 * Video - picked, never downloading by itself - the choices that change most downloads, each showing its value, everything
 * else a row away, and Download, saying what it comes to. A playlist's videos are picked first. A link that needs the
 * downloader offers it here, for this link alone.
 */
@Composable
internal fun LinkSheet(sheet: LinkSheet, viewModel: DownloadViewModel) {
    when {
        sheet is LinkSheet.Found && sheet.info.isPlaylist -> PlaylistSheet(sheet.info, viewModel)
        // One call for the read and the read in full: the same sheet stays open, filled in as the read lands.
        sheet is LinkSheet.Reading || sheet is LinkSheet.Found ->
            MediaSheet(info = (sheet as? LinkSheet.Found)?.info, preview = (sheet as? LinkSheet.Reading)?.preview, viewModel = viewModel)
        sheet is LinkSheet.NeedsDownloader -> DownloaderSheet(sheet, viewModel)
        sheet is LinkSheet.Unreadable -> UnreadableSheet(sheet, viewModel)
    }
}

/** Which picker the download sheet has open over it. */
private enum class MediaPicker { SONG, QUALITY, AUDIO_TRACK, SUBTITLES, MORE_OPTIONS }

/**
 * One video - [info], or while it is read what [preview] shows of it already, a skeleton for the rest. Every row is there
 * from the start, showing the choices it will download with, so nothing moves once the read is in: Download alone waits on it.
 */
@Composable
private fun MediaSheet(info: MediaInfo?, preview: LinkPreview?, viewModel: DownloadViewModel) {
    val context = LocalContext.current
    val choices by viewModel.sheetChoices.collectAsStateWithLifecycle()
    val options by viewModel.options.collectAsStateWithLifecycle()
    val pickedFormat by viewModel.pickedFormat.collectAsStateWithLifecycle()
    val song by viewModel.song.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf<MediaPicker?>(null) }
    val size = info?.estimatedSizeBytes(choices, pickedFormat)?.let { Formatter.formatShortFileSize(context, it) }
    SonaBottomSheet(onDismissRequest = viewModel::onDismissLink, header = { MediaHeader(info, preview) }) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            KindChoice(choices.kind) { kind -> viewModel.onSheetChoicesChange { it.copy(kind = kind) } }
            val quality = qualityRow(info, choices) { picker = MediaPicker.QUALITY }
            val track = audioTrackRow(info, choices) { picker = MediaPicker.AUDIO_TRACK }
            SonaRowGroup(
                if (choices.kind == DownloadKind.VIDEO) {
                    listOf(quality, track, subtitlesRow(info, choices, options) { picker = MediaPicker.SUBTITLES })
                } else {
                    listOfNotNull(
                        saveAsRow(choices.savesAsSong) { savesAsSong -> viewModel.onSheetChoicesChange(forEveryDownload = true) { it.copy(savesAsSong = savesAsSong) } },
                        songRow(info, song) { picker = MediaPicker.SONG }.takeIf { choices.savesAsSong },
                        track,
                        quality,
                    )
                },
                modifier = Modifier.padding(top = 16.dp),
            )
            SonaRowGroup(listOf(moreOptionsRow(enabled = info != null) { picker = MediaPicker.MORE_OPTIONS }), modifier = Modifier.padding(top = 12.dp))
            DownloadButton(
                label = size?.let { stringResource(R.string.download_download_size, it) } ?: stringResource(R.string.download_download),
                enabled = info != null,
                viewModel = viewModel,
                onDownload = { viewModel.onDownload() },
                modifier = Modifier.padding(top = 20.dp),
            )
        }
    }
    if (info == null) return
    val close = { picker = null }
    when (picker) {
        MediaPicker.SONG -> song?.let {
            SongSheet(it, onSaveAsFile = { viewModel.onSheetChoicesChange { choices -> choices.copy(savesAsSong = false) } }, onDismiss = close)
        }
        MediaPicker.QUALITY -> QualitySheet(viewModel, info, onDismiss = close)
        MediaPicker.AUDIO_TRACK -> AudioTrackSheet(viewModel, info, onDismiss = close)
        MediaPicker.SUBTITLES -> SubtitlesSheet(viewModel, info, onDismiss = close)
        MediaPicker.MORE_OPTIONS -> DownloadOptionsSheet(viewModel, info, onDismiss = close)
        null -> Unit
    }
}

/** Is it the right thing? Its picture with its length, its title, and who published it where - a skeleton until they are known. */
@Composable
private fun MediaHeader(info: MediaInfo?, preview: LinkPreview?) {
    val thumbnail = info?.thumbnailUrl ?: preview?.thumbnailUrl
    val title = info?.title ?: preview?.title
    val details = if (info?.isPlaylist == true) {
        val count = info.entries.orEmpty().size
        listOfNotNull(stringResource(R.string.download_playlist), pluralStringResource(R.plurals.download_videos, count, count), info.uploader)
    } else if (info != null) {
        listOfNotNull(info.uploader, info.site?.removeSuffix("Tab"))
    } else {
        listOfNotNull(preview?.uploader, stringResource(R.string.download_reading_link))
    }
    Row(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(width = 128.dp, height = 72.dp).clip(RoundedCornerShape(12.dp))) {
            if (thumbnail == null) {
                Box(modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceContainerHighest).shimmer())
            } else {
                AsyncImage(model = thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            }
            info?.durationSeconds?.let { seconds ->
                Text(
                    text = formatPlaybackTime(seconds * 1_000),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
        Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
            // Two lines held for every title, and for the skeleton before it, so nothing below moves as it comes.
            if (title == null) {
                Column(modifier = Modifier.height(44.dp)) {
                    Bone(widthFraction = 0.92f, modifier = Modifier.padding(top = 4.dp))
                    Bone(widthFraction = 0.6f, modifier = Modifier.padding(top = 10.dp))
                }
            } else {
                Text(title, style = MaterialTheme.typography.titleMedium, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(
                text = details.joinToString(InfoSeparator),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** A line of text not read yet. */
@Composable
private fun Bone(widthFraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(14.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .shimmer(),
    )
}

/** Audio or Video, joined - the one picked filled. Picking downloads nothing: Download does. */
@Composable
private fun KindChoice(kind: DownloadKind, onSelect: (DownloadKind) -> Unit) {
    SonaConnectedChoice(
        options = listOf(DownloadKind.AUDIO, DownloadKind.VIDEO),
        selected = kind,
        onSelect = onSelect,
        label = { stringResource(it.labelRes) },
        icon = { if (it == DownloadKind.AUDIO) Icons.Filled.MusicNote else Icons.Filled.Movie },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * How a sound is saved: as the song it is, or as an audio file named as the video is - kept for every download. [isMany] for
 * a playlist's: songs, or audio files.
 */
private fun saveAsRow(savesAsSong: Boolean, isMany: Boolean = false, onChange: (Boolean) -> Unit): SonaGroupRow = { shapes ->
    SonaValueRow(
        label = stringResource(R.string.download_save_as),
        shapes = shapes,
        icon = Icons.Filled.LibraryMusic,
        trailing = {
            SonaConnectedChoice(
                options = listOf(true, false),
                selected = savesAsSong,
                onSelect = onChange,
                label = {
                    stringResource(
                        when {
                            it && isMany -> R.string.download_save_as_songs
                            it -> R.string.download_save_as_song
                            isMany -> R.string.download_save_as_files
                            else -> R.string.download_save_as_file
                        },
                    )
                },
                isCompact = true,
            )
        },
        onClick = { onChange(!savesAsSong) },
    )
}

/**
 * The song the sound is saved as - its cover, its name, who sang it, and what of it the file will hold: "cover, tags, synced
 * lyrics" - or where finding it stands.
 */
private fun songRow(info: MediaInfo?, song: VideoSong?, onClick: () -> Unit): SonaGroupRow = { shapes ->
    val chosen = song?.takeIf { it.isChosen }
    val holds = chosen?.let {
        listOfNotNull(
            stringResource(R.string.download_song_holds_cover),
            stringResource(R.string.download_song_holds_tags),
            when {
                it.lyrics?.lyrics?.timing?.let { timing -> timing != LyricsTiming.NONE } == true -> stringResource(R.string.download_song_holds_synced_lyrics)
                it.lyrics != null || it.lyricsPick is LyricsPick.Written -> stringResource(R.string.download_song_holds_lyrics)
                else -> null
            },
        ).joinToString(", ")
    }
    SonaValueRow(
        label = when {
            chosen != null -> chosen.tags[TagField.TITLE]
            info == null || song?.isFinding != false -> stringResource(R.string.download_finding_song)
            else -> stringResource(R.string.download_song_not_found)
        },
        shapes = shapes,
        leading = { SongArt(chosen?.coverUri, size = 40) },
        value = when {
            chosen != null -> listOf(chosen.tags[TagField.ARTIST], holds.orEmpty()).filter { it.isNotBlank() }.joinToString(InfoSeparator)
            info == null -> stringResource(R.string.download_finding_song_after)
            song?.isFinding != false -> null
            else -> stringResource(R.string.download_song_find_it)
        },
        enabled = info != null,
        onClick = onClick,
    )
}

/** The quality row: a video's picture, or a song's format, and what it comes to. */
private fun qualityRow(info: MediaInfo?, choices: DownloadChoices, onClick: () -> Unit): @Composable (ListItemShapes) -> Unit = { shapes ->
    SonaValueRow(
        label = stringResource(R.string.download_row_quality),
        shapes = shapes,
        icon = if (choices.kind == DownloadKind.VIDEO) Icons.Filled.HighQuality else Icons.Filled.GraphicEq,
        value = qualityValue(info, choices),
        enabled = info != null,
        onClick = onClick,
    )
}

/** The audio track row: the sound kept - the one recorded unless another is picked - offered where the video has more than one. */
private fun audioTrackRow(info: MediaInfo?, choices: DownloadChoices, onClick: () -> Unit): @Composable (ListItemShapes) -> Unit = { shapes ->
    val track = info?.audioLanguageFor(choices.audioLanguage)?.let { language -> info.audioTracks.firstOrNull { it.language == language } }
    SonaValueRow(
        label = stringResource(R.string.download_audio_track),
        shapes = shapes,
        icon = Icons.Filled.RecordVoiceOver,
        value = track?.let { listOf(languageName(it.language), stringResource(it.kind.labelRes)).joinToString(InfoSeparator) }
            ?: stringResource(R.string.download_track_original),
        enabled = track != null,
        onClick = onClick,
    )
}

/** The subtitles row: the captions picked, and whether in the video or beside it - offered where the video has any. */
private fun subtitlesRow(info: MediaInfo?, choices: DownloadChoices, options: DownloadOptions, onClick: () -> Unit): @Composable (ListItemShapes) -> Unit = { shapes ->
    val picked = info?.let { options.subtitleLanguages ?: if (choices.captions) it.suggestedCaptions(Locale.getDefault().language) else emptyList() }
    SonaValueRow(
        label = stringResource(R.string.download_subtitles),
        shapes = shapes,
        icon = Icons.Filled.Subtitles,
        value = when {
            info == null -> null
            !info.hasCaptions -> stringResource(R.string.download_subtitles_none)
            picked.isNullOrEmpty() -> stringResource(R.string.download_subtitles_off)
            else -> stringResource(
                if (choices.subtitlesAsFile) R.string.download_subtitles_value_as_file else R.string.download_subtitles_value_in_video,
                picked.joinToString(", ") { languageName(captionLanguageOf(it)) },
            )
        },
        enabled = info?.hasCaptions == true,
        onClick = onClick,
    )
}

/** The row to everything the sheet does not show. */
private fun moreOptionsRow(enabled: Boolean, onClick: () -> Unit): @Composable (ListItemShapes) -> Unit = { shapes ->
    SonaValueRow(
        label = stringResource(R.string.download_more_options),
        shapes = shapes,
        icon = Icons.Filled.Tune,
        value = stringResource(R.string.download_more_options_summary),
        enabled = enabled,
        onClick = onClick,
    )
}

/** The trailing half of Download is as wide as a touch target. */
private val DownloadMenuButtonWidth = 56.dp
private val DownloadButtonHeight = 56.dp

/**
 * Download, saying what it downloads - Material 3 Expressive's split button: its arrow opens what changes when it starts -
 * later, at a time picked, or on Wi-Fi alone.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadButton(label: String, enabled: Boolean, viewModel: DownloadViewModel, onDownload: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val startAt by viewModel.startAt.collectAsStateWithLifecycle()
    var isMenuOpen by remember { mutableStateOf(false) }
    var isPickingTime by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val leadingWidth = maxWidth - DownloadMenuButtonWidth - SplitButtonDefaults.Spacing
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.LeadingButton(
                    onClick = onDownload,
                    enabled = enabled,
                    modifier = Modifier.width(leadingWidth).height(DownloadButtonHeight),
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize))
                    Text(label, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                }
            },
            trailingButton = {
                Box {
                    SplitButtonDefaults.TrailingButton(
                        checked = isMenuOpen,
                        onCheckedChange = { isMenuOpen = it },
                        enabled = enabled,
                        modifier = Modifier.width(DownloadMenuButtonWidth).height(DownloadButtonHeight),
                    ) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.download_later_or_wifi))
                    }
                    DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.download_option_start_at)) },
                            trailingIcon = startAt?.let { { Text(DateFormat.getTimeFormat(context).format(it)) } },
                            onClick = {
                                isMenuOpen = false
                                isPickingTime = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.downloads_settings_wifi_only_title)) },
                            trailingIcon = if (preferences.wifiOnly) ({ Icon(Icons.Filled.Check, contentDescription = null) }) else null,
                            onClick = { viewModel.onWifiOnlyChange(!preferences.wifiOnly) },
                        )
                    }
                }
            },
        )
    }
    if (isPickingTime) StartAtDialog(onConfirm = viewModel::onStartAtChange, onDismiss = { isPickingTime = false })
}

/**
 * A playlist's videos, every one picked to begin with - picked and unpicked by a tap - then downloaded as one kind, at one
 * quality: each picked its own download, read as it starts, several at once.
 */
@Composable
private fun PlaylistSheet(info: MediaInfo, viewModel: DownloadViewModel) {
    val choices by viewModel.sheetChoices.collectAsStateWithLifecycle()
    val entries = info.entries.orEmpty()
    val picks = remember(entries) { SelectionState().apply { selectAll(entries.map(PlaylistEntry::id)) } }
    val isAllPicked = picks.count == entries.size
    var picker by remember { mutableStateOf<MediaPicker?>(null) }
    SonaColumnBottomSheet(onDismissRequest = viewModel::onDismissLink, header = { MediaHeader(info, preview = null) }) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            KindChoice(choices.kind) { kind -> viewModel.onSheetChoicesChange { it.copy(kind = kind) } }
            SonaRowGroup(
                listOfNotNull(
                    // Each of its songs is found by its own title and artist as it downloads, and tagged only where surely found.
                    saveAsRow(choices.savesAsSong, isMany = true) { savesAsSong -> viewModel.onSheetChoicesChange(forEveryDownload = true) { it.copy(savesAsSong = savesAsSong) } }
                        .takeIf { choices.kind == DownloadKind.AUDIO },
                    qualityRow(info, choices) { picker = MediaPicker.QUALITY },
                    moreOptionsRow(enabled = true) { picker = MediaPicker.MORE_OPTIONS },
                ),
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.download_picked_of, picks.count, entries.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { if (isAllPicked) picks.clear() else picks.selectAll(entries.map(PlaylistEntry::id)) }, shapes = buttonPressShapes()) {
                Text(stringResource(if (isAllPicked) R.string.download_pick_none else R.string.download_pick_all))
            }
        }
        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(entries, key = PlaylistEntry::id) { entry ->
                SonaListRow(
                    title = entry.title,
                    subtitle = entry.durationSeconds?.let { formatPlaybackTime(it * 1000) }.orEmpty(),
                    selection = picks,
                    selectionKey = entry.id,
                    // Picking is all this list is for: a tap picks, as it does while selecting.
                    onClick = { picks.toggle(entry.id) },
                    onOpenOptions = null,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    cover = { isSelected -> SonaCoverArt(coverArtUri = entry.thumbnailUrl, contentDescription = null, isSelected = isSelected) },
                )
            }
        }
        DownloadButton(
            label = stringResource(R.string.download_download_picked, picks.count),
            enabled = picks.isActive,
            viewModel = viewModel,
            onDownload = { viewModel.onDownload(entries = entries.filter { picks.isSelected(it.id) }) },
            modifier = Modifier.padding(16.dp),
        )
    }
    val close = { picker = null }
    when (picker) {
        MediaPicker.QUALITY -> QualitySheet(viewModel, info, onDismiss = close)
        MediaPicker.MORE_OPTIONS -> DownloadOptionsSheet(viewModel, info, onDismiss = close)
        else -> Unit
    }
}

/**
 * A link that could not be read - why, in plain words where the reason is one the user can act on (no connection, the
 * video blocked, a sign-in wanted), with its one fix - then yt-dlp's own words, folded away, and the way to give another.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UnreadableSheet(sheet: LinkSheet.Unreadable, viewModel: DownloadViewModel) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val cancelLabel = stringResource(R.string.download_cancel)
    val pasteLabel = stringResource(R.string.download_paste_another)
    val presentation = sheet.failure.presentation
    val isKnown = sheet.failure != DownloadFailure.FAILED
    SonaBottomSheet(onDismissRequest = viewModel::onDismissLink, header = {}) {
        SheetMessage(
            icon = if (isKnown) presentation.icon else Icons.Filled.LinkOff,
            title = stringResource(if (isKnown) presentation.titleRes else R.string.download_unreadable_title),
            message = stringResource(if (isKnown) presentation.messageRes else R.string.download_unreadable_message),
        )
        FixButton(presentation.fix.labelRes, presentation.fix.icon) {
            when (presentation.fix) {
                FailureFix.TRY_AGAIN, FailureFix.TRY_BEST_FORMAT -> viewModel.open(sheet.url)
                FailureFix.OPEN_LINK -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sheet.url))) }
                FailureFix.SIGN_IN -> {
                    navigator.go(CookiesScreen(sheet.url))
                    dismiss()
                }
                FailureFix.FREE_UP_SPACE -> runCatching { context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }
                FailureFix.GET_DOWNLOADER -> viewModel.onGetDownloader()
            }
        }
        sheet.detail?.let { ErrorDetails(it) }
        SonaActionButtonGroup(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            actionButton(label = cancelLabel, onClick = { dismiss() })
            // The clipboard holds the link that failed: the link field, under the sheet, takes another.
            actionButton(label = pasteLabel, onClick = viewModel::onDismissLink)
        }
    }
}

/**
 * The downloader, offered for the link that needs it: why, its size, and its progress as it comes - the download it was got
 * for going on by itself once it is here. A YouTube video can be saved as a song instead, which needs nothing.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloaderSheet(sheet: LinkSheet.NeedsDownloader, viewModel: DownloadViewModel) {
    val state by viewModel.downloaderState.collectAsStateWithLifecycle()
    val host = hostOf(sheet.url)
    val savesAsAudio = sheet.savesAsAudio
    val notNowLabel = stringResource(R.string.download_not_now)
    val audioLabel = stringResource(R.string.download_save_as_audio)
    val getLabel = stringResource(R.string.download_get_downloader)
    SonaBottomSheet(
        onDismissRequest = viewModel::onDismissLink,
        header = {
            Row(modifier = Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(host, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
            }
        },
    ) {
        SheetMessage(
            icon = Icons.Filled.Extension,
            title = stringResource(if (savesAsAudio) R.string.download_video_needs_downloader else R.string.download_get_downloader_title),
            message = if (savesAsAudio) stringResource(R.string.download_video_needs_downloader_message) else stringResource(R.string.download_get_downloader_message, host),
        )
        when (val current = state) {
            is RuntimeState.Downloading -> Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.download_getting_downloader, (current.progress * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearWavyProgressIndicator(progress = { current.progress }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            }
            RuntimeState.Failed -> Text(
                text = stringResource(R.string.download_downloader_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            else -> Unit
        }
        SonaActionButtonGroup(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            if (savesAsAudio) actionButton(label = audioLabel, onClick = { viewModel.onDownload(DownloadKind.AUDIO) })
            else actionButton(label = notNowLabel, onClick = { dismiss() })
            actionButton(label = getLabel, enabled = state !is RuntimeState.Downloading, onClick = viewModel::onGetDownloader)
        }
    }
}

/** A sheet's message: its [icon] in a circle, a [title] and what it means. */
@Composable
internal fun SheetMessage(icon: ImageVector, title: String, message: String) {
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    }
}
