package com.lhacenmed.sona.feature.player

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
    // Whether the queue is open for reordering, which the header offers the way out of.
    isReordering: Boolean,
    backgroundColor: Color,
    onBackgroundColor: Color,
    onToggleFavorite: () -> Unit,
    onExitReorder: () -> Unit,
    onRepeatClick: () -> Unit,
    onShuffleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .bottomSheetDraggable(sheetState),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 20.dp)) {
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

                // The way out of reordering, beside the button it borrows its size from - the same
                // exit system back gives, for a mode a gesture opened and nothing else announces.
                if (isReordering) {
                    IconButton(
                        onClick = onExitReorder,
                        modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.iconButtonColors(contentColor = onBackgroundColor),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = stringResource(R.string.player_close),
                            modifier = Modifier.size(26.dp),
                        )
                    }
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
        }

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
