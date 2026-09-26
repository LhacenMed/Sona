package com.lhacenmed.sona.feature.settings.appearance

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lhacenmed.sona.core.datastore.CustomBackground
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.fab.screenList
import com.lhacenmed.sona.core.designsystem.theme.buttonPressShapes
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.player.background.CustomBackgroundScrimAlpha
import com.lhacenmed.sona.feature.player.background.customBackgroundColorFilter
import com.lhacenmed.sona.feature.settings.R
import kotlin.math.roundToInt

private const val DefaultBlur = 0f
private const val DefaultTone = 1f

/** From this width the previews stand beside the adjustments rather than above them. */
private val ExpandedContentBreakpoint = 840.dp
private const val PlayerPreviewAspectRatio = 718f / 1518f
private const val LyricsPreviewAspectRatio = 720f / 1386f

/**
 * The image the custom player background draws, and how it is adjusted - ArchiveTune's
 * `CustomizeBackground`: previewed under the player and the lyrics as it is adjusted.
 *
 * The image is kept as it is picked. An adjustment is shown as it is dragged and kept once let go, so a
 * drag writes the setting once rather than on every frame of it.
 */
data object CustomBackgroundScreen : Screen {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.current
        val viewModel: AppearanceSettingsViewModel = hiltViewModel()
        val player by viewModel.player.collectAsStateWithLifecycle()
        val stored = player.customBackground

        var blur by remember(stored.blur) { mutableFloatStateOf(stored.blur) }
        var contrast by remember(stored.contrast) { mutableFloatStateOf(stored.contrast) }
        var brightness by remember(stored.brightness) { mutableFloatStateOf(stored.brightness) }

        val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            if (!persistReadPermission(context, uri)) {
                Toast.makeText(context, R.string.custom_background_permission_error, Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            val previousUri = stored.imageUri
            viewModel.setCustomBackgroundImage(uri.toString())
            if (previousUri != null && previousUri != uri.toString() && !releaseReadPermission(context, previousUri)) {
                Toast.makeText(context, R.string.custom_background_permission_cleanup_error, Toast.LENGTH_SHORT).show()
            }
        }

        val adjustments = @Composable {
            AdjustmentSection(
                blur = blur,
                contrast = contrast,
                brightness = brightness,
                onBlurChange = { blur = it },
                onContrastChange = { contrast = it },
                onBrightnessChange = { brightness = it },
                onBlurChangeFinished = { viewModel.setCustomBackgroundBlur(blur) },
                onContrastChangeFinished = { viewModel.setCustomBackgroundContrast(contrast) },
                onBrightnessChangeFinished = { viewModel.setCustomBackgroundBrightness(brightness) },
                onReset = {
                    viewModel.setCustomBackgroundBlur(DefaultBlur)
                    viewModel.setCustomBackgroundContrast(DefaultTone)
                    viewModel.setCustomBackgroundBrightness(DefaultTone)
                },
            )
            FilledTonalButton(onClick = navigator::back, shapes = buttonPressShapes(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.save))
            }
        }
        val image = @Composable {
            PreviewSection(
                background = CustomBackground(stored.imageUri, blur, contrast, brightness),
            )
            ImageActions(
                hasImage = stored.imageUri != null,
                onChooseImage = { pickImage.launch(arrayOf("image/*")) },
                onRemoveImage = {
                    val released = stored.imageUri?.let { releaseReadPermission(context, it) } ?: true
                    viewModel.setCustomBackgroundImage(null)
                    if (!released) {
                        Toast.makeText(context, R.string.custom_background_permission_cleanup_error, Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            SonaTopAppBar(
                title = stringResource(R.string.customized_background_title),
                subtitle = stringResource(R.string.custom_background_subtitle),
                onNavigateBack = navigator::back,
            )
            val scrollState = rememberScrollState()
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .screenList(scrollState)
                    .verticalScroll(scrollState)
                    .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = LocalBottomContentPadding.current),
            ) {
                if (maxWidth >= ExpandedContentBreakpoint) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
                        Column(modifier = Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(16.dp)) { image() }
                        Column(modifier = Modifier.weight(0.9f), verticalArrangement = Arrangement.spacedBy(16.dp)) { adjustments() }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        image()
                        adjustments()
                    }
                }
            }
        }
    }
}

/** The image under the player and under the lyrics, as the background draws it. */
@Composable
private fun PreviewSection(background: CustomBackground) {
    val imageUri = remember(background.imageUri) { background.imageUri?.let(Uri::parse) }
    val colorFilter = remember(background.contrast, background.brightness) {
        customBackgroundColorFilter(background.contrast, background.brightness)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = stringResource(R.string.preview), style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                PreviewPane(
                    title = stringResource(R.string.custom_background_preview_player),
                    overlayRes = R.drawable.custom_background_preview_player,
                    aspectRatio = PlayerPreviewAspectRatio,
                    imageUri = imageUri,
                    blur = background.blur,
                    colorFilter = colorFilter,
                    modifier = Modifier.weight(1f),
                )
                PreviewPane(
                    title = stringResource(R.string.custom_background_preview_lyrics),
                    overlayRes = R.drawable.custom_background_preview_lyrics,
                    aspectRatio = LyricsPreviewAspectRatio,
                    imageUri = imageUri,
                    blur = background.blur,
                    colorFilter = colorFilter,
                    modifier = Modifier.weight(1f),
                )
            }
            if (imageUri == null) {
                Text(
                    text = stringResource(R.string.custom_background_preview_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PreviewPane(
    title: String,
    overlayRes: Int,
    aspectRatio: Float,
    imageUri: Uri?,
    blur: Float,
    colorFilter: ColorFilter,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            if (imageUri == null) {
                Icon(
                    imageVector = Icons.Filled.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp),
                )
            } else {
                AsyncImage(
                    model = imageUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = colorFilter,
                    modifier = Modifier.matchParentSize().blur(blur.dp),
                )
                Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = CustomBackgroundScrimAlpha)))
                Image(
                    painter = painterResource(overlayRes),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
    }
}

@Composable
private fun ImageActions(
    hasImage: Boolean,
    onChooseImage: () -> Unit,
    onRemoveImage: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(
            onClick = onChooseImage,
            shapes = buttonPressShapes(),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(if (hasImage) Icons.Filled.Edit else Icons.Filled.Image, contentDescription = null)
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(if (hasImage) R.string.custom_background_replace_image else R.string.custom_background_add_image))
        }
        if (hasImage) {
            OutlinedButton(
                onClick = onRemoveImage,
                shapes = buttonPressShapes(),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.custom_background_remove_image))
            }
        }
    }
}

@Composable
private fun AdjustmentSection(
    blur: Float,
    contrast: Float,
    brightness: Float,
    onBlurChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onBlurChangeFinished: () -> Unit,
    onContrastChangeFinished: () -> Unit,
    onBrightnessChangeFinished: () -> Unit,
    onReset: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.custom_background_adjustments),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            text = stringResource(R.string.custom_background_adjustments_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            AdjustmentSlider(
                label = stringResource(R.string.custom_background_blur),
                valueLabel = stringResource(R.string.custom_background_blur_value, blur.roundToInt()),
                value = blur,
                valueRange = CustomBackground.BLUR_RANGE,
                onValueChange = onBlurChange,
                onValueChangeFinished = onBlurChangeFinished,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            AdjustmentSlider(
                label = stringResource(R.string.custom_background_contrast),
                valueLabel = stringResource(R.string.custom_background_scale_value, (contrast * 100f).roundToInt()),
                value = contrast,
                valueRange = CustomBackground.TONE_RANGE,
                onValueChange = onContrastChange,
                onValueChangeFinished = onContrastChangeFinished,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            AdjustmentSlider(
                label = stringResource(R.string.custom_background_brightness),
                valueLabel = stringResource(R.string.custom_background_scale_value, (brightness * 100f).roundToInt()),
                value = brightness,
                valueRange = CustomBackground.TONE_RANGE,
                onValueChange = onBrightnessChange,
                onValueChangeFinished = onBrightnessChangeFinished,
            )
        }
        OutlinedButton(
            onClick = onReset,
            enabled = blur != DefaultBlur || contrast != DefaultTone || brightness != DefaultTone,
            shapes = buttonPressShapes(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.reset))
        }
    }
}

@Composable
private fun AdjustmentSlider(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(text = label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(text = valueLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
    }
}

/** Keeps the picked image readable across restarts; false when the provider will not allow it. */
private fun persistReadPermission(context: Context, uri: Uri): Boolean =
    try {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    } catch (_: SecurityException) {
        false
    }

/** Lets go of whatever grant is held on [uriString]; false when it could not be released. */
private fun releaseReadPermission(context: Context, uriString: String): Boolean {
    val uri = Uri.parse(uriString)
    val permission = context.contentResolver.persistedUriPermissions.firstOrNull { it.uri == uri } ?: return true
    val flags = (if (permission.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
        (if (permission.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
    if (flags == 0) return true
    return try {
        context.contentResolver.releasePersistableUriPermission(uri, flags)
        true
    } catch (_: SecurityException) {
        false
    }
}
