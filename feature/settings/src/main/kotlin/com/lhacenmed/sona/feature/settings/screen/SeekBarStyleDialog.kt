@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.settings.screen

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lhacenmed.sona.core.datastore.PlayerSliderStyle
import com.lhacenmed.sona.feature.player.StyledPlaybackSlider
import com.lhacenmed.sona.feature.settings.R

/**
 * The seek bar styles side by side, each a live slider to try before choosing, the current one outlined.
 * Ported from ArchiveTune's slider style dialog.
 */
@Composable
internal fun SeekBarStyleDialog(
    selectedStyle: PlayerSliderStyle,
    onSelect: (PlayerSliderStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    DefaultDialog(
        buttons = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
        onDismiss = onDismiss,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlayerSliderStyle.entries.chunked(3).forEach { styleRow ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    styleRow.forEach { style ->
                        SliderStyleOptionCard(
                            sliderStyle = style,
                            selected = selectedStyle == style,
                            onClick = {
                                onSelect(style)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(3 - styleRow.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
internal fun seekBarStyleLabel(sliderStyle: PlayerSliderStyle): String =
    when (sliderStyle) {
        PlayerSliderStyle.STANDARD -> stringResource(R.string.player_slider_style_standard)
        PlayerSliderStyle.CIRCULAR -> stringResource(R.string.player_slider_style_circular)
    }

@Composable
private fun SliderStyleOptionCard(
    sliderStyle: PlayerSliderStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sliderValue by remember {
        mutableFloatStateOf(0.5f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(16.dp),
                ).clickable(onClick = onClick)
                .padding(16.dp),
    ) {
        StyledPlaybackSlider(
            sliderStyle = sliderStyle,
            value = sliderValue,
            valueRange = 0f..1f,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {},
            activeColor = MaterialTheme.colorScheme.primary,
            isPlaying = true,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
        )

        Text(
            text = seekBarStyleLabel(sliderStyle),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** ArchiveTune's `DefaultDialog`, as this dialog uses it: a content column above a row of buttons. */
@Composable
private fun DefaultDialog(
    onDismiss: () -> Unit,
    buttons: @Composable RowScope.() -> Unit,
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        content()
                    }

                    Spacer(Modifier.height(24.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth(),
                    ) flowRowScope@{
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                            ProvideTextStyle(
                                value = MaterialTheme.typography.labelLarge,
                            ) {
                                this@flowRowScope.buttons()
                            }
                        }
                    }
                }
            }
        }
    }
}
