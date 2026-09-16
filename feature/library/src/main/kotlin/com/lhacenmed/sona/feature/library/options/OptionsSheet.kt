package com.lhacenmed.sona.feature.library.options

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.component.CoverArtDefaults
import com.lhacenmed.sona.core.designsystem.component.SonaAlbumCover
import com.lhacenmed.sona.core.designsystem.component.SonaArtistCover
import com.lhacenmed.sona.core.designsystem.component.SonaBottomSheet
import com.lhacenmed.sona.core.designsystem.component.SonaCoverArt
import com.lhacenmed.sona.core.designsystem.component.SonaGenreCover
import com.lhacenmed.sona.core.designsystem.component.SonaPlaylistCover

/** How faint a disabled action reads, next to the actions it sits among: Material's disabled content alpha. */
private const val DISABLED_ACTION_ALPHA = 0.38f

/**
 * The sheet every song, album, artist, genre and playlist opens for its overflow button - Auxio's menu
 * bottom sheet: the entity's cover over its type, name and a line of detail, then [target]'s actions in
 * order, each disabled exactly where [disabledActions] says.
 *
 * An action does nothing on its own - choosing one slides the sheet away and reports [onAction]. What
 * each [OptionsAction] does is for whatever calls this to wire up.
 */
@Composable
fun OptionsSheet(
    target: OptionsTarget,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    onAction: (OptionsAction) -> Unit = {},
) {
    val disabledActions = target.disabledActions()
    SonaBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        header = { OptionsSheetHeader(target) },
    ) {
        target.actions().forEach { action ->
            val enabled = action !in disabledActions
            ListItem(
                headlineContent = { Text(action.label) },
                leadingContent = { Icon(action.icon, contentDescription = null) },
                // The sheet already paints its own background; a row painting its own would seam
                // against it instead of reading as one surface.
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .alpha(if (enabled) 1f else DISABLED_ACTION_ALPHA)
                    .let { rowModifier ->
                        if (enabled) {
                            rowModifier.clickable {
                                dismiss()
                                onAction(action)
                            }
                        } else {
                            rowModifier
                        }
                    },
            )
        }
    }
}

/** The cover, type, name and info line every options sheet opens with - Auxio's `menuCover`/`menuType`/`menuName`/`menuInfo`. */
@Composable
private fun OptionsSheetHeader(target: OptionsTarget) {
    Column {
        Row(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OptionsSheetCover(target)
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    text = target.typeLabel(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(text = target.name(), style = MaterialTheme.typography.titleLarge)
                Text(
                    text = target.infoLine(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Auxio's `menu_mode_group`: the line the header ends on, edge to edge under the cover.
        HorizontalDivider()
    }
}

@Composable
private fun OptionsSheetCover(target: OptionsTarget) {
    val size = CoverArtDefaults.OptionsHeaderSize
    when (target) {
        is OptionsTarget.ForTrack -> SonaCoverArt(
            coverArtUri = target.track.coverArtUri,
            contentDescription = null,
            size = size,
        )

        is OptionsTarget.ForAlbum -> SonaAlbumCover(coverArtUri = target.album.coverArtUri, size = size)

        is OptionsTarget.ForArtist -> SonaArtistCover(
            coverArtUris = target.artist.coverArtUris,
            seed = target.artist.id.hashCode(),
            size = size,
        )

        is OptionsTarget.ForGenre -> SonaGenreCover(
            coverArtUris = target.genre.coverArtUris,
            seed = target.genre.id.hashCode(),
            size = size,
        )

        is OptionsTarget.ForPlaylist -> SonaPlaylistCover(
            coverArtUris = target.playlist.coverArtUris,
            seed = target.playlist.id.hashCode(),
            size = size,
        )
    }
}
