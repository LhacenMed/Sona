package com.lhacenmed.sona.feature.library.options

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.common.format.formatDuration
import com.lhacenmed.sona.core.common.format.formatFileSize
import com.lhacenmed.sona.core.designsystem.R as DesignR
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialog
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.library.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [track]'s properties - Auxio's `SongDetailDialog`, in its order: name, album, artist, genre, date,
 * track, disc, path, size, duration, format, bit rate and sample rate. ReplayGain, its last two rows,
 * is left out because nothing here reads or applies it.
 *
 * Size, format, bit rate and sample rate are read from the file when asked for rather than carried on
 * every track, and stay off if the file has since moved or lost its permission. The dialog opens once
 * they are read - a moment's read - so every row shows at once, the dialog never growing under them.
 */
@Composable
internal fun TrackPropertiesDialog(track: Track, onDismiss: () -> Unit) {
    var fileProperties by remember(track.path) { mutableStateOf<FileProperties?>(null) }
    LaunchedEffect(track.path) { fileProperties = readFileProperties(track.path) }
    val properties = fileProperties ?: return

    // Read here rather than inside the buttons: they are built outside composition.
    val okLabel = stringResource(DesignR.string.dialog_ok)
    SonaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.properties_title),
        buttons = { actionButton(label = okLabel, onClick = onDismiss) },
    ) {
        PropertyRow(stringResource(R.string.properties_name), track.title)
        PropertyRow(stringResource(R.string.properties_album), track.album)
        PropertyRow(stringResource(R.string.properties_artist), track.artist)
        track.genre?.let { PropertyRow(stringResource(R.string.properties_genre), it) }
        track.year?.let { PropertyRow(stringResource(R.string.properties_date), it.toString()) }
        track.trackNumber?.let { PropertyRow(stringResource(R.string.properties_track), it.toString()) }
        track.discNumber?.let { PropertyRow(stringResource(R.string.properties_disc), it.toString()) }
        PropertyRow(stringResource(R.string.properties_path), track.path)
        properties.sizeBytes?.let { PropertyRow(stringResource(R.string.properties_size), formatFileSize(it)) }
        PropertyRow(stringResource(R.string.properties_duration), formatDuration(track.durationMs))
        properties.format?.let { PropertyRow(stringResource(R.string.properties_format), it) }
        properties.bitrateKbps?.let { PropertyRow(stringResource(R.string.properties_bit_rate), stringResource(R.string.properties_kbps, it)) }
        properties.sampleRateHz?.let { PropertyRow(stringResource(R.string.properties_sample_rate), stringResource(R.string.properties_hz, it)) }
    }
}

/** One property, its name over its value - Auxio's `item_song_property`. */
@Composable
private fun PropertyRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

/** What a file on disk adds to what the library already knows. */
private data class FileProperties(
    val sizeBytes: Long?,
    val format: String?,
    val bitrateKbps: Int?,
    val sampleRateHz: Int?,
)

/** Reads [path]'s size and, best-effort, its audio format - null for whatever the file no longer offers. */
private suspend fun readFileProperties(path: String): FileProperties = withContext(Dispatchers.IO) {
    val sizeBytes = runCatching { File(path).takeIf { it.isFile }?.length() }.getOrNull()

    val retriever = MediaMetadataRetriever()
    val (format, bitrateKbps) = runCatching {
        retriever.setDataSource(path)
        val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
        val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.div(1000)
        mimeType to bitrate
    }.getOrDefault(null to null)
    runCatching { retriever.release() }

    // The retriever has no sample rate before API 31; the extractor reads it from the track's own format.
    val extractor = MediaExtractor()
    val sampleRateHz = runCatching {
        extractor.setDataSource(path)
        (0 until extractor.trackCount)
            .map(extractor::getTrackFormat)
            .firstOrNull { it.containsKey(MediaFormat.KEY_SAMPLE_RATE) }
            ?.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    }.getOrNull()
    extractor.release()

    FileProperties(sizeBytes, format, bitrateKbps, sampleRateHz)
}
