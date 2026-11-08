@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.player.style

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhacenmed.sona.core.designsystem.icon.PlayerIcons
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.feature.player.PlayerBarButton
import com.lhacenmed.sona.feature.player.PlayerBarButtonSize
import com.lhacenmed.sona.feature.player.PlayerButtonRowPadding
import com.lhacenmed.sona.feature.player.QueuePeekHeight
import com.lhacenmed.sona.feature.player.R
import com.lhacenmed.sona.feature.player.background.PlayerColors
import com.lhacenmed.sona.feature.player.makeTimeString

/**
 * The default player's bottom row, which the queue sheet is dragged up by: the lyrics - lit while they are open - at its
 * start, and the sleep timer and the queue at its end. The sleep timer says the time it has left while it runs.
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
                    .padding(horizontal = PlayerButtonRowPadding, vertical = (QueuePeekHeight - PlayerBarButtonSize) / 2)
                    .height(PlayerBarButtonSize),
        ) {
            PlayerBarButton(
                icon = PlayerIcons.Lyrics,
                contentDescription = stringResource(R.string.player_lyrics),
                color = if (isLyricsOpen) colors.button else colors.content,
                onClick = onToggleLyrics,
                isLit = isLyricsOpen,
            )
            Spacer(Modifier.weight(1f))
            AnimatedContent(targetState = sleepTimerEnabled, label = "sleepTimer") { enabled ->
                if (enabled) {
                    SleepTimerTimeLeft(timeLeftMs = sleepTimerTimeLeft, colors = colors, onClick = onSleepTimerClick)
                } else {
                    PlayerBarButton(PlayerIcons.SleepTimer, stringResource(R.string.player_sleep_timer), colors.content, onSleepTimerClick)
                }
            }
            PlayerBarButton(PlayerIcons.Queue, stringResource(R.string.player_queue), colors.content, onExpandQueue)
        }
    }
}

/** A running sleep timer in its button's place: the time it has left, lit - tapped, it stops. */
@Composable
private fun SleepTimerTimeLeft(timeLeftMs: Long, colors: PlayerColors, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        shapes = buttonPressShapes(),
        colors = ButtonDefaults.textButtonColors(contentColor = colors.button),
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = Modifier.heightIn(min = PlayerBarButtonSize).widthIn(min = PlayerBarButtonSize),
    ) {
        Text(
            text = makeTimeString(timeLeftMs),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
            maxLines = 1,
        )
    }
}
