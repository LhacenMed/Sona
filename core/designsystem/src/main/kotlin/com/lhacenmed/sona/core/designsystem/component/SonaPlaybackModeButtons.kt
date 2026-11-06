package com.lhacenmed.sona.core.designsystem.component

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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.theme.connectedLeadingButtonPressShapes
import com.lhacenmed.sona.core.designsystem.theme.connectedTrailingButtonPressShapes

/**
 * Shuffle and repeat, a connected pair of toggles - the top of a queue, the music's and the videos' alike, each over
 * modes of its own. [repeatIcon] is the repeat mode's own glyph, and [isRepeating] whether it repeats at all;
 * [contentColor] is what they are drawn in, over whatever lies behind them.
 */
@Composable
fun SonaPlaybackModeButtons(
    isShuffled: Boolean,
    isRepeating: Boolean,
    shuffleIcon: Painter,
    repeatIcon: Painter,
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
            Icon(painter = shuffleIcon, contentDescription = shuffleLabel, modifier = Modifier.size(22.dp))
        }

        ToggleButton(
            checked = isRepeating,
            onCheckedChange = { onRepeatClick() },
            modifier = Modifier.weight(1f).size(48.dp),
            shapes = connectedTrailingButtonPressShapes(),
            colors = if (isRepeating) checkedColors else uncheckedColors,
        ) {
            Icon(painter = repeatIcon, contentDescription = repeatLabel, modifier = Modifier.size(22.dp))
        }
    }
}
