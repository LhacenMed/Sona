@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.equalizer

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.datastore.EqualizerControlMode
import com.lhacenmed.sona.core.datastore.EqualizerProfile
import com.lhacenmed.sona.core.datastore.EqualizerSelection
import com.lhacenmed.sona.core.datastore.EqualizerSound
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.actionButton
import com.lhacenmed.sona.core.designsystem.component.dialog.SonaDialog
import com.lhacenmed.sona.core.designsystem.component.screen.screenList
import com.lhacenmed.sona.core.designsystem.component.toast
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.core.navigation.Screen
import kotlin.math.roundToInt

/** How wide the controls grow, centred beyond it - on a tablet or in landscape. */
private val ContentMaxWidth = 840.dp

/** Characters a file name is better without, swapped for an underscore in an exported profile's name. */
private val UnsafeFileCharacters = Regex("[^a-zA-Z0-9._-]")

/**
 * The equalizer - ArchiveTune's, cloned: sound shaping and the system's own equalizer up top, then the
 * control mode, then basic controls (presets and three tones) or advanced ones (presets, every band, the
 * output's gain and headroom, bass boost and virtualizer, and profiles that can be saved, shared and
 * imported).
 *
 * A [Screen] in Sona's host, reached from the player's queue bar, rather than ArchiveTune's full-screen
 * dialog: the host gives it the top bar, the back navigation and the playing cover's colours.
 */
data object EqualizerScreen : Screen {
    override val titleRes: Int get() = R.string.equalizer_title

    @Composable
    override fun Content() {
        val viewModel: EqualizerViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val context = LocalContext.current

        var isSaveProfileDialogShown by rememberSaveable { mutableStateOf(false) }
        var isManageProfilesDialogShown by rememberSaveable { mutableStateOf(false) }
        var exportedProfile by remember { mutableStateOf<EqualizerProfile?>(null) }

        val systemEqualizerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
        val openSystemEqualizer = {
            val intent = systemEqualizerIntent(context, viewModel.audioSessionId)
            if (intent.resolveActivity(context.packageManager) != null) systemEqualizerLauncher.launch(intent)
        }
        val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(viewModel::importProfiles)
        }
        val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            val profile = exportedProfile
            exportedProfile = null
            if (uri != null && profile != null) viewModel.exportProfile(uri, profile)
        }

        LaunchedEffect(viewModel) {
            viewModel.messages.collect { message ->
                context.toast(
                    when (message) {
                        EqualizerMessage.ProfileSaved -> context.getString(R.string.eq_profile_saved)
                        is EqualizerMessage.ProfilesImported ->
                            context.resources.getQuantityString(R.plurals.eq_import_success, message.count, message.count)
                        EqualizerMessage.ImportFailed -> context.getString(R.string.eq_import_failed)
                        EqualizerMessage.ProfileExported -> context.getString(R.string.eq_export_success)
                        EqualizerMessage.ExportFailed -> context.getString(R.string.eq_export_failed)
                    },
                )
            }
        }

        val equalizer = state
        if (equalizer == null) {
            EqualizerUnavailable(onOpenSystemEqualizer = openSystemEqualizer)
            return
        }

        EqualizerContent(
            state = equalizer,
            viewModel = viewModel,
            onOpenSystemEqualizer = openSystemEqualizer,
            onShowSaveProfile = { isSaveProfileDialogShown = true },
            onShowManageProfiles = { isManageProfilesDialogShown = true },
            onImportProfiles = { importLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) },
        )

        if (isSaveProfileDialogShown) {
            SaveProfileDialog(
                onSave = { name ->
                    isSaveProfileDialogShown = false
                    viewModel.saveProfile(name)
                },
                onDismiss = { isSaveProfileDialogShown = false },
            )
        }
        if (isManageProfilesDialogShown) {
            ManageProfilesDialog(
                profiles = equalizer.choices.profiles,
                selection = equalizer.choices.selection,
                onApply = { profile ->
                    isManageProfilesDialogShown = false
                    viewModel.applyProfile(profile)
                },
                onDelete = viewModel::deleteProfile,
                onExport = { profile ->
                    exportedProfile = profile
                    exportLauncher.launch("${profile.name.ifBlank { "equalizer" }.replace(UnsafeFileCharacters, "_")}-eq.json")
                },
                onDismiss = { isManageProfilesDialogShown = false },
            )
        }
    }
}

/** The system's own equalizer, opened on Sona's session while it has one. */
private fun systemEqualizerIntent(context: Context, audioSessionId: Int?) =
    Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
        .apply { audioSessionId?.let { putExtra(AudioEffect.EXTRA_AUDIO_SESSION, it) } }
        .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
        .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)

@Composable
private fun EqualizerContent(
    state: EqualizerUiState,
    viewModel: EqualizerViewModel,
    onOpenSystemEqualizer: () -> Unit,
    onShowSaveProfile: () -> Unit,
    onShowManageProfiles: () -> Unit,
    onImportProfiles: () -> Unit,
) {
    val choices = state.choices
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().screenList(listState),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 12.dp,
            end = 16.dp,
            bottom = LocalBottomContentPadding.current + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "hero", contentType = "hero") {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                EqualizerHero(choices.enabled, Modifier.widthIn(max = ContentMaxWidth), viewModel::setEnabled, onOpenSystemEqualizer)
            }
        }
        item(key = "mode", contentType = "mode") {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ModeSelector(choices.controlMode, Modifier.widthIn(max = ContentMaxWidth), viewModel::setControlMode)
            }
        }
        item(key = "controls", contentType = choices.controlMode) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                AnimatedContent(
                    targetState = choices.controlMode,
                    transitionSpec = { (fadeIn(spring()) togetherWith fadeOut(spring())).using(SizeTransform(clip = false)) },
                    label = "equalizerMode",
                    modifier = Modifier.widthIn(max = ContentMaxWidth),
                ) { mode ->
                    when (mode) {
                        EqualizerControlMode.BASIC -> BasicControls(state, viewModel)
                        EqualizerControlMode.ADVANCED -> AdvancedControls(
                            state = state,
                            viewModel = viewModel,
                            onShowSaveProfile = onShowSaveProfile,
                            onShowManageProfiles = onShowManageProfiles,
                            onImportProfiles = onImportProfiles,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EqualizerHero(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onEnabledChange: (Boolean) -> Unit,
    onOpenSystemEqualizer: () -> Unit,
) {
    val containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f)) {
                    Icon(imageVector = Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.padding(12.dp).size(28.dp))
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(if (enabled) R.string.eq_sound_shaping_on else R.string.eq_sound_shaping_off),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.eq_enable_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                    )
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            FilledTonalButton(onClick = onOpenSystemEqualizer, modifier = Modifier.fillMaxWidth(), shapes = buttonPressShapes()) {
                Icon(imageVector = Icons.Rounded.Tune, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(text = stringResource(R.string.eq_open_system_equalizer))
            }
        }
    }
}

@Composable
private fun ModeSelector(
    selectedMode: EqualizerControlMode,
    modifier: Modifier = Modifier,
    onModeChange: (EqualizerControlMode) -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = stringResource(R.string.eq_control_mode),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                EqualizerControlMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = selectedMode == mode,
                        onClick = { onModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, EqualizerControlMode.entries.size),
                        icon = {},
                    ) {
                        Text(text = stringResource(if (mode == EqualizerControlMode.BASIC) R.string.eq_basic else R.string.eq_advanced))
                    }
                }
            }
            Text(
                text = stringResource(
                    if (selectedMode == EqualizerControlMode.BASIC) R.string.eq_basic_description else R.string.eq_advanced_description,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BasicControls(state: EqualizerUiState, viewModel: EqualizerViewModel) {
    val enabled = state.choices.enabled
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PresetSection(state, viewModel::applyPreset)
        EqualizerSection(title = stringResource(R.string.eq_tone), subtitle = stringResource(R.string.eq_tone_description)) {
            EqualizerTone.entries.forEachIndexed { index, tone ->
                LevelSlider(
                    label = stringResource(tone.labelRes),
                    levelMb = state.toneLevelsMb.getValue(tone),
                    rangeMb = state.capabilities.bandLevelRangeMb,
                    enabled = enabled,
                    onLevelChange = { viewModel.moveTone(tone, it) },
                    onLevelChangeFinished = { viewModel.releaseTone(tone) },
                    isTone = true,
                )
                if (index != EqualizerTone.entries.lastIndex) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun AdvancedControls(
    state: EqualizerUiState,
    viewModel: EqualizerViewModel,
    onShowSaveProfile: () -> Unit,
    onShowManageProfiles: () -> Unit,
    onImportProfiles: () -> Unit,
) {
    val enabled = state.choices.enabled
    val sound = state.choices.sound
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PresetSection(state, viewModel::applyPreset)
        EqualizerSection(
            title = stringResource(R.string.eq_bands),
            subtitle = stringResource(R.string.eq_bands_description),
            action = {
                TextButton(onClick = viewModel::resetBands, enabled = enabled, shapes = buttonPressShapes()) {
                    Text(text = stringResource(R.string.eq_reset))
                }
            },
        ) {
            state.capabilities.centerFrequenciesHz.forEachIndexed { index, frequencyHz ->
                LevelSlider(
                    label = formatFrequency(frequencyHz),
                    levelMb = sound.bandLevelsMb[index],
                    rangeMb = state.capabilities.bandLevelRangeMb,
                    enabled = enabled,
                    onLevelChange = { viewModel.moveBand(index, it) },
                    onLevelChangeFinished = viewModel::releaseBands,
                )
                if (index != state.capabilities.bandCount - 1) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }
        EqualizerSection(title = stringResource(R.string.eq_signal), subtitle = stringResource(R.string.eq_signal_description)) {
            ToggleSlider(
                title = stringResource(R.string.eq_output_gain),
                description = stringResource(R.string.eq_output_gain_description),
                enabled = sound.outputGainEnabled,
                controlsEnabled = enabled && !sound.autoHeadroomEnabled,
                value = sound.outputGainMb,
                valueRange = EqualizerSound.OUTPUT_GAIN_RANGE_MB,
                valueLabel = formatDecibels(sound.outputGainMb),
                onEnabledChange = viewModel::setOutputGainEnabled,
                onValueChange = viewModel::moveOutputGain,
                onValueChangeFinished = viewModel::releaseOutputGain,
            )
            Spacer(Modifier.height(12.dp))
            SettingsToggle(
                title = stringResource(R.string.eq_auto_headroom),
                description = stringResource(R.string.eq_auto_headroom_description),
                checked = sound.autoHeadroomEnabled,
                enabled = enabled,
                onCheckedChange = viewModel::setAutoHeadroomEnabled,
            )
        }
        EqualizerSection(title = stringResource(R.string.eq_effects), subtitle = stringResource(R.string.eq_effects_description)) {
            ToggleSlider(
                title = stringResource(R.string.eq_bass_boost),
                description = stringResource(R.string.eq_bass_boost_description),
                enabled = sound.bassBoostEnabled,
                controlsEnabled = enabled,
                value = sound.bassBoostStrength,
                valueRange = EqualizerSound.STRENGTH_RANGE,
                valueLabel = stringResource(R.string.eq_percent, sound.bassBoostStrength / 10),
                onEnabledChange = viewModel::setBassBoostEnabled,
                onValueChange = viewModel::moveBassBoost,
                onValueChangeFinished = viewModel::releaseBassBoost,
            )
            Spacer(Modifier.height(12.dp))
            ToggleSlider(
                title = stringResource(R.string.eq_virtualizer),
                description = stringResource(R.string.eq_virtualizer_description),
                enabled = sound.virtualizerEnabled,
                controlsEnabled = enabled,
                value = sound.virtualizerStrength,
                valueRange = EqualizerSound.STRENGTH_RANGE,
                valueLabel = stringResource(R.string.eq_percent, sound.virtualizerStrength / 10),
                onEnabledChange = viewModel::setVirtualizerEnabled,
                onValueChange = viewModel::moveVirtualizer,
                onValueChangeFinished = viewModel::releaseVirtualizer,
            )
        }
        EqualizerSection(title = stringResource(R.string.eq_profiles), subtitle = stringResource(R.string.eq_profiles_description)) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onShowSaveProfile, enabled = enabled, shapes = buttonPressShapes()) {
                    Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(text = stringResource(R.string.eq_save_profile))
                }
                OutlinedButton(onClick = onShowManageProfiles, enabled = state.choices.profiles.isNotEmpty(), shapes = buttonPressShapes()) {
                    Text(text = stringResource(R.string.eq_manage))
                }
                OutlinedButton(onClick = onImportProfiles, shapes = buttonPressShapes()) {
                    Text(text = stringResource(R.string.eq_import))
                }
            }
        }
    }
}

/** Flat, then the device's own presets - each chosen as a whole set of band levels. */
@Composable
private fun PresetSection(state: EqualizerUiState, onPresetClick: (EqualizerSelection) -> Unit) {
    val presets = listOf(EqualizerSelection.Flat) + state.capabilities.presetNames.indices.map(EqualizerSelection::SystemPreset)
    EqualizerSection(title = stringResource(R.string.eq_presets), subtitle = stringResource(R.string.eq_presets_description)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(presets, key = { _, preset -> preset.toString() }, contentType = { _, _ -> "preset" }) { index, preset ->
                FilterChip(
                    selected = preset == state.choices.selection,
                    onClick = { onPresetClick(preset) },
                    enabled = state.choices.enabled,
                    label = {
                        val name = (preset as? EqualizerSelection.SystemPreset)?.let { state.capabilities.presetNames[it.index] }
                        Text(
                            text = when {
                                preset == EqualizerSelection.Flat -> stringResource(R.string.eq_flat)
                                name.isNullOrBlank() -> stringResource(R.string.eq_preset_number, index)
                                else -> name
                            },
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer),
                )
            }
        }
    }
}

@Composable
private fun EqualizerSection(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                action?.invoke()
            }
            Spacer(Modifier.height(18.dp))
            content()
        }
    }
}

/** A tone's or a band's slider, its level in decibels beside its name - a tone's name the larger. ArchiveTune's `ToneSlider` and `BandSlider`. */
@Composable
private fun LevelSlider(
    label: String,
    levelMb: Int,
    rangeMb: IntRange,
    enabled: Boolean,
    onLevelChange: (Int) -> Unit,
    onLevelChangeFinished: () -> Unit,
    isTone: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = if (isTone) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            ValuePill(formatDecibels(levelMb))
        }
        Slider(
            value = levelMb.toFloat(),
            onValueChange = { onLevelChange(it.roundToInt()) },
            onValueChangeFinished = onLevelChangeFinished,
            enabled = enabled,
            // At least a millibel wide: a device reporting no range at all still draws a slider.
            valueRange = rangeMb.first.toFloat()..maxOf(rangeMb.last, rangeMb.first + 1).toFloat(),
        )
    }
}

@Composable
private fun ToggleSlider(
    title: String,
    description: String,
    enabled: Boolean,
    controlsEnabled: Boolean,
    value: Int,
    valueRange: IntRange,
    valueLabel: String,
    onEnabledChange: (Boolean) -> Unit,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange, enabled = controlsEnabled)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Slider(
                    value = value.toFloat(),
                    onValueChange = { onValueChange(it.roundToInt()) },
                    onValueChangeFinished = onValueChangeFinished,
                    enabled = controlsEnabled && enabled,
                    valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
                    modifier = Modifier.weight(1f),
                )
                ValuePill(valueLabel)
            }
        }
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SegmentedListItem(
        onClick = { onCheckedChange(!checked) },
        enabled = enabled,
        shapes = ListItemDefaults.shapes(shape = MaterialTheme.shapes.large),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        supportingContent = { Text(text = description) },
        content = { Text(text = title) },
    )
}

@Composable
private fun ValuePill(value: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun SaveProfileDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    // Read here rather than inside the group: a group builds its items outside composition.
    val closeLabel = stringResource(R.string.eq_close)
    val saveLabel = stringResource(R.string.eq_save)
    SonaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.eq_save_profile),
        buttons = {
            actionButton(label = closeLabel, onClick = onDismiss)
            actionButton(label = saveLabel, onClick = { onSave(name.trim()) }, enabled = name.isNotBlank())
        },
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(text = stringResource(R.string.eq_profile_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ManageProfilesDialog(
    profiles: List<EqualizerProfile>,
    selection: EqualizerSelection,
    onApply: (EqualizerProfile) -> Unit,
    onDelete: (EqualizerProfile) -> Unit,
    onExport: (EqualizerProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    val closeLabel = stringResource(R.string.eq_close)
    SonaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.eq_profiles),
        buttons = { actionButton(label = closeLabel, onClick = onDismiss) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            profiles.forEach { profile ->
                ProfileRow(
                    profile = profile,
                    isSelected = selection == EqualizerSelection.Profile(profile.id),
                    onApply = onApply,
                    onDelete = onDelete,
                    onExport = onExport,
                )
            }
        }
    }
}

@Composable
private fun ProfileRow(
    profile: EqualizerProfile,
    isSelected: Boolean,
    onApply: (EqualizerProfile) -> Unit,
    onDelete: (EqualizerProfile) -> Unit,
    onExport: (EqualizerProfile) -> Unit,
) {
    SegmentedListItem(
        onClick = { onApply(profile) },
        shapes = ListItemDefaults.shapes(shape = MaterialTheme.shapes.large),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = {
            Icon(imageVector = if (isSelected) Icons.Rounded.Check else Icons.Rounded.Equalizer, contentDescription = null)
        },
        trailingContent = {
            Row {
                IconButton(onClick = { onExport(profile) }) {
                    Icon(imageVector = SonaIcons.Share, contentDescription = stringResource(R.string.eq_export))
                }
                IconButton(onClick = { onDelete(profile) }) {
                    Icon(imageVector = SonaIcons.Delete, contentDescription = stringResource(R.string.eq_delete))
                }
            }
        },
        supportingContent = { Text(text = stringResource(R.string.eq_custom_profile)) },
        content = {
            Text(
                text = profile.name.ifBlank { stringResource(R.string.eq_imported_profile) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

/** Nothing to shape yet - nothing has played, or the device has no equalizer - with the system's equalizer still on offer. */
@Composable
private fun EqualizerUnavailable(onOpenSystemEqualizer: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp).padding(bottom = LocalBottomContentPadding.current),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(imageVector = Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.size(48.dp))
            Text(
                text = stringResource(R.string.eq_waiting_for_audio_session),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            FilledTonalButton(onClick = onOpenSystemEqualizer, shapes = buttonPressShapes()) {
                Text(text = stringResource(R.string.eq_open_system_equalizer))
            }
        }
    }
}

private val EqualizerTone.labelRes: Int
    get() = when (this) {
        EqualizerTone.BASS -> R.string.eq_bass
        EqualizerTone.MIDRANGE -> R.string.eq_midrange
        EqualizerTone.TREBLE -> R.string.eq_treble
    }

@Composable
private fun formatDecibels(valueMb: Int): String = stringResource(R.string.eq_decibels, valueMb / 100f)

@Composable
private fun formatFrequency(frequencyHz: Int): String =
    if (frequencyHz >= 1000) {
        stringResource(R.string.eq_frequency_kilohertz, frequencyHz / 1000f)
    } else {
        stringResource(R.string.eq_frequency_hertz, frequencyHz)
    }
