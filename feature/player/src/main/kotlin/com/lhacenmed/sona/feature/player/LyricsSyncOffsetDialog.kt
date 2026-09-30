package com.lhacenmed.sona.feature.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialog
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialogOption
import kotlin.math.roundToInt

/** How far the slider reaches either way, and the steps it moves in - fine enough to line lyrics up by ear. */
private const val SliderLimitMs = 1000
private const val SliderStepMs = 25

/** How far a typed offset reaches either way: a minute, past which no lyrics belong to the track at all. */
private const val CustomLimitMs = 60_000

/** The most a typed offset can hold: a sign, and as many digits as [CustomLimitMs] has. */
private val CustomMaxLength = 1 + CustomLimitMs.toString().length

/**
 * Shifts the lyrics against the audio: on the slider, by up to a second either way in 25 ms steps - or on
 * Custom, by exactly the milliseconds typed, up to a minute either way, with the slider held still. It opens
 * on Custom for an offset the slider cannot show, and switching carries the offset across, the slider taking
 * the nearest step it has. Only digits and a leading sign can be typed, and OK is offered only for an offset
 * that can be applied - out of range or half-typed, the field says what it takes instead.
 */
@Composable
internal fun LyricsSyncOffsetDialog(
    lyricsSyncOffset: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var isCustom by rememberSaveable { mutableStateOf(!lyricsSyncOffset.isSliderOffset()) }
    var sliderOffset by rememberSaveable { mutableIntStateOf(lyricsSyncOffset.toSliderOffset()) }
    var typedOffset by rememberSaveable { mutableStateOf(lyricsSyncOffset.toString()) }
    val customOffset = typedOffset.toCustomOffsetOrNull()
    val offset = if (isCustom) customOffset else sliderOffset

    fun useCustom(custom: Boolean) {
        if (custom == isCustom) return
        if (custom) typedOffset = sliderOffset.toString() else customOffset?.let { sliderOffset = it.toSliderOffset() }
        isCustom = custom
    }

    // Read here rather than inside the group: a group builds its items outside composition.
    val cancelLabel = stringResource(R.string.player_cancel)
    val okLabel = stringResource(R.string.player_ok)

    SonaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.player_lyrics_sync_offset),
        icon = { Icon(Icons.Filled.Speed, contentDescription = null) },
        onReset = {
            sliderOffset = 0
            typedOffset = "0"
        },
        buttons = {
            actionButton(label = cancelLabel, onClick = onDismiss)
            actionButton(label = okLabel, enabled = offset != null, onClick = { offset?.let(onConfirm) })
        },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = offset?.let(::formatLyricsSyncOffset) ?: "–",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
                SonaDialogOption(
                    label = stringResource(R.string.player_lyrics_sync_offset_slider),
                    selected = !isCustom,
                    onClick = { useCustom(false) },
                )
                Slider(
                    value = sliderOffset.toFloat(),
                    onValueChange = { sliderOffset = it.toSliderOffset() },
                    valueRange = -SliderLimitMs.toFloat()..SliderLimitMs.toFloat(),
                    steps = 2 * SliderLimitMs / SliderStepMs - 1,
                    enabled = !isCustom,
                    modifier = Modifier.fillMaxWidth(),
                )
                SonaDialogOption(
                    label = stringResource(R.string.player_lyrics_sync_offset_custom),
                    selected = isCustom,
                    onClick = { useCustom(true) },
                )
            }
            OutlinedTextField(
                value = typedOffset,
                onValueChange = { typedOffset = it.asOffsetInput() },
                enabled = isCustom,
                singleLine = true,
                isError = isCustom && customOffset == null,
                label = { Text(stringResource(R.string.player_lyrics_sync_offset_milliseconds)) },
                suffix = { Text("ms") },
                supportingText = {
                    Text(stringResource(R.string.player_lyrics_sync_offset_custom_range, -CustomLimitMs, CustomLimitMs))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { customOffset?.let(onConfirm) }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** "+250 ms", "-75 ms", "0 ms": an offset as the menu and the dialog show it. */
internal fun formatLyricsSyncOffset(offsetMs: Int): String = if (offsetMs > 0) "+$offsetMs ms" else "$offsetMs ms"

/** Whether the slider can show this offset exactly: within its reach, on one of its steps. */
private fun Int.isSliderOffset(): Boolean = this in -SliderLimitMs..SliderLimitMs && this % SliderStepMs == 0

/** This offset on the slider: its nearest step, within its reach. */
private fun Int.toSliderOffset(): Int = toFloat().toSliderOffset()

private fun Float.toSliderOffset(): Int =
    (coerceIn(-SliderLimitMs.toFloat(), SliderLimitMs.toFloat()) / SliderStepMs).roundToInt() * SliderStepMs

/** What can be typed of an offset: digits, after a sign at the start, no longer than a minute's worth. */
private fun String.asOffsetInput(): String =
    filterIndexed { index, char -> char.isDigit() || index == 0 && (char == '-' || char == '+') }.take(CustomMaxLength)

/** The offset typed, or null while there is none to apply - nothing yet, a lone sign, or beyond a minute. */
private fun String.toCustomOffsetOrNull(): Int? = toIntOrNull()?.takeIf { it in -CustomLimitMs..CustomLimitMs }
