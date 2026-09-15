package com.lhacenmed.sona.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.designsystem.component.SonaBottomSheet
import com.lhacenmed.sona.core.model.Track

/** The player's overflow menu: the album and artist [track] can be opened from. Stands in for ArchiveTune's `PlayerMenu`. */
@Composable
internal fun PlayerMenuSheet(
    track: Track,
    onDismissRequest: () -> Unit,
    onGoToAlbum: () -> Unit,
    onGoToArtist: () -> Unit,
) {
    SonaBottomSheet(title = track.title, onDismissRequest = onDismissRequest) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.player_go_to_album)) },
            leadingContent = { Icon(Icons.Filled.Album, contentDescription = null) },
            modifier = Modifier.clickable {
                dismiss()
                onGoToAlbum()
            },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.player_go_to_artist)) },
            leadingContent = { Icon(Icons.Filled.Person, contentDescription = null) },
            modifier = Modifier.clickable {
                dismiss()
                onGoToArtist()
            },
        )
    }
}
