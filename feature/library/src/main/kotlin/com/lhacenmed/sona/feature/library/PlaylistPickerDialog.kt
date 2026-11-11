package com.lhacenmed.sona.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.designsystem.R as DesignR
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialog
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialogOption
import com.lhacenmed.sona.core.designsystem.component.title
import com.lhacenmed.sona.core.model.Playlist
import com.lhacenmed.sona.feature.library.R

/**
 * Which playlist tracks go into - Auxio's `AddToPlaylistDialog`: every playlist, then a new one. Adding
 * from an options sheet and importing a playlist file both pick here.
 *
 * Only real playlists are offered: "Recent" and "Most played" are computed from what has been played
 * rather than stored, so there is nothing there to add to.
 *
 * A playlist already holding every one of the tracks is in [fullPlaylistIds] and marked, as Auxio marks
 * it, but still chosen like any other - adding tracks a playlist already has leaves it as it was. The ids
 * can arrive after the dialog opens; the rows are all there from the first frame and only their marks
 * fill in.
 */
@Composable
internal fun PlaylistPickerDialog(
    title: String,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPlaylistSelected: (Playlist) -> Unit,
    onNewPlaylistSelected: () -> Unit,
    fullPlaylistIds: Set<Long> = emptySet(),
) {
    // Read here rather than inside the buttons: they are built outside composition.
    val cancelLabel = stringResource(DesignR.string.confirmation_dialog_cancel)
    SonaDialog(
        onDismissRequest = onDismiss,
        title = title,
        buttons = { actionButton(label = cancelLabel, onClick = onDismiss) },
    ) {
        Column(modifier = Modifier.selectableGroup()) {
            playlists.forEach { playlist ->
                SonaDialogOption(
                    label = playlist.title,
                    selected = playlist.id in fullPlaylistIds,
                    onClick = { onPlaylistSelected(playlist) },
                )
            }
            // Set apart because it is the one row that does not name somewhere that exists.
            HorizontalDivider()
            SonaDialogOption(label = stringResource(R.string.library_new_playlist), selected = false, onClick = onNewPlaylistSelected)
        }
    }
}
