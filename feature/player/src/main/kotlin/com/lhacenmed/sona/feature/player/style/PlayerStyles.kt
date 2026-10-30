package com.lhacenmed.sona.feature.player.style

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.lhacenmed.sona.core.datastore.PlayerAppearance
import com.lhacenmed.sona.core.datastore.PlayerStyle
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.player.LyricsPresentation
import com.lhacenmed.sona.feature.player.PlayerLyricsState
import com.lhacenmed.sona.feature.player.PlayerTitleActions
import com.lhacenmed.sona.feature.player.PlayerUiState
import com.lhacenmed.sona.feature.player.PlayerViewModel
import com.lhacenmed.sona.feature.player.background.PlayerColors

// The one place a player style is looked up. Each style draws three things - the sheet behind it, the
// expanded player, and the queue bar along its bottom - and says where its lyrics open; the rest of the
// player (mini player, queue, lyrics, gestures, seeking) is shared by every style. Where the lyrics open in
// the player, a style places their pill and makes way for the lyrics it opens into; where they open in their
// own sheet, its queue bar offers the way into it. These `when`s are exhaustive, so a new PlayerStyle entry
// does not compile until it has a branch in each.

/** Where the style's lyrics open. */
internal val PlayerStyle.lyricsPresentation: LyricsPresentation
    get() =
        when (this) {
            PlayerStyle.DEFAULT -> LyricsPresentation.SHEET
            PlayerStyle.IMMERSIVE -> LyricsPresentation.IN_PLAYER
        }

/** The colour of the sheet the expanded player is drawn on. */
@Composable
internal fun PlayerStyle.sheetColor(): Color =
    when (this) {
        PlayerStyle.DEFAULT, PlayerStyle.IMMERSIVE -> DefaultPlayerColors.sheet
    }

/**
 * The expanded player above the queue bar, leaving [queueBarHeight] clear at its bottom for it - with the
 * [lyrics] pill in it and room made for the lyrics while they are open, where they open in the player.
 */
@Composable
internal fun PlayerStyle.ExpandedPlayer(
    track: Track,
    uiState: PlayerUiState,
    appearance: PlayerAppearance,
    colors: PlayerColors,
    isLoading: Boolean,
    isPlayerExpanded: Boolean,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    queueBarHeight: Dp,
    titleActions: PlayerTitleActions,
    lyrics: PlayerLyricsState,
    onMenuClick: () -> Unit,
    onCollapse: () -> Unit,
    onOpenEqualizer: () -> Unit,
    viewModel: PlayerViewModel,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
) {
    when (this) {
        PlayerStyle.DEFAULT, PlayerStyle.IMMERSIVE ->
            DefaultPlayer(
                track = track,
                uiState = uiState,
                appearance = appearance,
                colors = colors,
                isLoading = isLoading,
                isPlayerExpanded = isPlayerExpanded,
                sliderPosition = sliderPosition,
                position = position,
                duration = duration,
                queueBarHeight = queueBarHeight,
                titleActions = titleActions,
                lyrics = lyrics,
                onMenuClick = onMenuClick,
                onCollapse = onCollapse,
                onOpenEqualizer = onOpenEqualizer,
                viewModel = viewModel,
                onSliderValueChange = onSliderValueChange,
                onSliderValueChangeFinished = onSliderValueChangeFinished,
            )
    }
}

/**
 * The bar the queue sheet shows while collapsed: the way into the queue, the sleep timer and - with
 * [onShowLyrics], for lyrics that open in their own sheet - the lyrics.
 */
@Composable
internal fun PlayerStyle.QueueBar(
    contentColor: Color,
    sleepTimerEnabled: Boolean,
    sleepTimerTimeLeft: Long,
    onExpandQueue: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onShowLyrics: (() -> Unit)?,
) {
    when (this) {
        PlayerStyle.DEFAULT, PlayerStyle.IMMERSIVE ->
            DefaultQueueBar(
                contentColor = contentColor,
                sleepTimerEnabled = sleepTimerEnabled,
                sleepTimerTimeLeft = sleepTimerTimeLeft,
                onExpandQueue = onExpandQueue,
                onSleepTimerClick = onSleepTimerClick,
                onShowLyrics = onShowLyrics,
            )
    }
}
