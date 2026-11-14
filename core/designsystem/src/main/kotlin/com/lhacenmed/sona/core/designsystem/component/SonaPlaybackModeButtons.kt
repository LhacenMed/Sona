package com.lhacenmed.sona.core.designsystem.component

import com.lhacenmed.sona.core.model.RepeatMode
import com.lhacenmed.sona.core.designsystem.player.transportIcon
import com.lhacenmed.sona.core.designsystem.icon.PlayerIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.theme.connectedLeadingButtonPressShapes
import com.lhacenmed.sona.core.designsystem.theme.connectedTrailingButtonPressShapes

/**
 * Shuffle and repeat, a connected pair of toggles - the top of a queue, the music's and the videos' alike, each over
 * modes of its own, each drawn with the players' glyphs - see [PlayerIcons] - repeat with [repeatMode]'s own;
 * [contentColor] is what they are drawn in, over whatever lies behind them.
 */
@Composable
fun SonaPlaybackModeButtons(
    isShuffled: Boolean,
    repeatMode: RepeatMode,
    shuffleLabel: String,
    repeatLabel: String,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val uncheckedColors =
            ToggleButtonDefaults.colors(
                containerColor = contentColor.copy(alpha = 0.12f),
                contentColor = contentColor,
            )
        val checkedColors =
            ToggleButtonDefaults.colors(
                checkedContainerColor = contentColor.copy(alpha = 0.22f),
                checkedContentColor = contentColor,
            )

        ToggleButton(
            checked = isShuffled,
            onCheckedChange = { onShuffleClick() },
            modifier = Modifier.weight(1f).size(48.dp),
            shapes = connectedLeadingButtonPressShapes(),
            colors = if (isShuffled) checkedColors else uncheckedColors,
        ) {
            Icon(PlayerIcons.Shuffle, contentDescription = shuffleLabel, modifier = Modifier.size(22.dp))
        }

        ToggleButton(
            checked = repeatMode != RepeatMode.OFF,
            onCheckedChange = { onRepeatClick() },
            modifier = Modifier.weight(1f).size(48.dp),
            shapes = connectedTrailingButtonPressShapes(),
            colors = if (repeatMode != RepeatMode.OFF) checkedColors else uncheckedColors,
        ) {
            Icon(repeatMode.transportIcon(), contentDescription = repeatLabel, modifier = Modifier.size(22.dp))
        }
    }
}
