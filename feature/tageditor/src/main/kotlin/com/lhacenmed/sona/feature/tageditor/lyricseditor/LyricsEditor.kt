package com.lhacenmed.sona.feature.tageditor.lyricseditor

import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncDisabled
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.ShowMiniPlayerSeekBar
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.rememberSelectionState
import com.lhacenmed.sona.core.designsystem.component.screen.screenList
import com.lhacenmed.sona.core.designsystem.component.selectAllAction
import com.lhacenmed.sona.core.designsystem.component.swipe.SwipeAction
import com.lhacenmed.sona.core.designsystem.component.swipe.SwipeActionTone
import com.lhacenmed.sona.core.designsystem.component.swipe.SwipeActions
import com.lhacenmed.sona.core.designsystem.component.swipe.SwipeActionsBox
import com.lhacenmed.sona.core.designsystem.component.toTopBarSelection
import com.lhacenmed.sona.core.designsystem.component.toast
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How often the line the player is on is looked for while it plays. */
private const val PositionTickMs = 100L

/** How far down the list the line the player is on is kept as it plays: a third, with what comes next beneath it. */
private const val CurrentLineViewportFraction = 1f / 3

/**
 * The lyrics editor, on [state]: every line with its time, in the order they are sung, the one the player is on
 * marked as it plays - wherever it is shown, on its own or over the tag editor.
 *
 * Lyrics come in from the plus above - the web, the clipboard or a lyrics file - taking the place of what is there;
 * plain ones come in untimed, every line at the start. The flow is the player's: play the track from the mini
 * player below, and press each line's clock as it is sung, which times it at that moment; then nudge any time a
 * step either way, or type it in, which plays from there. A line's plus opens what else can be done to it, and a
 * swipe towards its start takes it out, as a queue's row is - undone from the bar. The lines changed by hand are
 * marked down their start. While the track plays, the list follows the line it is on,
 * and the mini player beneath shows its seek bar to move through it closely.
 *
 * [confirmAction] is what keeps the lyrics - saving them, or handing them back. Leaving otherwise, by back or the
 * bar's arrow, calls [onLeave] - asking first while there are changes it would lose.
 */
@Composable
internal fun LyricsEditor(state: LyricsEditorState, confirmAction: TopBarAction, onLeave: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playback by state.playback.collectAsStateWithLifecycle()
    val isTrackPlaying = playback.libraryTrackId == state.track.id

    var isAddingLyrics by rememberSaveable { mutableStateOf(false) }
    var isSearchingLyrics by rememberSaveable { mutableStateOf(false) }
    var editingLineId by rememberSaveable { mutableStateOf<Long?>(null) }
    var isConfirmingDiscard by rememberSaveable { mutableStateOf(false) }
    var isEditingAsText by rememberSaveable { mutableStateOf(false) }
    var isConfirmingDelete by rememberSaveable { mutableStateOf(false) }
    var isBatchEditing by rememberSaveable { mutableStateOf(false) }
    var isFollowingPlayback by rememberSaveable { mutableStateOf(true) }
    val selection = rememberSelectionState()
    val selectedLineIds = selection.selectedKeys.filterIsInstance<Long>().toSet()

    // Beside play and pause on the mini player: whether the list follows the line the player is on.
    ShowMiniPlayerSeekBar(
        action = if (isFollowingPlayback) {
            TopBarAction(label = "Stop following the playing line", icon = Icons.Filled.Sync) { isFollowingPlayback = false }
        } else {
            TopBarAction(label = "Follow the playing line", icon = Icons.Filled.SyncDisabled) { isFollowingPlayback = true }
        },
    )

    val requestLeave = { if (state.hasChanges) isConfirmingDiscard = true else onLeave() }
    BackHandler(onBack = requestLeave)
    // Ahead of leaving: back lets go of the selection first.
    BackHandler(enabled = selection.isActive) { selection.clear() }
    // A selected line taken out of the lyrics is let go of with it.
    LaunchedEffect(state.lines) {
        val lineIds = state.lines.mapTo(HashSet()) { it.id }
        selection.deselectAll(selection.selectedKeys.filterNot { it in lineIds })
    }

    // Where the player is: looked at often while it plays, and again wherever it is moved to while paused.
    var positionMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isTrackPlaying, playback.isPlaying, playback.positionMs) {
        if (!isTrackPlaying) return@LaunchedEffect
        positionMs = state.currentPositionMs()
        if (!playback.isPlaying) return@LaunchedEffect
        while (true) {
            delay(PositionTickMs)
            positionMs = state.currentPositionMs()
        }
    }
    // Derived, so the list is recomposed only as the line it marks changes rather than on every tick.
    val currentLineId by remember(isTrackPlaying) {
        derivedStateOf { if (isTrackPlaying) state.lines.lineAt(positionMs)?.id else null }
    }

    // While the track plays, the list follows the line it is on, gliding it to a third of the way down as it is
    // reached - unless following is turned off on the mini player, or the list is held under the finger, which is
    // the user looking elsewhere.
    val listState = rememberLazyListState()
    LaunchedEffect(currentLineId, playback.isPlaying, isFollowingPlayback) {
        if (!isFollowingPlayback || !playback.isPlaying || listState.isScrollInProgress) return@LaunchedEffect
        val index = state.lines.indexOfFirst { it.id == currentLineId }.takeIf { it >= 0 } ?: return@LaunchedEffect
        val offset = (listState.layoutInfo.viewportSize.height * CurrentLineViewportFraction).toInt()
        listState.animateScrollToItem(index, scrollOffset = -offset)
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch { if (!state.replaceLinesFromFile(context.contentResolver, uri)) context.toast("No lyrics in that file") }
    }
    val paste = {
        val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        if (text == null || !state.replaceLines(text)) context.toast("No lyrics in the clipboard")
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SonaTopAppBar(
            title = "Lyrics editor",
            subtitle = state.track.title,
            onNavigateBack = requestLeave,
            selection = selection.toTopBarSelection(
                actions = listOf(
                    TopBarAction(label = "Batch edit", icon = Icons.Filled.MoreTime) { isBatchEditing = true },
                    selection.selectAllAction(state.lines.map { it.id }),
                    TopBarAction(label = "Delete", icon = Icons.Filled.Delete) {
                        state.remove(selectedLineIds)
                        selection.clear()
                    },
                ),
                onMoreOptions = null,
            ),
            actions = listOf(
                // Undo beside keeping, where it is reached for most; the bar folds what does not fit into its menu.
                confirmAction,
                TopBarAction(label = "Undo", icon = Icons.AutoMirrored.Filled.Undo, enabled = state.canUndo, onClick = state::undo),
                TopBarAction(label = "Redo", icon = Icons.AutoMirrored.Filled.Redo, enabled = state.canRedo, onClick = state::redo),
                TopBarAction(label = "Add lyrics", icon = Icons.Filled.Add) { isAddingLyrics = true },
                TopBarAction(label = "View/Edit lyrics plain text", icon = Icons.AutoMirrored.Filled.Article) {
                    isEditingAsText = true
                },
                TopBarAction(label = "Delete embedded lyrics", icon = Icons.Filled.Delete, enabled = state.lines.isNotEmpty()) {
                    isConfirmingDelete = true
                },
            ),
        )
        if (state.lines.isEmpty()) {
            NoLyrics()
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().screenList(listState),
                contentPadding = PaddingValues(bottom = LocalBottomContentPadding.current),
            ) {
                items(state.lines, key = { it.id }) { line ->
                    // Held still while lines are selected, as every swiping row in the app is.
                    SwipeActionsBox(
                        actions = SwipeActions(
                            startToEnd = null,
                            endToStart = SwipeAction(SonaIcons.Delete, SwipeActionTone.Danger) { state.remove(line.id) },
                        ).takeUnless { selection.isActive },
                    ) {
                        LyricsLineRow(
                            line = line,
                            isCurrent = line.id == currentLineId,
                            isEdited = state.isEdited(line),
                            canStamp = isTrackPlaying,
                            selection = selection,
                            actions = listOf(
                                TopBarAction(label = "Play from here", icon = Icons.Filled.PlayArrow) { state.playFrom(line.id) },
                                TopBarAction(label = "Add row above", icon = Icons.Filled.ArrowUpward) {
                                    state.addLine(line.id, below = false)
                                },
                                TopBarAction(label = "Add row below", icon = Icons.Filled.ArrowDownward) {
                                    state.addLine(line.id, below = true)
                                },
                                TopBarAction(label = "Reset timestamp", icon = Icons.Filled.RestartAlt, enabled = line.timeMs != 0L) {
                                    state.resetTime(line.id)
                                },
                                TopBarAction(label = "Remove", icon = Icons.Filled.DeleteOutline) { state.remove(line.id) },
                            ),
                            onSetTime = { timeMs -> state.setTime(line.id, timeMs) },
                            onStamp = { state.stamp(line.id) },
                            onEditText = { editingLineId = line.id },
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (isAddingLyrics) {
        AddLyricsSheet(
            onSearch = {
                state.searchLyrics()
                isSearchingLyrics = true
            },
            onPaste = paste,
            // Lyrics files carry no type of their own that Android knows, so they come as text or as bytes.
            onPickFile = { filePicker.launch(arrayOf("text/*", "application/octet-stream")) },
            onDismissRequest = { isAddingLyrics = false },
        )
    }
    if (isSearchingLyrics) {
        FoundLyricsSheet(
            found = state.foundLyrics,
            isSearching = state.isSearching,
            trackDurationMs = state.track.durationMs,
            onPick = { found -> state.replaceLines(found.lyrics.text) },
            onDismissRequest = {
                state.stopSearching()
                isSearchingLyrics = false
            },
        )
    }
    editingLineId?.let { lineId ->
        val line = state.lines.find { it.id == lineId } ?: return@let
        LyricsLineTextDialog(
            initialText = line.text,
            onDismiss = { editingLineId = null },
            onDone = { text ->
                state.setText(lineId, text)
                editingLineId = null
            },
        )
    }
    if (isBatchEditing) {
        BatchEditDialog(
            onDismiss = { isBatchEditing = false },
            onAdjust = { deltaMs ->
                state.offsetTimes(selectedLineIds, deltaMs)
                selection.clear()
                isBatchEditing = false
            },
        )
    }
    if (isEditingAsText) {
        LyricsTextDialog(
            initialText = state.lyrics,
            onDismiss = { isEditingAsText = false },
            onDone = { text ->
                state.setLyricsText(text)
                isEditingAsText = false
            },
        )
    }
    if (isConfirmingDelete) {
        DeleteLyricsDialog(
            onDismiss = { isConfirmingDelete = false },
            onDelete = {
                state.clearLines()
                isConfirmingDelete = false
            },
        )
    }
    if (isConfirmingDiscard) {
        DiscardChangesDialog(
            onKeepEditing = { isConfirmingDiscard = false },
            onDiscard = {
                isConfirmingDiscard = false
                onLeave()
            },
        )
    }
}

/** What the editor shows while it has no lines - in their place, so the screen keeps its shape. */
@Composable
private fun NoLyrics() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No lyrics yet", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            "Add them with the plus above: from the web, the clipboard or a lyrics file.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
