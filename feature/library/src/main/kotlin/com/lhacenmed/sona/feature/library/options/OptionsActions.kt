package com.lhacenmed.sona.feature.library.options

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.core.navigation.AppNavigator
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.library.AlbumDetailScreen
import com.lhacenmed.sona.feature.library.ArtistDetailScreen
import com.lhacenmed.sona.feature.library.FolderDetailScreen
import com.lhacenmed.sona.feature.library.GenreDetailScreen
import com.lhacenmed.sona.feature.library.M3U_MIME_TYPE
import com.lhacenmed.sona.feature.library.M3U_PICKER_MIME_TYPES
import com.lhacenmed.sona.feature.library.PlaylistDetailScreen
import com.lhacenmed.sona.feature.library.PlaylistNameDialog
import com.lhacenmed.sona.feature.library.operation.DeletePlaylistsDialog
import com.lhacenmed.sona.feature.library.operation.ExcludeFoldersDialog
import com.lhacenmed.sona.feature.library.playlist.AddCollectionsScreen
import com.lhacenmed.sona.feature.library.playlist.AddTracksScreen
import com.lhacenmed.sona.feature.library.playlist.EditPlaylistScreen
import com.lhacenmed.sona.feature.library.pluralCount

/**
 * A dialog or file picker a chosen action is waiting on, for [target]. Until it ends the action is still
 * under way, so whatever it answers reaches a composition that is still there.
 */
internal sealed interface FollowUp {
    val target: OptionsTarget

    data class PlaylistPicker(override val target: OptionsTarget) : FollowUp
    data class Properties(override val target: OptionsTarget.ForTrack) : FollowUp
    data class ExcludeFolder(override val target: OptionsTarget.ForFolder) : FollowUp
    data class DeletePlaylist(override val target: OptionsTarget.ForPlaylist) : FollowUp
    data class ImportFile(override val target: OptionsTarget.ForPlaylist) : FollowUp
    data class ExportFile(override val target: OptionsTarget) : FollowUp
}

/**
 * Carries out what an [OptionsAction] does to an [OptionsTarget] - the one place that logic lives, for a
 * row's options sheet and a collection's own menu alike, so the same action does the same thing wherever
 * it is chosen.
 *
 * Most actions happen at once. The rest - picking a playlist, a track's properties, confirming an
 * exclusion or a deletion, picking a file to import or to export to - open a [followUp], which
 * [OptionsFollowUps] draws until it ends.
 */
@Stable
internal class OptionsActions(
    val viewModel: OptionsActionsViewModel,
    private val navigator: AppNavigator,
    private val context: Context,
) {
    var followUp by mutableStateOf<FollowUp?>(null)
        private set

    /** Whether a chosen action is waiting on a dialog or a file picker. */
    val isFollowingUp: Boolean get() = followUp != null

    fun perform(target: OptionsTarget, action: OptionsAction) {
        when (action) {
            OptionsAction.PLAY -> viewModel.play(target)
            OptionsAction.SHUFFLE -> viewModel.shuffle(target)
            OptionsAction.PLAY_NEXT -> {
                viewModel.playNext(target)
                context.toast("Track will play next")
            }
            OptionsAction.QUEUE_ADD -> {
                viewModel.addToQueue(target)
                context.toast("Added to queue")
            }
            OptionsAction.PLAYLIST_ADD -> followUp = FollowUp.PlaylistPicker(target)
            OptionsAction.ARTIST_DETAILS -> navigator.go(ArtistDetailScreen(artistIdOf(target)))
            OptionsAction.ALBUM_DETAILS -> navigator.go(AlbumDetailScreen(albumIdOf(target)))
            OptionsAction.SONG_PROPERTIES -> followUp = FollowUp.Properties(target as OptionsTarget.ForTrack)
            OptionsAction.VIEW_DETAILS -> navigator.go(detailScreenOf(target))
            OptionsAction.ADD_TRACKS -> navigator.go(AddTracksScreen(playlistOf(target).playlist.id))
            OptionsAction.ADD_COLLECTIONS -> navigator.go(AddCollectionsScreen(playlistOf(target).playlist.id))
            OptionsAction.EDIT -> navigator.go(EditPlaylistScreen(playlistOf(target).playlist.id))
            OptionsAction.IMPORT -> followUp = FollowUp.ImportFile(playlistOf(target))
            OptionsAction.EXPORT -> followUp = FollowUp.ExportFile(target)
            OptionsAction.DELETE -> followUp = FollowUp.DeletePlaylist(playlistOf(target))
            OptionsAction.EXCLUDE -> followUp = FollowUp.ExcludeFolder(target as OptionsTarget.ForFolder)
            OptionsAction.SHARE -> if (target is OptionsTarget.ForTrack) {
                context.shareTrack(target.track)
            } else {
                viewModel.loadTracks(target) { tracks -> context.shareTracks(tracks) }
            }
        }
    }

    fun endFollowUp() {
        followUp = null
    }
}

@Composable
internal fun rememberOptionsActions(): OptionsActions {
    val viewModel: OptionsActionsViewModel = hiltViewModel()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    return remember(viewModel, navigator, context) { OptionsActions(viewModel, navigator, context) }
}

/**
 * Whatever dialog or file picker [actions] is waiting on. [onFinished] hears when it ends, whichever way;
 * [onPlaylistDeleted] hears when a deletion went through - how a playlist's own screen leaves once the
 * playlist it shows is gone.
 */
@Composable
internal fun OptionsFollowUps(
    actions: OptionsActions,
    onFinished: () -> Unit = {},
    onPlaylistDeleted: () -> Unit = {},
) {
    val context = LocalContext.current
    val viewModel = actions.viewModel
    val finish = {
        actions.endFollowUp()
        onFinished()
    }

    when (val followUp = actions.followUp) {
        null -> Unit

        is FollowUp.PlaylistPicker -> {
            val target = followUp.target
            val playlists by viewModel.playlists.collectAsStateWithLifecycle()
            var fullPlaylistIds by remember { mutableStateOf(emptySet<Long>()) }
            // Naming a new playlist opens over the picker rather than in its place, so cancelling the
            // name goes back to the playlists that already exist, still holding the same tracks.
            var isNamingNewPlaylist by remember { mutableStateOf(false) }
            LaunchedEffect(target) { fullPlaylistIds = viewModel.playlistIdsHoldingAll(target) }
            AddToPlaylistDialog(
                playlists = playlists.itemsOrEmpty,
                fullPlaylistIds = fullPlaylistIds,
                onDismiss = finish,
                onPlaylistSelected = { playlist ->
                    viewModel.addToPlaylist(target, playlist.id)
                    context.toast("Added to playlist")
                    finish()
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
                        viewModel.createPlaylistAndAddTo(target, name)
                        context.toast("Added to playlist")
                        finish()
                    },
                )
            }
        }

        is FollowUp.Properties -> TrackPropertiesDialog(track = followUp.target.track, onDismiss = finish)

        is FollowUp.ExcludeFolder -> ExcludeFoldersDialog(
            folderPaths = listOf(followUp.target.folder.path),
            onDismiss = finish,
        )

        is FollowUp.DeletePlaylist -> DeletePlaylistsDialog(
            playlists = listOf(followUp.target.playlist),
            onDismiss = finish,
            onDeleted = onPlaylistDeleted,
        )

        is FollowUp.ImportFile -> {
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                finish()
                if (uri != null) {
                    viewModel.importIntoPlaylist(
                        playlistId = followUp.target.playlist.id,
                        openStream = { context.contentResolver.openInputStream(uri) },
                    ) { succeeded ->
                        context.toast(if (succeeded) "Playlist imported" else "Could not import playlist")
                    }
                }
            }
            LaunchedEffect(followUp) { launcher.launch(M3U_PICKER_MIME_TYPES) }
        }

        is FollowUp.ExportFile -> {
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(M3U_MIME_TYPE)) { uri ->
                finish()
                if (uri != null) viewModel.exportTracks(followUp.target) { context.contentResolver.openOutputStream(uri) }
            }
            LaunchedEffect(followUp) { launcher.launch("${followUp.target.name()}.m3u") }
        }
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

private fun playlistOf(target: OptionsTarget): OptionsTarget.ForPlaylist =
    target as? OptionsTarget.ForPlaylist ?: error("$target is not a playlist")

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
 * Offers every one of [tracks]' audio files to another app at once - Auxio's `Context.share` for a
 * selection.
 *
 * Nothing is copied: a track is shared as its own address in the library, which the receiving app is
 * granted a read of. So a selection costs the same whether it holds three tracks or three hundred,
 * and the chooser opens on the same frame it is asked for.
 *
 * Manually scanned tracks are left out, having no address another app may open, and a selection that
 * loses any of them says so rather than quietly sharing fewer than were picked. A selection too large
 * for the intent to carry is turned down before it is sent - see [shareIntentCostBytes].
 */
private fun Context.shareTracks(tracks: List<Track>) {
    val streams = tracks.filterNot(Track::isManuallyScanned).map(Track::contentUri)
    if (streams.isEmpty()) {
        toast("Unable to share this")
        return
    }
    if (streams.shareIntentCostBytes() > SHARE_INTENT_BUDGET_BYTES) {
        toast("Too many tracks to share at once")
        return
    }
    val leftOutCount = tracks.size - streams.size
    if (leftOutCount > 0) toast("${pluralCount(leftOutCount, "track")} cannot be shared")

    val intent = if (streams.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, streams.single())
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(streams))
    }
    intent.setType("audio/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(Intent.createChooser(intent, null))
}

/**
 * How much of the intent these streams will take up.
 *
 * A URI is parcelled as its characters in UTF-16, with a few bytes of type and length around them -
 * and twice over, because the system copies `EXTRA_STREAM` into the ClipData it grants the read from
 * before the intent is sent.
 */
private fun List<Uri>.shareIntentCostBytes(): Int =
    sumOf { (it.toString().length * 2 + URI_PARCEL_OVERHEAD_BYTES) * 2 }

/** The type and length a parcelled URI carries besides its characters. */
private const val URI_PARCEL_OVERHEAD_BYTES = 16

/**
 * How large a share intent may grow. A Binder transaction has about a megabyte for everything in
 * flight; half of it is the streams', leaving room for the rest of the intent and for the chooser's
 * own reply.
 *
 * Counted before the intent is sent rather than caught afterwards: an intent over the limit usually
 * fails on the far side of the transaction, in the system or in the app receiving it, so there is
 * often nothing to catch here.
 */
private const val SHARE_INTENT_BUDGET_BYTES = 512 * 1024

private fun Track.contentUri() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaStoreId)
