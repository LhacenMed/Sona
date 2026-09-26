package com.lhacenmed.sona.feature.settings.appearance

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.fab.screenList
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.core.designsystem.theme.palette.ThemePalettes
import com.lhacenmed.sona.core.designsystem.theme.palette.ThemeSeedPalette
import com.lhacenmed.sona.core.designsystem.theme.palette.ThemeSeedPaletteCodec
import com.lhacenmed.sona.core.designsystem.theme.pillShape
import com.lhacenmed.sona.core.designsystem.theme.roundedShape
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.R
import com.lhacenmed.sona.feature.settings.appearance.palette.PalettePreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The longest a theme's name may be. */
private const val MaxThemeNameLength = 48

/** How much of an unreadable import is shown back to the user. */
private const val MaxImportErrorLength = 1200

/** How many preset colours the editor offers to start a seed from. */
private const val PresetSwatchCount = 18

/** The four seeds a theme is built from, in the order the editor offers them. */
private enum class SeedRole(val labelRes: Int) {
    PRIMARY(R.string.theme_seed_primary),
    SECONDARY(R.string.theme_seed_secondary),
    TERTIARY(R.string.theme_seed_tertiary),
    NEUTRAL(R.string.theme_seed_neutral),
}

private val ColorSaver = Saver<Color, Int>(save = { it.toArgb() }, restore = { Color(it) })

/**
 * A palette of one's own - ArchiveTune's `ThemeCreatorScreen`: four seed colours set by hex or by channel,
 * previewed as they are set, applied as the palette, and exported to or imported from a JSON file
 * ArchiveTune reads and writes too.
 *
 * It starts from the palette in use - a custom theme's seeds and name, or a preset's seeds.
 */
data object ThemeCreatorScreen : Screen {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.current
        val scope = rememberCoroutineScope()
        val viewModel: AppearanceSettingsViewModel = hiltViewModel()
        val theme by viewModel.theme.collectAsStateWithLifecycle()
        val storedPalette = theme.colorPalette

        val storedSeeds = remember(storedPalette) { ThemePalettes.seedsOf(storedPalette) }
        var themeName by rememberSaveable(storedPalette) {
            mutableStateOf(storedPalette?.let(ThemeSeedPaletteCodec::extractNameFromPreference).orEmpty())
        }
        var primary by rememberSaveable(storedPalette, stateSaver = ColorSaver) { mutableStateOf(storedSeeds.primary) }
        var secondary by rememberSaveable(storedPalette, stateSaver = ColorSaver) { mutableStateOf(storedSeeds.secondary) }
        var tertiary by rememberSaveable(storedPalette, stateSaver = ColorSaver) { mutableStateOf(storedSeeds.tertiary) }
        var neutral by rememberSaveable(storedPalette, stateSaver = ColorSaver) { mutableStateOf(storedSeeds.neutral) }
        val currentPalette = ThemeSeedPalette(primary, secondary, tertiary, neutral)

        var activeRole by rememberSaveable { mutableStateOf(SeedRole.PRIMARY) }
        var importErrorText by rememberSaveable { mutableStateOf<String?>(null) }

        val nameOrNull = themeName.takeIf { it.isNotBlank() }
        fun applyTheme() {
            viewModel.setColorPalette(ThemeSeedPaletteCodec.encodeForPreference(currentPalette, nameOrNull))
            Toast.makeText(context, R.string.theme_applied, Toast.LENGTH_SHORT).show()
        }

        val exportTheme = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val payload = ThemeSeedPaletteCodec.encodeAsJson(currentPalette, nameOrNull)
            scope.launch {
                val exported = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                            ?: error("No output stream")
                    }.isSuccess
                }
                Toast.makeText(
                    context,
                    if (exported) R.string.theme_export_success else R.string.theme_export_failed,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }

        val importTheme = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val text = readThemeFile(context, uri)
                val imported = ThemeSeedPaletteCodec.decodeFromJson(text)
                if (imported == null) {
                    importErrorText = text.take(MaxImportErrorLength)
                    return@launch
                }
                viewModel.setColorPalette(
                    ThemeSeedPaletteCodec.encodeForPreference(imported, ThemeSeedPaletteCodec.extractNameFromJson(text)),
                )
                Toast.makeText(context, R.string.theme_import_success, Toast.LENGTH_SHORT).show()
            }
        }

        importErrorText?.let { errorText ->
            AlertDialog(
                onDismissRequest = { importErrorText = null },
                confirmButton = {
                    TextButton(onClick = { importErrorText = null }, shapes = buttonPressShapes()) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                title = { Text(stringResource(R.string.theme_import_failed_title)) },
                text = {
                    Text(
                        text = errorText.ifBlank { stringResource(R.string.theme_import_failed) },
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            SonaTopAppBar(
                title = stringResource(R.string.theme_creator_title),
                onNavigateBack = navigator::back,
                actions = listOf(
                    TopBarAction(label = stringResource(R.string.reset), icon = Icons.Filled.RestartAlt) {
                        val default = ThemePalettes.Default.seeds
                        primary = default.primary
                        secondary = default.secondary
                        tertiary = default.tertiary
                        neutral = default.neutral
                        themeName = ""
                    },
                    TopBarAction(label = stringResource(R.string.save), icon = Icons.Filled.Check, onClick = ::applyTheme),
                ),
            )

            Box(modifier = Modifier.fillMaxSize()) {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .screenList(scrollState)
                        .verticalScroll(scrollState)
                        .padding(bottom = LocalBottomContentPadding.current),
                ) {
                    PalettePreview(
                        palette = currentPalette,
                        isDarkTheme = theme.mode.isDark(),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )

                    EditorCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(text = stringResource(R.string.theme_meta_title), style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(
                            value = themeName,
                            onValueChange = { themeName = it.take(MaxThemeNameLength) },
                            label = { Text(stringResource(R.string.theme_name_optional)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = ::applyTheme, shapes = buttonPressShapes(), modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.theme_apply_button))
                        }
                    }

                    SeedRolePicker(
                        activeRole = activeRole,
                        onRoleChange = { activeRole = it },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )

                    SeedColorEditor(
                        role = activeRole,
                        color = when (activeRole) {
                            SeedRole.PRIMARY -> primary
                            SeedRole.SECONDARY -> secondary
                            SeedRole.TERTIARY -> tertiary
                            SeedRole.NEUTRAL -> neutral
                        },
                        onColorChange = { color ->
                            when (activeRole) {
                                SeedRole.PRIMARY -> primary = color
                                SeedRole.SECONDARY -> secondary = color
                                SeedRole.TERTIARY -> tertiary = color
                                SeedRole.NEUTRAL -> neutral = color
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )

                    // Room for the buttons below to clear the last of the editor.
                    Spacer(modifier = Modifier.height(96.dp))
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(start = 16.dp, end = 16.dp, bottom = LocalBottomContentPadding.current),
                ) {
                    ExtendedFloatingActionButton(
                        text = { Text(stringResource(R.string.import_theme)) },
                        icon = { Icon(Icons.Filled.FileOpen, contentDescription = null) },
                        onClick = { importTheme.launch(ThemeFileTypes) },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                    ExtendedFloatingActionButton(
                        text = { Text(stringResource(R.string.export_theme)) },
                        icon = { Icon(Icons.Filled.Share, contentDescription = null) },
                        onClick = { exportTheme.launch("${themeFileName(themeName)}.json") },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

/** A file name for the theme: its name, kept to characters any file system takes, or the app's default. */
private fun themeFileName(themeName: String): String =
    themeName.trim()
        .ifBlank { "Sona Theme" }
        .replace(Regex("[^a-zA-Z0-9 _\\-]"), "_")
        .take(64)

@Composable
private fun EditorCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = roundedShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
        }
    }
}

/** The four seeds as chips, two by two, the one being edited filled. */
@Composable
private fun SeedRolePicker(
    activeRole: SeedRole,
    onRoleChange: (SeedRole) -> Unit,
    modifier: Modifier = Modifier,
) {
    EditorCard(modifier = modifier) {
        Text(text = stringResource(R.string.theme_seed_colors), style = MaterialTheme.typography.titleSmall)
        SeedRole.entries.chunked(2).forEach { rowRoles ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowRoles.forEach { role ->
                    SeedChip(
                        label = stringResource(role.labelRes),
                        selected = role == activeRole,
                        onClick = { onRoleChange(role) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SeedChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clip(pillShape).clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        shape = pillShape,
        shadowElevation = if (selected) 6.dp else 0.dp,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

/**
 * The seed being edited: its swatch and hex, copyable; a hex field that takes it as typed; a slider for
 * each channel; and the presets' colours to start from.
 */
@Composable
private fun SeedColorEditor(
    role: SeedRole,
    color: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val argb = color.toArgb()
    var red by rememberSaveable(role) { mutableIntStateOf((argb shr 16) and 0xFF) }
    var green by rememberSaveable(role) { mutableIntStateOf((argb shr 8) and 0xFF) }
    var blue by rememberSaveable(role) { mutableIntStateOf(argb and 0xFF) }
    LaunchedEffect(role, argb) {
        red = (argb shr 16) and 0xFF
        green = (argb shr 8) and 0xFF
        blue = argb and 0xFF
    }

    val hex = remember(argb) { String.format("#%08X", argb) }
    var hexInput by rememberSaveable(role) { mutableStateOf(hex) }
    var hexError by rememberSaveable(role) { mutableStateOf(false) }
    // Follows the colour however it changed, except while the field holds something that is not one yet.
    LaunchedEffect(hex) { if (!hexError) hexInput = hex }

    fun commitChannels() {
        hexError = false
        onColorChange(Color((0xFF shl 24) or (red shl 16) or (green shl 8) or blue))
    }

    EditorCard(modifier = modifier) {
        Text(
            text = stringResource(R.string.theme_editor_title, stringResource(role.labelRes)),
            style = MaterialTheme.typography.titleSmall,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .shadow(8.dp, CircleShape)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.theme_editor_hex),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = hex, style = MaterialTheme.typography.titleSmall)
            }
            Surface(shape = pillShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(
                    modifier = Modifier
                        .clickable {
                            context.getSystemService(ClipboardManager::class.java)
                                ?.setPrimaryClip(ClipData.newPlainText(hex, hex))
                            Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(text = stringResource(R.string.copy), style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        OutlinedTextField(
            value = hexInput,
            onValueChange = { next ->
                hexInput = next.take(12)
                val parsed = runCatching {
                    val normalized = hexInput.trim().let { if (it.startsWith("#")) it else "#$it" }
                    Color(android.graphics.Color.parseColor(normalized))
                }.getOrNull()
                hexError = parsed == null
                parsed?.let(onColorChange)
            },
            label = { Text(stringResource(R.string.theme_editor_hex_input)) },
            isError = hexError,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )

        ChannelSlider(label = "R", value = red, color = Color(0xFFE53935)) {
            red = it
            commitChannels()
        }
        ChannelSlider(label = "G", value = green, color = Color(0xFF43A047)) {
            green = it
            commitChannels()
        }
        ChannelSlider(label = "B", value = blue, color = Color(0xFF1E88E5)) {
            blue = it
            commitChannels()
        }

        PresetSwatches(current = color, onPick = onColorChange)
    }
}

/** One colour channel, 0 to 255, in that channel's own colour. */
@Composable
private fun ChannelSlider(
    label: String,
    value: Int,
    color: Color,
    onValueChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = roundedShape(10.dp), color = color.copy(alpha = 0.18f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = color,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier.weight(1f),
        )
        Text(text = value.toString(), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(44.dp))
    }
}

/** The presets' own colours, to start a seed from. */
@Composable
private fun PresetSwatches(
    current: Color,
    onPick: (Color) -> Unit,
) {
    val swatches = remember {
        ThemePalettes.all.map { it.seeds.primary }.distinctBy { it.toArgb() }.take(PresetSwatchCount)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.theme_presets_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            swatches.forEach { swatch ->
                val selected = swatch.toArgb() == current.toArgb()
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .shadow(if (selected) 8.dp else 2.dp, CircleShape)
                        .clip(CircleShape)
                        .background(swatch)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        )
                        .clickable { onPick(swatch) },
                )
            }
        }
    }
}
