package com.lhacenmed.sona.feature.player.style

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.theme.pillShape
import com.lhacenmed.sona.core.designsystem.theme.roundedShape
import com.lhacenmed.sona.feature.player.R
import com.lhacenmed.sona.feature.player.makeTimeString

/**
 * The Default player's queue bar: queue and lyrics pills either side of the sleep timer. Ported from
 * ArchiveTune's `QueueCollapsedContentV4`.
 */
@Composable
internal fun DefaultQueueBar(
    sleepTimerEnabled: Boolean,
    sleepTimerTimeLeft: Long,
    onExpandQueue: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onShowLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = DefaultPlayerColors.content

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
            val buttonSize = 48.dp
            val iconSize = 22.dp

            // Queue button (pill)
            Box(
                modifier =
                    Modifier
                        .height(buttonSize)
                        .weight(1f)
                        .clip(roundedShape(16.dp))
                        .background(contentColor.copy(alpha = 0.1f))
                        .clickable { onExpandQueue() },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.queue_music),
                        contentDescription = null,
                        modifier = Modifier.size(iconSize),
                        tint = contentColor,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.player_queue),
                        color = contentColor,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Sleep timer button (circle)
            Box(
                modifier =
                    Modifier
                        .size(buttonSize)
                        .clip(pillShape)
                        .background(contentColor.copy(alpha = if (sleepTimerEnabled) 0.2f else 0.1f))
                        .clickable { onSleepTimerClick() },
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
                            modifier = Modifier.size(iconSize),
                            tint = contentColor,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Lyrics button (pill)
            Box(
                modifier =
                    Modifier
                        .height(buttonSize)
                        .weight(1f)
                        .clip(roundedShape(16.dp))
                        .background(contentColor.copy(alpha = 0.1f))
                        .clickable { onShowLyrics() },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.lyrics),
                        contentDescription = null,
                        modifier = Modifier.size(iconSize),
                        tint = contentColor,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.player_lyrics),
                        color = contentColor,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
