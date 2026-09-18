package com.lhacenmed.sona.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.database.entity.LyricsEntity
import com.lhacenmed.sona.core.designsystem.component.SonaActionButtonGroup
import com.lhacenmed.sona.core.designsystem.component.SonaBottomSheet
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.model.Track
import kotlin.math.roundToInt

/**
 * The lyrics sheet's overflow menu: ArchiveTune's `LyricsMenu` without the actions that go online -
 * editing the lyrics, nudging their timing, and showing or hiding the player controls beneath them.
 */
@Composable
internal fun LyricsMenuSheet(
    track: Track,
    lyrics: String?,
    lyricsSyncOffset: Int,
    onLyricsSyncOffsetChange: (Int) -> Unit,
    showPlayerControls: Boolean,
    onShowPlayerControlsChange: (Boolean) -> Unit,
    onEditLyrics: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var showEditDialog by rememberSaveable { mutableStateOf(false) }
    var showLyricsSyncOffsetDialog by rememberSaveable { mutableStateOf(false) }

    SonaBottomSheet(title = track.title, onDismissRequest = onDismissRequest) {
        if (showEditDialog) {
            LyricsEditDialog(
                title = track.title,
                // The not-found marker is bookkeeping, not lyrics: editing starts from nothing instead.
                initialLyrics = lyrics?.takeUnless { it == LyricsEntity.LYRICS_NOT_FOUND }.orEmpty(),
                onDismiss = { showEditDialog = false },
                onDone = {
                    onEditLyrics(it)
                    showEditDialog = false
                },
            )
        }

        if (showLyricsSyncOffsetDialog) {
            LyricsSyncOffsetDialog(
                lyricsSyncOffset = lyricsSyncOffset,
                onDismiss = { showLyricsSyncOffsetDialog = false },
                onConfirm = {
                    onLyricsSyncOffsetChange(it)
                    showLyricsSyncOffsetDialog = false
                    dismiss()
                },
            )
        }

        ListItem(
            headlineContent = { Text(stringResource(R.string.player_edit)) },
            leadingContent = { Icon(Icons.Filled.Edit, contentDescription = null) },
            modifier = Modifier.clickable { showEditDialog = true },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.player_lyrics_sync_offset)) },
            supportingContent = { Text(formatLyricsSyncOffset(lyricsSyncOffset)) },
            leadingContent = { Icon(Icons.Filled.Speed, contentDescription = null) },
            modifier = Modifier.clickable { showLyricsSyncOffsetDialog = true },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.player_show_lyrics_player_controls)) },
            trailingContent = { Switch(checked = showPlayerControls, onCheckedChange = onShowPlayerControlsChange) },
            modifier = Modifier.clickable { onShowPlayerControlsChange(!showPlayerControls) },
        )
    }
}

@Composable
private fun LyricsEditDialog(
    title: String,
    initialLyrics: String,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(initialLyrics) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            val cancelLabel = stringResource(R.string.player_cancel)
            val saveLabel = stringResource(R.string.player_save)
            SonaActionButtonGroup {
                actionButton(label = cancelLabel, onClick = onDismiss)
                actionButton(label = saveLabel, onClick = { onDone(value) })
            }
        },
    )
}

/** Shifts the lyrics against the audio by up to a second either way, in 25 ms steps. */
@Composable
private fun LyricsSyncOffsetDialog(
    lyricsSyncOffset: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var tempLyricsSyncOffset by remember { mutableFloatStateOf(lyricsSyncOffset.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Speed, contentDescription = null) },
        title = { Text(stringResource(R.string.player_lyrics_sync_offset)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatLyricsSyncOffset(tempLyricsSyncOffset.roundToInt()),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                Slider(
                    value = tempLyricsSyncOffset,
                    onValueChange = { tempLyricsSyncOffset = it },
                    valueRange = -1000f..1000f,
                    steps = 79,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            val resetLabel = stringResource(R.string.player_reset)
            val cancelLabel = stringResource(R.string.player_cancel)
            val okLabel = stringResource(R.string.player_ok)
            SonaActionButtonGroup {
                actionButton(label = resetLabel, onClick = { tempLyricsSyncOffset = 0f })
                actionButton(label = cancelLabel, onClick = onDismiss)
                actionButton(label = okLabel, onClick = { onConfirm(tempLyricsSyncOffset.roundToInt()) })
            }
        },
    )
}

private fun formatLyricsSyncOffset(offsetMs: Int): String = if (offsetMs > 0) "+$offsetMs ms" else "$offsetMs ms"
