@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.player

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lhacenmed.sona.core.designsystem.component.SonaCoverArt
import com.lhacenmed.sona.core.designsystem.component.SonaCoverImage
import com.lhacenmed.sona.core.model.RepeatMode
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.R as PlaybackR
import kotlin.math.roundToInt

private const val DefaultSleepTimerMinutes = 30f

@DrawableRes
private fun RepeatMode.queueHeaderIconRes(): Int =
    when (this) {
        RepeatMode.OFF -> PlaybackR.drawable.repeat
        RepeatMode.ALL -> PlaybackR.drawable.repeat_on
        RepeatMode.ONE -> PlaybackR.drawable.repeat_one_on
        RepeatMode.STOP_AFTER_CURRENT -> PlaybackR.drawable.repeat_one_stop
    }

/**
 * Current Song Header shown at the top of the queue
 * Displays album art, song info, and control buttons. Ported from ArchiveTune.
 */
@Composable
internal fun CurrentSongHeader(
    sheetState: BottomSheetState,
    track: Track?,
    isFavorite: Boolean,
    repeatMode: RepeatMode,
    shuffleEnabled: Boolean,
    locked: Boolean,
    songCount: Int,
    queueDurationMs: Long,
    backgroundColor: Color,
    onBackgroundColor: Color,
    onToggleFavorite: () -> Unit,
    onMenuClick: () -> Unit,
    onClearQueueClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onLockClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .bottomSheetDraggable(sheetState)
                .padding(horizontal = 16.dp)
                .padding(top = 20.dp, bottom = 8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .width(48.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(onBackgroundColor.copy(alpha = 0.4f)),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SonaCoverImage(
                coverArtUri = track?.coverArtUri,
                contentDescription = null,
                cornerRadius = 12.dp,
                modifier = Modifier.size(64.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = track?.title.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = onBackgroundColor,
                )
                Text(
                    text = track?.artist.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = onBackgroundColor.copy(alpha = 0.6f),
                )
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(44.dp),
                colors =
                    IconButtonDefaults.iconButtonColors(
                        contentColor = if (isFavorite) MaterialTheme.colorScheme.primary else onBackgroundColor,
                    ),
            ) {
                Icon(
                    painter = painterResource(favoriteIconRes(isFavorite)),
                    contentDescription = stringResource(R.string.player_like),
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(onBackgroundColor.copy(alpha = 0.06f))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onLockClick,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = onBackgroundColor.copy(alpha = 0.7f)),
                ) {
                    Icon(
                        painter = painterResource(if (locked) R.drawable.lock else R.drawable.lock_open),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = onBackgroundColor.copy(alpha = 0.7f)),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(
                    onClick = onClearQueueClick,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.delete),
                        contentDescription = stringResource(R.string.player_clear),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Text(
                text =
                    pluralStringResource(R.plurals.player_n_song, songCount, songCount) +
                        "  •  " + makeTimeString(queueDurationMs),
                style = MaterialTheme.typography.labelMedium,
                color = onBackgroundColor.copy(alpha = 0.55f),
                modifier = Modifier.padding(end = 14.dp),
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val uncheckedColors =
                ToggleButtonDefaults.colors(
                    containerColor = onBackgroundColor.copy(alpha = 0.12f),
                    contentColor = onBackgroundColor,
                )
            val checkedColors =
                ToggleButtonDefaults.colors(
                    checkedContainerColor = onBackgroundColor.copy(alpha = 0.22f),
                    checkedContentColor = onBackgroundColor,
                )
            val repeatEnabled = repeatMode != RepeatMode.OFF

            ToggleButton(
                checked = shuffleEnabled,
                onCheckedChange = {
                    view.performContextClick()
                    onShuffleClick()
                },
                modifier = Modifier.weight(1f).size(48.dp),
                shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                colors = if (shuffleEnabled) checkedColors else uncheckedColors,
            ) {
                Icon(
                    painter = painterResource(PlaybackR.drawable.shuffle),
                    contentDescription = stringResource(R.string.player_shuffle_on),
                    modifier = Modifier.size(22.dp),
                )
            }

            ToggleButton(
                checked = repeatEnabled,
                onCheckedChange = {
                    view.performContextClick()
                    onRepeatClick()
                },
                modifier = Modifier.weight(1f).size(48.dp),
                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                colors = if (repeatEnabled) checkedColors else uncheckedColors,
            ) {
                Icon(
                    painter = painterResource(repeatMode.queueHeaderIconRes()),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        HorizontalDivider(
            color = onBackgroundColor.copy(alpha = 0.08f),
            thickness = 1.dp,
        )
    }
}

/** How long until playback pauses, or at the end of the current song. Ported from ArchiveTune. */
@Composable
internal fun SleepTimerDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    onEndOfSong: () -> Unit,
) {
    var sleepTimerValue by remember { mutableFloatStateOf(DefaultSleepTimerMinutes) }

    ActionPromptDialog(
        titleBar = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.player_sleep_timer),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        },
        onDismiss = onDismiss,
        onConfirm = { onConfirm(sleepTimerValue.roundToInt()) },
        onCancel = onDismiss,
        onReset = { sleepTimerValue = DefaultSleepTimerMinutes },
        content = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text =
                        pluralStringResource(
                            R.plurals.player_minute,
                            sleepTimerValue.roundToInt(),
                            sleepTimerValue.roundToInt(),
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                )

                Spacer(Modifier.height(16.dp))

                Slider(
                    value = sleepTimerValue,
                    onValueChange = { sleepTimerValue = it },
                    valueRange = 5f..120f,
                    steps = (120 - 5) / 5 - 1,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                OutlinedButton(onClick = onEndOfSong, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.player_end_of_song))
                }
            }
        },
    )
}

@Composable
private fun ActionPromptDialog(
    titleBar: @Composable RowScope.() -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .imePadding()
                    .navigationBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.heightIn(max = maxHeight),
                shape = AlertDialogDefaults.shape,
                color = AlertDialogDefaults.containerColor,
                tonalElevation = AlertDialogDefaults.TonalElevation,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row {
                            titleBar()
                        }

                        content()
                    }

                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(modifier = Modifier.weight(1f)) {
                            TextButton(
                                onClick = onReset,
                                shapes = ButtonDefaults.shapes(),
                            ) {
                                Text(stringResource(R.string.player_reset))
                            }
                        }

                        TextButton(
                            onClick = onCancel,
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(android.R.string.cancel))
                        }

                        TextButton(
                            onClick = onConfirm,
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(android.R.string.ok))
                        }
                    }
                }
            }
        }
    }
}

/**
 * The Minimal player's queue bar. Ported from ArchiveTune's `QueueCollapsedContentV3`.
 */
@Composable
internal fun MinimalQueueBar(
    textBackgroundColor: Color,
    sleepTimerEnabled: Boolean,
    sleepTimerTimeLeft: Long,
    onExpandQueue: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onShowLyrics: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 10.dp)
                    .windowInsetsPadding(
                        WindowInsets.systemBars.only(
                            WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                        ),
                    ),
        ) {
            // Queue button
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onExpandQueue() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.queue_music),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = textBackgroundColor.copy(alpha = 0.7f),
                    )
                    Text(
                        text = stringResource(id = R.string.player_queue),
                        color = textBackgroundColor.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }
            }

            // Sleep timer button
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSleepTimerClick() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    label = "sleepTimer",
                    targetState = sleepTimerEnabled,
                ) { enabled ->
                    if (enabled) {
                        Text(
                            text = makeTimeString(sleepTimerTimeLeft),
                            color = textBackgroundColor.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.bedtime),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = textBackgroundColor.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            // Lyrics button
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onShowLyrics() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.lyrics),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = textBackgroundColor.copy(alpha = 0.7f),
                    )
                    Text(
                        text = stringResource(id = R.string.player_lyrics),
                        color = textBackgroundColor.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }
            }

            // Menu button
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onMenuClick() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.more_vert),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = textBackgroundColor.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/**
 * The Classic player's queue bar (text buttons). Ported from ArchiveTune's `QueueCollapsedContentV1`.
 */
@Composable
internal fun ClassicQueueBar(
    textBackgroundColor: Color,
    sleepTimerEnabled: Boolean,
    sleepTimerTimeLeft: Long,
    onExpandQueue: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onShowLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 30.dp, vertical = 12.dp)
                    .windowInsetsPadding(
                        WindowInsets.systemBars
                            .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                    ),
        ) {
            TextButton(
                onClick = onExpandQueue,
                modifier = Modifier.weight(1f),
                shapes = ButtonDefaults.shapes(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.queue_music),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = textBackgroundColor,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(id = R.string.player_queue),
                        color = textBackgroundColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.basicMarquee(),
                    )
                }
            }

            TextButton(
                onClick = onSleepTimerClick,
                modifier = Modifier.weight(1.2f),
                shapes = ButtonDefaults.shapes(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.bedtime),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = textBackgroundColor,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    AnimatedContent(
                        label = "sleepTimer",
                        targetState = sleepTimerEnabled,
                    ) { enabled ->
                        Text(
                            text = if (enabled) makeTimeString(sleepTimerTimeLeft) else stringResource(id = R.string.player_sleep_timer),
                            color = textBackgroundColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.basicMarquee(),
                        )
                    }
                }
            }

            TextButton(
                onClick = onShowLyrics,
                modifier = Modifier.weight(1f),
                shapes = ButtonDefaults.shapes(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.lyrics),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = textBackgroundColor,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(id = R.string.player_lyrics),
                        color = textBackgroundColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.basicMarquee(),
                    )
                }
            }
        }
    }
}

/**
 * The Cinematic player's queue bar (pill buttons). Ported from ArchiveTune's `QueueCollapsedContentV4`.
 */
@Composable
internal fun CinematicQueueBar(
    textBackgroundColor: Color,
    sleepTimerEnabled: Boolean,
    sleepTimerTimeLeft: Long,
    onExpandQueue: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onShowLyrics: () -> Unit,
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
            val buttonSize = 48.dp
            val iconSize = 22.dp

            // Queue button (pill)
            Box(
                modifier =
                    Modifier
                        .height(buttonSize)
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(textBackgroundColor.copy(alpha = 0.1f))
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
                        tint = textBackgroundColor,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.player_queue),
                        color = textBackgroundColor,
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
                        .clip(CircleShape)
                        .background(textBackgroundColor.copy(alpha = if (sleepTimerEnabled) 0.2f else 0.1f))
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
                            color = textBackgroundColor,
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
                            tint = textBackgroundColor,
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
                        .clip(RoundedCornerShape(16.dp))
                        .background(textBackgroundColor.copy(alpha = 0.1f))
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
                        tint = textBackgroundColor,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.player_lyrics),
                        color = textBackgroundColor,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * The Immersive and Immersive Extended players' queue bar. Ported from ArchiveTune's `QueueCollapsedContentV7`.
 */
@Composable
internal fun ImmersiveQueueBar(
    textBackgroundColor: Color,
    sleepTimerEnabled: Boolean,
    sleepTimerTimeLeft: Long,
    onExpandQueue: () -> Unit,
    onShowLyrics: () -> Unit,
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
            val iconSize = 22.dp

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = onExpandQueue,
                    shape = CircleShape,
                    color = textBackgroundColor.copy(alpha = 0.08f),
                    modifier = Modifier.size(42.dp),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.queue_music),
                            contentDescription = null,
                            modifier = Modifier.size(iconSize),
                            tint = textBackgroundColor,
                        )
                    }
                }

                Surface(
                    onClick = onShowLyrics,
                    shape = CircleShape,
                    color = textBackgroundColor.copy(alpha = 0.08f),
                    modifier = Modifier.size(42.dp),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.lyrics),
                            contentDescription = null,
                            modifier = Modifier.size(iconSize),
                            tint = textBackgroundColor,
                        )
                    }
                }

                Surface(
                    onClick = onSleepTimerClick,
                    shape = if (sleepTimerEnabled) RoundedCornerShape(20.dp) else CircleShape,
                    color = textBackgroundColor.copy(alpha = if (sleepTimerEnabled) 0.16f else 0.08f),
                    modifier =
                        if (sleepTimerEnabled) {
                            Modifier.height(42.dp)
                        } else {
                            Modifier.size(42.dp)
                        },
                ) {
                    AnimatedContent(
                        label = "immersiveSleepTimer",
                        targetState = sleepTimerEnabled,
                    ) { enabled ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier =
                                Modifier.padding(
                                    start = 10.dp,
                                    end = if (enabled) 12.dp else 10.dp,
                                ),
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.bedtime),
                                contentDescription = stringResource(id = R.string.player_sleep_timer),
                                modifier = Modifier.size(iconSize),
                                tint = textBackgroundColor,
                            )
                            if (enabled) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = makeTimeString(sleepTimerTimeLeft),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = textBackgroundColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The Editorial player's queue bar. Ported from ArchiveTune's `QueueCollapsedContentV9`.
 */
@Composable
internal fun EditorialQueueBar(
    textBackgroundColor: Color,
    sleepTimerEnabled: Boolean,
    sleepTimerTimeLeft: Long,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    onShuffleClick: () -> Unit,
    onRepeatModeClick: () -> Unit,
    onMenuClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val railContainerColor = textBackgroundColor.copy(alpha = 0.14f)
    val buttonContainerColor = textBackgroundColor.copy(alpha = 0.08f)
    val selectedButtonContainerColor = textBackgroundColor.copy(alpha = 0.18f)
    val uncheckedColors =
        ToggleButtonDefaults.colors(
            containerColor = buttonContainerColor,
            contentColor = textBackgroundColor.copy(alpha = 0.76f),
        )
    val checkedColors =
        ToggleButtonDefaults.colors(
            checkedContainerColor = selectedButtonContainerColor,
            checkedContentColor = textBackgroundColor,
            containerColor = buttonContainerColor,
            contentColor = textBackgroundColor.copy(alpha = 0.76f),
        )
    val repeatEnabled = repeatMode != RepeatMode.OFF

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.systemBars.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                    ),
                ).padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (sleepTimerEnabled) {
            Surface(
                onClick = onSleepTimerClick,
                shape = RoundedCornerShape(18.dp),
                color = textBackgroundColor.copy(alpha = 0.08f),
                modifier =
                    Modifier
                        .padding(bottom = 8.dp)
                        .height(34.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.bedtime),
                        contentDescription = stringResource(R.string.player_sleep_timer),
                        tint = textBackgroundColor,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = makeTimeString(sleepTimerTimeLeft),
                        style = MaterialTheme.typography.labelMedium,
                        color = textBackgroundColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(42.dp),
            color = railContainerColor,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 52.dp)
                    .height(72.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                ToggleButton(
                    checked = shuffleEnabled,
                    onCheckedChange = {
                        view.performContextClick()
                        onShuffleClick()
                    },
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(56.dp),
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                    colors = if (shuffleEnabled) checkedColors else uncheckedColors,
                ) {
                    Icon(
                        painter = painterResource(PlaybackR.drawable.shuffle),
                        contentDescription =
                            stringResource(if (shuffleEnabled) R.string.player_shuffle_on else R.string.player_shuffle_off),
                        modifier = Modifier.size(26.dp),
                    )
                }

                ToggleButton(
                    checked = repeatEnabled,
                    onCheckedChange = {
                        view.performContextClick()
                        onRepeatModeClick()
                    },
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(56.dp),
                    shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                    colors = if (repeatEnabled) checkedColors else uncheckedColors,
                ) {
                    Icon(
                        painter = painterResource(repeatMode.transportIconRes()),
                        contentDescription =
                            stringResource(
                                when (repeatMode) {
                                    RepeatMode.ONE, RepeatMode.STOP_AFTER_CURRENT -> R.string.player_repeat_one
                                    RepeatMode.ALL -> R.string.player_repeat_all
                                    RepeatMode.OFF -> R.string.player_repeat_off
                                },
                            ),
                        modifier = Modifier.size(26.dp),
                    )
                }

                Surface(
                    onClick = {
                        view.performContextClick()
                        onMenuClick()
                    },
                    shape =
                        RoundedCornerShape(
                            topStart = 12.dp,
                            bottomStart = 12.dp,
                            topEnd = 34.dp,
                            bottomEnd = 34.dp,
                        ),
                    color = buttonContainerColor,
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(56.dp),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_horiz),
                            contentDescription = stringResource(R.string.player_more_options),
                            tint = textBackgroundColor,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
        }
    }
}

/** One row of the queue: cover with its playing indicator, title, artist and length. Stands in for ArchiveTune's `MediaMetadataListItem`. */
@Composable
internal fun QueueTrackItem(
    track: Track,
    isSelected: Boolean,
    isActive: Boolean,
    isPlaying: Boolean,
    trailingContent: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .height(QueueItemHeight)
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SonaCoverArt(
            coverArtUri = track.coverArtUri,
            contentDescription = null,
            isCurrent = isActive,
            isPlaying = isPlaying,
            isSelected = isSelected,
        )
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${track.artist} • ${makeTimeString(track.durationMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            content = trailingContent,
        )
    }
}

@Composable
internal fun QueueSelectionFloatingToolbar(
    allSelected: Boolean,
    onClose: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier.widthIn(max = 420.dp),
        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(
                onClick = onClose,
                containerColor = colorScheme.surfaceContainerHighest,
                contentColor = colorScheme.onSurface,
            ) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = stringResource(R.string.player_close),
                    modifier = Modifier.size(22.dp),
                )
            }
        },
        colors =
            FloatingToolbarDefaults.standardFloatingToolbarColors(
                toolbarContainerColor = colorScheme.surfaceContainerHigh,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QueueSelectionToolbarAction(
                icon = if (allSelected) R.drawable.deselect else R.drawable.select_all,
                contentDescription = null,
                tint = colorScheme.onSurface,
                onClick = onToggleSelectAll,
            )

            QueueSelectionToolbarAction(
                icon = R.drawable.delete,
                contentDescription = stringResource(R.string.player_delete),
                tint = colorScheme.error,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun QueueSelectionToolbarAction(
    @DrawableRes icon: Int,
    contentDescription: String?,
    tint: Color,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp),
            tint = tint,
        )
    }
}
