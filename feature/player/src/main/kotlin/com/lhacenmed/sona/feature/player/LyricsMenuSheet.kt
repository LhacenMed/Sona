package com.lhacenmed.sona.feature.player

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.designsystem.component.CoverArtDefaults
import com.lhacenmed.sona.core.designsystem.component.SonaCoverArt
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaBottomSheet
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaOptionRow
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaOptionsSheetHeader
import com.lhacenmed.sona.core.model.Track

/**
 * The lyrics sheet's overflow menu: ArchiveTune's `LyricsMenu` without the actions that go online - editing
 * the lyrics, nudging their timing, and showing or hiding the player controls beneath them - laid out as
 * every options sheet in the app is: the track's cover, what the menu is for, its name and artist, then its
 * options.
 *
 * Editing opens the lyrics editor - [onEditLyrics] - and is done with the menu. As with every options sheet, an
 * option that opens a dialog takes the sheet out of sight, and the menu is done once the dialog is; the switch
 * changes in place. The menu is about the track it opened for: should another start playing meanwhile, it
 * closes, rather than edit or retime lyrics that are no longer the ones on screen.
 */
@Composable
internal fun LyricsMenuSheet(
    track: Track,
    lyricsSyncOffset: Int,
    onLyricsSyncOffsetChange: (Int) -> Unit,
    showPlayerControls: Boolean,
    onShowPlayerControlsChange: (Boolean) -> Unit,
    /** Null for lyrics the editor cannot open - a Private Folder item's - which leaves Edit out. */
    onEditLyrics: (() -> Unit)?,
    onDismissRequest: () -> Unit,
) {
    val openedForTrackId = rememberSaveable { track.id }
    if (track.id != openedForTrackId) {
        LaunchedEffect(Unit) { onDismissRequest() }
        return
    }
    var isSettingSyncOffset by rememberSaveable { mutableStateOf(false) }

    if (!isSettingSyncOffset) {
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
            if (onEditLyrics != null) {
                SonaOptionRow(
                    label = stringResource(R.string.player_edit),
                    icon = Icons.Filled.Edit,
                    onClick = {
                        onEditLyrics()
                        onDismissRequest()
                    },
                )
            }
            SonaOptionRow(
                label = stringResource(R.string.player_lyrics_sync_offset),
                icon = Icons.Filled.Speed,
                detail = formatLyricsSyncOffset(lyricsSyncOffset),
                onClick = { isSettingSyncOffset = true },
            )
            SonaOptionRow(
                label = stringResource(R.string.player_show_lyrics_player_controls),
                icon = Icons.Filled.PlayCircle,
                trailingContent = { Switch(checked = showPlayerControls, onCheckedChange = onShowPlayerControlsChange) },
                onClick = { onShowPlayerControlsChange(!showPlayerControls) },
            )
        }
    } else {
        LyricsSyncOffsetDialog(
            lyricsSyncOffset = lyricsSyncOffset,
            onDismiss = onDismissRequest,
            onConfirm = {
                onLyricsSyncOffsetChange(it)
                onDismissRequest()
            },
        )
    }
}
