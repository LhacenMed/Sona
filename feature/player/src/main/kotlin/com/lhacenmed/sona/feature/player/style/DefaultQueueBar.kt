package com.lhacenmed.sona.feature.player.style

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.theme.pillShape
import com.lhacenmed.sona.core.designsystem.theme.roundedShape
import com.lhacenmed.sona.feature.player.PlayerPillCornerRadius
import com.lhacenmed.sona.feature.player.PlayerPillHeight
import com.lhacenmed.sona.feature.player.PlayerPillTintAlpha
import com.lhacenmed.sona.feature.player.R
import com.lhacenmed.sona.feature.player.makeTimeString

/**
 * The default player's queue bar. Ported from ArchiveTune's `QueueCollapsedContentV4`.
 *
 * With [onShowLyrics], for lyrics that open in their own sheet, the queue and lyrics pills sit either side of a
 * round sleep timer. Without it - lyrics in the player have their own pill - the queue and the sleep timer are
 * side by side in the player's pills, the timer's showing the time it has left while it runs.
 */
@Composable
internal fun DefaultQueueBar(
    contentColor: Color,
    sleepTimerEnabled: Boolean,
    sleepTimerTimeLeft: Long,
    onExpandQueue: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onShowLyrics: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .windowInsetsPadding(
                        WindowInsets.systemBars.only(
                            WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                        ),
                    ),
        ) {
            QueueBarPill(
                iconRes = R.drawable.queue_music,
                label = stringResource(R.string.player_queue),
                contentColor = contentColor,
                onClick = onExpandQueue,
            )

            Spacer(modifier = Modifier.width(10.dp))

            if (onShowLyrics == null) {
                AnimatedContent(
                    label = "sleepTimer",
                    targetState = sleepTimerEnabled,
                    modifier = Modifier.weight(1f),
                ) { enabled ->
                    Row {
                        QueueBarPill(
                            iconRes = R.drawable.bedtime,
                            label = if (enabled) makeTimeString(sleepTimerTimeLeft) else stringResource(R.string.player_sleep_timer),
                            contentColor = contentColor,
                            isActive = enabled,
                            onClick = onSleepTimerClick,
                        )
                    }
                }
            } else {
                QueueBarSleepTimerButton(
                    contentColor = contentColor,
                    sleepTimerEnabled = sleepTimerEnabled,
                    sleepTimerTimeLeft = sleepTimerTimeLeft,
                    onClick = onSleepTimerClick,
                )

                Spacer(modifier = Modifier.width(10.dp))

                QueueBarPill(
                    iconRes = R.drawable.lyrics,
                    label = stringResource(R.string.player_lyrics),
                    contentColor = contentColor,
                    onClick = onShowLyrics,
                )
            }
        }
    }
}

/** The sleep timer as a round button between two pills: its icon, or the time it has left while it runs. */
@Composable
private fun QueueBarSleepTimerButton(
    contentColor: Color,
    sleepTimerEnabled: Boolean,
    sleepTimerTimeLeft: Long,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(PlayerPillHeight)
                .clip(pillShape)
                .background(contentColor.copy(alpha = if (sleepTimerEnabled) PlayerPillTintAlpha * 2 else PlayerPillTintAlpha))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            label = "sleepTimer",
            targetState = sleepTimerEnabled,
        ) { enabled ->
            if (enabled) {
                Text(
                    text = makeTimeString(sleepTimerTimeLeft),
                    color = contentColor,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .basicMarquee(),
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.bedtime),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = contentColor,
                )
            }
        }
    }
}

/** One of the bar's pills: an icon beside its label, a shade brighter while what it stands for is on. */
@Composable
private fun RowScope.QueueBarPill(
    @DrawableRes iconRes: Int,
    label: String,
    contentColor: Color,
    onClick: () -> Unit,
    isActive: Boolean = false,
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .weight(1f)
                .height(PlayerPillHeight)
                .clip(roundedShape(PlayerPillCornerRadius))
                .background(contentColor.copy(alpha = if (isActive) PlayerPillTintAlpha * 2 else PlayerPillTintAlpha))
                .clickable(onClick = onClick),
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = contentColor,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}
