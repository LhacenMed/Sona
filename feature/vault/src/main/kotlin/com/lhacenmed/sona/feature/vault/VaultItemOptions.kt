package com.lhacenmed.sona.feature.vault

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.common.format.formatDuration
import com.lhacenmed.sona.core.designsystem.component.InfoSeparator
import com.lhacenmed.sona.core.designsystem.component.SonaCoverArt
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaConfirmationDialog
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaBottomSheet
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaOptionRow
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaOptionsSheetHeader
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.core.vault.VaultRepository
import com.lhacenmed.sona.core.vault.data.VaultItem
import com.lhacenmed.sona.feature.playback.PlaybackController
import com.lhacenmed.sona.feature.vault.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What a chosen option or a selection's action is waiting on - its confirmation. */
internal enum class VaultItemAction { MOVE_OUT, DELETE }

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
                    type = stringResource(if (item.isVideo) R.string.vault_type_video else R.string.vault_type_track),
                    name = item.title,
                    info = item.subtitle,
                )
            },
        ) {
            SonaOptionRow(
                label = stringResource(R.string.vault_move_out),
                icon = SonaIcons.LockOpen,
                onClick = {
                    action = VaultItemAction.MOVE_OUT
                    dismiss()
                },
            )
            SonaOptionRow(
                label = stringResource(R.string.vault_delete_permanently),
                icon = SonaIcons.Delete,
                onClick = {
                    action = VaultItemAction.DELETE
                    dismiss()
                },
            )
        }
    }
    action?.let { VaultItemsDialog(items = listOf(item), action = it, onDismiss = onDismissRequest) }
}

/**
 * The confirmation of [action] on [items] - one item's, from its options, or a selection's, from its bar - then the
 * action itself, item by item: one that cannot be moved or deleted stays where it is, and the rest are still done.
 * [onDone] hears which were, before the dialog is dismissed; a selection keeps the others picked.
 */
@Composable
internal fun VaultItemsDialog(
    items: List<VaultItem>,
    action: VaultItemAction,
    onDismiss: () -> Unit,
    onDone: (done: List<VaultItem>) -> Unit = {},
) {
    val viewModel: VaultItemOptionsViewModel = hiltViewModel()
    val isSingle = items.size == 1
    // Reported as succeeded only once every item is done, and as "some could not be" when only part of them were.
    val reporting: ((succeeded: Boolean) -> Unit) -> (done: List<VaultItem>) -> Unit = { onFinished ->
        { done ->
            onDone(done)
            onFinished(done.size == items.size)
        }
    }
    when (action) {
        VaultItemAction.MOVE_OUT -> MoveOutDialog(items = items, viewModel = viewModel, onDismiss = onDismiss, reporting = reporting)
        VaultItemAction.DELETE -> SonaConfirmationDialog(
            title = stringResource(R.string.vault_delete_permanently),
            message = stringResource(if (isSingle) R.string.vault_delete_message else R.string.vault_delete_some_message),
            confirmLabel = stringResource(R.string.vault_delete),
            successMessage = stringResource(R.string.vault_deleted),
            failureMessage = stringResource(if (isSingle) R.string.vault_delete_failed else R.string.vault_delete_some_failed),
            onDismiss = onDismiss,
            operation = { onFinished -> viewModel.delete(items, reporting(onFinished)) },
        )
    }
}

/**
 * Moving [items] back out - to Music/Sona or Movies/Sona, where the library picks them up again. Below Android
 * 10 that needs the storage permission, asked for here; from 10 on, none - and refused, nothing moves.
 */
@Composable
private fun MoveOutDialog(
    items: List<VaultItem>,
    viewModel: VaultItemOptionsViewModel,
    onDismiss: () -> Unit,
    reporting: ((succeeded: Boolean) -> Unit) -> (done: List<VaultItem>) -> Unit,
) {
    val context = LocalContext.current
    var pendingMove by remember { mutableStateOf<((done: List<VaultItem>) -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val onMoved = pendingMove ?: return@rememberLauncherForActivityResult
        pendingMove = null
        if (granted) viewModel.moveOut(items, onMoved) else onMoved(emptyList())
    }
    val folders = items.map { if (it.isVideo) "Movies/Sona" else "Music/Sona" }.distinct().joinToString(" & ")
    val isSingle = items.size == 1
    SonaConfirmationDialog(
        title = stringResource(R.string.vault_move_out),
        message = stringResource(if (isSingle) R.string.vault_move_out_message else R.string.vault_move_out_some_message, folders),
        confirmLabel = stringResource(R.string.vault_move_out_confirm),
        successMessage = stringResource(R.string.vault_moved_out),
        failureMessage = stringResource(if (isSingle) R.string.vault_move_out_failed else R.string.vault_move_out_some_failed),
        onDismiss = onDismiss,
        operation = { onFinished ->
            val onMoved = reporting(onFinished)
            if (context.needsStoragePermission()) {
                pendingMove = onMoved
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                viewModel.moveOut(items, onMoved)
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

@HiltViewModel
internal class VaultItemOptionsViewModel @Inject constructor(
    private val vault: VaultRepository,
    private val playbackController: PlaybackController,
) : ViewModel() {

    /** Null until read. */
    val itemsById: StateFlow<Map<Long, VaultItem>?> = vault.itemsById.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The file itself, which the app's covers already know to thumbnail - a track's art, a video's frame. */
    fun coverOf(item: VaultItem): String? = vault.trackOf(item).coverArtUri

    fun moveOut(items: List<VaultItem>, onFinished: (done: List<VaultItem>) -> Unit) = eachOf(items, onFinished, vault::moveOut)

    fun delete(items: List<VaultItem>, onFinished: (done: List<VaultItem>) -> Unit) = eachOf(items, onFinished, vault::delete)

    /**
     * Does [action] to each of [items] on its own, so one that fails - its file unreadable, say - leaves the others
     * done and itself untouched; [onFinished] hears which were done. Each leaves the queue first, so the player never
     * reaches a file that has gone.
     */
    private fun eachOf(items: List<VaultItem>, onFinished: (done: List<VaultItem>) -> Unit, action: suspend (VaultItem) -> Unit) {
        viewModelScope.launch {
            val done = items.filter { item ->
                try {
                    playbackController.removePrivateFromQueue(setOf(item.id))
                    action(item)
                    true
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    false
                }
            }
            onFinished(done)
        }
    }
}
