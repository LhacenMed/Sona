package com.lhacenmed.sona.feature.download

import android.os.Environment
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.InfoSeparator
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaBottomSheetScope
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaOptionRow
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.core.downloader.fractionOf
import com.lhacenmed.sona.core.model.Download
import com.lhacenmed.sona.core.model.DownloadStatus
import com.lhacenmed.sona.core.navigation.LocalNavigator
import kotlin.math.roundToInt

/**
 * A download not over, opened - everything its row leaves out: how far, as a large percent with the time left and a bar; how
 * much of how much, how fast, as what and where to; its log, the downloader's own words; Play now, once there is enough of it
 * to play as it downloads; and Pause - or Resume - beside Cancel, here so it is never hit by accident in the list.
 */
@Composable
internal fun SonaBottomSheetScope.ActiveDownloadContent(download: Download, viewModel: DownloadViewModel) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val runningAll by viewModel.running.collectAsStateWithLifecycle()
    val liveAll by viewModel.liveDownloads.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val running = runningAll[download.id]
    val heldAll by viewModel.held.collectAsStateWithLifecycle()
    val held = heldAll[download.id]
    val isPaused = download.status == DownloadStatus.PAUSED
    val downloaded = running?.downloadedBytes ?: held?.first
    val total = running?.totalBytes ?: held?.second
    val fraction = running?.fraction ?: fractionOf(downloaded, total)
    val status = rowStatus(context, download, running, held, preferences)
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                fraction?.let { context.getString(R.string.download_percent, (it * 100).roundToInt()) } ?: status.text,
                style = if (fraction != null) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (fraction != null) Text(status.text.substringAfterLast(InfoSeparator), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val barModifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        when {
            running != null && fraction == null -> LinearProgressIndicator(modifier = barModifier)
            else -> LinearProgressIndicator(
                progress = { fraction ?: 0f },
                modifier = barModifier,
                color = if (isPaused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            )
        }

        val folder = downloadFolderOf(preferences, download.choices.kind).path.removePrefix(Environment.getExternalStorageDirectory().path).trimStart('/')
        val tiles = listOf(
            R.string.download_tile_downloaded to (downloaded?.let { done -> total?.let { context.getString(R.string.download_size_of, size(done), size(it)) } ?: size(done) }),
            R.string.download_tile_speed to running?.bytesPerSecond?.let { context.getString(R.string.download_speed, size(it)) },
            R.string.download_tile_format to rowMeta(context, download.copy(uploader = null)),
            R.string.download_tile_folder to folder,
        )
        Column(modifier = Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tiles.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { (labelRes, value) -> InfoTile(stringResource(labelRes), value ?: "–", Modifier.weight(1f)) }
                }
            }
        }
    }
    ShowLogRow(download.id)
    // One video or one sound - a playlist's are each played once saved.
    if (running != null && running.itemCount == null && liveAll[download.id]?.isPlayable == true) {
        Button(
            onClick = {
                viewModel.onPlayDownloading(download)
                dismiss()
            },
            shapes = buttonPressShapes(),
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp).heightIn(min = 56.dp),
        ) {
            Icon(SonaIcons.Play, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Text(stringResource(R.string.download_play_now), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing), style = MaterialTheme.typography.titleMedium)
        }
    }
    Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val ids = listOf(download.id)
        SheetButton(
            label = stringResource(if (isPaused) R.string.download_resume else R.string.download_pause),
            icon = if (isPaused) SonaIcons.Play else SonaIcons.Pause,
            isTonal = true,
            modifier = Modifier.weight(1f),
        ) {
            if (isPaused) viewModel.onResume(ids) else viewModel.onPause(ids)
        }
        SheetButton(label = stringResource(R.string.download_cancel), icon = SonaIcons.Close, isTonal = false, modifier = Modifier.weight(1f)) {
            viewModel.onCancel(ids)
            dismiss()
        }
    }
}

/** One fact of a download, small label over its value, in a tile of its own. */
@Composable
private fun InfoTile(label: String, value: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** One of the sheet's two buttons: Pause or Resume, tonal; Cancel, outlined in the colour of what cannot be undone. */
@Composable
private fun SheetButton(label: String, icon: ImageVector, isTonal: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val content: @Composable () -> Unit = {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Text(label, modifier = Modifier.padding(start = ButtonDefaults.IconSpacing), maxLines = 1)
    }
    if (isTonal) {
        FilledTonalButton(onClick = onClick, shapes = buttonPressShapes(), modifier = modifier.heightIn(min = 56.dp)) { content() }
    } else {
        OutlinedButton(
            onClick = onClick,
            shapes = buttonPressShapes(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = modifier.heightIn(min = 56.dp),
        ) { content() }
    }
}

