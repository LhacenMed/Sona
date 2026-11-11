package com.lhacenmed.sona.feature.library.operation

import android.app.Activity
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.lhacenmed.sona.core.common.permission.AppPermission
import com.lhacenmed.sona.core.data.contentUri
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaConfirmationDialog
import com.lhacenmed.sona.core.designsystem.component.toast
import com.lhacenmed.sona.core.model.Playlist
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.library.R
import com.lhacenmed.sona.feature.library.options.OptionsActionsViewModel
import com.lhacenmed.sona.feature.library.options.RemovableTrackList
import com.lhacenmed.sona.feature.library.mediaCountLabel

/*
 * The confirmed operations more than one place in the library offers, each written once so its wording
 * and its outcome cannot drift between a row's options sheet and a selection's bar.
 */

/**
 * Excluding [folderPaths] from the library - from a folder's options sheet or the selection bar.
 *
 * [onConfirmed] runs the moment the exclusion starts, which is when a selection that asked for it ends.
 * The dialog stays through the rescan that takes the folders' music and videos out, so the toast only ever
 * announces an exclusion that has actually happened.
 */
@Composable
internal fun ExcludeFoldersDialog(
    folderPaths: List<String>,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit = {},
) {
    val actionsViewModel: OptionsActionsViewModel = hiltViewModel()
    val isSingle = folderPaths.size == 1
    SonaConfirmationDialog(
        title = if (isSingle) stringResource(R.string.exclude_folder_title) else pluralStringResource(R.plurals.exclude_folders_title, folderPaths.size, folderPaths.size),
        message = stringResource(if (isSingle) R.string.exclude_folder_message else R.string.exclude_folders_message),
        confirmLabel = stringResource(R.string.exclude_confirm),
        successMessage = if (isSingle) stringResource(R.string.exclude_folder_done) else pluralStringResource(R.plurals.exclude_folders_done, folderPaths.size, folderPaths.size),
        failureMessage = stringResource(if (isSingle) R.string.exclude_folder_failed else R.string.exclude_folders_failed),
        onDismiss = onDismiss,
        operation = { onFinished ->
            onConfirmed()
            actionsViewModel.excludeFolders(folderPaths, onFinished)
        },
    )
}

/**
 * Deleting [playlists] - from a playlist's options sheet, the playlists' selection bar, or a playlist's
 * own screen, which passes [onDeleted] to leave once the playlist it shows is gone.
 */
@Composable
internal fun DeletePlaylistsDialog(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit = {},
    onDeleted: () -> Unit = {},
) {
    val actionsViewModel: OptionsActionsViewModel = hiltViewModel()
    val isSingle = playlists.size == 1
    SonaConfirmationDialog(
        title = if (isSingle) stringResource(R.string.delete_playlist_title) else pluralStringResource(R.plurals.delete_playlists_title, playlists.size, playlists.size),
        message = stringResource(R.string.delete_playlist_message),
        confirmLabel = stringResource(R.string.delete_confirm),
        successMessage = if (isSingle) stringResource(R.string.delete_playlist_done) else pluralStringResource(R.plurals.delete_playlists_done, playlists.size, playlists.size),
        failureMessage = stringResource(if (isSingle) R.string.delete_playlist_failed else R.string.delete_playlists_failed),
        onDismiss = onDismiss,
        operation = { onFinished ->
            onConfirmed()
            actionsViewModel.deletePlaylists(playlists.map { it.id }) { succeeded ->
                onFinished(succeeded)
                if (succeeded) onDeleted()
            }
        },
    )
}

/**
 * Removing [trackIds] from [list] - a playlist or a listening history - from a track's options sheet inside it, or
 * its selection bar. [onConfirmed] runs the moment the removal starts, which is when a selection that asked for it
 * ends.
 */
@Composable
internal fun RemoveFromListDialog(
    list: RemovableTrackList,
    trackIds: List<Long>,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit = {},
) {
    val actionsViewModel: OptionsActionsViewModel = hiltViewModel()
    val isSingle = trackIds.size == 1
    SonaConfirmationDialog(
        title = if (isSingle) stringResource(R.string.remove_track_title) else pluralStringResource(R.plurals.remove_tracks_title, trackIds.size, trackIds.size),
        message = list.removalMessage,
        confirmLabel = stringResource(R.string.remove_confirm),
        successMessage = if (isSingle) stringResource(R.string.remove_track_done) else pluralStringResource(R.plurals.remove_tracks_done, trackIds.size, trackIds.size),
        failureMessage = stringResource(if (isSingle) R.string.remove_track_failed else R.string.remove_tracks_failed),
        onDismiss = onDismiss,
        operation = { onFinished ->
            onConfirmed()
            actionsViewModel.removeFrom(list, trackIds, onFinished)
        },
    )
}

/**
 * Deleting [tracks]' files from the device - from any track's, collection's or selection's options
 * sheet, as Fossify deletes: Sona asks, then deletes the files itself, as a file manager does.
 *
 * Doing so needs the storage permission below Android 11, and access to manage all files from 11 on,
 * which Android only grants from its own settings - FilesAi's model. Where that access is missing,
 * confirming takes the user there once; granted, it deletes without Android ever asking again.
 * Declined, the deletion still happens, through Android's own request, which asks every time.
 *
 * Either way the tracks leave the queue and every list the moment their files are gone, and
 * [onDeleted] hears it - how a collection's own screen leaves once the tracks it showed are gone. [onConfirmed]
 * runs the moment the deletion starts, which is when a selection that asked for it ends.
 */
@Composable
internal fun DeleteFromDeviceDialog(
    tracks: List<Track>,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit = {},
    onDeleted: () -> Unit = {},
) {
    val deleteFromDevice = rememberDeviceDeletion(tracks, onDeleted)
    val isSingle = tracks.size == 1
    SonaConfirmationDialog(
        title = if (isSingle) stringResource(R.string.delete_track_title) else pluralStringResource(R.plurals.delete_tracks_title, tracks.size, tracks.size),
        message = listOfNotNull(
            stringResource(if (isSingle) R.string.delete_track_message else R.string.delete_tracks_message),
            stringResource(R.string.delete_needs_all_files).takeIf { needsAllFilesAccess(LocalContext.current) },
        ).joinToString("\n\n"),
        confirmLabel = stringResource(R.string.delete_confirm),
        successMessage = deletionOutcome(tracks.size, succeeded = true),
        failureMessage = deletionOutcome(tracks.size, succeeded = false),
        onDismiss = onDismiss,
        operation = { onFinished ->
            onConfirmed()
            deleteFromDevice(onFinished)
        },
    )
}

/**
 * Moves [tracks] into the Private Folder - a track or a selection, from its options sheet, which offers it only once the
 * folder is set up, so opening it is not needed to put a file in. Copied in first, every one, then deleted from the
 * device exactly as [DeleteFromDeviceDialog] deletes - and the copies taken back out should that deletion not happen,
 * so no file is ever left in both places. [onConfirmed] runs the moment the move starts, which is when a selection that
 * asked for it ends.
 */
@Composable
internal fun AddToPrivateDialog(
    tracks: List<Track>,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit = {},
    onMoved: () -> Unit = {},
) {
    val actionsViewModel: OptionsActionsViewModel = hiltViewModel()
    val deleteOriginals = rememberDeviceDeletion(tracks, onDeleted = onMoved)
    val isSingle = tracks.size == 1
    SonaConfirmationDialog(
        title = if (isSingle) stringResource(R.string.private_add_title) else stringResource(R.string.private_add_some_title, mediaCountLabel(tracks)),
        message = listOfNotNull(
            stringResource(if (isSingle) R.string.private_add_message else R.string.private_add_some_message),
            stringResource(R.string.private_add_needs_all_files).takeIf { needsAllFilesAccess(LocalContext.current) },
        ).joinToString("\n\n"),
        confirmLabel = stringResource(R.string.private_add_confirm),
        successMessage = stringResource(R.string.private_add_done),
        failureMessage = stringResource(R.string.private_add_failed),
        onDismiss = onDismiss,
        operation = { onFinished ->
            onConfirmed()
            actionsViewModel.copyToPrivate(tracks) { items ->
                if (items == null) {
                    onFinished(false)
                } else {
                    deleteOriginals { deleted ->
                        if (!deleted) actionsViewModel.discardPrivateCopies(items)
                        onFinished(deleted)
                    }
                }
            }
        },
    )
}

/**
 * How [tracks]' files are deleted from the device once that is confirmed - with whatever permission it
 * takes, asked for on the way: see [DeleteFromDeviceDialog]. [onDeleted] hears the files go.
 */
@Composable
private fun rememberDeviceDeletion(
    tracks: List<Track>,
    onDeleted: () -> Unit,
): (onFinished: (succeeded: Boolean) -> Unit) -> Unit {
    val context = LocalContext.current
    val actionsViewModel: OptionsActionsViewModel = hiltViewModel()
    // The confirmed deletion waiting on a permission, a settings screen or Android's own request.
    var pendingDeletion by remember { mutableStateOf<((succeeded: Boolean) -> Unit)?>(null) }
    val reporting: ((succeeded: Boolean) -> Unit) -> (succeeded: Boolean) -> Unit = { onFinished ->
        { succeeded ->
            onFinished(succeeded)
            if (succeeded) onDeleted()
        }
    }
    // A manually scanned track has no MediaStore entry for Android's request to name, so it is left out
    // of one - and a request that loses any says so, as sharing does.
    val indexedTracks = remember(tracks) { tracks.filterNot(Track::isManuallyScanned) }

    val systemRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val onFinished = pendingDeletion ?: return@rememberLauncherForActivityResult
        pendingDeletion = null
        if (result.resultCode == Activity.RESULT_OK) {
            actionsViewModel.forgetDeletedTracks(indexedTracks, reporting(onFinished))
        } else {
            onFinished(false)
        }
    }
    val requestSystemDeletion: (onFinished: (succeeded: Boolean) -> Unit) -> Unit = { onFinished ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && indexedTracks.isNotEmpty()) {
            val leftOutCount = tracks.size - indexedTracks.size
            if (leftOutCount > 0) context.toast(context.resources.getQuantityString(R.plurals.delete_tracks_left_out, leftOutCount, leftOutCount))
            pendingDeletion = onFinished
            val request = MediaStore.createDeleteRequest(context.contentResolver, indexedTracks.map { it.contentUri })
            systemRequestLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        } else {
            onFinished(false)
        }
    }
    val allFilesAccessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val onFinished = pendingDeletion ?: return@rememberLauncherForActivityResult
        pendingDeletion = null
        if (AppPermission.FILE_CHANGES.isGranted(context)) {
            actionsViewModel.deleteTracks(tracks, reporting(onFinished))
        } else {
            requestSystemDeletion(onFinished)
        }
    }
    val writePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val onFinished = pendingDeletion ?: return@rememberLauncherForActivityResult
        pendingDeletion = null
        if (granted) actionsViewModel.deleteTracks(tracks, reporting(onFinished)) else onFinished(false)
    }

    return { onFinished ->
        val permission = AppPermission.FILE_CHANGES
        val runtimePermission = permission.runtimePermission
        when {
            permission.isGranted(context) -> actionsViewModel.deleteTracks(tracks, reporting(onFinished))
            runtimePermission != null -> {
                pendingDeletion = onFinished
                writePermissionLauncher.launch(runtimePermission)
            }
            else -> {
                pendingDeletion = onFinished
                allFilesAccessLauncher.launch(permission.settingsIntent(context))
            }
        }
    }
}

/** Whether deleting from the device first takes the user to Android's settings to allow managing all files. */
private fun needsAllFilesAccess(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !AppPermission.FILE_CHANGES.isGranted(context)

@Composable
private fun deletionOutcome(count: Int, succeeded: Boolean): String = when {
    succeeded && count == 1 -> stringResource(R.string.delete_track_done)
    succeeded -> pluralStringResource(R.plurals.delete_tracks_done, count, count)
    count == 1 -> stringResource(R.string.delete_track_failed)
    else -> stringResource(R.string.delete_tracks_failed)
}
