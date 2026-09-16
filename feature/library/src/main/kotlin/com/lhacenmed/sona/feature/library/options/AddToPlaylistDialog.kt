package com.lhacenmed.sona.feature.library.options

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.component.SonaActionButtonGroup
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.model.Playlist

/**
 * Which playlist an options sheet's tracks go into - Auxio's `AddToPlaylistDialog`: every playlist,
 * then a new one.
 *
 * A playlist already holding every one of the tracks is marked, as Auxio marks it, but still chosen
 * like any other - adding tracks a playlist already has leaves it as it was. [fullPlaylistIds] can
 * arrive after the dialog opens; the rows are all there from the first frame and only their marks fill in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    fullPlaylistIds: Set<Long>,
    onDismiss: () -> Unit,
    onPlaylistSelected: (Playlist) -> Unit,
    onNewPlaylistSelected: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(modifier = Modifier.padding(vertical = 24.dp)) {
                Text(
                    text = "Playlists",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(top = 16.dp)
                        .verticalScroll(rememberScrollState())
                        .selectableGroup(),
                ) {
                    playlists.forEach { playlist ->
                        PlaylistChoiceRow(
                            label = playlist.name,
                            isFull = playlist.id in fullPlaylistIds,
                            onClick = { onPlaylistSelected(playlist) },
                        )
                    }
                    // Set apart because it is the one row that does not name somewhere that exists.
                    HorizontalDivider()
                    PlaylistChoiceRow(label = "New playlist", isFull = false, onClick = onNewPlaylistSelected)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    SonaActionButtonGroup { actionButton(label = "Cancel", onClick = onDismiss) }
                }
            }
        }
    }
}

@Composable
private fun PlaylistChoiceRow(label: String, isFull: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isFull, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = isFull, onClick = null)
        Text(text = label, modifier = Modifier.padding(start = 16.dp))
    }
}
