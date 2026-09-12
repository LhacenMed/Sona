package com.lhacenmed.sona.feature.library

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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.model.Playlist

/**
 * Where an imported playlist file's tracks should go: one of the playlists that already exist, or a
 * new one.
 *
 * Picking a row is the whole interaction, so there is no confirm button to press afterwards - and no
 * title, because the row the user is looking for names itself. Only real playlists are offered:
 * "Recent" and "Most played" are computed from what has been played rather than stored, so there is
 * nothing there to import into.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImportDestinationDialog(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPlaylistSelected: (Playlist) -> Unit,
    onCreateNewSelected: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
                    .selectableGroup(),
            ) {
                playlists.forEach { playlist ->
                    ImportDestinationRow(
                        label = playlist.name,
                        onClick = { onPlaylistSelected(playlist) },
                    )
                }
                // Set apart because it is the one row that does not name somewhere that exists.
                HorizontalDivider()
                ImportDestinationRow(
                    label = "Create new playlist",
                    onClick = onCreateNewSelected,
                )
            }
        }
    }
}

/**
 * One destination.
 *
 * Never drawn as selected: choosing one is what closes the dialog, so a filled radio would only ever
 * be on screen for the frame before it goes away.
 */
@Composable
private fun ImportDestinationRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = false, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = false, onClick = null)
        Text(text = label, modifier = Modifier.padding(start = 16.dp))
    }
}
