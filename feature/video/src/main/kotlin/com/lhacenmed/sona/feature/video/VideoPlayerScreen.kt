package com.lhacenmed.sona.feature.video

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
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
import com.lhacenmed.sona.feature.video.controls.BatteryClock
import com.lhacenmed.sona.feature.video.controls.VideoAction
import com.lhacenmed.sona.feature.video.controls.VideoControlRail
import com.lhacenmed.sona.feature.video.controls.VideoControlsLayout
import com.lhacenmed.sona.feature.video.controls.VideoControlsState
import com.lhacenmed.sona.feature.video.controls.VideoSeekBar
import com.lhacenmed.sona.feature.video.controls.VideoTopBar
import com.lhacenmed.sona.feature.video.controls.VideoTransportBar
import com.lhacenmed.sona.feature.video.controls.rememberVideoPosition
import com.lhacenmed.sona.feature.video.gesture.GestureFeedback
import com.lhacenmed.sona.feature.video.gesture.GestureFeedbackBadge
import com.lhacenmed.sona.feature.video.gesture.GestureFeedbackState
import com.lhacenmed.sona.feature.video.gesture.ScreenHalf
import com.lhacenmed.sona.feature.video.gesture.TapZone
import com.lhacenmed.sona.feature.video.gesture.VideoGestureActions
import com.lhacenmed.sona.feature.video.gesture.icon
import com.lhacenmed.sona.feature.video.gesture.label
import com.lhacenmed.sona.feature.video.gesture.videoGestures
import com.lhacenmed.sona.feature.video.sheet.VideoChoiceSheet
import com.lhacenmed.sona.feature.video.sheet.VideoQueueSheet
import com.lhacenmed.sona.feature.video.surface.VideoFrame
import com.lhacenmed.sona.feature.video.surface.VideoSurface
import com.lhacenmed.sona.feature.video.surface.VideoZoomState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The speeds the speed button offers. */
private val PlaybackSpeeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)

/** The sheets the player opens over itself. */
private enum class VideoSheet { QUEUE, SPEED }

/** Where the drag under way started, and the level it has reached - kept across its reports and recompositions. */
private class DragProgress {
    var seekStartMs: Long? = null
    var volumeTarget: Float? = null
}

/**
 * The video player: the picture - or, played for its sound alone, a disc in its place - the gestures over it,
 * and the controls over those, which a tap shows and hides.
 *
 * Laid out in three layers that never change places: the picture, the gesture feedback and loader, then the
 * controls. Locking the controls swaps the controls for the lock alone, in the place it always has.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VideoPlayerScreen(viewModel: VideoPlayerViewModel, onClose: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
    val showClock by viewModel.showClock.collectAsStateWithLifecycle()
    val isScrubbing by viewModel.isScrubbing.collectAsStateWithLifecycle()
    val playback = uiState.playback
    val current = uiState.current
    // Music queued after the videos has no picture to show.
    val isAudioOnly = session.isAudioOnly || current?.isVideo == false

    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val controls = remember { VideoControlsState() }
    val zoom = remember { VideoZoomState() }
    val feedback = remember(scope) { GestureFeedbackState(scope) }
    val brightness = rememberWindowBrightness()
    val volume = rememberDeviceMusicVolumeController()
    var sheet by remember { mutableStateOf<VideoSheet?>(null) }
    val drag = remember { DragProgress() }
    // Held for the screenshot button, which reads the frame straight off it.
    val surface = remember { arrayOfNulls<SurfaceView>(1) }

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

    VideoWindowEffects(
        orientation = session.orientation,
        keepScreenOn = playback.isPlaying && !isAudioOnly,
        showSystemBars = isAudioOnly || (controls.isVisible && !session.isLocked),
    )
    controls.HideAfterTimeout(
        when {
            session.isLocked -> VideoPlayerTokens.LockedHintTimeoutMs
            isAudioOnly || sheet != null || !playback.isPlaying -> null
            else -> VideoPlayerTokens.ControlsTimeoutMs
        },
    )
    // Locked, back does nothing but show the lock, so the player is not left by accident either.
    BackHandler(enabled = session.isLocked) { controls.show() }

    val position by rememberVideoPosition(viewModel::currentPositionMs, isFollowing = controls.isVisible || isAudioOnly)
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

    val gestureActions = rememberUpdatedState(
        videoGestureActions(
            playback = playback,
            settings = gestureSettings,
            isAnswering = !session.isLocked && !isAudioOnly,
            controls = controls,
            feedback = feedback,
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
            .videoGestures(gestureActions),
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
        if (isAudioOnly) {
            AudioOnlyContent(
                coverArtUri = current?.coverArtUri,
                isPlaying = playback.isPlaying,
                onBackToVideo = {
                    viewModel.onAudioOnlyChange(false)
                    controls.show()
                },
                // Clear of the bars, which never hide while it shows.
                modifier = Modifier
                    .background(Color.Black)
                    .padding(vertical = VideoPlayerTokens.BarHeight * 2),
            )
        } else if (!isPictureShown) {
            Box(Modifier.fillMaxSize().background(Color.Black))
        }

        // Dragging through the video, its frames are the feedback; a loader would only cover them.
        if (isLoaderShown && !isScrubbing && !isAudioOnly) {
            CircularProgressIndicator(color = VideoPlayerTokens.ContentColor, modifier = Modifier.align(Alignment.Center))
        }
        GestureFeedbackBadge(feedback.current, modifier = Modifier.align(Alignment.Center))

        if (showClock && !controls.isVisible) {
            BatteryClock(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(
                        WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)
                            .only(WindowInsetsSides.Top + WindowInsetsSides.End),
                    )
                    .padding(VideoPlayerTokens.EdgePadding),
            )
        }

        AnimatedVisibility(
            visible = controls.isVisible || isAudioOnly,
            enter = VideoPlayerTokens.ControlsEnter,
            exit = VideoPlayerTokens.ControlsExit,
        ) {
            when {
                session.isLocked -> LockedControls(
                    positionMs = position,
                    durationMs = playback.durationMs,
                    onUnlock = {
                        viewModel.onLockedChange(false)
                        controls.show()
                    },
                )

                else -> PlayerControls(
                    title = current?.title.orEmpty(),
                    playback = playback,
                    positionMs = position,
                    isAudioOnly = isAudioOnly,
                    session = session,
                    viewModel = viewModel,
                    onClose = onClose,
                    onLock = {
                        viewModel.onLockedChange(true)
                        controls.show()
                    },
                    onScreenshot = screenshot,
                    onCycleOrientation = {
                        viewModel.onCycleOrientation()
                        feedback.flash(GestureFeedback.Orientation(session.orientation.next()))
                    },
                    onCycleAspect = {
                        viewModel.onCycleAspect()
                        feedback.flash(GestureFeedback.Aspect(session.aspect.next()))
                    },
                    onOpenSheet = { sheet = it },
                )
            }
        }
    }

    when (sheet) {
        VideoSheet.QUEUE -> VideoQueueSheet(
            queue = uiState.queue,
            currentIndex = playback.currentQueueIndex,
            isPlaying = playback.isPlaying,
            onPlay = viewModel::onPlayQueueItem,
            onDismissRequest = { sheet = null },
        )

        VideoSheet.SPEED -> VideoChoiceSheet(
            title = stringResource(R.string.video_speed),
            options = PlaybackSpeeds,
            selected = playback.playbackSpeed,
            label = { speedLabel(it) },
            onSelect = viewModel::onSpeedChange,
            onDismissRequest = { sheet = null },
        )

        null -> Unit
    }
}

/** Every control, unlocked: the top bar, the rails down either side, and the seek and transport bars. */
@Composable
private fun PlayerControls(
    title: String,
    playback: PlaybackUiState,
    positionMs: Long,
    isAudioOnly: Boolean,
    session: VideoSession,
    viewModel: VideoPlayerViewModel,
    onClose: () -> Unit,
    onLock: () -> Unit,
    onScreenshot: () -> Unit,
    onCycleOrientation: () -> Unit,
    onCycleAspect: () -> Unit,
    onOpenSheet: (VideoSheet) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    VideoControlsLayout(
        topBar = {
            VideoTopBar(
                title = title,
                onBack = onClose,
                actions = listOf(
                    VideoAction(
                        icon = Icons.Rounded.Headphones,
                        label = stringResource(if (isAudioOnly) R.string.video_back_to_video else R.string.video_play_as_audio),
                        onClick = { viewModel.onAudioOnlyChange(!session.isAudioOnly) },
                        tint = if (isAudioOnly) accent else VideoPlayerTokens.ContentColor,
                    ),
                    VideoAction(Icons.AutoMirrored.Rounded.PlaylistPlay, stringResource(R.string.video_queue), { onOpenSheet(VideoSheet.QUEUE) }),
                    // The more-actions sheet is still to come.
                    VideoAction(Icons.Rounded.MoreVert, stringResource(R.string.video_more), onClick = null),
                ),
            )
        },
        startRail = {
            VideoControlRail(listOf(muteAction(playback.isMuted, viewModel::onToggleMute), lockAction(isLocked = false, onLock)))
        },
        endRail = {
            if (!isAudioOnly) {
                VideoControlRail(
                    listOf(
                        // A Private Folder video is never saved out to the gallery; its place is kept, empty.
                        VideoAction(Icons.Rounded.Screenshot, stringResource(R.string.video_screenshot), onScreenshot)
                            .takeIf { playback.space != PlaybackSpace.Private },
                        VideoAction(session.orientation.icon, session.orientation.label(), onCycleOrientation),
                    ),
                )
            }
        },
        bottomBar = {
            VideoSeekBar(
                positionMs = positionMs,
                durationMs = playback.durationMs,
                onSeek = viewModel::onSeek,
                onScrubbingChange = viewModel::onScrubbingChange,
            )
            VideoTransportBar(
                isPlaying = playback.isPlaying,
                canSkipPrevious = playback.canSkipPrevious,
                canSkipNext = playback.canSkipNext,
                speedLabel = if (playback.playbackSpeed == 1f) stringResource(R.string.video_speed) else speedLabel(playback.playbackSpeed),
                onTogglePlayPause = viewModel::onTogglePlayPause,
                onSkipPrevious = viewModel::onSkipPrevious,
                onSkipNext = viewModel::onSkipNext,
                onSpeedClick = { onOpenSheet(VideoSheet.SPEED) },
                trailing = if (isAudioOnly) {
                    emptyList()
                } else {
                    listOf(
                        VideoAction(Icons.Rounded.AspectRatio, stringResource(R.string.video_aspect), onCycleAspect),
                        // The floating window is still to come.
                        VideoAction(Icons.Rounded.PictureInPictureAlt, stringResource(R.string.video_floating_window), onClick = null),
                    )
                },
            )
        },
    )
}

/**
 * The controls locked away: the lock alone, in its own place, and how far the video is - where the seek bar
 * always is, and as still as the rest. The transport bar's place is kept empty, so nothing moves.
 */
@Composable
private fun LockedControls(positionMs: Long, durationMs: Long, onUnlock: () -> Unit) {
    VideoControlsLayout(
        startRail = { VideoControlRail(listOf(null, lockAction(isLocked = true, onUnlock))) },
        bottomBar = {
            VideoSeekBar(positionMs = positionMs, durationMs = durationMs, onSeek = {}, onScrubbingChange = {}, enabled = false)
            Spacer(Modifier.height(VideoPlayerTokens.TransportBarHeight))
        },
    )
}

@Composable
private fun muteAction(isMuted: Boolean, onToggle: () -> Unit) = VideoAction(
    icon = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
    label = stringResource(if (isMuted) R.string.video_unmute else R.string.video_mute),
    onClick = onToggle,
)

@Composable
private fun lockAction(isLocked: Boolean, onClick: () -> Unit) = VideoAction(
    icon = if (isLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
    label = stringResource(if (isLocked) R.string.video_unlock else R.string.video_lock),
    onClick = onClick,
    tint = if (isLocked) MaterialTheme.colorScheme.error else VideoPlayerTokens.ContentColor,
)

/**
 * What each gesture does - none of them while not [isAnswering] but the tap, which shows and hides the controls,
 * and the touch, which keeps them up.
 */
private fun videoGestureActions(
    playback: PlaybackUiState,
    settings: VideoGestureSettings,
    isAnswering: Boolean,
    controls: VideoControlsState,
    feedback: GestureFeedbackState,
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
        onTap = controls::toggle,
        // Either side seeks back or on; the middle pauses or plays.
        onDoubleTap = settings.doubleTapSeekSeconds?.takeIf { isAnswering }?.let { seconds ->
            { zone ->
                if (zone == TapZone.CENTER) {
                    viewModel.onTogglePlayPause()
                    feedback.flash(GestureFeedback.PlayPause(isPlaying = !playback.isPlaying))
                } else {
                    val deltaMs = (if (zone == TapZone.END) seconds else -seconds) * 1_000L
                    val targetMs = (viewModel.currentPositionMs() + deltaMs).coerceIn(0L, durationMs.coerceAtLeast(0L))
                    viewModel.onSeek(targetMs)
                    feedback.flash(GestureFeedback.Seek(targetMs, deltaMs, durationMs))
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
                val startMs = drag.seekStartMs ?: viewModel.currentPositionMs().also {
                    drag.seekStartMs = it
                    viewModel.onScrubbingChange(true)
                }
                val sweepMs = minOf(durationMs, VideoPlayerTokens.SeekDragSweepMs)
                val targetMs = (startMs + (fraction * sweepMs).toLong()).coerceIn(0L, durationMs)
                viewModel.onSeek(targetMs)
                feedback.show(GestureFeedback.Seek(targetMs, targetMs - startMs, durationMs))
            }
        } else {
            null
        },
        onSeekDragEnd = {
            drag.seekStartMs = null
            viewModel.onScrubbingChange(false)
            feedback.release()
        },
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
