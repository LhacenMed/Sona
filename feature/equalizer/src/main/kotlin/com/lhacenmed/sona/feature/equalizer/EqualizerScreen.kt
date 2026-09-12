package com.lhacenmed.sona.feature.equalizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.playback.CUSTOM_PRESET
import com.lhacenmed.sona.feature.playback.EqualizerBand
import com.lhacenmed.sona.feature.playback.EqualizerState
import kotlin.math.roundToInt

private const val MILLIBELS_PER_DECIBEL = 100

/**
 * Reserved up front so a band's slider starts in the same place whatever its label reads - "60 Hz"
 * and "14 kHz" are different widths, and letting them size themselves would stagger the column.
 */
private val FrequencyLabelWidth = 64.dp
private val GainLabelWidth = 56.dp

/**
 * The equalizer, recreated in Compose from Fossify's `EqualizerActivity`.
 *
 * It is a [Screen] rather than an Activity of its own, which is what makes it themed by the playing
 * track's artwork: every pushed screen is rendered by the shared host, and the host already seeds
 * [com.lhacenmed.sona.core.designsystem.theme.SonaTheme] from the process-wide colour and animates
 * it. A separate Activity would have had to re-derive all of that by hand.
 *
 * Nothing here decides what an equalizer looks like - band count, centre frequencies, the gain range
 * and the presets are all read off the device, exactly as the reference app does it.
 */
object EqualizerScreen : Screen {
    override val titleRes: Int get() = R.string.equalizer_title

    @Composable
    override fun Content() {
        val viewModel: EqualizerViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()

        Box(modifier = Modifier.fillMaxSize()) {
            val equalizer = state
            if (equalizer == null) {
                Text(
                    text = stringResource(R.string.equalizer_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 32.dp),
                )
            } else {
                EqualizerCurve(
                    state = equalizer,
                    onSelectPreset = viewModel::selectPreset,
                    onBandLevelChange = viewModel::setBandLevel,
                    onBandLevelSettled = viewModel::saveCurve,
                )
            }
        }
    }
}

@Composable
private fun EqualizerCurve(
    state: EqualizerState,
    onSelectPreset: (Int) -> Unit,
    onBandLevelChange: (bandIndex: Int, levelMillibels: Int) -> Unit,
    onBandLevelSettled: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PresetPicker(
            presetNames = state.presetNames,
            selectedPreset = state.selectedPreset,
            onSelectPreset = onSelectPreset,
        )

        state.bands.forEachIndexed { index, band ->
            BandSlider(
                band = band,
                levelRangeMillibels = state.levelRangeMillibels,
                onLevelChange = { level -> onBandLevelChange(index, level) },
                onLevelSettled = onBandLevelSettled,
            )
        }
    }
}

/**
 * The device's own presets, plus the hand-tuned curve.
 *
 * "Custom" is not a preset the device has - it is the curve the user last left the bands at, which
 * is why it is added here rather than coming back from the platform.
 */
@Composable
private fun PresetPicker(
    presetNames: List<String>,
    selectedPreset: Int,
    onSelectPreset: (Int) -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }
    val customLabel = stringResource(R.string.equalizer_preset_custom)

    Box {
        OutlinedButton(
            onClick = { isExpanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = presetNames.getOrNull(selectedPreset) ?: customLabel)
        }

        DropdownMenu(expanded = isExpanded, onDismissRequest = { isExpanded = false }) {
            presetNames.forEachIndexed { index, name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        isExpanded = false
                        onSelectPreset(index)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(customLabel) },
                onClick = {
                    isExpanded = false
                    onSelectPreset(CUSTOM_PRESET)
                },
            )
        }
    }
}

/**
 * One band: what it covers, how far it is turned up, and by how much.
 *
 * The slider steps in whole decibels, which is the granularity the reference app rounds to by hand
 * on every drag event - asking the slider for it instead means the value can never be off-step.
 */
@Composable
private fun BandSlider(
    band: EqualizerBand,
    levelRangeMillibels: IntRange,
    onLevelChange: (levelMillibels: Int) -> Unit,
    onLevelSettled: () -> Unit,
) {
    val decibelsAcrossRange =
        (levelRangeMillibels.last - levelRangeMillibels.first) / MILLIBELS_PER_DECIBEL

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = band.frequencyLabel(),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(FrequencyLabelWidth),
        )
        Slider(
            value = band.levelMillibels.toFloat(),
            onValueChange = { level -> onLevelChange(level.roundToInt()) },
            onValueChangeFinished = onLevelSettled,
            valueRange = levelRangeMillibels.first.toFloat()..levelRangeMillibels.last.toFloat(),
            steps = (decibelsAcrossRange - 1).coerceAtLeast(0),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        )
        Text(
            text = band.gainLabel(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(GainLabelWidth),
        )
    }
}

/** Hertz up to a kilohertz, kilohertz above it - the scale a band is actually read at. */
private fun EqualizerBand.frequencyLabel(): String = when {
    centerFrequencyHz < 1_000 -> "$centerFrequencyHz Hz"
    centerFrequencyHz % 1_000 == 0 -> "${centerFrequencyHz / 1_000} kHz"
    else -> "${centerFrequencyHz / 1_000}.${centerFrequencyHz % 1_000 / 100} kHz"
}

/** Always signed, so a boost and a cut are told apart without reading the slider. */
private fun EqualizerBand.gainLabel(): String {
    val decibels = levelMillibels / MILLIBELS_PER_DECIBEL
    return if (decibels > 0) "+$decibels dB" else "$decibels dB"
}
