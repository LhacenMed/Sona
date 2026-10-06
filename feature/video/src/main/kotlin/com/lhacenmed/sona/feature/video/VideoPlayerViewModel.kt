package com.lhacenmed.sona.feature.video

import android.view.SurfaceView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.datastore.VideoAspect
import com.lhacenmed.sona.core.datastore.VideoOrientation
import com.lhacenmed.sona.core.datastore.VideoSettings
import com.lhacenmed.sona.core.datastore.stateIn
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.PlaybackController
import com.lhacenmed.sona.feature.playback.PlaybackUiState
import com.lhacenmed.sona.feature.playback.QueueEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A slot of the queue with the video it holds. */
data class QueueVideo(
    val entry: QueueEntry,
    val video: Track,
)

/**
 * Playback resolved against the library. [playback] carries no position - it moves on its own, and is read
 * through [VideoPlayerViewModel.currentPositionMs] only while something shows it.
 */
data class VideoPlayerUiState(
    val playback: PlaybackUiState = PlaybackUiState(),
    /** What plays now - a video, or music queued after one. */
    val current: Track? = null,
    val queue: List<QueueVideo> = emptyList(),
)

/** What the user chose in this player, as long as it is open - stored too where a setting says to keep it. */
data class VideoSession(
    val orientation: VideoOrientation,
    val aspect: VideoAspect,
    /** Played for its sound alone, the picture let go. */
    val isAudioOnly: Boolean = false,
    /** The controls locked away, so nothing on the screen is pressed by accident. */
    val isLocked: Boolean = false,
)

/** The gestures the screen answers - each one off while null or false. */
data class VideoGestureSettings(
    /** Seeking by dragging across, and brightness and volume by dragging down either half. */
    val dragsEnabled: Boolean,
    val doubleTapSeekSeconds: Int?,
    val longPressSpeed: Float?,
    val longPressVibration: Boolean,
    val zoomEnabled: Boolean,
)

@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    private val playbackController: PlaybackController,
    repository: LibraryRepository,
    private val videoSettings: VideoSettings,
) : ViewModel() {

    private val playbackWithoutPosition = playbackController.playbackState
        .map { it.copy(positionMs = 0L) }
        .distinctUntilChanged()

    // Narrowed to the queue itself, so it is not resolved again for every other change of playback.
    private val queue = combine(
        playbackWithoutPosition.map { it.queue }.distinctUntilChanged(),
        repository.tracksById,
        ::resolveQueue,
    )

    // Started from what is already in memory, so the player draws the video on its first frame.
    val uiState: StateFlow<VideoPlayerUiState> = combine(playbackWithoutPosition, queue, repository.tracksById) { playback, queue, tracksById ->
        VideoPlayerUiState(playback, playback.currentTrackId?.let(tracksById::get), queue)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        playbackController.playbackState.value.copy(positionMs = 0L).let { playback ->
            val tracksById = repository.tracksById.value
            VideoPlayerUiState(playback, playback.currentTrackId?.let(tracksById::get), resolveQueue(playback.queue, tracksById))
        },
    )

    private val _session = MutableStateFlow(
        VideoSession(
            orientation = videoSettings.orientation.value,
            // A ratio chosen once applies to every video only while the user keeps it; otherwise each one opens fitted.
            aspect = if (videoSettings.keepAspect.value) videoSettings.aspect.value else VideoAspect.Default,
        ),
    )
    val session: StateFlow<VideoSession> = _session.asStateFlow()

    val gestureSettings: StateFlow<VideoGestureSettings> = with(videoSettings) {
        merge(
            gestures.flow,
            doubleTapSeek.flow,
            doubleTapSeekSeconds.flow,
            longPressSpeedUp.flow,
            longPressSpeed.flow,
            longPressVibration.flow,
            zoomPan.flow,
        ).map { readGestureSettings() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), readGestureSettings())
    }

    val showClock: StateFlow<Boolean> = videoSettings.showClock.stateIn(viewModelScope)

    private val _isScrubbing = MutableStateFlow(false)

    /** Whether a finger is dragging through the video - its frames are the feedback then, not a loader. */
    val isScrubbing: StateFlow<Boolean> = _isScrubbing.asStateFlow()

    init {
        // The silence chosen last time comes back with the player; it is the player's alone, never the music's.
        playbackController.setMuted(videoSettings.muted.value)
    }

    /** The speed to go back to once a long press lets go, while one holds the video sped up. */
    private var speedBeforeHold: Float? = null

    /** The player's position at this moment, for whatever follows it closer than [uiState] could. */
    fun currentPositionMs(): Long = playbackController.currentPositionMs()

    fun onTogglePlayPause() = playbackController.togglePlayPause()

    fun onSkipNext() = playbackController.skipToNext()

    fun onSkipPrevious() = playbackController.skipToPrevious()

    fun onSeek(positionMs: Long) = playbackController.seekTo(positionMs)

    /** Seeks [deltaMs] on from where the video is now - back for a negative one - within the video. */
    fun onSeekBy(deltaMs: Long) {
        val durationMs = playbackController.playbackState.value.durationMs
        val targetMs = playbackController.currentPositionMs() + deltaMs
        playbackController.seekTo(if (durationMs > 0L) targetMs.coerceIn(0L, durationMs) else targetMs.coerceAtLeast(0L))
    }

    fun onScrubbingChange(isScrubbing: Boolean) {
        _isScrubbing.value = isScrubbing
        playbackController.setScrubbing(isScrubbing)
    }

    fun onSpeedChange(speed: Float) = playbackController.setPlaybackSpeed(speed)

    /** Plays at [speed] until [onSpeedHoldEnd] - a long press on the video. */
    fun onSpeedHoldStart(speed: Float) {
        if (speedBeforeHold == null) speedBeforeHold = playbackController.playbackState.value.playbackSpeed
        playbackController.setPlaybackSpeed(speed)
    }

    fun onSpeedHoldEnd() {
        speedBeforeHold?.let(playbackController::setPlaybackSpeed)
        speedBeforeHold = null
    }

    /** Silences the video, or lets it be heard - and keeps that for the next one. */
    fun onToggleMute() {
        val muted = !playbackController.playbackState.value.isMuted
        playbackController.setMuted(muted)
        viewModelScope.launch { videoSettings.setMuted(muted) }
    }

    fun onPlayQueueItem(item: QueueVideo) = playbackController.playQueueItem(item.entry.mediaItemIndex)

    fun onSurfaceAvailable(view: SurfaceView) = playbackController.setVideoSurface(view)

    fun onSurfaceGone(view: SurfaceView) = playbackController.clearVideoSurface(view)

    /** Moves on to the next fitting - kept for every video while the user keeps it. */
    fun onCycleAspect() {
        val aspect = _session.value.aspect.next()
        _session.update { it.copy(aspect = aspect) }
        if (videoSettings.keepAspect.value) viewModelScope.launch { videoSettings.setAspect(aspect) }
    }

    /** Moves on to the next way of standing - kept for every video while the user keeps it. */
    fun onCycleOrientation() {
        val orientation = _session.value.orientation.next()
        _session.update { it.copy(orientation = orientation) }
        if (videoSettings.keepOrientation.value) viewModelScope.launch { videoSettings.setOrientation(orientation) }
    }

    fun onAudioOnlyChange(isAudioOnly: Boolean) = _session.update { it.copy(isAudioOnly = isAudioOnly) }

    fun onLockedChange(isLocked: Boolean) = _session.update { it.copy(isLocked = isLocked) }

    /**
     * The screen has gone from view: a picture nobody is watching pauses, while sound alone - a video played as
     * audio, or music queued after the videos - plays on.
     */
    fun onScreenLeft() {
        val isPictureShown = !_session.value.isAudioOnly && uiState.value.current?.isVideo != false
        if (isPictureShown) playbackController.pause()
    }

    /**
     * The player is closing: its speed and silence were for watching here, so whatever plays next - this video's
     * sound in the mini player, or music - plays as recorded and heard. The silence is kept for the next video.
     */
    fun onPlayerClosed() {
        speedBeforeHold = null
        playbackController.setPlaybackSpeed(1f)
        playbackController.setMuted(false)
    }

    private fun readGestureSettings() = with(videoSettings) {
        VideoGestureSettings(
            dragsEnabled = gestures.value,
            doubleTapSeekSeconds = doubleTapSeekSeconds.value.takeIf { doubleTapSeek.value },
            longPressSpeed = longPressSpeed.value.takeIf { longPressSpeedUp.value },
            longPressVibration = longPressVibration.value,
            zoomEnabled = zoomPan.value,
        )
    }

    private fun resolveQueue(entries: List<QueueEntry>, tracksById: Map<Long, Track>): List<QueueVideo> =
        entries.mapNotNull { entry -> tracksById[entry.trackId]?.let { QueueVideo(entry, it) } }
}
