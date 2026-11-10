package com.lhacenmed.sona.feature.download

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lhacenmed.sona.core.designsystem.component.InfoSeparator
import com.lhacenmed.sona.core.designsystem.component.SelectionState
import com.lhacenmed.sona.core.designsystem.component.SonaActionButtonGroup
import com.lhacenmed.sona.core.designsystem.component.SonaCoverArt
import com.lhacenmed.sona.core.designsystem.component.SonaListRow
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
import com.lhacenmed.sona.core.model.DownloadFailure
import com.lhacenmed.sona.core.model.DownloadKind
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.feature.download.tools.CookiesScreen

/**
 * The link given, in a sheet over the list: what it leads to, big enough to tell at a glance it is the right thing, then
 * Audio or Video - the kind used last filled - and Options for everything else. A playlist's videos are picked first. A link
 * that needs the downloader offers it here, for this link alone.
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

/**
 * One video - [info], or while it is read what [preview] shows of it already, a skeleton for the rest - the buttons waiting with
 * it, so nothing moves once it is in.
 */
@Composable
private fun MediaSheet(info: MediaInfo?, preview: LinkPreview?, viewModel: DownloadViewModel) {
    val choices by viewModel.choices.collectAsStateWithLifecycle()
    var isChoosingOptions by remember { mutableStateOf(false) }
    SonaBottomSheet(onDismissRequest = viewModel::onDismissLink, header = { MediaPreview(info, preview) }) {
        KindButtons(lastKind = choices.kind, enabled = info != null, onDownload = viewModel::onDownload)
        OptionsButton(viewModel, enabled = info != null, onClick = { isChoosingOptions = true })
    }
    if (isChoosingOptions && info != null) DownloadOptionsSheet(viewModel, info, onDismiss = { isChoosingOptions = false })
}

@Composable
private fun MediaPreview(info: MediaInfo?, preview: LinkPreview?) {
    val thumbnail = info?.thumbnailUrl ?: preview?.thumbnailUrl
    val title = info?.title ?: preview?.title
    val details = if (info != null) {
        listOfNotNull(info.uploader, info.durationSeconds?.let { formatPlaybackTime(it * 1_000) }, info.site?.removeSuffix("Tab"))
    } else {
        listOfNotNull(preview?.uploader, stringResource(R.string.download_reading_link))
    }
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
        val pictureModifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp))
        if (thumbnail == null) {
            Box(modifier = pictureModifier.background(MaterialTheme.colorScheme.surfaceContainerHighest).shimmer())
        } else {
            AsyncImage(model = thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = pictureModifier)
        }
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp)) {
            // Two lines held for every title, and for the skeleton before it, so nothing below moves as it comes.
            if (title == null) {
                Column(modifier = Modifier.height(56.dp)) {
                    Bone(widthFraction = 0.88f, modifier = Modifier.padding(top = 7.dp))
                    Bone(widthFraction = 0.56f, modifier = Modifier.padding(top = 14.dp))
                }
            } else {
                Text(title, style = MaterialTheme.typography.titleLarge, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
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

/** Audio and Video, side by side - the one used last filled, so the usual tap is the obvious one. */
@Composable
private fun KindButtons(lastKind: DownloadKind, enabled: Boolean, onDownload: (DownloadKind) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
        listOf(DownloadKind.AUDIO to Icons.Filled.MusicNote, DownloadKind.VIDEO to Icons.Filled.Movie).forEach { (kind, icon) ->
            KindButton(kind, icon, isLast = kind == lastKind, enabled = enabled, onClick = { onDownload(kind) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun KindButton(kind: DownloadKind, icon: ImageVector, isLast: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val height = Modifier.heightIn(min = 56.dp)
    val content: @Composable () -> Unit = {
        Icon(icon, contentDescription = null)
        Text(stringResource(kind.labelRes), modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
    }
    if (isLast) {
        Button(onClick = onClick, enabled = enabled, shapes = buttonPressShapes(), modifier = modifier.then(height)) { content() }
    } else {
        FilledTonalButton(onClick = onClick, enabled = enabled, shapes = buttonPressShapes(), modifier = modifier.then(height)) { content() }
    }
}

/** Every other choice, a tap away - saying how many of this download's own are made. */
@Composable
private fun OptionsButton(viewModel: DownloadViewModel, enabled: Boolean, onClick: () -> Unit) {
    val options by viewModel.options.collectAsStateWithLifecycle()
    val pickedFormat by viewModel.pickedFormat.collectAsStateWithLifecycle()
    val startAt by viewModel.startAt.collectAsStateWithLifecycle()
    val changes = listOf(pickedFormat != null, options.clip != null, options.splitChapters, options.commandArguments != null, startAt != null).count { it }
    Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
        TextButton(onClick = onClick, enabled = enabled, shapes = buttonPressShapes()) {
            Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = if (changes == 0) stringResource(R.string.download_options) else stringResource(R.string.download_options_changed, changes),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** A playlist's videos, every one picked to begin with - picked and unpicked by a tap - then downloaded as one kind. */
@Composable
private fun PlaylistSheet(info: MediaInfo, viewModel: DownloadViewModel) {
    val context = LocalContext.current
    val choices by viewModel.choices.collectAsStateWithLifecycle()
    val entries = info.entries.orEmpty()
    val picks = remember(entries) { SelectionState().apply { selectAll(entries.map(PlaylistEntry::id)) } }
    val isAllPicked = picks.count == entries.size
    SonaColumnBottomSheet(title = info.title, onDismissRequest = viewModel::onDismissLink) {
        Text(
            text = listOfNotNull(context.resources.getQuantityString(R.plurals.download_videos, entries.size, entries.size), info.uploader).joinToString(InfoSeparator),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
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
                    subtitle = listOfNotNull(entry.uploader, entry.durationSeconds?.let { formatPlaybackTime(it * 1000) }).joinToString(InfoSeparator),
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
        Box(modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)) {
            KindButtons(lastKind = choices.kind, enabled = picks.isActive, onDownload = { kind -> viewModel.onDownload(kind, entries.filter { picks.isSelected(it.id) }) })
        }
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
            actionButton(label = pasteLabel, onClick = {
                // The clipboard holds the link that failed: the bar takes another.
                viewModel.onDismissLink()
                viewModel.onPaste(null)
            })
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
