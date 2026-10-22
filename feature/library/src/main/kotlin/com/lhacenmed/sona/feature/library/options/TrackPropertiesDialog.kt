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
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.common.format.formatDuration
import com.lhacenmed.sona.core.common.format.formatFileSize
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialog
import com.lhacenmed.sona.core.model.Track
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

    SonaDialog(
        onDismissRequest = onDismiss,
        title = "Track properties",
        buttons = { actionButton(label = "OK", onClick = onDismiss) },
    ) {
        PropertyRow("Name", track.title)
        PropertyRow("Album", track.album)
        PropertyRow("Artist", track.artist)
        track.genre?.let { PropertyRow("Genre", it) }
        track.year?.let { PropertyRow("Date", it.toString()) }
        track.trackNumber?.let { PropertyRow("Track", it.toString()) }
        track.discNumber?.let { PropertyRow("Disc", it.toString()) }
        PropertyRow("Path", track.path)
        properties.sizeBytes?.let { PropertyRow("Size", formatFileSize(it)) }
        PropertyRow("Duration", formatDuration(track.durationMs))
        properties.format?.let { PropertyRow("Format", it) }
        properties.bitrateKbps?.let { PropertyRow("Bit rate", "$it kbps") }
        properties.sampleRateHz?.let { PropertyRow("Sample rate", "$it Hz") }
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
