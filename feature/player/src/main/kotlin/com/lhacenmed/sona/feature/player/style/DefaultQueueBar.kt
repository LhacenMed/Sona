@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.player.style

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.lhacenmed.sona.core.designsystem.icon.PlayerIcons
import com.lhacenmed.sona.core.designsystem.player.PlayerBarButton
import com.lhacenmed.sona.core.designsystem.player.PlayerButtonGroup
import com.lhacenmed.sona.core.designsystem.player.formatPlaybackTime
import com.lhacenmed.sona.core.designsystem.player.playerButton
import com.lhacenmed.sona.core.designsystem.player.playerTextButton
import com.lhacenmed.sona.feature.player.PlayerBarButtonSize
import com.lhacenmed.sona.feature.player.PlayerHorizontalPadding
import com.lhacenmed.sona.feature.player.QueuePeekHeight
import com.lhacenmed.sona.feature.player.R
import com.lhacenmed.sona.feature.player.background.PlayerColors

/**
 * The default player's bottom row, which the queue sheet is dragged up by: the queue at its start, and the sleep timer and
 * the lyrics - on while they are open - together at its end. The sleep timer says the time it has left while it runs.
 */
@Composable
internal fun DefaultQueueBar(
    colors: PlayerColors,
    isLyricsOpen: Boolean,
    sleepTimerEnabled: Boolean,
    sleepTimerTimeLeft: Long,
    onToggleLyrics: () -> Unit,
    onExpandQueue: () -> Unit,
    onSleepTimerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The bar's buttons are their own touch targets: Material would otherwise pad each out past the row's keyline.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides PlayerBarButtonSize) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .padding(horizontal = PlayerHorizontalPadding, vertical = (QueuePeekHeight - PlayerBarButtonSize) / 2)
                    .height(PlayerBarButtonSize),
        ) {
            PlayerBarButton(PlayerIcons.Queue, stringResource(R.string.player_queue), colors.content, onExpandQueue)
            Spacer(Modifier.weight(1f))
            val sleepTimerLabel = stringResource(R.string.player_sleep_timer)
            val lyricsLabel = stringResource(R.string.player_lyrics)
            PlayerButtonGroup {
                // Running, the time it has left in its button's place, on - tapped, it stops.
                if (sleepTimerEnabled) {
                    playerTextButton(formatPlaybackTime(sleepTimerTimeLeft), sleepTimerLabel, colors.content, checked = true, onClick = onSleepTimerClick)
                } else {
                    playerButton(PlayerIcons.SleepTimer, sleepTimerLabel, colors.content, onSleepTimerClick)
                }
                playerButton(PlayerIcons.Lyrics, lyricsLabel, colors.content, onToggleLyrics, checked = isLyricsOpen)
            }
        }
    }
}
