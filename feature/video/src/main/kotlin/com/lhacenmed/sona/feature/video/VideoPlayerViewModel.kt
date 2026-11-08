package com.lhacenmed.sona.feature.video

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import android.view.TextureView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import com.lhacenmed.sona.core.data.LibrarySubtitleStore
import com.lhacenmed.sona.core.datastore.SubtitleTextSize
import com.lhacenmed.sona.core.datastore.VideoAspect
import com.lhacenmed.sona.core.datastore.VideoOrientation
import com.lhacenmed.sona.core.datastore.PlaybackSettings
import com.lhacenmed.sona.core.datastore.PlayerStyleSettings
import com.lhacenmed.sona.core.datastore.VideoSettings
import com.lhacenmed.sona.core.datastore.stateIn
import com.lhacenmed.sona.core.designsystem.component.toast
import com.lhacenmed.sona.core.vault.VaultRepository
import com.lhacenmed.sona.feature.playback.MediaTrack
import com.lhacenmed.sona.feature.playback.PlaybackController
import com.lhacenmed.sona.feature.playback.SonaEqualizer
import com.lhacenmed.sona.feature.playback.QueueVideo
import com.lhacenmed.sona.feature.playback.VideoPlayback
import com.lhacenmed.sona.feature.playback.VideoPlaybackState
import com.lhacenmed.sona.feature.playback.labelRes
import com.lhacenmed.sona.feature.video.subtitle.SubtitleSession
import com.lhacenmed.sona.feature.video.subtitle.SubtitleStyle
import com.lhacenmed.sona.feature.video.subtitle.WatchedVideo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.lhacenmed.sona.feature.playback.R as PlaybackR

/** What the user chose in this player, as long as it is open - stored too where a setting says to keep it. */
data class VideoSession(
    val orientation: VideoOrientation,
    val aspect: VideoAspect,
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

/**
 * One player screen, on the one opening of a video it was started for - see [VideoPlaybackState.opening]. Everything it does
 * goes through [video], which holds nothing once that opening is over, so a screen still on its way out touches nothing that
 * came after; and what it shows stays as it last was, so it leaves with nothing seen being reset.
 */
@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    private val videoPlayback: VideoPlayback,
    savedStateHandle: SavedStateHandle,
    private val playbackController: PlaybackController,
    vault: VaultRepository,
    private val videoSettings: VideoSettings,
    private val playbackSettings: PlaybackSettings,
    private val playerStyleSettings: PlayerStyleSettings,
    private val equalizer: SonaEqualizer,
    librarySubtitles: LibrarySubtitleStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val opening: Long = savedStateHandle[EXTRA_OPENING] ?: 0L

    /** The player, for as long as this screen's opening is what it plays. */
    private val video: VideoPlayback? get() = videoPlayback.takeIf { it.state.value.opening == opening }

    /** This screen's opening as it stands, or as it last stood once over. */
    val playback: StateFlow<VideoPlaybackState> = videoPlayback.state
        .filter { it.opening == opening }
        .stateIn(viewModelScope, SharingStarted.Eagerly, videoPlayback.state.value)

    /** Whether this screen's opening is still what plays. */
    val isShowing: StateFlow<Boolean> = videoPlayback.state
        .map { it.opening == opening && it.current != null }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, videoPlayback.state.value.let { it.opening == opening && it.current != null })

    /** Where it last was, read on as this screen leaves. */
    private var lastPositionMs = 0L

    private val _session = MutableStateFlow(
        VideoSession(
            // Every video opens turning with the device; the rotation button holds only while this player is open.
            orientation = VideoOrientation.Default,
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

    /** Whether the seek bar's end says how long the video is rather than how much is left - every player's, see [PlayerStyleSettings.showsTotalDuration]. */
    val showsTotalDuration: StateFlow<Boolean> = playerStyleSettings.showsTotalDuration.stateIn(viewModelScope)

    fun onToggleEndTime() {
        viewModelScope.launch { playerStyleSettings.setShowsTotalDuration(!showsTotalDuration.value) }
    }

    /**
     * How far past the device's loudest the video plays, 0 to 1 - its volume from 100% to 200%, see
     * [SonaEqualizer.setVolumeBoost]. For as long as this player is open: what plays after it plays as it did.
     */
    var volumeBoost = 0f
        private set

    fun onVolumeBoostChange(boost: Float) {
        volumeBoost = boost
        equalizer.setVolumeBoost(boost)
    }

    override fun onCleared() = equalizer.setVolumeBoost(0f)

    /** The subtitles of the video watched - each space's kept in its own store, and only the library's found beside it. */
    internal val subtitles = SubtitleSession(
        context = context,
        scope = viewModelScope,
        video = videoPlayback,
        playback = playback,
        settings = videoSettings,
        watched = playback.map { state ->
            state.current?.let { track ->
                WatchedVideo(track, if (state.isPrivate) vault.videoSubtitles else librarySubtitles, looksBesideIt = !state.isPrivate)
            }
        },
    )

    /** The lines of the file's own subtitle track - see [VideoPlayback.subtitleCues]. */
    val subtitleCues = videoPlayback.subtitleCues

    internal val subtitleStyle: StateFlow<SubtitleStyle> = with(videoSettings) {
        merge(subtitleCustomization.flow, subtitleTextSize.flow, subtitleBackdrop.flow, subtitlePosition.flow)
            .map { readSubtitleStyle() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), readSubtitleStyle())
    }

    /** The speed to go back to once a long press lets go, while one holds the video sped up. */
    private var speedBeforeHold: Float? = null

    /** The player's position at this moment, for whatever follows it closer than [playback] could. */
    fun currentPositionMs(): Long {
        video?.let { lastPositionMs = it.currentPositionMs() }
        return lastPositionMs
    }

    fun onTogglePlayPause() {
        video?.togglePlayPause()
    }

    fun onSkipNext() {
        video?.skipToNext()
    }

    fun onSkipPrevious() {
        video?.skipToPrevious()
    }

    fun onSeek(positionMs: Long) {
        video?.seekTo(positionMs)
    }

    /** Seeks [deltaMs] on from where the video is now - back for a negative one - within the video. */
    fun onSeekBy(deltaMs: Long) {
        val video = video ?: return
        val durationMs = video.state.value.durationMs
        val targetMs = video.currentPositionMs() + deltaMs
        video.seekTo(if (durationMs > 0L) targetMs.coerceIn(0L, durationMs) else targetMs.coerceAtLeast(0L))
    }

    fun onStartOver() {
        video?.startOver()
    }

    fun onScrubPreviewStart(view: TextureView, positionMs: Long) {
        video?.startScrubPreview(view, positionMs)
    }

    fun onScrubPreviewMove(positionMs: Long) = videoPlayback.moveScrubPreview(positionMs)

    /**
     * How high each moment of the video stands in its Most replayed graph, from 0 to 1 - null while no moment stands out,
     * see [com.lhacenmed.sona.core.model.ReplayHeatmap.heights].
     */
    val replayHeights: StateFlow<FloatArray?> = videoPlayback.replayHeatmap
        .map { it?.heights() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** A scrub has begun or ended - see [VideoPlayback.setScrubbing]. */
    fun onScrubbingChange(isScrubbing: Boolean) {
        video?.setScrubbing(isScrubbing)
    }

    fun onSpeedChange(speed: Float) {
        video?.setPlaybackSpeed(speed)
    }

    /** Plays at [speed] until [onSpeedHoldEnd] - a long press on the video. */
    fun onSpeedHoldStart(speed: Float) {
        val video = video ?: return
        if (speedBeforeHold == null) speedBeforeHold = video.state.value.playbackSpeed
        video.setPlaybackSpeed(speed)
    }

    fun onSpeedHoldEnd() {
        speedBeforeHold?.let { video?.setPlaybackSpeed(it) }
        speedBeforeHold = null
    }

    /** Shuffles the videos, or stops - the videos' own shuffle, kept for the next ones; the player follows it. */
    fun onToggleShuffle() {
        val isShuffled = !(video?.state?.value?.isShuffled ?: return)
        viewModelScope.launch { videoSettings.setShuffleEnabled(isShuffled) }
        context.toast(if (isShuffled) PlaybackR.string.playback_action_shuffle_on else PlaybackR.string.playback_action_shuffle_off)
    }

    /** Moves the videos' repeat mode on - kept for the next ones; the player follows it. */
    fun onCycleRepeatMode() {
        val stopAfterCurrentEnabled = playbackSettings.stopAfterCurrentEnabled.value
        viewModelScope.launch { videoSettings.cycleRepeatMode(stopAfterCurrentEnabled) }
        context.toast(videoSettings.repeatMode.value.next(stopAfterCurrentEnabled).labelRes)
    }

    /** Silences the video, or lets it be heard - and keeps that for the next one. */
    fun onToggleMute() {
        val video = video ?: return
        val muted = !video.state.value.isMuted
        video.setMuted(muted)
        viewModelScope.launch { videoSettings.setMuted(muted) }
    }

    /** Plays [track] of this video - and the next video in its language, where it has one. */
    fun onSelectAudioTrack(track: MediaTrack) {
        val video = video ?: return
        track.language?.let { language ->
            video.setPreferredAudioLanguage(language)
            viewModelScope.launch { videoSettings.setAudioLanguage(language) }
        }
        video.selectAudioTrack(track)
    }

    fun onAttachSubtitle(uri: Uri) = subtitles.attach(uri)

    /** Sets how large subtitles are - which only means anything as the app's own look, so that is what they then take. */
    fun onSubtitleTextSizeChange(size: SubtitleTextSize) {
        viewModelScope.launch {
            videoSettings.setSubtitleTextSize(size)
            videoSettings.setSubtitleCustomization(true)
        }
    }

    fun onPlayQueueItem(item: QueueVideo) {
        video?.playQueueItem(item.entry.mediaItemIndex)
    }

    fun onSurfaceAvailable(view: SurfaceView) {
        video?.attachSurface(view)
    }

    /** Asked of the player whichever opening is playing: it lets go of this surface alone, if it still holds it. */
    fun onSurfaceGone(view: SurfaceView) = videoPlayback.detachSurface(view)

    /** Moves on to the next fitting - kept for every video while the user keeps it. */
    fun onCycleAspect() {
        val aspect = _session.value.aspect.next()
        _session.update { it.copy(aspect = aspect) }
        if (videoSettings.keepAspect.value) viewModelScope.launch { videoSettings.setAspect(aspect) }
        context.toast(aspect.labelRes)
    }

    /** Moves on to the next way of standing, for as long as this player is open - and says which, as a toast. */
    fun onCycleOrientation() {
        val orientation = _session.value.orientation.next()
        _session.update { it.copy(orientation = orientation) }
        context.toast(orientation.labelRes)
    }

    /** Has the video listened to from here on, in the music player - which closes this one. */
    fun onPlayAsAudio() {
        if (video != null) playbackController.listenToWatchedVideo()
    }

    fun onLockedChange(isLocked: Boolean) = _session.update { it.copy(isLocked = isLocked) }

    /** The screen has gone from view: a picture nobody is watching pauses. */
    fun onScreenLeft() {
        video?.pause()
    }

    /** The user is closing the screen, and what it shows with it - there and then, for the next video to find nothing in its way. */
    fun onPlayerClosed() {
        video?.close()
    }

    private fun readSubtitleStyle() = with(videoSettings) {
        SubtitleStyle(subtitleCustomization.value, subtitleTextSize.value, subtitleBackdrop.value, subtitlePosition.value)
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
}
