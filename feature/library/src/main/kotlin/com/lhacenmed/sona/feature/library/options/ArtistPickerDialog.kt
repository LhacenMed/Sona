package com.lhacenmed.sona.feature.library.options

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialog
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialogOption
import com.lhacenmed.sona.core.model.Artist

/** Which of the [artists] a track or album is credited to Go to artist opens, as they are credited. */
@Composable
internal fun ArtistPickerDialog(
    artists: List<Artist>,
    onDismiss: () -> Unit,
    onArtistSelected: (Artist) -> Unit,
) {
    SonaDialog(
        onDismissRequest = onDismiss,
        title = "Go to artist",
        buttons = { actionButton(label = "Cancel", onClick = onDismiss) },
    ) {
        Column(modifier = Modifier.selectableGroup()) {
            artists.forEach { artist ->
                SonaDialogOption(label = artist.name, selected = false, onClick = { onArtistSelected(artist) })
            }
        }
    }
}
