package com.lhacenmed.sona.feature.library.options

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.os.TransactionTooLargeException
import android.provider.MediaStore
import android.widget.Toast
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.common.cover.rankedCoverArtUris
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.designsystem.component.CoverArtDefaults
import com.lhacenmed.sona.core.designsystem.component.SonaAlbumCover
import com.lhacenmed.sona.core.designsystem.component.SonaArtistCover
import com.lhacenmed.sona.core.designsystem.component.SonaBottomSheet
import com.lhacenmed.sona.core.designsystem.component.SonaCoverArt
import com.lhacenmed.sona.core.designsystem.component.SonaFolderCover
import com.lhacenmed.sona.core.designsystem.component.SonaGenreCover
import com.lhacenmed.sona.core.designsystem.component.SonaPlaylistCover
import com.lhacenmed.sona.core.designsystem.component.SonaSelectionCover
import com.lhacenmed.sona.core.model.Playlist
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.core.navigation.AppNavigator
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.library.AlbumDetailScreen
import com.lhacenmed.sona.feature.library.ArtistDetailScreen
import com.lhacenmed.sona.feature.library.FolderDetailScreen
import com.lhacenmed.sona.feature.library.GenreDetailScreen
import com.lhacenmed.sona.feature.library.PlaylistDetailScreen
import com.lhacenmed.sona.feature.library.PlaylistNameDialog
import com.lhacenmed.sona.feature.library.operation.ExcludeFoldersDialog
import com.lhacenmed.sona.feature.library.playlist.AddCollectionsScreen
import com.lhacenmed.sona.feature.library.playlist.AddTracksScreen
import com.lhacenmed.sona.feature.library.playlist.EditPlaylistScreen

/** How faint a disabled action reads, next to the actions it sits among: Material's disabled content alpha. */
private const val DISABLED_ACTION_ALPHA = 0.38f

/** What a playlist's Import, Export or Delete row asks the screen showing it to do. */
enum class PlaylistManageAction { IMPORT, EXPORT, DELETE }

/**
 * What the sheet is doing besides listing [OptionsTarget]'s actions - a playlist picker, a track's
 * properties, or confirming a folder's exclusion. Owned here rather than by whatever opened the sheet,
 * so every entity gets the same follow-up dialogs without its caller building them.
 */
private sealed interface FollowUp {
    data object PlaylistPicker : FollowUp
    data class Properties(val track: Track) : FollowUp
    data class ExcludeFolder(val folderPath: String) : FollowUp
}

/**
 * The sheet every song, album, artist, genre, folder and playlist opens for its overflow button - Auxio's menu
 * bottom sheet: the entity's cover over its type, name and a line of detail, then [target]'s actions in
 * order, each disabled exactly where [disabledActions] says.
 *
 * Every action plays, queues, navigates or shares on its own - this is the one place that logic lives,
 * so a track's sheet in the tracks tab and the same track's sheet in an album behave identically.
 * [onManagePlaylist] is the one exception: importing into, exporting or deleting a playlist
 * needs a file picker or a confirm dialog that must outlive this sheet, so those three are handed to
 * whatever screen is already showing the playlist row - only [PlaylistsScreen][com.lhacenmed.sona.feature.library.PlaylistsScreen]
 * does today, since it is the only screen a [OptionsTarget.ForPlaylist] can open from.
 *
 * [onActionChosen] hears of any action being chosen, before it runs - how a selection's sheet ends the
 * selection, as Auxio's does for every one of its actions.
 */
@Composable
fun OptionsSheet(
    target: OptionsTarget,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    onManagePlaylist: (PlaylistManageAction, Playlist) -> Unit = { _, _ -> },
    onActionChosen: () -> Unit = {},
) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val actionsViewModel: OptionsActionsViewModel = hiltViewModel()
    var followUp by remember { mutableStateOf<FollowUp?>(null) }
    val disabledActions = target.disabledActions()

    if (followUp == null) {
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
                                    // Play next and Add to queue open nothing further, so they slide
                                    // away like every other action; a picker, the properties dialog or
                                    // an exclusion to confirm has to outlive that slide, so those skip it
                                    // and cut straight to their follow-up instead.
                                    when (action) {
                                        OptionsAction.PLAYLIST_ADD -> followUp = FollowUp.PlaylistPicker
                                        OptionsAction.SONG_PROPERTIES ->
                                            followUp = FollowUp.Properties((target as OptionsTarget.ForTrack).track)
                                        OptionsAction.EXCLUDE ->
                                            followUp = FollowUp.ExcludeFolder((target as OptionsTarget.ForFolder).folder.path)
                                        else -> {
                                            dismiss()
                                            performAction(action, target, actionsViewModel, navigator, context, onManagePlaylist)
                                        }
                                    }
                                }
                            } else {
                                rowModifier
                            }
                        },
                )
            }
        }
    }

    when (val current = followUp) {
        null -> Unit

        FollowUp.PlaylistPicker -> {
            val playlists by actionsViewModel.playlists.collectAsStateWithLifecycle()
            var fullPlaylistIds by remember { mutableStateOf(emptySet<Long>()) }
            // Naming a new playlist opens over the picker rather than in its place, so cancelling the
            // name goes back to the playlists that already exist, still holding the same tracks.
            var isNamingNewPlaylist by remember { mutableStateOf(false) }
            LaunchedEffect(target) { fullPlaylistIds = actionsViewModel.playlistIdsHoldingAll(target) }
            AddToPlaylistDialog(
                playlists = playlists.itemsOrEmpty,
                fullPlaylistIds = fullPlaylistIds,
                onDismiss = onDismissRequest,
                onPlaylistSelected = { playlist ->
                    actionsViewModel.addToPlaylist(target, playlist.id)
                    context.toast("Added to playlist")
                    onDismissRequest()
                },
                onNewPlaylistSelected = { isNamingNewPlaylist = true },
            )
            if (isNamingNewPlaylist) {
                PlaylistNameDialog(
                    dialogTitle = "New playlist",
                    confirmLabel = "Create",
                    initialName = "",
                    takenNames = playlists.itemsOrEmpty.map { it.name },
                    onDismiss = { isNamingNewPlaylist = false },
                    onConfirm = { name ->
                        actionsViewModel.createPlaylistAndAddTo(target, name)
                        context.toast("Added to playlist")
                        onDismissRequest()
                    },
                )
            }
        }

        is FollowUp.Properties -> TrackPropertiesDialog(track = current.track, onDismiss = onDismissRequest)

        is FollowUp.ExcludeFolder -> ExcludeFoldersDialog(
            folderPaths = listOf(current.folderPath),
            onDismiss = onDismissRequest,
        )
    }
}

/** Everything a chosen [action] does, apart from Add to playlist and Song properties - see [OptionsSheet]. */
private fun performAction(
    action: OptionsAction,
    target: OptionsTarget,
    actionsViewModel: OptionsActionsViewModel,
    navigator: AppNavigator,
    context: Context,
    onManagePlaylist: (PlaylistManageAction, Playlist) -> Unit,
) {
    when (action) {
        OptionsAction.PLAY -> actionsViewModel.play(target)
        OptionsAction.SHUFFLE -> actionsViewModel.shuffle(target)
        OptionsAction.PLAY_NEXT -> {
            actionsViewModel.playNext(target)
            context.toast("Track will play next")
        }
        OptionsAction.QUEUE_ADD -> {
            actionsViewModel.addToQueue(target)
            context.toast("Added to queue")
        }
        OptionsAction.ARTIST_DETAILS -> navigator.go(ArtistDetailScreen(artistIdOf(target)))
        OptionsAction.ALBUM_DETAILS -> navigator.go(AlbumDetailScreen(albumIdOf(target)))
        OptionsAction.VIEW_DETAILS -> navigator.go(detailScreenOf(target))
        OptionsAction.SHARE -> if (target is OptionsTarget.ForTrack) {
            context.shareTrack(target.track)
        } else {
            actionsViewModel.loadTracks(target) { tracks -> context.shareTracks(tracks) }
        }
        OptionsAction.ADD_TRACKS -> navigator.go(AddTracksScreen(playlistOf(target).id))
        OptionsAction.ADD_COLLECTIONS -> navigator.go(AddCollectionsScreen(playlistOf(target).id))
        OptionsAction.EDIT -> navigator.go(EditPlaylistScreen(playlistOf(target).id))
        OptionsAction.IMPORT -> onManagePlaylist(PlaylistManageAction.IMPORT, playlistOf(target))
        OptionsAction.EXPORT -> onManagePlaylist(PlaylistManageAction.EXPORT, playlistOf(target))
        OptionsAction.DELETE -> onManagePlaylist(PlaylistManageAction.DELETE, playlistOf(target))
        OptionsAction.PLAYLIST_ADD, OptionsAction.SONG_PROPERTIES, OptionsAction.EXCLUDE ->
            error("$action opens a follow-up dialog and never reaches performAction")
    }
}

private fun artistIdOf(target: OptionsTarget): Long = when (target) {
    is OptionsTarget.ForTrack -> target.track.artistId
    is OptionsTarget.ForAlbum -> target.album.artistId
    else -> error("$target has no artist to go to")
}

private fun albumIdOf(target: OptionsTarget): Long = when (target) {
    is OptionsTarget.ForTrack -> target.track.albumId
    else -> error("$target has no album to go to")
}

private fun detailScreenOf(target: OptionsTarget): Screen = when (target) {
    is OptionsTarget.ForAlbum -> AlbumDetailScreen(target.album.id)
    is OptionsTarget.ForArtist -> ArtistDetailScreen(target.artist.id)
    is OptionsTarget.ForGenre -> GenreDetailScreen(target.genre.id)
    is OptionsTarget.ForPlaylist -> PlaylistDetailScreen(target.playlist.id)
    is OptionsTarget.ForFolder -> FolderDetailScreen(target.folder.path)
    is OptionsTarget.ForTrack -> error("A track's own detail is Song properties, not View")
    is OptionsTarget.ForSelection -> error("A selection has no detail screen of its own")
}

private fun playlistOf(target: OptionsTarget): Playlist =
    (target as? OptionsTarget.ForPlaylist)?.playlist ?: error("$target is not a playlist")

internal fun Context.toast(message: String) {
    Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
}

/**
 * Offers [track]'s audio to another app - the player's own share.
 *
 * A manually scanned track has only a file path, which another app is not allowed to open, so its title
 * and artist are shared instead.
 */
private fun Context.shareTrack(track: Track) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        if (track.isManuallyScanned) {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "${track.title} - ${track.artist}")
        } else {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, track.contentUri())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    startActivity(Intent.createChooser(intent, null))
}

/**
 * Offers every one of [tracks]' audio files to another app at once - Auxio's `Context.share` for an
 * album, artist, genre or playlist.
 *
 * Manually scanned tracks are left out, having no address another app may open. A collection large
 * enough to overflow the share intent is reported rather than crashing, as Auxio reports it.
 */
private fun Context.shareTracks(tracks: List<Track>) {
    val streams = tracks.filterNot(Track::isManuallyScanned).map(Track::contentUri)
    if (streams.isEmpty()) {
        toast("Unable to share this")
        return
    }
    val intent = if (streams.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, streams.single())
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(streams))
    }
    intent.setType("audio/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { startActivity(Intent.createChooser(intent, null)) }.onFailure { error ->
        val isTooLarge = error is TransactionTooLargeException || error.cause is TransactionTooLargeException
        toast(if (isTooLarge) "This is too large to share" else "Unable to share this")
    }
}

private fun Track.contentUri() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaStoreId)

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
