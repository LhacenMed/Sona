package com.lhacenmed.sona.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.component.swipe.LocalSwipeActions
import com.lhacenmed.sona.core.designsystem.component.swipe.SwipeActionsBox
import com.lhacenmed.sona.core.designsystem.theme.SonaComponentStyle
import com.lhacenmed.sona.core.model.Track

/** How much of the selection colour washes over a selected row. */
private const val SELECTED_ROW_TINT_ALPHA = 0.12f

/** A row takes the selection colour faster than it lets it go, so selecting reads as the deliberate act. */
private const val SELECTED_ROW_FADE_IN_MILLIS = 200
private const val SELECTED_ROW_FADE_OUT_MILLIS = 100

/**
 * The slot a row's trailing glyph - a drag handle, a pin - is centred in: Auxio's `size_touchable_small`,
 * the touch target the options button's glyph sits in too, so every trailing glyph lines up with it.
 */
private val TrailingSlotSize = 48.dp

/**
 * The drag handle a reorderable list hands its rows, or null in a list whose rows do not move.
 *
 * A row is drawn the same wherever it is listed, so it is told through the composition rather than
 * through a parameter every list without a handle would have to pass.
 */
val LocalDragHandle = compositionLocalOf<Modifier?> { null }

/**
 * A row that joins whatever selection is running: once one is, tapping it selects rather than opens.
 *
 * In a list with a [DragSelection], a long press selects the row and starts a drag across its
 * neighbours, and the row tells the list where it is laid out; elsewhere a long press toggles it.
 */
fun Modifier.selectableRow(
    selection: SelectionState,
    selectionKey: Any?,
    dragSelection: DragSelection?,
    onClick: () -> Unit,
): Modifier = combinedClickable(
    onClick = {
        dragSelection?.onRowTap()
        when {
            !selection.isActive -> onClick()
            selectionKey != null -> selection.toggle(selectionKey)
        }
    },
    onLongClick = {
        when {
            selectionKey == null -> Unit
            dragSelection != null -> dragSelection.onRowLongPress(selectionKey)
            else -> selection.toggle(selectionKey)
        }
    },
).then(
    if (dragSelection != null && selectionKey != null) Modifier.dragSelectableRow(dragSelection, selectionKey) else Modifier,
)

/**
 * The one row every list in the app is built from - the library's tracks, albums, artists, genres,
 * folders and playlists, and the player's queue - so the same thing looks and behaves the same
 * wherever it is listed.
 *
 * [cover] draws whatever stands for the row, told whether it is selected. [onOpenOptions] is null for
 * a row with no options of its own, which then draws no options button; [selection] is null for a list
 * that cannot be selected from, whose rows are plainly clickable. [isPinned] marks a row held at the top
 * of its list, with a pin where an options button would be.
 *
 * [containerColor] is what the row is painted on - the surface of whatever is listing it, so a row in
 * a sheet sits on the sheet's own colour rather than cutting a hole in it. [contentColor] is what reads
 * against it, taken from whatever is listing the row unless it is given one.
 */
@Composable
fun SonaListRow(
    title: String,
    subtitle: String,
    selection: SelectionState?,
    selectionKey: Any?,
    onClick: () -> Unit,
    onOpenOptions: (() -> Unit)?,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    isPinned: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = LocalContentColor.current,
    cover: @Composable (isSelected: Boolean) -> Unit,
) {
    val isSelected = selectionKey != null && selection?.isSelected(selectionKey) == true
    // The fade is animated rather than the colour, and read only when drawing: an animated colour would
    // trail behind every theme transition, and reading it here would recompose the row every frame.
    val selectedFraction = animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isSelected) SELECTED_ROW_FADE_IN_MILLIS else SELECTED_ROW_FADE_OUT_MILLIS,
        ),
        label = "listRowSelection",
    )
    val selectedTint = MaterialTheme.colorScheme.primary
    val dragHandle = LocalDragHandle.current
    val dragSelection = LocalDragSelection.current
    // A row being selected is not swiped: a sideways swipe over a selection would act on one row of it.
    val swipeActions = LocalSwipeActions.current?.takeUnless { selection?.isActive == true }
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        SwipeActionsBox(actions = swipeActions, modifier = modifier) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(containerColor)
                    .drawBehind {
                        drawRect(selectedTint.copy(alpha = SELECTED_ROW_TINT_ALPHA * selectedFraction.value))
                    }
                    .then(
                        if (selection == null) {
                            Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                        } else {
                            Modifier.selectableRow(selection, selectionKey, dragSelection, onClick)
                        },
                    )
                    .padding(
                        start = SonaComponentStyle.ContentHorizontalPadding,
                        top = 12.dp,
                        // The overflow glyph, not its 48dp touch target, ends on the keyline: the target
                        // holds the glyph 12dp in from its edge.
                        end = SonaComponentStyle.ContentHorizontalPadding - 12.dp,
                        bottom = 12.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                cover(isSelected)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp, end = 12.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (dragHandle != null) {
                    Box(
                        modifier = Modifier
                            .size(TrailingSlotSize)
                            .then(dragHandle),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DragHandle,
                            contentDescription = "Reorder",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (isPinned) {
                    Box(modifier = Modifier.size(TrailingSlotSize), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (onOpenOptions != null) {
                    SonaIconButton(
                        onClick = onOpenOptions,
                        icon = Icons.Filled.MoreHoriz,
                        contentDescription = "More options",
                    )
                }
            }
        }
    }
}

/**
 * A track row: cover art, title over "artist - album" - the library's lists and the player's queue
 * alike.
 *
 * [isCurrent] and [isPlaying] are lambdas, not values, on purpose. Passed as `Boolean`s, every row in
 * the list recomposes whenever the playing track changes, because each row's parameters changed.
 * Read inside the row's own composition, only the row that was marked and the row that now is do any
 * work.
 */
@Composable
fun SonaTrackRow(
    track: Track,
    isCurrent: () -> Boolean,
    isPlaying: () -> Boolean,
    selection: SelectionState?,
    selectionKey: Any?,
    onClick: () -> Unit,
    onOpenOptions: (() -> Unit)?,
    modifier: Modifier = Modifier,
    // Null for the usual "artist - album"; a list whose rows share one of the two names the other.
    subtitle: String? = null,
    onLongClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = LocalContentColor.current,
) {
    val current = isCurrent()
    SonaListRow(
        title = track.title,
        subtitle = subtitle ?: "${track.artist} - ${track.album}",
        selection = selection,
        selectionKey = selectionKey,
        onClick = onClick,
        onOpenOptions = onOpenOptions,
        modifier = modifier,
        isCurrent = current,
        onLongClick = onLongClick,
        containerColor = containerColor,
        contentColor = contentColor,
    ) { isSelected ->
        SonaCoverArt(
            coverArtUri = track.coverArtUri,
            contentDescription = null,
            isCurrent = current,
            isPlaying = isPlaying(),
            isSelected = isSelected,
        )
    }
}
