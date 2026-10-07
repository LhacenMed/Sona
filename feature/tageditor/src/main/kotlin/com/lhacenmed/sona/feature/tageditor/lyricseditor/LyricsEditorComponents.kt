package com.lhacenmed.sona.feature.tageditor.lyricseditor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.component.SelectionState
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialog
import com.lhacenmed.sona.core.designsystem.component.selectableRow
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaBottomSheet
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaOptionRow
import com.lhacenmed.sona.feature.tageditor.LyricsResultCard
import com.lhacenmed.sona.feature.tageditor.LyricsSearchStatus
import com.lhacenmed.sona.feature.tageditor.lyrics.FoundLyrics

/** Digits that all take one width, so a time keeps its place as it is nudged. */
private const val TabularFigures = "tnum"

/** The room on either side of a time part's digits, inside the highlight it takes while typed in. */
private val TimePartPadding = 2.dp

/** Room for the cursor after a time part's last digit, so a full field never scrolls its first one away. */
private val TimePartCursorWidth = 2.dp

/** How much of the selection colour washes over a selected line - as over every selected row in the app. */
private const val SelectedLineTintAlpha = 0.12f

/** How wide the mark down a line changed by hand is. */
private val EditedMarkWidth = 4.dp

/**
 * One line of the editor: its time on the left, each part typed in or nudged - a step later from the button above,
 * earlier from the one below; its words, which a tap opens for writing; and on the right the [actions] menu, and the
 * button that times it at where the player is - only while the track is the one playing, [canStamp]. The line the
 * player is on, [isCurrent], is marked, and one changed by hand, [isEdited], carries a mark down its start.
 *
 * It joins [selection] as every row in the app does: a long press selects it, and while lines are selected a tap
 * selects or lets go of it rather than opening its words. It is painted on the list's own surface, so a swipe aside
 * - see [LyricsEditor] - uncovers its action rather than showing through it.
 */
@Composable
internal fun LyricsLineRow(
    line: LyricsLine,
    isCurrent: Boolean,
    isEdited: Boolean,
    canStamp: Boolean,
    selection: SelectionState,
    actions: List<TopBarAction>,
    onSetTime: (Long) -> Unit,
    onStamp: () -> Unit,
    onEditText: () -> Unit,
) {
    val editedColor = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val color = when {
        selection.isSelected(line.id) -> MaterialTheme.colorScheme.primary.copy(alpha = SelectedLineTintAlpha).compositeOver(surface)
        isCurrent -> MaterialTheme.colorScheme.secondaryContainer
        else -> surface
    }
    Surface(color = color) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableRow(selection, selectionKey = line.id, dragSelection = null, onClick = onEditText)
                // Drawn rather than laid out, so a line keeps its shape as it is marked.
                .drawBehind { if (isEdited) drawRect(editedColor, size = Size(EditedMarkWidth.toPx(), size.height)) }
                .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimeControls(timeMs = line.timeMs, onSetTime = onSetTime)
            Text(
                text = line.text.ifEmpty { "Tap to write this line" },
                style = MaterialTheme.typography.bodyLarge,
                color = if (line.text.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LineActionsMenu(actions)
                IconButton(onClick = onStamp, enabled = canStamp) {
                    Icon(Icons.Outlined.Schedule, contentDescription = "Time to now")
                }
            }
        }
    }
}

/** What else can be done to a line, in a menu opening from its plus. */
@Composable
private fun LineActionsMenu(actions: List<TopBarAction>) {
    var isExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { isExpanded = true }) {
            Icon(Icons.Filled.Add, contentDescription = "Line actions")
        }
        DropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    leadingIcon = { Icon(action.icon, contentDescription = null) },
                    enabled = action.enabled,
                    onClick = {
                        isExpanded = false
                        action.onClick()
                    },
                )
            }
        }
    }
}

/** A time, "01 : 02 . 345", each part typed in or nudged - see [TimePartControl]. */
@Composable
private fun TimeControls(timeMs: Long, onSetTime: (Long) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LyricsTimePart.entries.forEachIndexed { index, part ->
            if (index > 0) TimeSeparator(if (part == LyricsTimePart.MILLISECONDS) "." else ":")
            TimePartControl(part = part, timeMs = timeMs, onSetTime = onSetTime)
        }
    }
}

/** One [part] of a line's time: typed in, between a button that nudges it a step later and one that nudges it a step earlier. */
@Composable
private fun TimePartControl(part: LyricsTimePart, timeMs: Long, onSetTime: (Long) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = { onSetTime(timeMs + part.stepMs) }) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Later", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TimePartField(
            text = part.of(timeMs),
            digits = part.digits,
            onDone = { value -> onSetTime(part.withValue(timeMs, value)) },
        )
        IconButton(onClick = { onSetTime(timeMs - part.stepMs) }) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Earlier", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * A part of a time, typed in on the number pad: all of it chosen as it is tapped, so typing replaces it, and handed
 * over by [onDone] as the field is left - by the keyboard's done, or a tap elsewhere - where it changed. Left empty,
 * it goes back to what it was.
 */
@Composable
private fun TimePartField(text: String, digits: Int, onDone: (Long) -> Unit) {
    val focusManager = LocalFocusManager.current
    val textStyle = MaterialTheme.typography.titleMedium.copy(
        fontFeatureSettings = TabularFigures,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
    // As wide as every digit it can hold, whatever it holds now - digits all take one width - so the field neither
    // clips its value nor moves as it changes.
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val width = remember(textStyle, digits, density) {
        val digitsWidth = with(density) { textMeasurer.measure("0".repeat(digits), textStyle).size.width.toDp() }
        digitsWidth + TimePartPadding * 2 + TimePartCursorWidth
    }
    var field by remember(text) { mutableStateOf(TextFieldValue(text)) }
    var isFocused by remember { mutableStateOf(false) }
    // A frame after focus, once the tap that gave it has placed its cursor, so the choosing is not undone by it.
    LaunchedEffect(isFocused) {
        if (isFocused) field = field.copy(selection = TextRange(0, field.text.length))
    }
    BasicTextField(
        value = field,
        onValueChange = { if (it.text.length <= digits && it.text.all(Char::isDigit)) field = it },
        textStyle = textStyle,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(if (isFocused) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
                    .padding(horizontal = TimePartPadding),
            ) {
                innerTextField()
            }
        },
        modifier = Modifier
            .width(width)
            .onFocusChanged { focus ->
                if (isFocused && !focus.isFocused) {
                    val value = field.text.toLongOrNull()
                    if (value != null && field.text != text) onDone(value) else field = TextFieldValue(text)
                }
                isFocused = focus.isFocused
            },
    )
}

@Composable
private fun TimeSeparator(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Where lyrics can be brought in from, each taking the lines' place: the web, the clipboard, or a lyrics file. */
@Composable
internal fun AddLyricsSheet(
    onSearch: () -> Unit,
    onPaste: () -> Unit,
    onPickFile: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    SonaBottomSheet(title = "Add lyrics from", onDismissRequest = onDismissRequest) {
        SonaOptionRow(label = "Search lyrics", icon = Icons.Filled.Search, onClick = { onSearch(); dismiss() })
        SonaOptionRow(label = "Paste from clipboard", icon = Icons.Filled.ContentPaste, onClick = { onPaste(); dismiss() })
        SonaOptionRow(label = "Select lyrics file (.lrc)", icon = Icons.Filled.Description, onClick = { onPickFile(); dismiss() })
    }
}

/**
 * The lyrics the web has for the track, as they arrive, best first - the tag editor's own cards. Picking a set has
 * it take the lines' place.
 */
@Composable
internal fun FoundLyricsSheet(
    found: List<FoundLyrics>,
    isSearching: Boolean,
    trackDurationMs: Long,
    onPick: (FoundLyrics) -> Unit,
    onDismissRequest: () -> Unit,
) {
    SonaBottomSheet(title = "Lyrics found", onDismissRequest = onDismissRequest) {
        found.forEach { lyrics ->
            LyricsResultCard(
                found = lyrics,
                trackDurationMs = trackDurationMs,
                isApplied = false,
                onApply = {
                    onPick(lyrics)
                    dismiss()
                },
            )
        }
        LyricsSearchStatus(isSearching = isSearching, hasResults = found.isNotEmpty())
    }
}

/** Writing one line's words. */
@Composable
internal fun LyricsLineTextDialog(initialText: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initialText) }
    val focusRequester = remember { FocusRequester() }
    SonaDialog(
        onDismissRequest = onDismiss,
        title = "Line",
        buttons = {
            actionButton(label = "Cancel", onClick = onDismiss)
            actionButton(label = "Done", onClick = { onDone(text) })
        },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        )
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

/** The lyrics written out in full, as they are kept - LRC, each line at its time - to read, or to change all at once. */
@Composable
internal fun LyricsTextDialog(initialText: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initialText) }
    SonaDialog(
        onDismissRequest = onDismiss,
        title = "Lyrics as text",
        buttons = {
            actionButton(label = "Cancel", onClick = onDismiss)
            actionButton(label = "Done", onClick = { onDone(text) })
        },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().heightIn(max = LyricsTextMaxHeight),
        )
    }
}

/** As tall as the lyrics written out may stand in their dialog, which scrolls them past it. */
private val LyricsTextMaxHeight = 360.dp

/**
 * Moving the times of the selected lines all at once by one amount - later, or earlier with its sign turned - set
 * as a line's own time is.
 */
@Composable
internal fun BatchEditDialog(onDismiss: () -> Unit, onAdjust: (deltaMs: Long) -> Unit) {
    var isEarlier by rememberSaveable { mutableStateOf(false) }
    var offsetMs by rememberSaveable { mutableLongStateOf(0L) }
    SonaDialog(
        onDismissRequest = onDismiss,
        title = "Batch edit",
        buttons = {
            actionButton(label = "Cancel", onClick = onDismiss)
            actionButton(label = "Adjust", onClick = { onAdjust(if (isEarlier) -offsetMs else offsetMs) }, enabled = offsetMs > 0L)
        },
    ) {
        Text("Offset the timestamps of the selected lines.", style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = { isEarlier = !isEarlier }) {
                Text(if (isEarlier) "\u2212" else "+", style = MaterialTheme.typography.titleMedium)
            }
            TimeControls(timeMs = offsetMs, onSetTime = { offsetMs = it.coerceAtLeast(0L) })
        }
    }
}

/** Asked before every line is taken out - which, saved, takes the lyrics out of the file. */
@Composable
internal fun DeleteLyricsDialog(onDismiss: () -> Unit, onDelete: () -> Unit) {
    SonaDialog(
        onDismissRequest = onDismiss,
        title = "Delete embedded lyrics?",
        buttons = {
            actionButton(label = "Cancel", onClick = onDismiss)
            actionButton(label = "Delete", onClick = onDelete)
        },
    ) {
        Text("Every line is taken out. Once saved, the file holds no lyrics.", style = MaterialTheme.typography.bodyMedium)
    }
}

/** Asked before leaving an editor with changes not kept, which leaving would lose - the lyrics editor's and the tag editor's. */
@Composable
internal fun DiscardChangesDialog(onKeepEditing: () -> Unit, onDiscard: () -> Unit) {
    SonaDialog(
        onDismissRequest = onKeepEditing,
        title = "Discard changes?",
        buttons = {
            actionButton(label = "Keep editing", onClick = onKeepEditing)
            actionButton(label = "Discard", onClick = onDiscard)
        },
    ) {
        Text("Changes that have not been kept will be lost.", style = MaterialTheme.typography.bodyMedium)
    }
}
