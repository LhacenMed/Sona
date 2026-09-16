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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.common.cover.rankedCoverArtUris
import com.lhacenmed.sona.core.designsystem.component.CoverArtDefaults
import com.lhacenmed.sona.core.designsystem.component.SonaAlbumCover
import com.lhacenmed.sona.core.designsystem.component.SonaArtistCover
import com.lhacenmed.sona.core.designsystem.component.SonaBottomSheet
import com.lhacenmed.sona.core.designsystem.component.SonaCoverArt
import com.lhacenmed.sona.core.designsystem.component.SonaFolderCover
import com.lhacenmed.sona.core.designsystem.component.SonaGenreCover
import com.lhacenmed.sona.core.designsystem.component.SonaPlaylistCover
import com.lhacenmed.sona.core.designsystem.component.SonaSelectionCover

/** How faint a disabled action reads, next to the actions it sits among: Material's disabled content alpha. */
private const val DISABLED_ACTION_ALPHA = 0.38f

/**
 * The sheet every song, album, artist, genre, folder and playlist opens for its overflow button - Auxio's
 * menu bottom sheet: the entity's cover over its type, name and a line of detail, then [target]'s actions
 * in order, each disabled exactly where [disabledActions] says.
 *
 * What each action does is [OptionsActions]'s, shared with a collection's own menu. An action that opens
 * a dialog or a file picker takes the sheet out of sight without dismissing it, so the follow-up still
 * has somewhere to report to; the sheet is dismissed once that ends. Any other action slides it away.
 *
 * [onActionChosen] hears of any action being chosen, before it runs - how a selection's sheet ends the
 * selection, as Auxio's does for every one of its actions.
 */
@Composable
fun OptionsSheet(
    target: OptionsTarget,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    onActionChosen: () -> Unit = {},
) {
    val actions = rememberOptionsActions()
    val disabledActions = target.disabledActions()

    if (!actions.isFollowingUp) {
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
                                    onActionChosen()
                                    actions.perform(target, action)
                                    if (!actions.isFollowingUp) dismiss()
                                }
                            } else {
                                rowModifier
                            }
                        },
                )
            }
        }
    }

    OptionsFollowUps(actions = actions, onFinished = onDismissRequest)
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

        is OptionsTarget.ForFolder -> SonaFolderCover(
            coverArtUris = target.folder.coverArtUris,
            seed = target.folder.path.hashCode(),
            size = size,
        )

        is OptionsTarget.ForSelection -> SonaSelectionCover(
            coverArtUris = remember(target.tracks) { rankedCoverArtUris(target.tracks.map { it.coverArtUri }) },
            seed = remember(target.tracks) { target.tracks.hashCode() },
            size = size,
        )
    }
}
