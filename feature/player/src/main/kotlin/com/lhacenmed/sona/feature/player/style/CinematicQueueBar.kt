package com.lhacenmed.sona.feature.player.style

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.theme.roundedShape
import com.lhacenmed.sona.feature.player.PlayerPillCornerRadius
import com.lhacenmed.sona.feature.player.PlayerPillHeight
import com.lhacenmed.sona.feature.player.PlayerPillTintAlpha
import com.lhacenmed.sona.feature.player.R
import com.lhacenmed.sona.feature.player.makeTimeString

/**
 * The Cinematic player's queue bar: the queue and the sleep timer, side by side in the player's pills - the
 * timer's showing the time it has left while it runs. Ported from ArchiveTune's `QueueCollapsedContentV4`.
 */
@Composable
internal fun CinematicQueueBar(
    contentColor: Color,
    sleepTimerEnabled: Boolean,
    sleepTimerTimeLeft: Long,
    onExpandQueue: () -> Unit,
    onSleepTimerClick: () -> Unit,
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
