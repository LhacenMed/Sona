package com.lhacenmed.sona.feature.player.style

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.lhacenmed.sona.core.datastore.PlayerSliderStyle
import com.lhacenmed.sona.core.datastore.PlayerStyle
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.player.PlayerTitleActions
import com.lhacenmed.sona.feature.player.PlayerUiState
import com.lhacenmed.sona.feature.player.PlayerViewModel

// The one place a player style is looked up. Each style draws three things - the sheet behind it, the
// expanded player, and the queue bar along its bottom - and the rest of the player (mini player,
// queue, lyrics, gestures, seeking) is shared by every style. These `when`s are exhaustive, so a new
// PlayerStyle entry does not compile until it has a branch in each.

/** The colour of the sheet the expanded player is drawn on. */
@Composable
internal fun PlayerStyle.sheetColor(): Color =
    when (this) {
        PlayerStyle.DEFAULT -> DefaultPlayerColors.sheet
    }

/** The expanded player above the queue bar, leaving [queueBarHeight] clear at its bottom for it. */
@Composable
internal fun PlayerStyle.ExpandedPlayer(
    track: Track,
    uiState: PlayerUiState,
    sliderStyle: PlayerSliderStyle,
    isLoading: Boolean,
    isPlayerExpanded: Boolean,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    queueBarHeight: Dp,
    titleActions: PlayerTitleActions,
    onMenuClick: () -> Unit,
    viewModel: PlayerViewModel,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
) {
    when (this) {
        PlayerStyle.DEFAULT ->
            DefaultPlayer(
                track = track,
                uiState = uiState,
                sliderStyle = sliderStyle,
                isLoading = isLoading,
                isPlayerExpanded = isPlayerExpanded,
                sliderPosition = sliderPosition,
                position = position,
                duration = duration,
                queueBarHeight = queueBarHeight,
                titleActions = titleActions,
                onMenuClick = onMenuClick,
                viewModel = viewModel,
                onSliderValueChange = onSliderValueChange,
                onSliderValueChangeFinished = onSliderValueChangeFinished,
            )
    }
}

/** The bar the queue sheet shows while collapsed: the way into the queue, the sleep timer and lyrics. */
@Composable
internal fun PlayerStyle.QueueBar(
    sleepTimerEnabled: Boolean,
    sleepTimerTimeLeft: Long,
    onExpandQueue: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onShowLyrics: () -> Unit,
) {
    when (this) {
        PlayerStyle.DEFAULT ->
            DefaultQueueBar(
                sleepTimerEnabled = sleepTimerEnabled,
                sleepTimerTimeLeft = sleepTimerTimeLeft,
                onExpandQueue = onExpandQueue,
                onSleepTimerClick = onSleepTimerClick,
                onShowLyrics = onShowLyrics,
            )
    }
}
