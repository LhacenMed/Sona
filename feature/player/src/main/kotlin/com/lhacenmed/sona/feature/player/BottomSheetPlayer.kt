@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.lhacenmed.sona.feature.player

import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.lhacenmed.sona.core.datastore.PlayerSliderStyle
import com.lhacenmed.sona.core.datastore.PlayerStyle
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.SleepTimerState
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private const val SeekbarSettleToleranceMs = 1_500L
private const val PositionPollIntervalMs = 100L
private const val KeyboardSeekStepMs = 5_000L
private const val KeyboardVolumeStep = 0.05f

/** How strongly the Immersive backdrops blur the cover - ArchiveTune's default backdrop blur amount, out of 100. */
private const val BackdropBlurAmount = 60
private const val ImmersiveBackdropBlurDp = 44
private const val ImmersiveBackdropBlurScale = 1.18f
private const val ImmersiveSharpStagePortraitFraction = 0.62f
private const val ImmersiveSharpStageLandscapeFraction = 0.58f
private const val ImmersiveBackdropOverlapDp = 72
private const val ImmersiveSharpStageBottomScrimStartFraction = 0.40f
private const val ImmersiveBackdropFloorStartFraction = 0.88f

/**
 * The full player: the mini player as the sheet's collapsed content, expanding into the chosen player
 * style over its queue. Ported from ArchiveTune's `BottomSheetPlayer`.
 */
@Composable
internal fun BottomSheetPlayer(
    state: BottomSheetState,
    uiState: PlayerUiState,
    playerStyle: PlayerStyle,
    sliderStyle: PlayerSliderStyle,
    sleepTimer: SleepTimerState,
    viewModel: PlayerViewModel,
    onGoToAlbum: (Long) -> Unit,
    onGoToArtist: (Long) -> Unit,
    trackOptionsSheet: @Composable (track: Track, onDismissRequest: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val playback = uiState.playback
    val track = uiState.currentTrack
    val useDarkTheme = isSystemInDarkTheme()

    val deviceMusicVolumeController = rememberDeviceMusicVolumeController()
    val onPlayerVolumeChange =
        remember(deviceMusicVolumeController) {
            { volume: Float ->
                deviceMusicVolumeController.setVolumeFraction(volume)
            }
        }

    val duration = track?.durationMs?.takeIf { it > 0L } ?: playback.durationMs
    val latestDuration by rememberUpdatedState(duration)
    var position by remember(track?.id) { mutableLongStateOf(viewModel.currentPositionMs()) }
    var lyricsSyncOffset by rememberSaveable(track?.id) { mutableIntStateOf(0) }
    var sliderPosition by remember(track?.id) { mutableStateOf<Long?>(null) }
    var isUserSeeking by remember(track?.id) { mutableStateOf(false) }

    // Track loading state: when buffering or when user is seeking
    val isLoading = playback.isBuffering || sliderPosition != null

    // Followed only while the activity is started: a player in an activity left behind in the back stack
    // has nothing to show and nobody to show it to.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(track?.id, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                val currentPlayerPosition = viewModel.currentPositionMs()
                position = currentPlayerPosition
                if (!isUserSeeking) {
                    sliderPosition?.let { targetPosition ->
                        val clampedTargetPosition =
                            if (latestDuration > 0L) {
                                targetPosition.coerceIn(0L, latestDuration)
                            } else {
                                targetPosition.coerceAtLeast(0L)
                            }
                        if (abs(currentPlayerPosition - clampedTargetPosition) <= SeekbarSettleToleranceMs) {
                            sliderPosition = null
                        }
                    }
                }
                delay(PositionPollIntervalMs)
            }
        }
    }

    var gradientColors by remember {
        mutableStateOf<List<Color>>(emptyList())
    }

    // Cache for gradient colors to prevent re-extraction for same songs
    val gradientColorsCache = remember { mutableMapOf<Long, List<Color>>() }

    // Default gradient colors for fallback
    val defaultGradientColors = listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant)
    val fallbackColor = MaterialTheme.colorScheme.surface.toArgb()

    LaunchedEffect(track?.id, track?.coverArtUri, playerStyle) {
        val coverArtUri = track?.coverArtUri
        if (playerStyle != PlayerStyle.EDITORIAL || track == null || coverArtUri == null) {
            gradientColors = emptyList()
            return@LaunchedEffect
        }

        val cachedColors = gradientColorsCache[track.id]
        if (cachedColors != null) {
            gradientColors = cachedColors
            return@LaunchedEffect
        }

        val request =
            ImageRequest
                .Builder(context)
                .data(coverArtUri)
                .size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE)
                .allowHardware(false)
                .build()
        val bitmap =
            runCatching {
                withContext(Dispatchers.IO) {
                    context.imageLoader.execute(request)
                }.image?.toBitmap()
            }.getOrNull()

        gradientColors =
            if (bitmap != null) {
                val palette =
                    withContext(Dispatchers.Default) {
                        Palette
                            .from(bitmap)
                            .maximumColorCount(PlayerColorExtractor.Config.MAX_COLOR_COUNT)
                            .resizeBitmapArea(PlayerColorExtractor.Config.BITMAP_AREA)
                            .generate()
                    }
                PlayerColorExtractor
                    .extractGradientColors(palette = palette, fallbackColor = fallbackColor)
                    .also { gradientColorsCache[track.id] = it }
            } else {
                defaultGradientColors
            }
    }

    val dominantColor = gradientColors.firstOrNull() ?: MaterialTheme.colorScheme.primary
    val targetBgColor =
        remember(dominantColor, useDarkTheme) {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(dominantColor.toArgb(), hsv)
            if (useDarkTheme) {
                hsv[1] = hsv[1].coerceIn(0.12f, 0.35f)
                hsv[2] = 0.08f
            } else {
                hsv[1] = hsv[1].coerceIn(0.04f, 0.12f)
                hsv[2] = 0.96f
            }
            Color(android.graphics.Color.HSVToColor(hsv))
        }
    val dynamicBgColor by animateColorAsState(
        targetValue = targetBgColor,
        animationSpec = tween(durationMillis = 800),
        label = "dynamicBgColor",
    )

    val dynamicAccentColor by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(durationMillis = 800),
        label = "dynamicAccentColor",
    )

    val targetTextColor =
        remember(dominantColor, useDarkTheme) {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(dominantColor.toArgb(), hsv)
            if (useDarkTheme) {
                hsv[1] = hsv[1].coerceAtMost(0.12f)
                hsv[2] = 0.96f
            } else {
                hsv[1] = hsv[1].coerceIn(0.12f, 0.35f)
                hsv[2] = 0.08f
            }
            Color(android.graphics.Color.HSVToColor(hsv))
        }
    val dynamicTextColor by animateColorAsState(
        targetValue = targetTextColor,
        animationSpec = tween(durationMillis = 800),
        label = "dynamicTextColor",
    )

    val targetIconButtonColor =
        remember(dynamicAccentColor) {
            val luminance = 0.299f * dynamicAccentColor.red + 0.587f * dynamicAccentColor.green + 0.114f * dynamicAccentColor.blue
            if (luminance > 0.5f) Color.Black else Color.White
        }
    val dynamicIconButtonColor by animateColorAsState(
        targetValue = targetIconButtonColor,
        animationSpec = tween(durationMillis = 800),
        label = "dynamicIconButtonColor",
    )

    val textBackgroundColor =
        when (playerStyle) {
            PlayerStyle.EDITORIAL -> dynamicTextColor
            PlayerStyle.IMMERSIVE, PlayerStyle.IMMERSIVE_EXTENDED -> Color.White
            PlayerStyle.CLASSIC, PlayerStyle.MINIMAL, PlayerStyle.CINEMATIC -> MaterialTheme.colorScheme.onBackground
        }

    val icBackgroundColor =
        when (playerStyle) {
            PlayerStyle.EDITORIAL -> dynamicBgColor
            PlayerStyle.IMMERSIVE, PlayerStyle.IMMERSIVE_EXTENDED -> Color.Black
            PlayerStyle.CLASSIC, PlayerStyle.MINIMAL, PlayerStyle.CINEMATIC -> MaterialTheme.colorScheme.surface
        }

    val (textButtonColor, iconButtonColor) =
        when (playerStyle) {
            PlayerStyle.EDITORIAL -> dynamicAccentColor to dynamicIconButtonColor
            PlayerStyle.IMMERSIVE, PlayerStyle.IMMERSIVE_EXTENDED -> Color.White to Color.Black
            PlayerStyle.CLASSIC, PlayerStyle.MINIMAL, PlayerStyle.CINEMATIC -> textBackgroundColor to icBackgroundColor
        }

    val sheetColor =
        when (playerStyle) {
            PlayerStyle.EDITORIAL -> dynamicBgColor
            PlayerStyle.IMMERSIVE, PlayerStyle.IMMERSIVE_EXTENDED -> Color.Black
            PlayerStyle.CLASSIC, PlayerStyle.MINIMAL, PlayerStyle.CINEMATIC -> MaterialTheme.colorScheme.surface
        }

    val dynamicQueuePeekHeight =
        if (playerStyle == PlayerStyle.EDITORIAL) {
            88.dp + (if (sleepTimer.isActive) 42.dp else 0.dp)
        } else {
            QueuePeekHeight
        }

    val dismissedBound = dynamicQueuePeekHeight + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

    val queueSheetState =
        rememberBottomSheetState(
            dismissedBound = dismissedBound,
            expandedBound = state.expandedBound,
            collapsedBound = dismissedBound,
            initialAnchor = DISMISSED_ANCHOR,
        )

    var isLyricsSheetVisible by rememberSaveable { mutableStateOf(false) }
    val openQueue =
        remember(state, queueSheetState) {
            {
                isLyricsSheetVisible = false
                if (!state.isExpandedOrExpanding) {
                    state.expandSoft()
                }
                queueSheetState.expandSoft()
            }
        }

    BackHandler(
        enabled =
            queueSheetState.isExpandedOrExpanding ||
                state.isExpandedOrExpanding,
    ) {
        when {
            isLyricsSheetVisible && state.isExpandedOrExpanding -> isLyricsSheetVisible = false
            queueSheetState.isExpandedOrExpanding -> queueSheetState.collapseSoft()
            state.isExpandedOrExpanding -> state.collapseSoft()
        }
    }

    var menuTrack by remember { mutableStateOf<Track?>(null) }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(state.isExpanded) {
        if (state.isExpanded) {
            focusRequester.requestFocus()
        }
    }

    BottomSheet(
        state = state,
        // A swipe up anywhere on the open player raises the queue, not only one begun on its bar.
        swipeUpSheet = queueSheetState,
        modifier =
            modifier
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type != KeyEventType.KeyDown || state.isCollapsed) return@onKeyEvent false

                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            viewModel.onSeek((viewModel.currentPositionMs() - KeyboardSeekStepMs).coerceAtLeast(0))
                            true
                        }

                        Key.DirectionRight -> {
                            viewModel.onSeek((viewModel.currentPositionMs() + KeyboardSeekStepMs).coerceAtMost(duration))
                            true
                        }

                        Key.DirectionUp -> {
                            deviceMusicVolumeController.setVolumeFraction(
                                (deviceMusicVolumeController.volumeFraction + KeyboardVolumeStep).coerceAtMost(1f),
                            )
                            true
                        }

                        Key.DirectionDown -> {
                            deviceMusicVolumeController.setVolumeFraction(
                                (deviceMusicVolumeController.volumeFraction - KeyboardVolumeStep).coerceAtLeast(0f),
                            )
                            true
                        }

                        Key.Spacebar -> {
                            viewModel.onTogglePlayPause()
                            true
                        }

                        Key.N -> {
                            if (keyEvent.isShiftPressed) {
                                viewModel.onSkipNext()
                                true
                            } else {
                                false
                            }
                        }

                        Key.P -> {
                            if (keyEvent.isShiftPressed) {
                                viewModel.onSkipPrevious()
                                true
                            } else {
                                false
                            }
                        }

                        Key.L -> {
                            viewModel.onToggleFavorite()
                            true
                        }

                        else -> {
                            false
                        }
                    }
                },
        backgroundColor = run {
            val progress =
                ((state.value - state.collapsedBound) / (state.expandedBound - state.collapsedBound))
                    .coerceIn(0f, 1f)
            // Only start fading when very close to dismissal (last 20%)
            val fadeProgress =
                if (progress < 0.2f) {
                    ((0.2f - progress) / 0.2f).coerceIn(0f, 1f)
                } else {
                    0f
                }
            sheetColor.copy(alpha = 1f - fadeProgress)
        },
        onDismiss = viewModel::onStopAndClearQueue,
        collapsedContent = {
            MiniPlayer(
                track = track,
                playback = playback,
                position = position,
                duration = duration,
                viewModel = viewModel,
            )
        },
    ) {
        val onSliderValueChange: (Long) -> Unit = {
            isUserSeeking = true
            sliderPosition = it
        }
        val onSliderValueChangeFinished: () -> Unit = {
            sliderPosition?.let {
                viewModel.onSeek(it)
                position = it
            }
            isUserSeeking = false
        }

        if (track != null) {
            val titleActions = rememberPlayerTitleActions(track, state, onGoToAlbum, onGoToArtist)
            val onMenuClick = { menuTrack = track }
            val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

            when (playerStyle) {
                PlayerStyle.IMMERSIVE -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        ImmersivePlayerBackdrop(
                            coverArtUri = track.coverArtUri,
                            landscape = landscape,
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier =
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = queueSheetState.collapsedBound)
                                    .windowInsetsPadding(
                                        WindowInsets.systemBars.only(
                                            if (landscape) {
                                                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                                            } else {
                                                WindowInsetsSides.Horizontal
                                            },
                                        ),
                                    ),
                        ) {
                            ImmersiveControlsContent(
                                track = track,
                                playback = playback,
                                isLoading = isLoading,
                                isFavorite = uiState.isCurrentTrackFavorite,
                                sliderPosition = sliderPosition,
                                position = position,
                                duration = duration,
                                volume = deviceMusicVolumeController.volumeFraction,
                                titleActions = titleActions,
                                onMenuClick = onMenuClick,
                                viewModel = viewModel,
                                onSliderValueChange = onSliderValueChange,
                                onSliderValueChangeFinished = onSliderValueChangeFinished,
                                onVolumeChange = onPlayerVolumeChange,
                                landscape = landscape,
                            )

                            Spacer(Modifier.height(if (landscape) 16.dp else 24.dp))
                        }
                    }
                }

                PlayerStyle.IMMERSIVE_EXTENDED -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        ImmersiveExtendedPlayerBackdrop(coverArtUri = track.coverArtUri)

                        ImmersiveExtendedPlayerContent(
                            track = track,
                            playback = playback,
                            isLoading = isLoading,
                            isFavorite = uiState.isCurrentTrackFavorite,
                            sliderPosition = sliderPosition,
                            position = position,
                            duration = duration,
                            volume = deviceMusicVolumeController.volumeFraction,
                            titleActions = titleActions,
                            onMenuClick = onMenuClick,
                            viewModel = viewModel,
                            onSliderValueChange = onSliderValueChange,
                            onSliderValueChangeFinished = onSliderValueChangeFinished,
                            onVolumeChange = onPlayerVolumeChange,
                            landscape = landscape,
                            modifier = Modifier.playerContentPadding(queueSheetState, state, landscape),
                        )
                    }
                }

                PlayerStyle.EDITORIAL -> {
                    EditorialPlayerContent(
                        track = track,
                        playback = playback,
                        isFavorite = uiState.isCurrentTrackFavorite,
                        sliderPosition = sliderPosition,
                        position = position,
                        duration = duration,
                        textBackgroundColor = textBackgroundColor,
                        textButtonColor = textButtonColor,
                        iconButtonColor = iconButtonColor,
                        titleActions = titleActions,
                        onCollapseClick = state::collapseSoft,
                        onQueueClick = openQueue,
                        onLyricsClick = { isLyricsSheetVisible = true },
                        onMenuClick = onMenuClick,
                        viewModel = viewModel,
                        onSliderValueChange = onSliderValueChange,
                        onSliderValueChangeFinished = onSliderValueChangeFinished,
                        landscape = landscape,
                        modifier = Modifier.playerContentPadding(queueSheetState, state, landscape),
                    )
                }

                PlayerStyle.CLASSIC, PlayerStyle.MINIMAL, PlayerStyle.CINEMATIC -> {
                    val controlsContent: @Composable () -> Unit = {
                        PlayerControlsContent(
                            track = track,
                            playerStyle = playerStyle,
                            sliderStyle = sliderStyle,
                            playback = playback,
                            isLoading = isLoading,
                            isFavorite = uiState.isCurrentTrackFavorite,
                            textButtonColor = textButtonColor,
                            iconButtonColor = iconButtonColor,
                            textBackgroundColor = textBackgroundColor,
                            icBackgroundColor = icBackgroundColor,
                            sliderPosition = sliderPosition,
                            position = position,
                            duration = duration,
                            titleActions = titleActions,
                            onMenuClick = onMenuClick,
                            viewModel = viewModel,
                            onSliderValueChange = onSliderValueChange,
                            onSliderValueChangeFinished = onSliderValueChangeFinished,
                        )
                    }

                    if (landscape) {
                        Row(
                            modifier =
                                Modifier
                                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                                    .padding(bottom = queueSheetState.collapsedBound + 48.dp),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.weight(1f),
                            ) {
                                val thumbnailSize = (LocalConfiguration.current.screenWidthDp * 0.4).dp
                                Thumbnail(
                                    uiState = uiState,
                                    durationMs = duration,
                                    textBackgroundColor = textBackgroundColor,
                                    isPlayerExpanded = state.isExpanded,
                                    viewModel = viewModel,
                                    modifier = Modifier.size(thumbnailSize),
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
                            ) {
                                Spacer(Modifier.weight(1f))

                                controlsContent()

                                Spacer(Modifier.weight(1f))
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier =
                                Modifier
                                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                                    .padding(bottom = queueSheetState.collapsedBound),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.weight(1f),
                            ) {
                                Thumbnail(
                                    uiState = uiState,
                                    durationMs = duration,
                                    textBackgroundColor = textBackgroundColor,
                                    isPlayerExpanded = state.isExpanded,
                                    viewModel = viewModel,
                                )
                            }

                            controlsContent()

                            Spacer(Modifier.height(30.dp))
                        }
                    }
                }
            }
        }

        Queue(
            state = queueSheetState,
            playerBottomSheetState = state,
            uiState = uiState,
            playerStyle = playerStyle,
            sleepTimer = sleepTimer,
            durationMs = duration,
            backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
            onBackgroundColor = MaterialTheme.colorScheme.onSurface,
            textBackgroundColor = textBackgroundColor,
            onMenuClick = { menuTrack = it },
            onShowLyrics = { isLyricsSheetVisible = true },
            viewModel = viewModel,
        )

        track?.let { currentTrack ->
            LyricsSheetTransition(
                visible = isLyricsSheetVisible,
                backHandlerEnabled = isLyricsSheetVisible && state.isExpandedOrExpanding,
                track = currentTrack,
                playback = playback,
                durationMs = duration,
                lyricsSyncOffset = lyricsSyncOffset,
                onLyricsSyncOffsetChange = { lyricsSyncOffset = it },
                onDismiss = { isLyricsSheetVisible = false },
                viewModel = viewModel,
            )
        }
    }

    menuTrack?.let { menuFor ->
        trackOptionsSheet(menuFor) { menuTrack = null }
    }
}

/** The Immersive Extended and Editorial players' clearance: above the queue bar, inside the system bars. */
@Composable
private fun Modifier.playerContentPadding(
    queueSheetState: BottomSheetState,
    state: BottomSheetState,
    landscape: Boolean,
): Modifier =
    fillMaxSize()
        .padding(bottom = queueSheetState.collapsedBound)
        .windowInsetsPadding(
            WindowInsets.systemBars.only(
                if (landscape) {
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                } else {
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                },
            ),
        )

/** Blurs below API 31 would need a bitmap blur ArchiveTune ships separately; there the backdrop is drawn unblurred. */
private val canBlur: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
private fun ImmersiveExtendedPlayerBackdrop(
    coverArtUri: String?,
    modifier: Modifier = Modifier,
) {
    val blurRadiusDp = ImmersiveBackdropBlurDp.dp * (BackdropBlurAmount.toFloat() / 100f)

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        if (coverArtUri != null) {
            AsyncImage(
                model = coverArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .then(if (canBlur) Modifier.blur(blurRadiusDp) else Modifier)
                        .graphicsLayer {
                            scaleX = 1.16f
                            scaleY = 1.16f
                            alpha = 0.66f
                        },
            )
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.52f)),
        )
    }
}

@Composable
private fun ImmersivePlayerBackdrop(
    coverArtUri: String?,
    landscape: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val fallbackColor = Color.Black.toArgb()
    var backdropPalette by remember(coverArtUri, fallbackColor) {
        mutableStateOf(ImmersiveBackdropPalette.fromColors(emptyList(), fallbackColor))
    }

    LaunchedEffect(coverArtUri, fallbackColor) {
        backdropPalette = ImmersiveBackdropPalette.fromColors(emptyList(), fallbackColor)
        if (coverArtUri == null) return@LaunchedEffect

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
                    withContext(Dispatchers.Default) {
                        val palette =
                            Palette
                                .from(image.toBitmap())
                                .maximumColorCount(PlayerColorExtractor.Config.MAX_COLOR_COUNT)
                                .resizeBitmapArea(PlayerColorExtractor.Config.BITMAP_AREA)
                                .generate()
                        val dominantRgb = palette.dominantSwatch?.rgb ?: palette.getDominantColor(fallbackColor)
                        listOf(Color(dominantRgb))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }

        backdropPalette = ImmersiveBackdropPalette.fromColors(extractedColors.orEmpty(), fallbackColor)
    }

    val sharpStageBottomScrim =
        remember(backdropPalette) {
            val blendColor = backdropPalette.bottom
            Brush.verticalGradient(
                colorStops =
                    arrayOf(
                        0f to Color.Transparent,
                        ImmersiveSharpStageBottomScrimStartFraction to Color.Transparent,
                        0.60f to blendColor.copy(alpha = 0.18f),
                        0.76f to blendColor.copy(alpha = 0.52f),
                        0.88f to blendColor.copy(alpha = 0.82f),
                        1f to blendColor,
                    ),
            )
        }
    val backdropFloor =
        remember(backdropPalette) {
            Brush.verticalGradient(
                colorStops =
                    arrayOf(
                        0f to backdropPalette.bottom,
                        ImmersiveBackdropFloorStartFraction to backdropPalette.bottom,
                        1f to backdropPalette.bottom,
                    ),
            )
        }
    val backdropBlurRadius = ImmersiveBackdropBlurDp.dp * (BackdropBlurAmount.toFloat() / 100f)

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .background(backdropPalette.top),
    ) {
        val sharpStageFraction =
            if (landscape) ImmersiveSharpStageLandscapeFraction else ImmersiveSharpStagePortraitFraction
        val sharpStageHeight = maxHeight * sharpStageFraction
        val backdropTopOffset = (sharpStageHeight - ImmersiveBackdropOverlapDp.dp).coerceAtLeast(0.dp)
        val backdropHeight = maxHeight - backdropTopOffset

        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(backdropHeight)
                    .clipToBounds()
                    .background(backdropPalette.bottom),
        ) {
            if (coverArtUri != null) {
                AsyncImage(
                    model = coverArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = ImmersiveBackdropBlurScale
                                scaleY = ImmersiveBackdropBlurScale
                                alpha = if (canBlur) 0.58f else 0.20f
                            }.then(if (canBlur) Modifier.blur(backdropBlurRadius) else Modifier),
                )
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(backdropFloor),
            )
        }

        AnimatedContent(
            targetState = coverArtUri,
            transitionSpec = {
                fadeIn(tween(900)) togetherWith fadeOut(tween(900))
            },
            label = "immersiveBackdrop",
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(sharpStageHeight)
                    .clipToBounds(),
        ) { artworkUri ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(backdropPalette.top),
                contentAlignment = Alignment.Center,
            ) {
                if (artworkUri != null) {
                    AsyncImage(
                        model = artworkUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        Box(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(sharpStageHeight)
                    .background(sharpStageBottomScrim),
        )
    }
}

@Immutable
private data class ImmersiveBackdropPalette(
    val top: Color,
    val bottom: Color,
) {
    companion object {
        fun fromColors(
            colors: List<Color>,
            fallbackColor: Int,
        ): ImmersiveBackdropPalette {
            // Only the dominant hue: the rest of the extracted colours are hue-shifted variants that would
            // not read as one backdrop. The bottom darkens that same hue instead.
            val dominantColor = colors.firstOrNull()
            val fallback = Color(fallbackColor).immersiveBackdropTone(valueMin = 0.12f, valueMax = 0.38f)
            val top = dominantColor?.immersiveBackdropTone(valueMin = 0.20f, valueMax = 0.72f) ?: fallback
            val bottom = dominantColor?.immersiveBackdropTone(valueMin = 0.08f, valueMax = 0.32f) ?: top
            return ImmersiveBackdropPalette(
                top = top,
                bottom = bottom,
            )
        }
    }
}

private fun Color.immersiveBackdropTone(
    valueMin: Float,
    valueMax: Float,
): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[1] =
        if (hsv[1] < 0.12f) {
            hsv[1].coerceAtMost(0.08f)
        } else {
            (hsv[1] * 1.27f).coerceIn(0f, 1f)
        }
    hsv[2] = hsv[2].coerceIn(valueMin, valueMax)
    return Color(android.graphics.Color.HSVToColor(hsv))
}
