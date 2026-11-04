package com.lhacenmed.sona.feature.playback

import android.content.Context
import android.view.SurfaceView
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.session.MediaSession
import com.lhacenmed.sona.core.database.dao.PlayStatsDao
import com.lhacenmed.sona.core.datastore.PlaybackSettings
import com.lhacenmed.sona.core.datastore.VideoSettings
import com.lhacenmed.sona.core.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A seek lands on a keyframe this close to where it was asked for, which shows at once; otherwise, this far short of it. */
private const val SeekToleranceUs = 2_000_000L

private const val FilesHolder = "video"

/** How long a video waits for its first frame before playing regardless - its picture may be one the device cannot decode. */
private const val PictureWaitMs = 2_000L

/**
 * How much of a video is read ahead, however large it is: every byte of it sits on the app's heap, and an app near its heap's
 * limit has Android destroy the screens stopped behind it. Two seconds are read in any case, which a badly interleaved or very
 * high bitrate file may need more of this to reach.
 */
private const val ReadAheadBytes = 16 * 1024 * 1024

/** How the video player opens on a video started - over everything, or floating over the app as it goes on. */
enum class VideoPlayerOpening {
    FULL_SCREEN,

    /** In its floating window, so what was on screen goes on under it - a video looked at while picking files. */
    FLOATING,
}

/** A slot of the watched queue with the video it holds. */
data class QueueVideo(
    val entry: QueueEntry,
    val video: Track,
)

/** A player screen to open, on [opening] - see [VideoPlaybackState.opening] - as it asks to. */
data class VideoScreenRequest(val opening: Long, val isFloating: Boolean)

/** One video to watch, as the player takes it. */
internal class VideoQueueItem(val track: Track, val mediaItem: MediaItem)

/** A queue passed on to the music player, to be listened to from where it was watched. */
internal class VideoHandOver(val queue: List<VideoQueueItem>, val index: Int, val positionMs: Long)

/**
 * What is being watched. Holds no position, which moves on its own - see [VideoPlayback.currentPositionMs].
 */
data class VideoPlaybackState(
    /** Which opening of a video this is - 0 with nothing being watched. What a screen is given, and all it may act on. */
    val opening: Long = 0L,
    /** The video playing - null with nothing being watched. */
    val current: Track? = null,
    /** The queue in the order it plays. */
    val queue: List<QueueVideo> = emptyList(),
    /** Where [current] sits in [queue]. */
    val currentQueueIndex: Int = -1,
    /** Whether [current] is the Private Folder's. */
    val isPrivate: Boolean = false,
    /** Whether playback is on - including while it buffers - which is what play and pause show. */
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    /** Whether the video could not be played, and nothing follows it to go on to. */
    val hasFailed: Boolean = false,
    val durationMs: Long = 0L,
    val canSkipPrevious: Boolean = false,
    val canSkipNext: Boolean = false,
    val playbackSpeed: Float = 1f,
    val isMuted: Boolean = false,
    /** The picture as it is shown, its pixels' own shape applied - 0 by 0 before it is known. */
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    /** Whether [current]'s first frame has been drawn. */
    val hasPicture: Boolean = false,
    /** Where [current] came back from, having been left there - null for one that began at its beginning. */
    val resumedFromMs: Long? = null,
    /** The file's own audio and subtitle tracks the device can play. */
    val audioTracks: List<MediaTrack> = emptyList(),
    val subtitleTracks: List<MediaTrack> = emptyList(),
)

/**
 * The videos being watched, on a player of their own - apart from the music's, which is told to pause and is otherwise
 * left exactly as it was: its queue, its mini player and its notification never hear of a video.
 *
 * Every command goes straight to the one player, on the main thread, in the order it is given - there is no session or
 * saved queue between them, so nothing arrives late. Opening and closing are done before they return, and each opening is
 * known by a number - [VideoPlaybackState.opening] - that its screen is given: a screen acts only on its own, so one still
 * on its way out cannot touch the next.
 *
 * Decoding starts as a video is opened, while the player's window is still on its way; playing waits until its first frame
 * is on the screen's own surface, so the sound never runs ahead of a black screen. A queue is all videos: music in a list a video is started from stays out
 * of it. Played for its sound, a queue is handed to the music player - see [handOver].
 */
@OptIn(UnstableApi::class)
@Singleton
class VideoPlayback @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackFiles: TrackFiles,
    private val videoSettings: VideoSettings,
    private val playbackSettings: PlaybackSettings,
    private val playStatsDao: PlayStatsDao,
    private val resumePositions: ResumePositions,
    private val equalizer: SonaEqualizer,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(VideoPlaybackState())
    val state: StateFlow<VideoPlaybackState> = _state.asStateFlow()

    private val _subtitleCues = MutableStateFlow<List<Cue>>(emptyList())

    /** The lines of the subtitle track playing, as they show - apart from [state], as they change so often. */
    val subtitleCues: StateFlow<List<Cue>> = _subtitleCues.asStateFlow()

    private val screenRequests = Channel<VideoScreenRequest>(Channel.CONFLATED)

    /**
     * Each time a video is opened, the video player is to open. Taken by one screen, the one in front, so it opens once; held
     * until a screen is there to take it, and forgotten if that video has ended by then.
     */
    val openings: Flow<VideoScreenRequest> = screenRequests.receiveAsFlow().filter { it.opening == _state.value.opening }

    /** Built with the first video watched, and kept: it holds nothing while idle, and starts the next one sooner. */
    private val player: ExoPlayer by lazy(LazyThreadSafetyMode.NONE, ::buildPlayer)

    private val scrubPreview = VideoScrubPreviewPlayer(context, trackFiles)

    private var isOpen = false

    /** The number of the latest opening - see [VideoPlaybackState.opening]. */
    private var lastOpening = 0L

    private var queue: List<VideoQueueItem> = emptyList()
    private var tracksByMediaId: Map<String, Track> = emptyMap()
    private var queueVideos: List<QueueVideo> = emptyList()

    /** What to tell of where the video was left, as it closes. */
    private var onClosed: ((MediaItem, Long) -> Unit)? = null

    /** The surface showing the picture. */
    private var surfaceView: SurfaceView? = null

    /** Whether it has yet to start playing, once its first frame is on the screen's surface. */
    private var isAwaitingPicture = false
    private var pictureWait: Job? = null

    /** Where the video playing came back from, having been left there - null for one that began at its beginning. */
    private var resumedFromMs: Long? = null

    /** The item whose first frame has been drawn. */
    private var pictureMediaId: String? = null

    /** What media buttons reach while a video plays - made once it does, and gone with it, so they go back to the music. */
    private var mediaSession: MediaSession? = null

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // The same video come round again keeps its picture; another has none until its first frame.
            if (mediaItem?.mediaId != pictureMediaId) pictureMediaId = null
            // One opened says for itself; the next in the queue comes back where it was left - see ResumePositionRecorder.
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                resumedFromMs = mediaItem?.takeUnless { reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT }
                    ?.let(resumePositions::startPositionOf)?.takeIf { it > 0L }
            }
        }

        override fun onRenderedFirstFrame() {
            // Reported a moment late, it may be of a video closed since.
            if (!isOpen) return
            // A frame on the stand-in the player draws to until the screen's surface arrives, which nobody sees.
            if (surfaceView?.holder?.surface?.isValid != true) return
            pictureMediaId = player.currentMediaItem?.mediaId
            if (isAwaitingPicture) startPlaying()
            if (mediaSession == null) mediaSession = MediaSession.Builder(context, player).setId("video").build()
            publish()
        }

        override fun onCues(cueGroup: CueGroup) {
            _subtitleCues.value = cueGroup.cues
        }

        /** A file gone or unreadable gives way to the next one; with none, the screen says so. */
        override fun onPlayerError(error: PlaybackException) {
            if (!isOpen || !player.hasNextMediaItem()) return
            player.seekToNextMediaItem()
            player.prepare()
            player.play()
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (!isOpen) return
            if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) readQueue()
            if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION)) trackFiles.hold(FilesHolder, player.heldFileUris())
            if (events.containsAny(*PublishedEvents)) publish()
        }
    }

    /**
     * Starts [queue] from [startIndex], in place of anything being watched, at [startPositionMs] - where it was left, while
     * that is 0. [onClosed] is told where the video was left, should it end by being closed rather than replaced.
     *
     * Asked for the very video already playing - its row tapped twice, or tapped under its floating window - it simply goes on.
     */
    internal fun open(
        queue: List<VideoQueueItem>,
        startIndex: Int,
        shuffled: Boolean = false,
        startPositionMs: Long = 0L,
        opening: VideoPlayerOpening = VideoPlayerOpening.FULL_SCREEN,
        onClosed: ((MediaItem, Long) -> Unit)? = null,
    ) {
        val start = queue.getOrNull(startIndex) ?: return
        if (isOpen && player.currentMediaItem?.mediaId == start.mediaItem.mediaId) {
            if (!isAwaitingPicture) player.play()
            return
        }
        isOpen = true
        lastOpening++
        stopAwaitingPicture()
        isAwaitingPicture = true
        pictureMediaId = null
        this.queue = queue
        this.onClosed = onClosed
        tracksByMediaId = queue.associate { it.mediaItem.mediaId to it.track }
        _subtitleCues.value = emptyList()

        val player = player
        // The last screen's surface, kept until now for its last frame to stand as it leaves: this video waits for its own.
        surfaceView = null
        player.clearVideoSurface()
        player.playWhenReady = false
        player.setPlaybackSpeed(1f)
        player.volume = if (videoSettings.muted.value) 0f else 1f
        player.pauseAtEndOfMediaItems = !videoSettings.continuousPlay.value
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverrides()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setPreferredAudioLanguage(videoSettings.audioLanguage.value)
            .build()
        // On the music's audio session, so the equalizer shapes its sound just the same.
        equalizer.audioSessionId?.takeIf { it != player.audioSessionId }?.let { player.audioSessionId = it }
        // Before the items, for the shuffle order to be built around the one starting.
        player.shuffleModeEnabled = shuffled
        val position = startPositionMs.takeIf { it > 0L } ?: resumePositions.startPositionOf(start.mediaItem)
        // Asked to go on from a place, it has come back from nothing: only where it was left says so.
        resumedFromMs = position.takeIf { startPositionMs <= 0L && it > 0L }
        player.setMediaItems(queue.map { it.mediaItem }, startIndex, position)
        player.prepare()
        readQueue()
        publish()
        screenRequests.trySend(VideoScreenRequest(lastOpening, opening == VideoPlayerOpening.FLOATING))
    }

    /** Closes whatever is being watched, for whichever reason. */
    fun close() {
        if (!isOpen) return
        val closedItem = player.currentMediaItem
        val closedAtMs = player.currentPosition
        val notifyClosed = onClosed
        shutDown()
        if (closedItem != null) notifyClosed?.invoke(closedItem, closedAtMs)
    }

    /** Closes what is being watched and gives it over to be played on - null with nothing to give. */
    internal fun handOver(): VideoHandOver? {
        if (!isOpen) return null
        val current = player.currentMediaItem ?: return null
        val index = queue.indexOfFirst { it.mediaItem.mediaId == current.mediaId }.coerceAtLeast(0)
        val handOver = VideoHandOver(queue, index, player.currentPosition)
        shutDown()
        return handOver
    }

    /** Closes what is being watched where it is of the Private Folder, so nothing of it plays once that is locked. */
    internal fun closeIfPrivate() {
        if (isOpen && player.currentMediaItem?.isVaultItem == true) close()
    }

    /** Takes the [space]'s items with the ids [ids] out of the queue - their files about to go. Emptied, it closes. */
    internal fun remove(ids: Set<Long>, space: PlaybackSpace) {
        if (!isOpen) return
        for (index in player.mediaItemCount - 1 downTo 0) {
            val item = player.getMediaItemAt(index)
            if (item.space == space && item.idInSpace in ids) player.removeMediaItem(index)
        }
        if (player.mediaItemCount == 0) close()
    }

    fun pause() {
        if (!isOpen) return
        stopAwaitingPicture()
        player.pause()
    }

    /** Shows the picture on [view], and starts the video if that is what it was waiting for. */
    fun attachSurface(view: SurfaceView) {
        if (!isOpen) return
        surfaceView = view
        player.setVideoSurfaceView(view)
        if (isAwaitingPicture) {
            pictureWait = scope.launch {
                delay(PictureWaitMs)
                startPlaying()
            }
        }
    }

    private fun startPlaying() {
        stopAwaitingPicture()
        player.play()
    }

    private fun stopAwaitingPicture() {
        isAwaitingPicture = false
        pictureWait?.cancel()
        pictureWait = null
    }

    /** Lets go of [view], should it still be the one showing the picture - never another screen's that came since. */
    fun detachSurface(view: SurfaceView) {
        if (surfaceView !== view) return
        surfaceView = null
        player.clearVideoSurfaceView(view)
    }

    /** Pauses or plays by what the button shows - plays again from the start once ended, and tries again once failed. */
    fun togglePlayPause() {
        if (!isOpen) return
        if (isAwaitingPicture) {
            // It shows as playing, so a press means to pause: it stays as it is, and no longer starts by itself.
            stopAwaitingPicture()
            publish()
        } else {
            Util.handlePlayPauseButtonAction(player)
        }
    }

    fun seekTo(positionMs: Long) {
        if (isOpen) player.seekTo(positionMs)
    }

    /** Shows on [view] the frames a scrub reaches, starting at [positionMs], leaving the video itself to play on. */
    fun startScrubPreview(view: TextureView, positionMs: Long) {
        if (isOpen) player.currentMediaItem?.let { scrubPreview.start(it, view, positionMs) }
    }

    fun moveScrubPreview(positionMs: Long) = scrubPreview.moveTo(positionMs)

    fun stopScrubPreview(view: TextureView) = scrubPreview.stop(view)

    /** Plays the video from its beginning, rather than from where it came back. */
    fun startOver() {
        if (!isOpen) return
        resumedFromMs = null
        player.seekTo(0L)
        startPlaying()
        publish()
    }

    fun skipToNext() {
        if (!isOpen) return
        player.seekToNext()
        if (!playbackSettings.rememberPause.value) player.play()
    }

    fun skipToPrevious() {
        if (!isOpen) return
        player.seekToPrevious()
        if (!playbackSettings.rememberPause.value) player.play()
    }

    fun playQueueItem(mediaItemIndex: Int) {
        if (!isOpen) return
        player.seekToDefaultPosition(mediaItemIndex)
        player.play()
    }

    fun setPlaybackSpeed(speed: Float) {
        if (isOpen) player.setPlaybackSpeed(speed)
    }

    fun setMuted(isMuted: Boolean) {
        if (isOpen) player.volume = if (isMuted) 0f else 1f
    }

    /** The player's position at this moment, for whatever follows it closer than [state] could. */
    fun currentPositionMs(): Long = if (isOpen) player.currentPosition else 0L

    /** The language a file's audio is played in from now on, wherever it has a track in it. */
    fun setPreferredAudioLanguage(language: String) {
        if (!isOpen) return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setPreferredAudioLanguage(language).build()
    }

    fun selectAudioTrack(track: MediaTrack) = selectTrack(C.TRACK_TYPE_AUDIO, track)

    /** Plays the subtitle [track] of the video - none at all while null. */
    fun selectSubtitleTrack(track: MediaTrack?) {
        if (!isOpen) return
        if (track != null) {
            selectTrack(C.TRACK_TYPE_TEXT, track)
        } else {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        }
    }

    /** Leaves it to the player which subtitle track plays, which is one the file itself marks to be shown, if any. */
    fun followDefaultSubtitleTrack() {
        if (!isOpen) return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
    }

    private fun selectTrack(type: @C.TrackType Int, track: MediaTrack) {
        if (!isOpen) return
        // Read afresh: the queue may have moved on from the file this track was listed for.
        val group = player.currentTracks.groups.getOrNull(track.groupIndex)?.takeIf { it.type == type && track.trackIndex < it.length } ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex))
            .build()
    }

    private fun shutDown() {
        // Written down now, whatever the player tells of it as it stops: it may have been sought while paused.
        player.currentMediaItem?.let { resumePositions.record(it, player.currentPosition) }
        isOpen = false
        stopAwaitingPicture()
        onClosed = null
        pictureMediaId = null
        resumedFromMs = null
        player.stop()
        player.clearMediaItems()
        mediaSession?.release()
        mediaSession = null
        queue = emptyList()
        tracksByMediaId = emptyMap()
        queueVideos = emptyList()
        trackFiles.hold(FilesHolder, emptySet())
        _subtitleCues.value = emptyList()
        _state.value = VideoPlaybackState()
    }

    private fun readQueue() {
        queueVideos = player.queueEntries().mapNotNull { entry ->
            tracksByMediaId[player.getMediaItemAt(entry.mediaItemIndex).mediaId]?.let { QueueVideo(entry, it) }
        }
    }

    private fun publish() {
        val player = player
        val item = player.currentMediaItem
        val videoSize = player.videoSize
        val currentIndex = player.currentMediaItemIndex
        _state.update {
            it.copy(
                opening = lastOpening,
                current = item?.let { current -> tracksByMediaId[current.mediaId] },
                queue = queueVideos,
                currentQueueIndex = queueVideos.indexOfFirst { video -> video.entry.mediaItemIndex == currentIndex },
                isPrivate = item?.isVaultItem == true,
                // As the user asked for it rather than as heard: a video starting stays playing while it buffers.
                isPlaying = isAwaitingPicture || !Util.shouldShowPlayButton(player),
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                hasFailed = player.playerError != null,
                durationMs = player.duration.takeUnless { duration -> duration == C.TIME_UNSET } ?: item?.mediaMetadata?.durationMs ?: 0L,
                canSkipPrevious = player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS),
                canSkipNext = player.hasNextMediaItem(),
                playbackSpeed = player.playbackParameters.speed,
                isMuted = player.volume == 0f,
                videoWidth = (videoSize.width * videoSize.pixelWidthHeightRatio).roundToInt(),
                videoHeight = videoSize.height,
                hasPicture = item != null && item.mediaId == pictureMediaId,
                resumedFromMs = resumedFromMs,
                audioTracks = player.currentTracks.mediaTracksOf(C.TRACK_TYPE_AUDIO),
                subtitleTracks = player.currentTracks.mediaTracksOf(C.TRACK_TYPE_TEXT),
            )
        }
    }

    private fun buildPlayer(): ExoPlayer {
        // Every file is on the device, read far faster than it plays: a moment of it is all there is to wait for, or to hold.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 2_000,
                /* maxBufferMs = */ 10_000,
                /* bufferForPlaybackMs = */ 100,
                /* bufferForPlaybackAfterRebufferMs = */ 250,
            )
            .setTargetBufferBytes(ReadAheadBytes)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        return ExoPlayer.Builder(context, sonaRenderersFactory(context))
            .setLoadControl(loadControl)
            .setMediaSourceFactory(videoMediaSourceFactory(context, trackFiles))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekParameters(SeekParameters(SeekToleranceUs, SeekToleranceUs))
            .build()
            .also { player ->
                player.addListener(listener)
                // On the player itself, so a video counts and is remembered exactly as one heard in the music player does.
                PlayHistoryRecorder(player, scope, playStatsDao, playbackSettings).attach()
                ResumePositionRecorder(player, resumePositions).attach()
            }
    }

    private companion object {
        val PublishedEvents = intArrayOf(
            Player.EVENT_TIMELINE_CHANGED,
            Player.EVENT_MEDIA_ITEM_TRANSITION,
            Player.EVENT_TRACKS_CHANGED,
            Player.EVENT_PLAYBACK_STATE_CHANGED,
            Player.EVENT_IS_PLAYING_CHANGED,
            Player.EVENT_PLAY_WHEN_READY_CHANGED,
            Player.EVENT_PLAYER_ERROR,
            Player.EVENT_POSITION_DISCONTINUITY,
            Player.EVENT_PLAYBACK_PARAMETERS_CHANGED,
            Player.EVENT_VOLUME_CHANGED,
            Player.EVENT_VIDEO_SIZE_CHANGED,
            Player.EVENT_AVAILABLE_COMMANDS_CHANGED,
        )
    }
}

/**
 * The videos of [all] to watch, and where the one at [startIndex] now stands among them: music in the same list stays out
 * of a queue that is watched.
 */
internal fun <T> videosFrom(all: List<T>, startIndex: Int, isVideo: (T) -> Boolean): Pair<List<T>, Int> =
    all.filter(isVideo) to all.take(startIndex.coerceAtLeast(0)).count(isVideo)
