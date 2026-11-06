package com.lhacenmed.sona.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhacenmed.sona.core.designsystem.component.SonaCoverArt
import com.lhacenmed.sona.core.designsystem.component.SonaCoverImage
import com.lhacenmed.sona.core.designsystem.component.SonaPlaybackModeButtons
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialog
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.core.designsystem.theme.iconButtonPressShapes
import com.lhacenmed.sona.core.designsystem.theme.roundedShape
import com.lhacenmed.sona.core.model.RepeatMode
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.iconRes
import com.lhacenmed.sona.feature.playback.labelRes
import com.lhacenmed.sona.feature.playback.R as PlaybackR
import kotlin.math.roundToInt

private const val DefaultSleepTimerMinutes = 30f

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
    backgroundColor: Color,
    onBackgroundColor: Color,
    /** Null for a track that cannot be a favourite, which leaves its button out. */
    onToggleFavorite: (() -> Unit)?,
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
                            .clip(roundedShape(2.5.dp))
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

                if (onToggleFavorite != null) IconButton(
                    onClick = onToggleFavorite,
                    shapes = iconButtonPressShapes(),
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

            SonaPlaybackModeButtons(
                isShuffled = shuffleEnabled,
                isRepeating = repeatMode != RepeatMode.OFF,
                shuffleIcon = painterResource(PlaybackR.drawable.shuffle),
                repeatIcon = painterResource(repeatMode.iconRes),
                shuffleLabel = stringResource(R.string.player_shuffle_on),
                repeatLabel = stringResource(repeatMode.labelRes),
                onShuffleClick = {
                    view.performContextClick()
                    onShuffleClick()
                },
                onRepeatClick = {
                    view.performContextClick()
                    onRepeatClick()
                },
                contentColor = onBackgroundColor,
            )

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
    // Read here rather than inside the group: a group builds its items outside composition.
    val cancelLabel = stringResource(R.string.player_cancel)
    val okLabel = stringResource(R.string.player_ok)

    SonaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.player_sleep_timer),
        icon = { Icon(painterResource(R.drawable.bedtime), contentDescription = null) },
        onReset = { sleepTimerValue = DefaultSleepTimerMinutes },
        buttons = {
            actionButton(label = cancelLabel, onClick = onDismiss)
            actionButton(label = okLabel, onClick = { onConfirm(sleepTimerValue.roundToInt()) })
        },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
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

            OutlinedButton(onClick = onEndOfSong, shapes = buttonPressShapes()) {
                Text(stringResource(R.string.player_end_of_song))
            }
        }
    }
}
