package com.lhacenmed.sona.feature.settings.appearance

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.fab.screenList
import com.lhacenmed.sona.core.designsystem.theme.palette.ThemePalette
import com.lhacenmed.sona.core.designsystem.theme.palette.ThemePalettes
import com.lhacenmed.sona.core.designsystem.theme.palette.ThemeSeedPaletteCodec
import com.lhacenmed.sona.core.designsystem.theme.roundedShape
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.settings.R
import com.lhacenmed.sona.feature.settings.appearance.palette.PalettePreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The file types a theme is imported from: ArchiveTune exports JSON, which file managers label variously. */
internal val ThemeFileTypes = arrayOf("application/json", "text/plain", "*/*")

/** How many palettes one dot of the carousel stands for. */
private const val PalettesPerPage = 4

/**
 * The preset palettes, and the ways to a palette of one's own - ArchiveTune's `PalettePickerScreen`: the
 * chosen palette previewed above the presets, and a custom theme built in the [ThemeCreatorScreen] or
 * imported from a file.
 */
data object ColorPaletteScreen : Screen {
    override val titleRes: Int get() = R.string.color_palette_title

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.current
        val scope = rememberCoroutineScope()
        val viewModel: AppearanceSettingsViewModel = hiltViewModel()
        val theme by viewModel.theme.collectAsStateWithLifecycle()
        val selectedSeeds = remember(theme.colorPalette) { ThemePalettes.seedsOf(theme.colorPalette) }
        // A custom theme is none of the presets, so none of them is marked.
        val selectedPresetId = remember(theme.colorPalette) {
            if (theme.colorPalette?.let(ThemeSeedPaletteCodec::decodeFromPreference) != null) {
                null
            } else {
                theme.colorPalette?.let(ThemePalettes::findById)?.id ?: ThemePalettes.Default.id
            }
        }

        val importTheme = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val text = readThemeFile(context, uri)
                val imported = ThemeSeedPaletteCodec.decodeFromJson(text)
                if (imported == null) {
                    Toast.makeText(context, R.string.theme_import_failed, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                viewModel.setColorPalette(
                    ThemeSeedPaletteCodec.encodeForPreference(imported, ThemeSeedPaletteCodec.extractNameFromJson(text)),
                )
                Toast.makeText(context, R.string.theme_import_success, Toast.LENGTH_SHORT).show()
                navigator.go(ThemeCreatorScreen)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            val scrollState = rememberScrollState()
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .screenList(scrollState)
                    .verticalScroll(scrollState)
                    .padding(bottom = LocalBottomContentPadding.current),
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                PalettePreview(
                    palette = selectedSeeds,
                    isDarkTheme = theme.mode.isDark(),
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(modifier = Modifier.height(24.dp))
                PaletteCarousel(
                    palettes = ThemePalettes.all,
                    selectedId = selectedPresetId,
                    onSelect = { viewModel.setColorPalette(it.id) },
                )
                // Room for the buttons below to clear the last of the content.
                Spacer(modifier = Modifier.height(160.dp))
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = LocalBottomContentPadding.current),
            ) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.custom_theme)) },
                    icon = { Icon(Icons.Filled.Palette, contentDescription = null) },
                    onClick = { navigator.go(ThemeCreatorScreen) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.import_theme)) },
                    icon = { Icon(Icons.Filled.FileOpen, contentDescription = null) },
                    onClick = { importTheme.launch(ThemeFileTypes) },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** What the palette row says it is set to: a custom theme's name, "Custom", or the preset's name. */
@Composable
internal fun colorPaletteLabel(storedPalette: String?): String {
    val customTheme = storedPalette?.let(ThemeSeedPaletteCodec::decodeFromPreference)
    return when {
        customTheme != null ->
            storedPalette.let(ThemeSeedPaletteCodec::extractNameFromPreference)
                ?: stringResource(R.string.color_palette_custom)
        else -> stringResource((storedPalette?.let(ThemePalettes::findById) ?: ThemePalettes.Default).nameRes)
    }
}

/** A theme file's text, or nothing when it cannot be read. */
internal suspend fun readThemeFile(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    }.getOrNull().orEmpty()
}

/** The presets in a row, brought round to the chosen one, with a dot for every page of them beneath. */
@Composable
private fun PaletteCarousel(
    palettes: List<ThemePalette>,
    selectedId: String?,
    onSelect: (ThemePalette) -> Unit,
) {
    val listState = rememberLazyListState()
    val selectedIndex = palettes.indexOfFirst { it.id == selectedId }
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex / PalettesPerPage } }
    val selectedColor = palettes.getOrNull(selectedIndex)?.seeds?.primary ?: MaterialTheme.colorScheme.primary

    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) listState.animateScrollToItem(index = selectedIndex, scrollOffset = -100)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(palettes, key = { it.id }) { palette ->
                PaletteSwatch(
                    palette = palette,
                    isSelected = palette.id == selectedId,
                    onClick = { onSelect(palette) },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        PageDots(
            count = (palettes.size + PalettesPerPage - 1) / PalettesPerPage,
            currentPage = currentPage,
            selectedColor = selectedColor,
        )
    }
}

/** A palette as ArchiveTune's `SelectableMiniPalette` draws it: its three colours in one disc, checked when chosen. */
@Composable
private fun PaletteSwatch(
    palette: ThemePalette,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "palette-swatch-scale",
    )

    Surface(
        modifier = Modifier.scale(scale),
        shape = roundedShape(16.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Surface(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(16.dp)
                .size(48.dp),
            shape = CircleShape,
            color = palette.seeds.primary,
        ) {
            Box {
                Surface(modifier = Modifier.size(48.dp).offset((-24).dp, 24.dp), color = palette.seeds.tertiary) {}
                Surface(modifier = Modifier.size(48.dp).offset(24.dp, 24.dp), color = palette.seeds.secondary) {}
                AnimatedVisibility(
                    visible = isSelected,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    enter = fadeIn() + expandIn(expandFrom = Alignment.Center),
                    exit = shrinkOut(shrinkTowards = Alignment.Center) + fadeOut(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(8.dp).size(16.dp),
                    )
                }
            }
        }
    }
}

/** One dot per page, the current one larger and in [selectedColor]. */
@Composable
private fun PageDots(count: Int, currentPage: Int, selectedColor: Color) {
    val dotContainerSize = 10.dp
    Row(
        modifier = Modifier.height(dotContainerSize).padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val isCurrent = index == currentPage
            val dotSize by animateDpAsState(
                targetValue = if (isCurrent) 8.dp else 4.dp,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                label = "page-dot-size",
            )
            Box(
                modifier = Modifier.padding(horizontal = 2.dp).size(dotContainerSize),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .clip(CircleShape)
                        .background(
                            if (isCurrent) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        ),
                )
            }
        }
    }
}
