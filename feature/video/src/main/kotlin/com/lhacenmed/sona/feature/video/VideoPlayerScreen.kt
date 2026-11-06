package com.lhacenmed.sona.feature.video

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.common.permission.AppPermission
import com.lhacenmed.sona.core.data.contentUri
import com.lhacenmed.sona.core.designsystem.component.toast
import com.lhacenmed.sona.core.designsystem.effect.rememberDeviceMusicVolumeController
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.VideoPlaybackState
import com.lhacenmed.sona.feature.video.capture.captureVideoFrame
import com.lhacenmed.sona.feature.video.capture.saveScreenshot
import com.lhacenmed.sona.feature.video.controls.AmbientProgress
import com.lhacenmed.sona.feature.video.controls.BatteryClock
import com.lhacenmed.sona.feature.video.controls.ResumePrompt
import com.lhacenmed.sona.feature.video.controls.VideoAction
import com.lhacenmed.sona.feature.video.controls.VideoBarButton
import com.lhacenmed.sona.feature.video.controls.VideoButtonGroup
import com.lhacenmed.sona.feature.video.controls.VideoControlSlots
import com.lhacenmed.sona.feature.video.controls.VideoControlsLayout
import com.lhacenmed.sona.feature.video.controls.VideoControlsState
import com.lhacenmed.sona.feature.video.controls.VideoElapsedTime
import com.lhacenmed.sona.feature.video.controls.VideoIcons
import com.lhacenmed.sona.feature.video.controls.VideoPosition
import com.lhacenmed.sona.feature.video.controls.VideoRemainingTime
import com.lhacenmed.sona.feature.video.controls.VideoSeekBar
import com.lhacenmed.sona.feature.video.controls.VideoTopBar
import com.lhacenmed.sona.feature.video.controls.VideoTransport
import com.lhacenmed.sona.feature.video.controls.rememberIsResumePromptDue
import com.lhacenmed.sona.feature.video.controls.rememberVideoPosition
import com.lhacenmed.sona.feature.video.controls.speedButton
import com.lhacenmed.sona.feature.video.controls.videoButton
import com.lhacenmed.sona.feature.video.gesture.GestureFeedback
import com.lhacenmed.sona.feature.video.gesture.GestureFeedbackLayer
import com.lhacenmed.sona.feature.video.gesture.GestureFeedbackState
import com.lhacenmed.sona.feature.video.gesture.ScreenHalf
import com.lhacenmed.sona.feature.video.gesture.TapZone
import com.lhacenmed.sona.feature.video.gesture.VideoGestureActions
import com.lhacenmed.sona.feature.video.gesture.label
import com.lhacenmed.sona.feature.video.gesture.videoGestures
import com.lhacenmed.sona.feature.video.panel.MorePanel
import com.lhacenmed.sona.feature.video.panel.PlayerPanelHost
import com.lhacenmed.sona.feature.video.panel.QueuePanel
import com.lhacenmed.sona.feature.video.panel.SpeedPanel
import com.lhacenmed.sona.feature.video.panel.TracksPanel
import com.lhacenmed.sona.feature.video.panel.VideoPanel
import com.lhacenmed.sona.feature.video.scrub.ScrubFrames
import com.lhacenmed.sona.feature.video.scrub.ScrubPill
import com.lhacenmed.sona.feature.video.scrub.ScrubPreview
import com.lhacenmed.sona.feature.video.scrub.VideoScrubState
import com.lhacenmed.sona.feature.video.subtitle.SubtitleLayer
import com.lhacenmed.sona.feature.video.subtitle.SubtitleSource
import com.lhacenmed.sona.feature.video.surface.VideoFrame
import com.lhacenmed.sona.feature.video.surface.VideoSurface
import com.lhacenmed.sona.feature.video.surface.VideoZoomState
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The level the drag under way has reached - kept across its reports and recompositions. */
private class DragProgress {
    var volumeTarget: Float? = null
}

/**
 * The video player: the picture, the subtitles over it, the gestures' feedback over those, then the controls, which a
 * tap shows and hides, and any panel over all of it.
 *
 * Built so nothing that moves composes this screen: a finger's scrub, a level's drag, the position's tick and the
 * subtitles are each followed by state read only where they are drawn or laid out. This composes again only as
 * playback itself changes - a new video, play or pause - or a control comes or goes.
 *
 * Locking the controls swaps them for the way to unlock alone. [isFloating], in its floating window, it is the picture
 * and its subtitles alone - the window has controls of its own. [onFloat], where the device has floating windows, puts
 * it in one.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun VideoPlayerScreen(viewModel: VideoPlayerViewModel, onClose: () -> Unit, isFloating: Boolean, onFloat: (() -> Unit)?) {
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
    val showClock by viewModel.showClock.collectAsStateWithLifecycle()
    val subtitles by viewModel.subtitles.state.collectAsStateWithLifecycle()
    val subtitleStyle by viewModel.subtitleStyle.collectAsStateWithLifecycle()
    val current = playback.current
    val windowSize = LocalWindowInfo.current.containerSize
    val isLandscape = windowSize.width > windowSize.height

    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val controls = remember { VideoControlsState() }
    val zoom = remember { VideoZoomState() }
    val feedback = remember(scope) { GestureFeedbackState(scope) }
    val scrub = remember(viewModel) {
        VideoScrubState(viewModel::onSeek, onSnappedToOrigin = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) })
    }
    val brightness = rememberWindowBrightness()
    val volume = rememberDeviceMusicVolumeController()
    var panel by remember { mutableStateOf<VideoPanel?>(null) }
    val drag = remember { DragProgress() }
    // Held for the screenshot button and the scrubbing preview, which read the frame straight off it.
    val surface = remember { arrayOfNulls<SurfaceView>(1) }
    // Any file: a subtitle's type is rarely one the system knows, so it is told by its extension once picked.
    val subtitleFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::onAttachSubtitle)
    }
    LaunchedEffect(viewModel) {
        viewModel.subtitles.readFailures.collect { context.toast(R.string.video_subtitle_failed) }
    }

    // Nothing left to watch - the Private Folder locked, music started - or a newer screen showing it leaves nothing to show.
    val isShowing by viewModel.isShowing.collectAsStateWithLifecycle()
    LaunchedEffect(isShowing) {
        if (!isShowing) onClose()
    }
    // A new video, or a new fitting, starts unzoomed.
    LaunchedEffect(current?.id, session.aspect) { zoom.reset() }

    // The picture's size as last known for this video - a plain holder, kept through a moment of not knowing it,
    // as when the surface comes back from the background, so the frame never falls back to filling the screen.
    val pictureSize = remember(current?.id) { IntArray(2) }
    if (playback.videoWidth > 0 && playback.videoHeight > 0) {
        pictureSize[0] = playback.videoWidth
        pictureSize[1] = playback.videoHeight
    }
    // Shown only once this video's first frame is drawn at its own size - never a frame of another shape first.
    val isPictureShown = playback.hasPicture && pictureSize[0] > 0

    val areControlsUp = controls.isVisible && panel == null
    val isResumePromptDue = rememberIsResumePromptDue(
        resumedFromMs = playback.resumedFromMs,
        isPlaying = playback.isPlaying && playback.hasPicture,
        isScrubbing = scrub.isActive,
        areControlsVisible = controls.isVisible,
    )
    val isResumePromptUp = isResumePromptDue && playback.hasPicture && panel == null && !session.isLocked && !isFloating

    VideoWindowEffects(
        orientation = session.orientation,
        keepScreenOn = playback.isPlaying,
        showSystemBars = controls.isVisible && !session.isLocked,
    )
    controls.HideAfterTimeout(
        when {
            session.isLocked -> VideoPlayerTokens.LockedHintTimeoutMs
            panel != null || !playback.isPlaying || scrub.isActive -> null
            else -> VideoPlayerTokens.ControlsTimeoutMs
        },
    )
    // Locked, back does nothing but show the way to unlock, so the player is not left by accident either.
    BackHandler(enabled = session.isLocked) { controls.show() }
    BackHandler(enabled = panel != null) { panel = null }
    // A panel has no room in the floating window: it is closed as the player floats.
    LaunchedEffect(isFloating) { if (isFloating) panel = null }

    val position = rememberVideoPosition(viewModel::currentPositionMs, isFollowing = !session.isLocked && !isFloating)
    val isLoaderShown by produceState(false, playback.isBuffering) {
        if (playback.isBuffering) delay(VideoPlayerTokens.LoaderDelayMs)
        value = playback.isBuffering
    }

    val screenshot = rememberScreenshotAction(
        surface = { surface[0] },
        video = current,
        videoWidth = pictureSize[0],
        videoHeight = pictureSize[1],
        positionMs = viewModel::currentPositionMs,
    )
    val cycleOrientation = {
        viewModel.onCycleOrientation()
        feedback.flash(GestureFeedback.Orientation(session.orientation.next()))
    }
    val cycleAspect = {
        viewModel.onCycleAspect()
        feedback.flash(GestureFeedback.Aspect(session.aspect.next()))
    }

    val gestureActions = rememberUpdatedState(
        videoGestureActions(
            playback = playback,
            settings = gestureSettings,
            isAnswering = !session.isLocked && panel == null,
            onTap = { if (panel != null) panel = null else controls.toggle() },
            controls = controls,
            feedback = feedback,
            scrub = scrub,
            drag = drag,
            viewModel = viewModel,
            onHoldStarted = { if (gestureSettings.longPressVibration) haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
            brightness = brightness::level,
            setBrightness = { brightness.level = it },
            volume = { volume.volumeFraction },
            setVolume = volume::setVolumeFraction,
            zoom = zoom,
        ),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(if (isFloating) Modifier else Modifier.videoGestures(gestureActions)),
    ) {
        VideoFrame(
            aspect = session.aspect,
            videoWidth = pictureSize[0],
            videoHeight = pictureSize[1],
            zoom = zoom,
            reservedTop = if (isLandscape) 0.dp else videoSafeArea.asPaddingValues().calculateTopPadding() + VideoPlayerTokens.BarButtonSize,
            reservedBottom = if (isLandscape) 0.dp else portraitFootMargin + VideoPlayerTokens.PortraitControlsHeight,
            modifier = Modifier.fillMaxSize(),
        ) {
            VideoSurface(
                onAvailable = { view ->
                    surface[0] = view
                    viewModel.onSurfaceAvailable(view)
                },
                onGone = { view ->
                    surface[0] = null
                    viewModel.onSurfaceGone(view)
                },
            )
        }
        if (panel != null) Box(Modifier.fillMaxSize().background(VideoPlayerTokens.PanelDim))
        if (!isPictureShown) {
            Box(Modifier.fillMaxSize().background(Color.Black))
        } else {
            SubtitleLayer(
                source = subtitles.source,
                timeline = subtitles.timeline,
                offsetMs = subtitles.offsetMs,
                playerCues = viewModel.subtitleCues,
                currentPositionMs = viewModel::currentPositionMs,
                style = subtitleStyle,
                isFloating = isFloating,
                areControlsUp = areControlsUp,
                isResumePromptUp = isResumePromptUp,
                openPanelWidth = panel?.width?.takeIf { isLandscape },
                pictureBottomPx = { zoom.frameBottomPx },
            )
        }

        // Floating, the picture is all there is.
        if (isFloating) return@Box

        val showsLoader = isLoaderShown
        if (showsLoader) {
            // In landscape, exactly where the play button stands; in portrait, over the picture, the controls being at the foot.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize().then(
                    if (isLandscape) Modifier.windowInsetsPadding(videoSafeArea) else Modifier.offset { IntOffset(0, zoom.frameShiftPx.roundToInt()) },
                ),
            ) {
                ContainedLoadingIndicator(containerColor = VideoPlayerTokens.PillColor, indicatorColor = VideoPlayerTokens.ContentColor)
            }
        }

        if (playback.hasFailed) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize().windowInsetsPadding(videoSafeArea),
            ) {
                PlayerText(stringResource(R.string.video_cannot_play), VideoPlayerType.Pill, modifier = Modifier.padding(bottom = 96.dp))
            }
        }

        if (showClock && !controls.isVisible) {
            BatteryClock(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(
                        WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)
                            .only(WindowInsetsSides.Top + WindowInsetsSides.End),
                    )
                    .padding(12.dp),
            )
        }
        if (!areControlsUp && !session.isLocked && panel == null) {
            AmbientProgress(position, playback.durationMs, Modifier.align(Alignment.BottomCenter))
        }

        GestureFeedbackLayer(feedback)

        AnimatedVisibility(
            visible = if (session.isLocked) controls.isVisible else areControlsUp,
            enter = VideoPlayerTokens.ControlsEnter,
            exit = VideoPlayerTokens.ControlsExit,
        ) {
            if (session.isLocked) {
                LockedControls(
                    onUnlock = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.onLockedChange(false)
                        controls.show()
                    },
                    onHint = controls::show,
                )
            } else {
                PlayerControls(
                    title = current?.title.orEmpty(),
                    playback = playback,
                    position = position,
                    isLandscape = isLandscape,
                    isShowingSubtitles = subtitles.source is SubtitleSource.FromFile || subtitles.source is SubtitleSource.Embedded,
                    isLoading = showsLoader,
                    pictureShiftPx = { zoom.frameShiftPx },
                    session = session,
                    scrub = scrub,
                    viewModel = viewModel,
                    onClose = onClose,
                    onLock = {
                        viewModel.onLockedChange(true)
                        controls.show()
                    },
                    onCycleOrientation = cycleOrientation,
                    onCycleAspect = cycleAspect,
                    onFloat = onFloat,
                    onOpenPanel = { panel = it },
                )
            }
        }

        ResumePrompt(playback.resumedFromMs, isResumePromptUp, isLandscape, viewModel::onStartOver)

        ScrubPill(scrub, isVisible = scrub.isActive && !areControlsUp, playback.durationMs, Modifier.align(Alignment.TopCenter))
        ScrubPreview(
            scrub = scrub,
            frames = remember(viewModel) { ScrubFrames(viewModel::onScrubPreviewStart, viewModel::onScrubPreviewMove, viewModel::onScrubPreviewEnd) },
            isVisible = scrub.isActive && areControlsUp,
            videoWidth = pictureSize[0],
            videoHeight = pictureSize[1],
            durationMs = playback.durationMs,
        )

        PlayerPanelHost(panel, isLandscape) { shown ->
            val close = { panel = null }
            when (shown) {
                VideoPanel.TRACKS -> TracksPanel(
                    audioTracks = playback.audioTracks,
                    subtitles = subtitles,
                    textSize = subtitleStyle.textSize,
                    onSelectAudio = viewModel::onSelectAudioTrack,
                    onSelectSubtitle = viewModel.subtitles::choose,
                    onAddFile = { subtitleFilePicker.launch(arrayOf("*/*")) },
                    onTextSizeChange = viewModel::onSubtitleTextSizeChange,
                    onOffsetChange = viewModel.subtitles::setOffset,
                    onClose = close,
                )

                VideoPanel.QUEUE -> QueuePanel(
                    queue = playback.queue,
                    currentIndex = playback.currentQueueIndex,
                    isPlaying = playback.isPlaying,
                    isShuffled = playback.isShuffled,
                    repeatMode = playback.repeatMode,
                    onPlay = viewModel::onPlayQueueItem,
                    onToggleShuffle = viewModel::onToggleShuffle,
                    onCycleRepeatMode = viewModel::onCycleRepeatMode,
                    onClose = close,
                )

                VideoPanel.SPEED -> SpeedPanel(playback.playbackSpeed, viewModel::onSpeedChange, close)

                VideoPanel.MORE -> MorePanel(
                    actions = moreActions(playback, viewModel, screenshot),
                    onClose = close,
                )
            }
        }
    }
}

/** Every control, unlocked: the same controls however the screen stands - see [VideoControlsLayout]. */
@Composable
private fun PlayerControls(
    title: String,
    playback: VideoPlaybackState,
    position: VideoPosition,
    isLandscape: Boolean,
    isShowingSubtitles: Boolean,
    isLoading: Boolean,
    pictureShiftPx: () -> Float,
    session: VideoSession,
    scrub: VideoScrubState,
    viewModel: VideoPlayerViewModel,
    onClose: () -> Unit,
    onLock: () -> Unit,
    onCycleOrientation: () -> Unit,
    onCycleAspect: () -> Unit,
    onFloat: (() -> Unit)?,
    onOpenPanel: (VideoPanel) -> Unit,
) {
    val durationMs = playback.durationMs
    val topActions = listOf(
        VideoAction(VideoIcons.Headphones, stringResource(R.string.video_play_as_audio), viewModel::onPlayAsAudio),
        VideoAction(VideoIcons.Queue, stringResource(R.string.video_queue), { onOpenPanel(VideoPanel.QUEUE) }),
        VideoAction(
            icon = VideoIcons.Captions,
            label = stringResource(R.string.video_tracks),
            onClick = { onOpenPanel(VideoPanel.TRACKS) },
            tint = if (isShowingSubtitles) VideoPlayerTokens.Accent else VideoPlayerTokens.ContentColor,
        ),
        VideoAction(VideoIcons.More, stringResource(R.string.video_more), { onOpenPanel(VideoPanel.MORE) }),
    )
    val speed = speedLabel(playback.playbackSpeed)
    val speedName = stringResource(R.string.video_speed)
    val trailingActions = listOf(
        VideoAction(VideoIcons.Fit, stringResource(R.string.video_aspect), onCycleAspect),
        VideoAction(VideoIcons.PictureInPicture, stringResource(R.string.video_floating_window), onClick = onFloat),
        VideoAction(VideoIcons.Rotate, session.orientation.label(), onCycleOrientation),
    )
    VideoControlsLayout(
        isLandscape = isLandscape,
        pictureShiftPx = pictureShiftPx,
        slots = VideoControlSlots(
            topBar = { VideoTopBar(title, onClose, topActions) },
            transport = { modifier ->
                VideoTransport(
                    isPlaying = playback.isPlaying,
                    isLoading = isLoading,
                    onTogglePlayPause = viewModel::onTogglePlayPause,
                    onSkipPrevious = { viewModel.onSkipPrevious() }.takeIf { playback.canSkipPrevious },
                    onSkipNext = { viewModel.onSkipNext() }.takeIf { playback.canSkipNext },
                    modifier = modifier,
                )
            },
            seekBar = { modifier -> VideoSeekBar(position, durationMs, scrub, modifier) },
            elapsedTime = { modifier -> VideoElapsedTime(position, scrub, modifier) },
            remainingTime = { modifier -> VideoRemainingTime(position, durationMs, scrub, modifier) },
            leadingActions = { VideoBarButton(VideoAction(VideoIcons.LockOpen, stringResource(R.string.video_lock), onLock)) },
            trailingActions = {
                VideoButtonGroup {
                    speedButton(speed, speedName) { onOpenPanel(VideoPanel.SPEED) }
                    trailingActions.forEach { videoButton(it) }
                }
            },
        ),
    )
}

/**
 * The controls locked away: only the way back, held so it is not pressed by accident, and a tap on it saying so by
 * keeping it up.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun LockedControls(onUnlock: () -> Unit, onHint: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout))
            .padding(start = 24.dp, top = 20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .height(VideoPlayerTokens.BarButtonSize)
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(VideoPlayerTokens.PillColor)
                .combinedClickable(role = Role.Button, onClick = onHint, onLongClick = onUnlock)
                .padding(start = 14.dp, end = 18.dp),
        ) {
            Icon(VideoIcons.Locked, contentDescription = null, tint = VideoPlayerTokens.ContentColor, modifier = Modifier.size(18.dp))
            PlayerText(stringResource(R.string.video_hold_to_unlock), VideoPlayerType.Pill)
        }
    }
}

/** What has no place of its own on the screen: silencing a video, taking a frame. */
@Composable
private fun moreActions(
    playback: VideoPlaybackState,
    viewModel: VideoPlayerViewModel,
    onScreenshot: () -> Unit,
): List<VideoAction> = listOfNotNull(
    VideoAction(
        icon = if (playback.isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
        label = stringResource(if (playback.isMuted) R.string.video_unmute else R.string.video_mute),
        onClick = viewModel::onToggleMute,
    ),
    // A Private Folder video is never saved out to the gallery.
    VideoAction(Icons.Rounded.Screenshot, stringResource(R.string.video_screenshot), onScreenshot)
        .takeUnless { playback.isPrivate },
)

/**
 * What each gesture does - none of them while not [isAnswering] but the tap, and the touch, which keeps the controls up.
 * A swipe across scrubs exactly as the seek bar's thumb does, through the one [scrub].
 */
private fun videoGestureActions(
    playback: VideoPlaybackState,
    settings: VideoGestureSettings,
    isAnswering: Boolean,
    onTap: () -> Unit,
    controls: VideoControlsState,
    feedback: GestureFeedbackState,
    scrub: VideoScrubState,
    drag: DragProgress,
    viewModel: VideoPlayerViewModel,
    onHoldStarted: () -> Unit,
    brightness: () -> Float,
    setBrightness: (Float) -> Unit,
    volume: () -> Float,
    setVolume: (Float) -> Unit,
    zoom: VideoZoomState,
): VideoGestureActions {
    val durationMs = playback.durationMs
    val canDrag = isAnswering && settings.dragsEnabled
    return VideoGestureActions(
        onTouch = controls::onTouch,
        onTap = onTap,
        // Either side seeks back or on; the middle pauses or plays.
        onDoubleTap = settings.doubleTapSeekSeconds?.takeIf { isAnswering }?.let { seconds ->
            { zone ->
                if (zone == TapZone.CENTER) {
                    viewModel.onTogglePlayPause()
                } else {
                    val deltaMs = (if (zone == TapZone.END) seconds else -seconds) * 1_000L
                    viewModel.onSeekBy(deltaMs)
                    feedback.flashDoubleTapSeek(deltaMs)
                }
            }
        },
        onHoldStart = settings.longPressSpeed?.takeIf { isAnswering }?.let { speed ->
            {
                viewModel.onSpeedHoldStart(speed)
                onHoldStarted()
                feedback.show(GestureFeedback.Speed(speed))
            }
        },
        onHoldEnd = {
            viewModel.onSpeedHoldEnd()
            feedback.release()
        },
        onSeekDrag = if (canDrag && durationMs > 0L) {
            { fraction ->
                scrub.begin(viewModel.currentPositionMs())
                val sweepMs = minOf(durationMs, VideoPlayerTokens.SeekDragSweepMs)
                scrub.moveTo((scrub.originMs + (fraction * sweepMs).toLong()).coerceIn(0L, durationMs))
            }
        } else {
            null
        },
        onSeekDragEnd = scrub::end,
        onLevelDrag = if (canDrag) {
            { half, fraction ->
                when (half) {
                    ScreenHalf.START -> {
                        setBrightness(brightness() + fraction)
                        feedback.show(GestureFeedback.Brightness(brightness()))
                    }
                    ScreenHalf.END -> {
                        // Followed from the drag rather than read back: the volume moves in steps, a drag smoothly.
                        val target = ((drag.volumeTarget ?: volume()) + fraction).coerceIn(0f, 1f)
                        drag.volumeTarget = target
                        setVolume(target)
                        feedback.show(GestureFeedback.Volume(target))
                    }
                }
            }
        } else {
            null
        },
        onLevelDragEnd = {
            drag.volumeTarget = null
            feedback.release()
        },
        onTransform = if (isAnswering && settings.zoomEnabled) zoom::transform else null,
    )
}

/** A frame taken, waiting for the storage permission it is saved with on Android 9 and earlier. */
private class PendingScreenshot(val frame: Bitmap, val videoName: String)

/**
 * The screenshot button's action: the frame on screen now, at the video's own size, saved among the device's
 * pictures - asking first for the storage permission Android 9 and earlier save with. Every press ends in a toast
 * saying whether it was saved.
 */
@Composable
private fun rememberScreenshotAction(
    surface: () -> SurfaceView?,
    video: Track?,
    videoWidth: Int,
    videoHeight: Int,
    positionMs: () -> Long,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<PendingScreenshot?>(null) }
    val save = { screenshot: PendingScreenshot ->
        scope.launch {
            val isSaved = context.saveScreenshot(screenshot.frame, screenshot.videoName)
            context.toast(if (isSaved) R.string.video_screenshot_saved else R.string.video_screenshot_failed)
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val screenshot = pending ?: return@rememberLauncherForActivityResult
        pending = null
        if (granted) save(screenshot) else context.toast(R.string.video_screenshot_failed)
    }
    val latestVideo by rememberUpdatedState(video)
    val latestWidth by rememberUpdatedState(videoWidth)
    val latestHeight by rememberUpdatedState(videoHeight)
    val latestPositionMs by rememberUpdatedState(positionMs)
    return remember {
        {
            val shown = latestVideo
            if (shown == null) {
                context.toast(R.string.video_screenshot_failed)
            } else {
                // Read at once, so the frame saved is the one on screen when the button was pressed.
                val atMs = latestPositionMs()
                scope.launch {
                    val frame = context.captureVideoFrame(surface(), latestWidth, latestHeight, shown.contentUri, atMs)
                    when {
                        frame == null -> context.toast(R.string.video_screenshot_failed)
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !AppPermission.FILE_CHANGES.isGranted(context) -> {
                            pending = PendingScreenshot(frame, shown.title)
                            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                        else -> save(PendingScreenshot(frame, shown.title))
                    }
                }
            }
        }
    }
}
