package com.lhacenmed.sona.feature.player

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.database.entity.LyricsEntity
import com.lhacenmed.sona.core.designsystem.component.CoverArtDefaults
import com.lhacenmed.sona.core.designsystem.component.SonaBottomSheet
import com.lhacenmed.sona.core.designsystem.component.SonaCoverArt
import com.lhacenmed.sona.core.designsystem.component.SonaOptionRow
import com.lhacenmed.sona.core.designsystem.component.SonaOptionsSheetHeader
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialog
import com.lhacenmed.sona.core.model.Track

/** The dialog an option of the lyrics menu goes on to. */
private enum class LyricsMenuFollowUp { EDIT, SYNC_OFFSET }

/**
 * The lyrics sheet's overflow menu: ArchiveTune's `LyricsMenu` without the actions that go online - editing
 * the lyrics, nudging their timing, and showing or hiding the player controls beneath them - laid out as
 * every options sheet in the app is: the track's cover, what the menu is for, its name and artist, then its
 * options.
 *
 * As with those, an option that opens a dialog takes the sheet out of sight, and the menu is done once the
 * dialog is; the switch changes in place. The menu is about the track it opened for: should another start
 * playing meanwhile, it closes, rather than edit or retime lyrics that are no longer the ones on screen.
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
    val openedForTrackId = rememberSaveable { track.id }
    if (track.id != openedForTrackId) {
        LaunchedEffect(Unit) { onDismissRequest() }
        return
    }
    var followUp by rememberSaveable { mutableStateOf<LyricsMenuFollowUp?>(null) }

    if (followUp == null) {
        SonaBottomSheet(
            onDismissRequest = onDismissRequest,
            header = {
                SonaOptionsSheetHeader(
                    cover = { SonaCoverArt(coverArtUri = track.coverArtUri, contentDescription = null, size = CoverArtDefaults.OptionsHeaderSize) },
                    type = stringResource(R.string.player_lyrics),
                    name = track.title,
                    info = track.artist,
                )
            },
        ) {
            SonaOptionRow(
                label = stringResource(R.string.player_edit),
                icon = Icons.Filled.Edit,
                onClick = { followUp = LyricsMenuFollowUp.EDIT },
            )
            SonaOptionRow(
                label = stringResource(R.string.player_lyrics_sync_offset),
                icon = Icons.Filled.Speed,
                detail = formatLyricsSyncOffset(lyricsSyncOffset),
                onClick = { followUp = LyricsMenuFollowUp.SYNC_OFFSET },
            )
            SonaOptionRow(
                label = stringResource(R.string.player_show_lyrics_player_controls),
                icon = Icons.Filled.PlayCircle,
                trailingContent = { Switch(checked = showPlayerControls, onCheckedChange = onShowPlayerControlsChange) },
                onClick = { onShowPlayerControlsChange(!showPlayerControls) },
            )
        }
    }

    when (followUp) {
        LyricsMenuFollowUp.EDIT -> LyricsEditDialog(
            title = track.title,
            // The not-found marker is bookkeeping, not lyrics: editing starts from nothing instead.
            initialLyrics = lyrics?.takeUnless { it == LyricsEntity.LYRICS_NOT_FOUND }.orEmpty(),
            onDismiss = onDismissRequest,
            onDone = {
                onEditLyrics(it)
                onDismissRequest()
            },
        )
        LyricsMenuFollowUp.SYNC_OFFSET -> LyricsSyncOffsetDialog(
            lyricsSyncOffset = lyricsSyncOffset,
            onDismiss = onDismissRequest,
            onConfirm = {
                onLyricsSyncOffsetChange(it)
                onDismissRequest()
            },
        )
        null -> Unit
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
    // Read here rather than inside the group: a group builds its items outside composition.
    val cancelLabel = stringResource(R.string.player_cancel)
    val saveLabel = stringResource(R.string.player_save)

    SonaDialog(
        onDismissRequest = onDismiss,
        title = title,
        icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
        buttons = {
            actionButton(label = cancelLabel, onClick = onDismiss)
            actionButton(label = saveLabel, onClick = { onDone(value) })
        },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
