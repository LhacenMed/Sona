package com.lhacenmed.sona.feature.library.operation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.lhacenmed.sona.core.designsystem.component.SonaConfirmationDialog
import com.lhacenmed.sona.core.model.Playlist
import com.lhacenmed.sona.feature.library.options.OptionsActionsViewModel

/*
 * The confirmed operations more than one place in the library offers, each written once so its wording
 * and its outcome cannot drift between a row's options sheet and a selection's bar.
 */

/**
 * Excluding [folderPaths] from the library - from a folder's options sheet or the selection bar.
 *
 * [onConfirmed] runs the moment the exclusion starts, which is when a selection that asked for it ends.
 * The dialog stays through the rescan that takes the folders' tracks out, so the toast only ever
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
            "Its tracks leave your library. The files themselves are not deleted."
        } else {
            "Their tracks leave your library. The files themselves are not deleted."
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
