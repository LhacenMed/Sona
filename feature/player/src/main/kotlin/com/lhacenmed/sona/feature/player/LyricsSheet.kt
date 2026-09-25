@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.player

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.palette.graphics.Palette
import com.lhacenmed.sona.core.designsystem.theme.iconButtonPressShapes
import com.lhacenmed.sona.core.designsystem.theme.pillShape
import com.lhacenmed.sona.core.designsystem.theme.roundedShape
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.lhacenmed.sona.core.datastore.LyricsBackgroundStyle
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.PlaybackUiState
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private val AppleMusicFallbackGradient =
    listOf(
        Color(0xFF202020),
        Color(0xFF141414),
        Color(0xFF050505),
    )

/**
 * Grows the lyrics sheet up out of the player and shrinks it back down - ArchiveTune's
 * `MikoLyricsTransition`. Nothing is composed while it is fully hidden.
 */
@Composable
internal fun LyricsSheetTransition(
    visible: Boolean,
    backHandlerEnabled: Boolean,
    track: Track,
    playback: PlaybackUiState,
    durationMs: Long,
    lyricsSyncOffset: Int,
    onLyricsSyncOffsetChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec =
            spring(
                dampingRatio = 0.82f,
                stiffness = Spring.StiffnessMediumLow,
            ),
        label = "lyricsSheetTransition",
    )

    val boundedProgress = progress.coerceIn(0f, 1f)

    if (visible || boundedProgress > 0.001f) {
        val scaleX = 0.92f + (0.08f * boundedProgress)
        val scaleY = 0.78f + (0.22f * boundedProgress)
        val alpha = (0.2f + (0.8f * boundedProgress)).coerceIn(0f, 1f)
        val cornerRadius = 32.dp * (1f - boundedProgress)

        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .graphicsLayer { this.alpha = boundedProgress }
                    .background(Color.Black.copy(alpha = 0.24f * boundedProgress)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            this.scaleX = scaleX
                            this.scaleY = scaleY
                            this.alpha = alpha
                            translationY = size.height * 0.16f * (1f - boundedProgress)
                        }.clip(roundedShape(cornerRadius))
                        .background(MaterialTheme.colorScheme.surface),
            ) {
                LyricsSheet(
                    track = track,
                    playback = playback,
                    durationMs = durationMs,
                    lyricsSyncOffset = lyricsSyncOffset,
                    onLyricsSyncOffsetChange = onLyricsSyncOffsetChange,
                    onBackClick = onDismiss,
                    viewModel = viewModel,
                    backHandlerEnabled = backHandlerEnabled,
                )
            }
        }
    }
}

/**
 * The current track's lyrics over its artwork, with the transport controls beneath them - ArchiveTune's
 * player `LyricsScreen`, with the V2 renderer.
 */
@Composable
private fun LyricsSheet(
    track: Track,
    playback: PlaybackUiState,
    durationMs: Long,
    lyricsSyncOffset: Int,
    onLyricsSyncOffsetChange: (Int) -> Unit,
    onBackClick: () -> Unit,
    viewModel: PlayerViewModel,
    backHandlerEnabled: Boolean,
    lyricsViewModel: LyricsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val view = LocalView.current

    val deviceMusicVolumeController = rememberDeviceMusicVolumeController()
    val onVolumeChange =
        remember(deviceMusicVolumeController) {
            { volume: Float ->
                deviceMusicVolumeController.setVolumeFraction(volume)
            }
        }
    val currentLyrics by remember(track.id) { lyricsViewModel.lyrics(track.id) }
        .collectAsStateWithLifecycle(initialValue = null)
    val lyrics = currentLyrics?.lyrics

    val lyricsBackground by lyricsViewModel.lyricsBackgroundStyle.collectAsStateWithLifecycle()
    val foregroundColor =
        if (lyricsBackground == LyricsBackgroundStyle.FOLLOW_THEME) {
            MaterialTheme.colorScheme.onSurface
        } else {
            Color.White
        }
    val showPlayerControls by lyricsViewModel.showLyricsPlayerControls.collectAsStateWithLifecycle()

    LaunchedEffect(track.id, lyrics) {
        if (currentLyrics != null) return@LaunchedEffect
        lyricsViewModel.loadLyrics(track)
    }

    var position by remember(track.id) { mutableLongStateOf(viewModel.currentPositionMs()) }
    var sliderPosition by remember(track.id) { mutableStateOf<Long?>(null) }
    var gradientColors by remember(track.coverArtUri) { mutableStateOf(AppleMusicFallbackGradient) }

    val gradientColorsCache =
        remember {
            object : LinkedHashMap<String, List<Color>>(20, 0.75f, true) {
                override fun removeEldestEntry(eldest: Map.Entry<String, List<Color>>) = size > 20
            }
        }
    val fallbackColor = remember { Color.Black.toArgb() }

    LaunchedEffect(track.id, track.coverArtUri, lyricsBackground) {
        if (lyricsBackground == LyricsBackgroundStyle.FOLLOW_THEME) {
            gradientColors = AppleMusicFallbackGradient
            return@LaunchedEffect
        }
        val coverArtUri = track.coverArtUri
        if (coverArtUri == null) {
            gradientColors = AppleMusicFallbackGradient
            return@LaunchedEffect
        }

        gradientColorsCache[coverArtUri]?.let {
            gradientColors = it
            return@LaunchedEffect
        }

        gradientColors = AppleMusicFallbackGradient

        val request =
            ImageRequest
                .Builder(context)
                .data(coverArtUri)
                .size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE)
                .allowHardware(false)
                .build()

        val extractedColors =
            try {
                val image =
                    withContext(Dispatchers.IO) {
                        context.imageLoader.execute(request)
                    }.image
                if (image == null) {
                    null
                } else {
                    val bitmap = image.toBitmap()
                    val palette =
                        withContext(Dispatchers.Default) {
                            Palette
                                .from(bitmap)
                                .maximumColorCount(PlayerColorExtractor.Config.MAX_COLOR_COUNT)
                                .resizeBitmapArea(PlayerColorExtractor.Config.BITMAP_AREA)
                                .generate()
                        }
                    PlayerColorExtractor.extractGradientColors(
                        palette = palette,
                        fallbackColor = fallbackColor,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }

        gradientColors = extractedColors ?: AppleMusicFallbackGradient
        gradientColorsCache[coverArtUri] = gradientColors
    }

    // Followed only while the activity is started, as the player's own position is.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(track.id, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                position = viewModel.currentPositionMs().coerceAtLeast(0L)
                delay(250)
            }
        }
    }

    var showLyricsMenu by remember { mutableStateOf(false) }

    val isLoading = playback.isBuffering || sliderPosition != null
    val orientation = LocalConfiguration.current.orientation

    BackHandler(enabled = backHandlerEnabled, onBack = onBackClick)

    val controls: @Composable (Modifier) -> Unit = { controlsModifier ->
        AppleMusicControls(
            position = position,
            duration = durationMs,
            sliderPosition = sliderPosition,
            isPlaying = playback.isPlaying,
            isLoading = isLoading,
            volume = deviceMusicVolumeController.volumeFraction,
            onPositionChange = { sliderPosition = it },
            onPositionChangeFinished = {
                sliderPosition?.let {
                    viewModel.onSeek(it)
                    position = it
                }
                sliderPosition = null
            },
            onVolumeChange = onVolumeChange,
            onPreviousClick = {
                view.performContextClick()
                viewModel.onSkipPrevious()
            },
            onPlayPauseClick = {
                view.performContextClick()
                viewModel.onTogglePlayPause()
            },
            onNextClick = {
                view.performContextClick()
                viewModel.onSkipNext()
            },
            foregroundColor = foregroundColor,
            modifier = controlsModifier,
        )
    }
    val lyricsPane: @Composable (Modifier) -> Unit = { paneModifier ->
        LyricsV2(
            track = track,
            lyrics = lyrics,
            durationMs = durationMs,
            positionMsProvider = viewModel::currentPositionMs,
            sliderPositionProvider = { sliderPosition },
            lyricsSyncOffset = lyricsSyncOffset,
            onSeek = viewModel::onSeek,
            textColor = foregroundColor,
            lyricsViewModel = lyricsViewModel,
            modifier =
                paneModifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LyricsSheetBackground(
            style = lyricsBackground,
            coverArtUri = track.coverArtUri,
            gradientColors = gradientColors,
        )

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .consumeUnhandledPointerInput(),
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            AppleMusicGrabber(onClick = onBackClick)
            AppleMusicTrackHeader(
                track = track,
                foregroundColor = foregroundColor,
                onMoreClick = { showLyricsMenu = true },
                onDismissClick = onBackClick,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
            )

            if (orientation == Configuration.ORIENTATION_LANDSCAPE && showPlayerControls) {
                Row(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 36.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    lyricsPane(
                        Modifier
                            .weight(1.15f)
                            .fillMaxHeight()
                            .padding(end = 32.dp),
                    )

                    Column(
                        modifier =
                            Modifier
                                .weight(0.85f)
                                .widthIn(max = 420.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        controls(Modifier.fillMaxWidth())
                    }
                }
            } else {
                lyricsPane(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )

                if (showPlayerControls) {
                    controls(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 40.dp),
                    )
                }
            }
        }
    }

    if (showLyricsMenu) {
        LyricsMenuSheet(
            track = track,
            lyrics = lyrics,
            lyricsSyncOffset = lyricsSyncOffset,
            onLyricsSyncOffsetChange = onLyricsSyncOffsetChange,
            showPlayerControls = showPlayerControls,
            onShowPlayerControlsChange = lyricsViewModel::setShowLyricsPlayerControls,
            onEditLyrics = { lyricsViewModel.updateLyrics(track.id, it) },
            onDismissRequest = { showLyricsMenu = false },
        )
    }
}

/** Keeps touches from falling through the sheet to the player beneath it. */
private fun Modifier.consumeUnhandledPointerInput(): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                event.changes.forEach { pointerInputChange ->
                    if (!pointerInputChange.isConsumed) {
                        pointerInputChange.consume()
                    }
                }
            }
        }
    }

@Composable
private fun LyricsSheetBackground(
    style: LyricsBackgroundStyle,
    coverArtUri: String?,
    gradientColors: List<Color>,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    if (style == LyricsBackgroundStyle.FOLLOW_THEME) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        Color.Black
                    },
                ),
    ) {
        when (style) {
            LyricsBackgroundStyle.DEFAULT -> {
                AppleMusicBackground(
                    coverArtUri = coverArtUri,
                    gradientColors = gradientColors,
                )
            }

            LyricsBackgroundStyle.FOLLOW_THEME -> Unit

            LyricsBackgroundStyle.COLORING -> {
                ColoringBackground(gradientColors = gradientColors)
            }
        }
    }
}

@Composable
private fun AppleMusicBackground(
    coverArtUri: String?,
    gradientColors: List<Color>,
    modifier: Modifier = Modifier,
) {
    val colors = gradientColors.ifEmpty { AppleMusicFallbackGradient }
    val backgroundBrush =
        remember(colors) {
            Brush.verticalGradient(
                listOf(
                    colors.getOrElse(0) { AppleMusicFallbackGradient[0] }.copy(alpha = 0.88f),
                    colors.getOrElse(1) { AppleMusicFallbackGradient[1] }.copy(alpha = 0.76f),
                    colors.getOrElse(2) { AppleMusicFallbackGradient[2] }.copy(alpha = 0.96f),
                ),
            )
        }
    val bottomScrim =
        remember {
            Brush.verticalGradient(
                listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.28f),
                ),
            )
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(AppleMusicFallbackGradient.last()),
    ) {
        AnimatedContent(
            targetState = coverArtUri,
            transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(700)) },
            label = "lyrics-apple-background",
        ) { artworkUri ->
            if (artworkUri != null) {
                AsyncImage(
                    model = artworkUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .blur(46.dp)
                            .alpha(0.62f),
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(backgroundBrush),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f)),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(bottomScrim),
        )
    }
}

/** The artwork's dominant colour, darkened down the sheet - ArchiveTune's `COLORING` player background. */
@Composable
private fun ColoringBackground(gradientColors: List<Color>) {
    AnimatedContent(
        targetState = gradientColors,
        transitionSpec = {
            fadeIn(tween(1000)) togetherWith fadeOut(tween(1000))
        },
        label = "lyrics-coloring-background",
    ) { colors ->
        if (colors.isNotEmpty()) {
            val baseColor = ensureComfortableColor(colors.first())
            val gradientStops = buildColoringStops(baseColor)
            Box(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize().background(baseColor))
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(colorStops = gradientStops)),
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.25f)),
                )
            }
        }
    }
}

private fun ensureComfortableColor(
    color: Color,
    minBrightness: Float = 0.15f,
    maxBrightness: Float = 0.58f,
    minSaturation: Float = 0.32f,
): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[1] = hsv[1].coerceAtLeast(minSaturation)
    hsv[2] = hsv[2].coerceIn(minBrightness, maxBrightness)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun darkenColor(
    color: Color,
    factor: Float,
): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[2] = (hsv[2] * factor).coerceAtLeast(0f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun buildColoringStops(baseColor: Color): Array<Pair<Float, Color>> {
    val comfortable = ensureComfortableColor(baseColor, minBrightness = 0.18f, maxBrightness = 0.5f)
    val mid = darkenColor(comfortable, 0.82f)
    val deep = darkenColor(comfortable, 0.6f)
    return arrayOf(
        0f to comfortable.copy(alpha = 0.97f),
        0.4f to mid.copy(alpha = 0.94f),
        0.75f to deep.copy(alpha = 0.92f),
        1f to Color.Black.copy(alpha = 0.88f),
    )
}

@Composable
private fun AppleMusicGrabber(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val closeDescription = stringResource(R.string.player_close)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(44.dp)
                .semantics { contentDescription = closeDescription }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
    )
}

@Composable
private fun AppleMusicTrackHeader(
    track: Track,
    foregroundColor: Color,
    onMoreClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(58.dp)
                    .clip(roundedShape(7.dp))
                    .background(foregroundColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = track.coverArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (track.coverArtUri == null) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = foregroundColor.copy(alpha = 0.72f),
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = foregroundColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = foregroundColor.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        AppleMusicHeaderIconButton(
            iconRes = R.drawable.close,
            contentDescription = stringResource(R.string.player_close),
            foregroundColor = foregroundColor,
            onClick = onDismissClick,
        )

        Spacer(modifier = Modifier.width(4.dp))

        AppleMusicHeaderIconButton(
            iconRes = R.drawable.more_horiz,
            contentDescription = stringResource(R.string.player_more_options),
            foregroundColor = foregroundColor,
            onClick = onMoreClick,
        )
    }
}

@Composable
private fun AppleMusicHeaderIconButton(
    iconRes: Int,
    contentDescription: String,
    foregroundColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(48.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 24.dp),
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(pillShape)
                    .background(foregroundColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = foregroundColor,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun AppleMusicControls(
    position: Long,
    duration: Long,
    sliderPosition: Long?,
    isPlaying: Boolean,
    isLoading: Boolean,
    volume: Float,
    onPositionChange: (Long) -> Unit,
    onPositionChangeFinished: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    foregroundColor: Color,
    modifier: Modifier = Modifier,
) {
    val hasDuration = duration > 0L
    val safeDuration = if (hasDuration) duration else 1L
    val currentPosition = (sliderPosition ?: position).coerceIn(0L, safeDuration)
    val remainingPosition = (safeDuration - currentPosition).coerceAtLeast(0L)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppleMusicSlider(
            value = currentPosition.toFloat(),
            valueRange = 0f..safeDuration.toFloat(),
            activeColor = foregroundColor.copy(alpha = 0.94f),
            inactiveColor = foregroundColor.copy(alpha = 0.28f),
            trackHeight = 8.dp,
            onValueChange = { onPositionChange(it.toLong()) },
            onValueChangeFinished = onPositionChangeFinished,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = makeTimeString(currentPosition),
                style = MaterialTheme.typography.labelMedium,
                color = foregroundColor.copy(alpha = 0.54f),
            )
            Text(
                text = if (hasDuration) "-${makeTimeString(remainingPosition)}" else "",
                style = MaterialTheme.typography.labelMedium,
                color = foregroundColor.copy(alpha = 0.54f),
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 26.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppleMusicTransportButton(
                iconRes = R.drawable.skip_previous,
                contentDescription = stringResource(R.string.player_previous),
                iconSize = 44.dp,
                touchSize = 68.dp,
                foregroundColor = foregroundColor,
                onClick = onPreviousClick,
            )
            IconButton(
                onClick = onPlayPauseClick,
                shapes = iconButtonPressShapes(),
                modifier = Modifier.size(74.dp),
            ) {
                if (isLoading) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(42.dp),
                        color = foregroundColor,
                    )
                } else {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription =
                            if (isPlaying) {
                                stringResource(R.string.player_pause)
                            } else {
                                stringResource(R.string.player_play)
                            },
                        tint = foregroundColor,
                        modifier = Modifier.size(54.dp),
                    )
                }
            }
            AppleMusicTransportButton(
                iconRes = R.drawable.skip_next,
                contentDescription = stringResource(R.string.player_next),
                iconSize = 44.dp,
                touchSize = 68.dp,
                foregroundColor = foregroundColor,
                onClick = onNextClick,
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 26.dp, bottom = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.volume_off),
                contentDescription = stringResource(R.string.player_minimum_volume),
                tint = foregroundColor.copy(alpha = 0.66f),
                modifier = Modifier.size(17.dp),
            )
            AppleMusicSlider(
                value = volume.coerceIn(0f, 1f),
                valueRange = 0f..1f,
                activeColor = foregroundColor.copy(alpha = 0.88f),
                inactiveColor = foregroundColor.copy(alpha = 0.24f),
                trackHeight = 8.dp,
                onValueChange = onVolumeChange,
                onValueChangeFinished = {},
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
            )
            Icon(
                painter = painterResource(R.drawable.volume_up),
                contentDescription = stringResource(R.string.player_maximum_volume),
                tint = foregroundColor.copy(alpha = 0.66f),
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun AppleMusicTransportButton(
    iconRes: Int,
    contentDescription: String?,
    iconSize: Dp,
    touchSize: Dp,
    foregroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        shapes = iconButtonPressShapes(),
        modifier = modifier.size(touchSize),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = foregroundColor,
            modifier = Modifier.size(iconSize),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppleMusicSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    activeColor: Color,
    inactiveColor: Color,
    trackHeight: Dp,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeStart = valueRange.start
    val safeEnd = valueRange.endInclusive.coerceAtLeast(safeStart + 1f)
    val safeRange = safeStart..safeEnd
    val sliderColors =
        SliderDefaults.colors(
            activeTrackColor = activeColor,
            activeTickColor = activeColor,
            thumbColor = Color.Transparent,
            inactiveTrackColor = inactiveColor,
        )

    Slider(
        value = value.coerceIn(safeRange),
        valueRange = safeRange,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        colors = sliderColors,
        thumb = { Spacer(modifier = Modifier.size(0.dp)) },
        track = { sliderState ->
            PlayerSliderTrack(
                sliderState = sliderState,
                colors = sliderColors,
                trackHeight = trackHeight,
            )
        },
        modifier = modifier.height(28.dp),
    )
}
