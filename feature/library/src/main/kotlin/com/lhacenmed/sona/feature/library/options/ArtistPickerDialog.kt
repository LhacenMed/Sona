package com.lhacenmed.sona.feature.library.options

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.designsystem.R as DesignR
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialog
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialogOption
import com.lhacenmed.sona.core.model.Artist
import com.lhacenmed.sona.feature.library.R

/** Which of the [artists] a track or album is credited to Go to artist opens, as they are credited. */
@Composable
internal fun ArtistPickerDialog(
    artists: List<Artist>,
    onDismiss: () -> Unit,
    onArtistSelected: (Artist) -> Unit,
) {
    // Read here rather than inside the buttons: they are built outside composition.
    val cancelLabel = stringResource(DesignR.string.confirmation_dialog_cancel)
    SonaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.options_artist_details),
        buttons = { actionButton(label = cancelLabel, onClick = onDismiss) },
    ) {
        Column(modifier = Modifier.selectableGroup()) {
            artists.forEach { artist ->
                SonaDialogOption(label = artist.name, selected = false, onClick = { onArtistSelected(artist) })
            }
        }
    }
}
