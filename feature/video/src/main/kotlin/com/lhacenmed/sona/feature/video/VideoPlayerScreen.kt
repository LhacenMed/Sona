package com.lhacenmed.sona.feature.video

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.common.permission.AppPermission
import com.lhacenmed.sona.core.data.contentUri
import com.lhacenmed.sona.core.designsystem.component.toast
import com.lhacenmed.sona.core.designsystem.effect.rememberDeviceMusicVolumeController
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.PlaybackSpace
import com.lhacenmed.sona.feature.playback.PlaybackUiState
import com.lhacenmed.sona.feature.video.audio.AudioOnlyContent
import com.lhacenmed.sona.feature.video.capture.captureVideoFrame
import com.lhacenmed.sona.feature.video.capture.saveScreenshot
import com.lhacenmed.sona.feature.video.controls.AmbientProgress
import com.lhacenmed.sona.feature.video.controls.BatteryClock
import com.lhacenmed.sona.feature.video.controls.VideoAction
import com.lhacenmed.sona.feature.video.controls.VideoActionRow
import com.lhacenmed.sona.feature.video.controls.VideoBarButton
import com.lhacenmed.sona.feature.video.controls.VideoControlSlots
import com.lhacenmed.sona.feature.video.controls.VideoControlsLayout
import com.lhacenmed.sona.feature.video.controls.VideoControlsState
import com.lhacenmed.sona.feature.video.controls.VideoElapsedTime
import com.lhacenmed.sona.feature.video.controls.VideoIcons
import com.lhacenmed.sona.feature.video.controls.VideoPosition
import com.lhacenmed.sona.feature.video.controls.VideoRemainingTime
import com.lhacenmed.sona.feature.video.controls.VideoSeekBar
import com.lhacenmed.sona.feature.video.controls.VideoSpeedButton
import com.lhacenmed.sona.feature.video.controls.VideoTopBar
import com.lhacenmed.sona.feature.video.controls.VideoTransport
import com.lhacenmed.sona.feature.video.controls.rememberVideoPosition
import com.lhacenmed.sona.feature.video.gesture.GestureFeedback
import com.lhacenmed.sona.feature.video.gesture.GestureFeedbackLayer
import com.lhacenmed.sona.feature.video.gesture.GestureFeedbackState
import com.lhacenmed.sona.feature.video.gesture.ScreenHalf
import com.lhacenmed.sona.feature.video.gesture.TapZone
import com.lhacenmed.sona.feature.video.gesture.VideoGestureActions
import com.lhacenmed.sona.feature.video.gesture.icon
import com.lhacenmed.sona.feature.video.gesture.label
import com.lhacenmed.sona.feature.video.gesture.videoGestures
import com.lhacenmed.sona.feature.video.panel.MorePanel
import com.lhacenmed.sona.feature.video.panel.PlayerPanelHost
import com.lhacenmed.sona.feature.video.panel.QueuePanel
import com.lhacenmed.sona.feature.video.panel.SpeedPanel
import com.lhacenmed.sona.feature.video.panel.TracksPanel
import com.lhacenmed.sona.feature.video.panel.VideoPanel
import com.lhacenmed.sona.feature.video.scrub.ScrubPreview
import com.lhacenmed.sona.feature.video.scrub.VideoScrubState
import com.lhacenmed.sona.feature.video.subtitle.SubtitleLayer
import com.lhacenmed.sona.feature.video.subtitle.SubtitleSource
import com.lhacenmed.sona.feature.video.surface.VideoFrame
import com.lhacenmed.sona.feature.video.surface.VideoSurface
import com.lhacenmed.sona.feature.video.surface.VideoZoomState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the skip buttons skip while double taps do not seek, and so have no length set. */
private const val DefaultSkipSeconds = 10

/** The level the drag under way has reached - kept across its reports and recompositions. */
private class DragProgress {
    var volumeTarget: Float? = null
}

/**
 * The video player: the picture - or, played for its sound alone, a disc in its place - the subtitles over it, the
 * gestures' feedback over those, then the controls, which a tap shows and hides, and any panel over all of it.
 *
 * Built so nothing that moves composes this screen: a finger's scrub, a level's drag, the position's tick and the
 * subtitles are each followed by state read only where they are drawn or laid out. This composes again only as
 * playback itself changes - a new video, play or pause - or a control comes or goes.
 *
 * Locking the controls swaps them for the way to unlock alone. [isFloating], in its floating window, it is the picture
 * and its subtitles alone - the window has controls of its own. [onFloat], where the device has floating windows, puts
 * it in one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VideoPlayerScreen(viewModel: VideoPlayerViewModel, onClose: () -> Unit, isFloating: Boolean, onFloat: (() -> Unit)?) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
    val showClock by viewModel.showClock.collectAsStateWithLifecycle()
    val subtitles by viewModel.subtitles.state.collectAsStateWithLifecycle()
    val subtitleStyle by viewModel.subtitleStyle.collectAsStateWithLifecycle()
    val playback = uiState.playback
    val current = uiState.current
    // Music queued after the videos has no picture to show.
    val isAudioOnly = session.isAudioOnly || current?.isVideo == false
    val windowSize = LocalWindowInfo.current.containerSize
    val isLandscape = windowSize.width > windowSize.height

    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val controls = remember { VideoControlsState() }
    val zoom = remember { VideoZoomState() }
    val feedback = remember(scope) { GestureFeedbackState(scope) }
    val scrub = remember(viewModel) { VideoScrubState(viewModel::onSeek, viewModel::onScrubbingChange) }
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

    // Nothing left to watch - the queue cleared from the notification, or the Private Folder locked - leaves
    // nothing to show. Only once something was shown: the player can open a moment before the queue it was
    // opened for arrives.
    val hasShownSomething = remember { booleanArrayOf(false) }
    val isShowing = current != null && playback.isWatching
    LaunchedEffect(isShowing) {
        if (isShowing) hasShownSomething[0] = true else if (hasShownSomething[0]) onClose()
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
    val isPictureShown = current != null && playback.pictureTrackId == current.id && pictureSize[0] > 0

    val areControlsUp = (controls.isVisible || isAudioOnly) && panel == null

    VideoWindowEffects(
        orientation = session.orientation,
        keepScreenOn = playback.isPlaying && !isAudioOnly,
        showSystemBars = isAudioOnly || (controls.isVisible && !session.isLocked),
    )
    controls.HideAfterTimeout(
        when {
            session.isLocked -> VideoPlayerTokens.LockedHintTimeoutMs
            isAudioOnly || panel != null || !playback.isPlaying -> null
            else -> VideoPlayerTokens.ControlsTimeoutMs
        },
    )
    // Locked, back does nothing but show the way to unlock, so the player is not left by accident either.
    BackHandler(enabled = session.isLocked) { controls.show() }
    BackHandler(enabled = panel != null) { panel = null }
    // A panel has no room in the floating window: it is closed as the player floats.
    LaunchedEffect(isFloating) { if (isFloating) panel = null }

    val position = rememberVideoPosition(viewModel::currentPositionMs, viewModel::bufferedPositionMs, isFollowing = !session.isLocked && !isFloating)
    val scrubProgress = animateFloatAsState(
        targetValue = if (scrub.isActive) 1f else 0f,
        animationSpec = tween(VideoPlayerTokens.ScrubFadeMs),
        label = "scrubProgress",
    )
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
            isAnswering = !session.isLocked && !isAudioOnly && panel == null,
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
        // The surface stays while the player is on screen - covered, not dropped, while the video plays as audio -
        // so the picture is still decoding when it comes back, and comes back at once.
        VideoFrame(
            aspect = session.aspect,
            videoWidth = pictureSize[0],
            videoHeight = pictureSize[1],
            zoom = zoom,
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
        if (panel != null && !isAudioOnly) Box(Modifier.fillMaxSize().background(VideoPlayerTokens.PanelDim))
        if (isAudioOnly) {
            AudioOnlyContent(
                coverArtUri = current?.coverArtUri,
                isPlaying = playback.isPlaying,
                showsDetails = !isLandscape,
                onBackToVideo = {
                    viewModel.onAudioOnlyChange(false)
                    controls.show()
                },
                // Clear of the controls, which never hide while it shows; in landscape, the transport stands over it.
                modifier = Modifier
                    .background(Color.Black)
                    .padding(
                        top = if (isLandscape) 0.dp else VideoPlayerTokens.BarButtonSize,
                        bottom = if (isLandscape) 0.dp else VideoPlayerTokens.PortraitControlsHeight,
                    ),
            )
        } else if (!isPictureShown) {
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
                openPanelWidth = panel?.width?.takeIf { isLandscape },
                pictureBottomPx = { zoom.frameBottomPx },
            )
        }

        // Floating, the picture is all there is.
        if (isFloating) return@Box

        if (isLoaderShown && !scrub.isActive && !isAudioOnly) {
            // Ringing the play button in the middle of the landscape screen, rather than covering it.
            CircularProgressIndicator(
                color = VideoPlayerTokens.ContentColor,
                modifier = Modifier.align(Alignment.Center).size(VideoPlayerTokens.PlayButtonSize),
            )
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
        if (!areControlsUp && !scrub.isActive && !session.isLocked && !isAudioOnly && panel == null) {
            AmbientProgress(position, playback.durationMs, Modifier.align(Alignment.BottomCenter))
        }

        GestureFeedbackLayer(feedback)

        AnimatedVisibility(
            visible = if (session.isLocked) controls.isVisible else areControlsUp || scrub.isActive,
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
                    details = videoDetails(current?.year, pictureSize[1]),
                    playback = playback,
                    position = position,
                    isLandscape = isLandscape,
                    isAudioOnly = isAudioOnly,
                    isShowingSubtitles = subtitles.source is SubtitleSource.FromFile || subtitles.source is SubtitleSource.Embedded,
                    skipSeconds = gestureSettings.doubleTapSeekSeconds ?: DefaultSkipSeconds,
                    session = session,
                    scrub = scrub,
                    scrubProgress = scrubProgress,
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

        ScrubPreview(
            scrub = scrub,
            surface = { surface[0] },
            showsPicture = !isAudioOnly,
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
                    queue = uiState.queue,
                    currentIndex = playback.currentQueueIndex,
                    isPlaying = playback.isPlaying,
                    onPlay = viewModel::onPlayQueueItem,
                    onClose = close,
                )

                VideoPanel.SPEED -> SpeedPanel(playback.playbackSpeed, viewModel::onSpeedChange, close)

                VideoPanel.MORE -> MorePanel(
                    actions = moreActions(
                        playback = playback,
                        isLandscape = isLandscape,
                        isAudioOnly = isAudioOnly,
                        orientationIcon = session.orientation.icon,
                        orientationLabel = session.orientation.label(),
                        viewModel = viewModel,
                        onScreenshot = screenshot,
                        onCycleOrientation = cycleOrientation,
                        onCycleAspect = cycleAspect,
                    ),
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
    details: String,
    playback: PlaybackUiState,
    position: VideoPosition,
    isLandscape: Boolean,
    isAudioOnly: Boolean,
    isShowingSubtitles: Boolean,
    skipSeconds: Int,
    session: VideoSession,
    scrub: VideoScrubState,
    scrubProgress: State<Float>,
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
        VideoAction(
            icon = VideoIcons.Headphones,
            label = stringResource(if (isAudioOnly) R.string.video_back_to_video else R.string.video_play_as_audio),
            onClick = { viewModel.onAudioOnlyChange(!session.isAudioOnly) },
            tint = if (isAudioOnly) VideoPlayerTokens.Accent else VideoPlayerTokens.ContentColor,
        ),
        VideoAction(VideoIcons.Queue, stringResource(R.string.video_queue), { onOpenPanel(VideoPanel.QUEUE) }),
        VideoAction(
            icon = VideoIcons.Captions,
            label = stringResource(R.string.video_tracks),
            onClick = { onOpenPanel(VideoPanel.TRACKS) }.takeUnless { isAudioOnly },
            tint = if (isShowingSubtitles && !isAudioOnly) VideoPlayerTokens.Accent else VideoPlayerTokens.ContentColor,
        ),
        VideoAction(VideoIcons.More, stringResource(R.string.video_more), { onOpenPanel(VideoPanel.MORE) }),
    )
    val floatAction = VideoAction(VideoIcons.PictureInPicture, stringResource(R.string.video_floating_window), onClick = onFloat)
    val trailingActions = when {
        isAudioOnly -> emptyList()
        isLandscape -> listOf(VideoAction(VideoIcons.Fit, stringResource(R.string.video_aspect), onCycleAspect), floatAction)
        else -> listOf(floatAction, VideoAction(VideoIcons.Rotate, session.orientation.label(), onCycleOrientation))
    }
    VideoControlsLayout(
        isLandscape = isLandscape,
        scrubProgress = scrubProgress,
        slots = VideoControlSlots(
            topBar = { VideoTopBar(title, details, onClose, topActions, scrubProgress) },
            transport = { modifier ->
                VideoTransport(
                    isPlaying = playback.isPlaying,
                    seekSeconds = skipSeconds,
                    onTogglePlayPause = viewModel::onTogglePlayPause,
                    onSeekBy = { seconds -> viewModel.onSeekBy(seconds * 1_000L) },
                    isLandscape = isLandscape,
                    modifier = modifier,
                )
            },
            seekBar = { modifier -> VideoSeekBar(position, durationMs, scrub, scrubProgress, modifier) },
            elapsedTime = { modifier -> VideoElapsedTime(position, scrub, scrubProgress, modifier) },
            remainingTime = { modifier -> VideoRemainingTime(position, durationMs, scrub, scrubProgress, modifier) },
            leadingActions = { VideoBarButton(VideoAction(VideoIcons.LockOpen, stringResource(R.string.video_lock), onLock)) },
            trailingActions = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VideoSpeedButton(speedLabel(playback.playbackSpeed), { onOpenPanel(VideoPanel.SPEED) })
                    VideoActionRow(trailingActions)
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

/**
 * What has no place of its own on the screen: skipping between videos, silencing one, taking a frame - and what the
 * layout showing leaves out of its bars.
 */
@Composable
private fun moreActions(
    playback: PlaybackUiState,
    isLandscape: Boolean,
    isAudioOnly: Boolean,
    orientationIcon: ImageVector,
    orientationLabel: String,
    viewModel: VideoPlayerViewModel,
    onScreenshot: () -> Unit,
    onCycleOrientation: () -> Unit,
    onCycleAspect: () -> Unit,
): List<VideoAction> = listOfNotNull(
    VideoAction(Icons.Rounded.SkipPrevious, stringResource(R.string.video_previous), { viewModel.onSkipPrevious() }.takeIf { playback.canSkipPrevious }),
    VideoAction(Icons.Rounded.SkipNext, stringResource(R.string.video_next), { viewModel.onSkipNext() }.takeIf { playback.canSkipNext }),
    VideoAction(
        icon = if (playback.isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
        label = stringResource(if (playback.isMuted) R.string.video_unmute else R.string.video_mute),
        onClick = viewModel::onToggleMute,
    ),
    // A Private Folder video is never saved out to the gallery.
    VideoAction(Icons.Rounded.Screenshot, stringResource(R.string.video_screenshot), onScreenshot)
        .takeIf { !isAudioOnly && playback.space != PlaybackSpace.Private },
    VideoAction(orientationIcon, orientationLabel, onCycleOrientation).takeIf { !isAudioOnly && isLandscape },
    VideoAction(VideoIcons.Fit, stringResource(R.string.video_aspect), onCycleAspect).takeIf { !isAudioOnly && !isLandscape },
)

/**
 * What each gesture does - none of them while not [isAnswering] but the tap, and the touch, which keeps the controls up.
 * A swipe across scrubs exactly as the seek bar's thumb does, through the one [scrub].
 */
private fun videoGestureActions(
    playback: PlaybackUiState,
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
                    feedback.flash(GestureFeedback.PlayPause(isPlaying = !playback.isPlaying))
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
