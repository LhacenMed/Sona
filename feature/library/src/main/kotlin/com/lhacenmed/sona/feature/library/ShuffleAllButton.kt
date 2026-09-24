package com.lhacenmed.sona.feature.library

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.component.SonaFloatingActionButton
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons

/** How far up the button reaches from where it sits: Material's FAB container height. */
private val ShuffleAllButtonHeight = 56.dp

/**
 * Whether [listState]'s last row has come down to [ShuffleAllButton] - the list scrolled to or near its
 * end, or too short to scroll with its rows reaching that far. The button then steps aside rather than
 * cover the row, so a list needs no room of its own for it and ends where every list does, just above
 * the player. A short list whose rows end above the button keeps it.
 *
 * The button sits on the list's end padding, so it covers the [ShuffleAllButtonHeight] above where that
 * padding starts. Only a change of the answer is read from here, not every frame of a scroll.
 */
@Composable
internal fun rememberLastRowReachesShuffleAllButton(listState: LazyListState): State<Boolean> {
    val buttonHeightPx = with(LocalDensity.current) { ShuffleAllButtonHeight.toPx() }
    return remember(listState, buttonHeightPx) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastRow = layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            val buttonTop = layoutInfo.viewportSize.height - layoutInfo.afterContentPadding - buttonHeightPx
            lastRow.index == layoutInfo.totalItemsCount - 1 && lastRow.offset + lastRow.size > buttonTop
        }
    }
}

/** The library's button for shuffling every track - Auxio's home shuffle FAB. */
@Composable
internal fun ShuffleAllButton(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SonaFloatingActionButton(
        onClick = onClick,
        icon = SonaIcons.Shuffle,
        contentDescription = "Shuffle all",
        visible = visible,
        modifier = modifier,
    )
}
