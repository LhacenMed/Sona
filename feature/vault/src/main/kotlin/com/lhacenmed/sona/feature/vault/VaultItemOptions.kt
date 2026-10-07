package com.lhacenmed.sona.feature.vault

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.common.coroutines.launchOperation
import com.lhacenmed.sona.core.designsystem.component.InfoSeparator
import com.lhacenmed.sona.core.designsystem.component.SonaBottomSheet
import com.lhacenmed.sona.core.designsystem.component.SonaCoverArt
import com.lhacenmed.sona.core.designsystem.component.SonaOptionRow
import com.lhacenmed.sona.core.designsystem.component.SonaOptionsSheetHeader
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaConfirmationDialog
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.core.vault.VaultRepository
import com.lhacenmed.sona.core.vault.data.VaultItem
import com.lhacenmed.sona.feature.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** What a chosen option is waiting on - its confirmation. */
private enum class VaultItemAction { MOVE_OUT, DELETE }

/**
 * The options of the Private Folder track or video the player is showing [track] for - the folder's own, as
 * every one of its rows offers them: the library's options know nothing of it.
 */
@Composable
fun VaultTrackOptionsSheet(track: Track, onDismissRequest: () -> Unit) {
    val viewModel: VaultItemOptionsViewModel = hiltViewModel()
    val itemsById by viewModel.itemsById.collectAsStateWithLifecycle()
    val item = itemsById?.get(track.id)
    if (item == null) {
        // Read once its list is - and, gone meanwhile, there is nothing to offer.
        if (itemsById != null) LaunchedEffect(Unit) { onDismissRequest() }
        return
    }
    VaultItemOptions(item = item, onDismissRequest = onDismissRequest)
}

/**
 * [item]'s options - moving it back out, deleting it - and the confirmation of whichever is chosen.
 * [onDismissRequest] hears once all of it is done with.
 */
@Composable
internal fun VaultItemOptions(item: VaultItem, onDismissRequest: () -> Unit) {
    val viewModel: VaultItemOptionsViewModel = hiltViewModel()
    var action by remember { mutableStateOf<VaultItemAction?>(null) }
    var isSheetGone by remember { mutableStateOf(false) }

    if (!isSheetGone) {
        SonaBottomSheet(
            onDismissRequest = {
                isSheetGone = true
                if (action == null) onDismissRequest()
            },
            header = {
                SonaOptionsSheetHeader(
                    cover = { SonaCoverArt(coverArtUri = viewModel.coverOf(item), contentDescription = null) },
                    type = if (item.isVideo) "Video" else "Track",
                    name = item.title,
                    info = item.subtitle,
                )
            },
        ) {
            SonaOptionRow(
                label = "Move out of Private Folder",
                icon = Icons.Filled.LockOpen,
                onClick = {
                    action = VaultItemAction.MOVE_OUT
                    dismiss()
                },
            )
            SonaOptionRow(
                label = "Delete permanently",
                icon = SonaIcons.Delete,
                onClick = {
                    action = VaultItemAction.DELETE
                    dismiss()
                },
            )
        }
    }
    when (action) {
        VaultItemAction.MOVE_OUT -> MoveOutDialog(item = item, viewModel = viewModel, onDismiss = onDismissRequest)
        VaultItemAction.DELETE -> SonaConfirmationDialog(
            title = "Delete permanently",
            message = "Its file is deleted from this device. This cannot be undone.",
            confirmLabel = "Delete",
            successMessage = "Deleted",
            failureMessage = "Could not delete",
            onDismiss = onDismissRequest,
            operation = { onFinished -> viewModel.delete(item, onFinished) },
        )
        null -> Unit
    }
}

/**
 * Moving [item] back out - to Music/Sona or Movies/Sona, where the library picks it up again. Below Android
 * 10 that needs the storage permission, asked for here; from 10 on, none.
 */
@Composable
private fun MoveOutDialog(item: VaultItem, viewModel: VaultItemOptionsViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var pendingMove by remember { mutableStateOf<((succeeded: Boolean) -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val onFinished = pendingMove ?: return@rememberLauncherForActivityResult
        pendingMove = null
        if (granted) viewModel.moveOut(item, onFinished) else onFinished(false)
    }
    SonaConfirmationDialog(
        title = "Move out of Private Folder",
        message = "It goes back to your library, in ${if (item.isVideo) "Movies" else "Music"}/Sona.",
        confirmLabel = "Move out",
        successMessage = "Moved out of Private Folder",
        failureMessage = "Could not move out of Private Folder",
        onDismiss = onDismiss,
        operation = { onFinished ->
            if (context.needsStoragePermission()) {
                pendingMove = onFinished
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                viewModel.moveOut(item, onFinished)
            }
        },
    )
}

private fun Context.needsStoragePermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
        checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED

/** A video's length; a track's artist and album, as the library's own rows read. */
internal val VaultItem.subtitle: String
    get() = if (isVideo) formatDuration(durationMs) else "$artist$InfoSeparator$album"

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1000
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

@HiltViewModel
internal class VaultItemOptionsViewModel @Inject constructor(
    private val vault: VaultRepository,
    private val playbackController: PlaybackController,
) : ViewModel() {

    /** Null until read. */
    val itemsById: StateFlow<Map<Long, VaultItem>?> = vault.itemsById.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The file itself, which the app's covers already know to thumbnail - a track's art, a video's frame. */
    fun coverOf(item: VaultItem): String? = vault.trackOf(item).coverArtUri

    /** Out of the queue first, so the player never reaches a file that has gone. */
    fun moveOut(item: VaultItem, onFinished: (succeeded: Boolean) -> Unit) {
        viewModelScope.launchOperation(onFinished) {
            playbackController.removePrivateFromQueue(setOf(item.id))
            vault.moveOut(item)
        }
    }

    fun delete(item: VaultItem, onFinished: (succeeded: Boolean) -> Unit) {
        viewModelScope.launchOperation(onFinished) {
            playbackController.removePrivateFromQueue(setOf(item.id))
            vault.delete(item)
        }
    }
}
