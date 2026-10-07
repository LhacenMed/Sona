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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.common.permission.AppPermission
import com.lhacenmed.sona.core.data.contentUri
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaConfirmationDialog
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaBottomSheet
import com.lhacenmed.sona.core.designsystem.component.toast
import com.lhacenmed.sona.core.model.Playlist
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.core.vault.VaultState
import com.lhacenmed.sona.feature.library.options.OptionsActionsViewModel
import com.lhacenmed.sona.feature.library.pluralCount
import com.lhacenmed.sona.feature.vault.VaultSetupFlow

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
        title = if (isSingle) "Exclude folder" else "Exclude ${folderPaths.size} folders",
        message = if (isSingle) {
            "Its music and videos leave your library. The files themselves are not deleted."
        } else {
            "Their music and videos leave your library. The files themselves are not deleted."
        },
        confirmLabel = "Exclude",
        successMessage = if (isSingle) "Folder excluded" else "${folderPaths.size} folders excluded",
        failureMessage = if (isSingle) "Could not exclude folder" else "Could not exclude folders",
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
        title = if (isSingle) "Delete playlist" else "Delete ${playlists.size} playlists",
        message = "The tracks themselves are not deleted.",
        confirmLabel = "Delete",
        successMessage = if (isSingle) "Playlist deleted" else "${playlists.size} playlists deleted",
        failureMessage = if (isSingle) "Could not delete playlist" else "Could not delete playlists",
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
 * Removing [trackIds] from [playlist] - from a track's options sheet inside the playlist, or the
 * playlist's selection bar. [onConfirmed] runs the moment the removal starts, which is when a selection
 * that asked for it ends.
 */
@Composable
internal fun RemoveFromPlaylistDialog(
    playlist: Playlist,
    trackIds: List<Long>,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit = {},
) {
    val actionsViewModel: OptionsActionsViewModel = hiltViewModel()
    val isSingle = trackIds.size == 1
    SonaConfirmationDialog(
        title = if (isSingle) "Remove track" else "Remove ${trackIds.size} tracks",
        message = "From ${playlist.name}. The files themselves are not deleted.",
        confirmLabel = "Remove",
        successMessage = if (isSingle) "Track removed" else "${trackIds.size} tracks removed",
        failureMessage = if (isSingle) "Could not remove track" else "Could not remove tracks",
        onDismiss = onDismiss,
        operation = { onFinished ->
            onConfirmed()
            actionsViewModel.removeFromPlaylist(playlist.id, trackIds, onFinished)
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
 * [onDeleted] hears it - how a collection's own screen leaves once the tracks it showed are gone.
 */
@Composable
internal fun DeleteFromDeviceDialog(
    tracks: List<Track>,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit = {},
) {
    val deleteFromDevice = rememberDeviceDeletion(tracks, onDeleted)
    val isSingle = tracks.size == 1
    SonaConfirmationDialog(
        title = if (isSingle) "Delete track" else "Delete ${tracks.size} tracks",
        message = buildString {
            append(if (isSingle) "Its file is deleted" else "Their files are deleted")
            append(" from this device. This cannot be undone.")
            if (needsAllFilesAccess(LocalContext.current)) {
                append("\n\nNext, allow Sona to manage all files, and it deletes without Android asking each time.")
            }
        },
        confirmLabel = "Delete",
        successMessage = deletionOutcome(tracks.size, succeeded = true),
        failureMessage = deletionOutcome(tracks.size, succeeded = false),
        onDismiss = onDismiss,
        operation = deleteFromDevice,
    )
}

/**
 * Adding [track] to the Private Folder - from its options sheet. Creates the folder first if there is
 * none yet; opening it is not needed to put a file in.
 */
@Composable
internal fun AddToPrivateDialog(
    track: Track,
    onDismiss: () -> Unit,
    onMoved: () -> Unit = {},
) {
    val actionsViewModel: OptionsActionsViewModel = hiltViewModel()
    val vault by actionsViewModel.vaultState.collectAsStateWithLifecycle()
    when (vault) {
        VaultState.Loading -> Unit
        VaultState.NotCreated -> SonaBottomSheet(title = "Create Private Folder", onDismissRequest = onDismiss) {
            VaultSetupFlow(opensFolder = false)
        }
        is VaultState.Created -> MoveToPrivateDialog(track = track, onDismiss = onDismiss, onMoved = onMoved)
    }
}

/**
 * Moves [track] into the Private Folder: copied in first, then deleted from the device exactly as
 * [DeleteFromDeviceDialog] deletes - and the copy taken back out should that deletion not happen, so the
 * file is never left in both places.
 */
@Composable
private fun MoveToPrivateDialog(
    track: Track,
    onDismiss: () -> Unit,
    onMoved: () -> Unit,
) {
    val actionsViewModel: OptionsActionsViewModel = hiltViewModel()
    val deleteOriginal = rememberDeviceDeletion(listOf(track), onDeleted = onMoved)
    SonaConfirmationDialog(
        title = "Add to Private Folder",
        message = buildString {
            append("It leaves your library for the Private Folder, which only its PIN opens.")
            if (needsAllFilesAccess(LocalContext.current)) {
                append("\n\nNext, allow Sona to manage all files, and it moves without Android asking each time.")
            }
        },
        confirmLabel = "Add",
        successMessage = "Added to Private Folder",
        failureMessage = "Could not add to Private Folder",
        onDismiss = onDismiss,
        operation = { onFinished ->
            actionsViewModel.copyToPrivate(track) { item ->
                if (item == null) {
                    onFinished(false)
                } else {
                    deleteOriginal { deleted ->
                        if (!deleted) actionsViewModel.discardPrivateCopy(item)
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
            if (leftOutCount > 0) context.toast("${pluralCount(leftOutCount, "track")} cannot be deleted")
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

private fun deletionOutcome(count: Int, succeeded: Boolean): String = when {
    succeeded && count == 1 -> "Track deleted"
    succeeded -> "$count tracks deleted"
    count == 1 -> "Could not delete track"
    else -> "Could not delete tracks"
}
