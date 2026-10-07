package com.lhacenmed.sona.feature.library.options

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.common.cover.rankedCoverArtUris
import com.lhacenmed.sona.core.designsystem.component.CoverArtDefaults
import com.lhacenmed.sona.core.designsystem.component.FavoriteToggle
import com.lhacenmed.sona.core.designsystem.component.SonaAlbumCover
import com.lhacenmed.sona.core.designsystem.component.SonaArtistCover
import com.lhacenmed.sona.core.designsystem.component.SonaCoverArt
import com.lhacenmed.sona.core.designsystem.component.SonaFolderCover
import com.lhacenmed.sona.core.designsystem.component.SonaGenreCover
import com.lhacenmed.sona.core.designsystem.component.SonaPlaylistCover
import com.lhacenmed.sona.core.designsystem.component.SonaSelectionCover
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaBottomSheet
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaOptionRow
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaOptionsSheetHeader
import com.lhacenmed.sona.feature.library.favorite.favoriteItems

/**
 * The sheet every song, album, artist, genre, folder and playlist opens for its overflow button - Auxio's
 * menu bottom sheet: the entity's cover over its type, name and a line of detail, with its heart at the end
 * wherever it can be favorited - see [favoriteItems] - then [target]'s actions in order, each disabled
 * exactly where [disabledActions] says. The heart flips in place, leaving the sheet open.
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
    val favorites by actions.viewModel.favorites.collectAsStateWithLifecycle()
    val favoriteItems = remember(target) { target.favoriteItems() }

    if (!actions.isFollowingUp) {
        SonaBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            header = {
                SonaOptionsSheetHeader(
                    cover = { OptionsSheetCover(target) },
                    type = target.typeLabel(),
                    name = target.name(),
                    info = target.infoLine(),
                    favorite = favoriteItems?.let { items ->
                        val isFavorite = favorites.containsAll(items)
                        FavoriteToggle(isFavorite) {
                            onActionChosen()
                            actions.viewModel.setFavorite(items, !isFavorite)
                        }
                    },
                )
            },
        ) {
            target.actions().forEach { action ->
                SonaOptionRow(
                    label = action.label,
                    icon = action.icon,
                    enabled = action !in disabledActions,
                    onClick = {
                        onActionChosen()
                        actions.perform(target, action)
                        if (!actions.isFollowingUp) dismiss()
                    },
                )
            }
        }
    }

    OptionsFollowUps(actions = actions, onFinished = onDismissRequest)
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
