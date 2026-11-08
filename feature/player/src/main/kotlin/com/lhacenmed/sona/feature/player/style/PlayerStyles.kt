package com.lhacenmed.sona.feature.player.style

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
// own sheet, it places their line - the preview, which opens them - and its queue bar offers the way in too, the
// sheet drawing its seek bar, transport and queue bar again in their places. These `when`s are exhaustive, so a
// new PlayerStyle entry does not compile until it has a branch in each.

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
 * [lyrics] pill in it and room made for the lyrics while they are open, where they open in the player; otherwise
 * with [lyricsPreview], their line playing, which opens their sheet.
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
    lyricsPreview: @Composable (Modifier) -> Unit,
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
                lyricsPreview = lyricsPreview,
                onMenuClick = onMenuClick,
                onCollapse = onCollapse,
                onOpenEqualizer = onOpenEqualizer,
                viewModel = viewModel,
                onSliderValueChange = onSliderValueChange,
                onSliderValueChangeFinished = onSliderValueChangeFinished,
            )
    }
}

/** The bar the queue sheet shows while collapsed: the lyrics, the sleep timer and the way into the queue. */
@Composable
internal fun PlayerStyle.QueueBar(
    colors: PlayerColors,
    isLyricsOpen: Boolean,
    sleepTimerEnabled: Boolean,
    sleepTimerTimeLeft: Long,
    onToggleLyrics: () -> Unit,
    onExpandQueue: () -> Unit,
    onSleepTimerClick: () -> Unit,
) {
    when (this) {
        PlayerStyle.DEFAULT, PlayerStyle.IMMERSIVE ->
            DefaultQueueBar(
                colors = colors,
                isLyricsOpen = isLyricsOpen,
                sleepTimerEnabled = sleepTimerEnabled,
                sleepTimerTimeLeft = sleepTimerTimeLeft,
                onToggleLyrics = onToggleLyrics,
                onExpandQueue = onExpandQueue,
                onSleepTimerClick = onSleepTimerClick,
            )
    }
}
