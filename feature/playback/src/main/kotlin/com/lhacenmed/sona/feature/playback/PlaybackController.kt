package com.lhacenmed.sona.feature.playback

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.database.dao.PlayStatsDao
import com.lhacenmed.sona.core.database.dao.QueueItemDao
import com.lhacenmed.sona.core.database.entity.QueueItemEntity
import com.lhacenmed.sona.core.datastore.PlaybackSettings
import com.lhacenmed.sona.core.model.PlaybackParent
import com.lhacenmed.sona.core.model.RepeatMode
import com.lhacenmed.sona.core.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UI-facing façade over the [PlaybackService]'s media session.
 *
 * Connects to the service via a [MediaController], forwards transport commands to it, and
 * republishes its state as a [StateFlow] of [PlaybackUiState] for observers (e.g. a ViewModel)
 * to collect. Also owns queue persistence (save on every relevant player event, restore once per
 * process on first connect) - ported from Fossify's `PlayerListener`/`AudioHelper` queue table.
 */
/**
 * How long a track has to play before the listen is counted, rather than merely remembered.
 *
 * Ported verbatim from Fossify's `PLAY_COUNT_THRESHOLD_MS`.
 */
private const val PLAY_COUNT_THRESHOLD_MS = 10_000L

@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queueItemDao: QueueItemDao,
    private val playStatsDao: PlayStatsDao,
    private val repository: LibraryRepository,
    private val playbackSettings: PlaybackSettings,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var controller: MediaController? = null
    private var positionPollJob: Job? = null
    private var playCountJob: Job? = null

    // The player's repeat int cannot tell RepeatMode.ONE from STOP_AFTER_CURRENT, so the stored
    // mode is what the UI is told about.
    @Volatile private var storedRepeatMode: RepeatMode = playbackSettings.repeatMode.value

    // Read from the setting rather than held only in memory, so a queue restored on a cold start is
    // still playing from the collection it was started from.
    @Volatile private var storedParent: PlaybackParent? = playbackSettings.playbackParent.value

    /** The track already counted, so pausing and resuming cannot count the same listen twice. */
    private var countedTrackId: Long? = null
    private val hasRestoredQueue = AtomicBoolean(false)

    private val _playbackState = MutableStateFlow(PlaybackUiState())
    val playbackState: StateFlow<PlaybackUiState> = _playbackState.asStateFlow()

    // Owned here, process-wide, so a timer set from one screen keeps running whichever screen is open.
    private val sleepTimerHolder = SleepTimer(scope) { controller?.pause() }
    val sleepTimer: StateFlow<SleepTimerState> = sleepTimerHolder.state

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) startPositionPolling() else stopPositionPolling()
            schedulePlayCount(isPlaying)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            recordPlayStarted(mediaItem?.mediaId?.toLongOrNull())
            schedulePlayCount(controller?.isPlaying == true)
            sleepTimerHolder.onTrackEnd()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) sleepTimerHolder.onTrackEnd()
        }

        // Ported from Fossify's PlayerListener.onEvents: recompute UI state and re-save the
        // queue on essentially every relevant player event - not on a timer.
        override fun onEvents(player: Player, events: Player.Events) {
            val mediaController = controller ?: return
            if (events.containsAny(
                    Player.EVENT_POSITION_DISCONTINUITY,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_TRACKS_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                    Player.EVENT_AVAILABLE_COMMANDS_CHANGED,
                )
            ) {
                updateUiState(mediaController)
            }
            if (events.containsAny(
                    Player.EVENT_POSITION_DISCONTINUITY,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_TRACKS_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED,
                )
            ) {
                persistQueueState(mediaController)
            }
        }
    }

    init {
        scope.launch {
            playbackSettings.repeatMode.flow.collect { mode ->
                storedRepeatMode = mode
                controller?.let(::updateUiState)
            }
        }

        // This class is the only writer, so collecting is how the stored value reaches the UI once
        // the settings have loaded - the same path the repeat mode takes.
        scope.launch {
            playbackSettings.playbackParent.flow.collect { parent ->
                storedParent = parent
                controller?.let(::updateUiState)
            }
        }

        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener(
            {
                val mediaController = future.get()
                controller = mediaController
                mediaController.addListener(playerListener)
                updateUiState(mediaController)
                if (mediaController.isPlaying) startPositionPolling()
                restoreQueueIfNeeded(mediaController)
            },
            MoreExecutors.directExecutor(),
        )
    }

    /**
     * Builds a fresh queue from [tracks] and starts playback at [startIndex].
     *
     * [parent] is the collection those tracks came from, which is what every list marks as playing -
     * null for a queue that stands for the whole library rather than one collection. It is written
     * down with the queue, so the list playing when the app closes is still the one marked on the
     * next launch.
     */
    fun playTracks(tracks: List<Track>, startIndex: Int, parent: PlaybackParent? = null) {
        val mediaController = controller ?: return
        scope.launch { playbackSettings.setPlaybackParent(parent) }
        val mediaItems = tracks.map(Track::toMediaItem)
        mediaController.setMediaItems(mediaItems, startIndex, 0L)
        mediaController.prepare()
        mediaController.play()
    }

    /** Pauses or plays by what the play/pause button shows, so a press always does what it says. */
    @OptIn(UnstableApi::class)
    fun togglePlayPause() {
        val mediaController = controller ?: return
        Util.handlePlayPauseButtonAction(mediaController)
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    /** The player's position at this moment, for a screen that follows it closer than [playbackState] ticks. */
    fun currentPositionMs(): Long = controller?.currentPosition ?: _playbackState.value.positionMs

    fun skipToNext() {
        controller?.seekToNext()
    }

    fun skipToPrevious() {
        controller?.seekToPrevious()
    }

    /** Moves to the track before the current one without rewinding the current one first - what a swipe means. */
    fun skipToPreviousTrack() {
        controller?.seekToPreviousMediaItem()
    }

    fun playQueueItem(mediaItemIndex: Int) {
        val mediaController = controller ?: return
        mediaController.seekToDefaultPosition(mediaItemIndex)
        mediaController.play()
    }

    fun moveQueueItem(fromMediaItemIndex: Int, toMediaItemIndex: Int) {
        controller?.moveMediaItem(fromMediaItemIndex, toMediaItemIndex)
    }

    fun removeQueueItem(mediaItemIndex: Int) {
        controller?.removeMediaItem(mediaItemIndex)
    }

    fun insertQueueItem(mediaItemIndex: Int, track: Track) {
        controller?.addMediaItem(mediaItemIndex, track.toMediaItem())
    }

    /** Stops playback and drops the queue, the saved copy included, so nothing comes back on the next launch. */
    fun stopAndClearQueue() {
        val mediaController = controller ?: return
        mediaController.stop()
        mediaController.clearMediaItems()
        scope.launch { playbackSettings.setPlaybackParent(null) }
        scope.launch(Dispatchers.IO) { queueItemDao.clear() }
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimerHolder.start(minutes)
    }

    fun startSleepTimerAtEndOfTrack() {
        sleepTimerHolder.startAtEndOfTrack()
    }

    fun clearSleepTimer() {
        sleepTimerHolder.clear()
    }

    fun setShuffleEnabled(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
        scope.launch { playbackSettings.setShuffleEnabled(enabled) }
    }

    // Only the stored mode is written; PlaybackService puts it on the player and the notification,
    // and the collector in init hands it to the UI - the same path a notification press takes.
    fun cycleRepeatMode() {
        scope.launch { playbackSettings.cycleRepeatMode() }
    }

    @OptIn(UnstableApi::class)
    private fun updateUiState(mediaController: MediaController) {
        val queue = currentQueue(mediaController)
        val currentMediaItemIndex = mediaController.currentMediaItemIndex
        _playbackState.update {
            it.copy(
                // Playing as the user asked for it rather than as heard: a newly chosen track stays
                // playing while it buffers, instead of passing through a moment of pause.
                isPlaying = !Util.shouldShowPlayButton(mediaController),
                isBuffering = mediaController.playbackState == Player.STATE_BUFFERING,
                hasEnded = mediaController.playbackState == Player.STATE_ENDED,
                currentTrackId = mediaController.currentMediaItem?.mediaId?.toLongOrNull(),
                parent = storedParent,
                positionMs = mediaController.currentPosition,
                durationMs = currentDurationMsOrElse(it.durationMs),
                shuffleEnabled = mediaController.shuffleModeEnabled,
                repeatMode = storedRepeatMode,
                canSkipPrevious = mediaController.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS),
                canSkipNext = mediaController.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT),
                hasPreviousTrack = mediaController.hasPreviousMediaItem(),
                hasNextTrack = mediaController.hasNextMediaItem(),
                queue = queue,
                currentQueueIndex = queue.indexOfFirst { entry -> entry.mediaItemIndex == currentMediaItemIndex },
            )
        }
    }

    /** The queue in the order it plays - shuffle order while shuffling - the way ArchiveTune's `getQueueWindows` walks it. */
    private fun currentQueue(mediaController: MediaController): List<QueueEntry> {
        val timeline = mediaController.currentTimeline
        if (timeline.isEmpty) return emptyList()
        val shuffleEnabled = mediaController.shuffleModeEnabled
        val window = Timeline.Window()
        val occurrences = HashMap<Long, Int>()
        val queue = ArrayList<QueueEntry>(timeline.windowCount)
        var mediaItemIndex = timeline.getFirstWindowIndex(shuffleEnabled)
        while (mediaItemIndex != C.INDEX_UNSET) {
            timeline.getWindow(mediaItemIndex, window).mediaItem.mediaId.toLongOrNull()?.let { trackId ->
                val occurrence = occurrences.merge(trackId, 1, Int::plus)
                queue += QueueEntry(key = "$trackId:$occurrence", mediaItemIndex = mediaItemIndex, trackId = trackId)
            }
            mediaItemIndex = timeline.getNextWindowIndex(mediaItemIndex, Player.REPEAT_MODE_OFF, shuffleEnabled)
        }
        return queue
    }

    // Ported from Fossify's AudioHelper.resetQueue: a full delete-and-reinsert of the queue table
    // on every save, keeping it trivially consistent with the player's current timeline.
    /**
     * Remembers that a track was played, the moment it starts.
     *
     * Two different things are recorded, following Fossify's `PlayHistoryRecorder`: starting a track
     * is enough to make it recent, but only a listen that lasts counts towards how often it has been
     * played. Skipping through an album therefore reorders "Recent" without inflating any counts.
     */
    private fun recordPlayStarted(trackId: Long?) {
        playCountJob?.cancel()
        countedTrackId = null
        if (trackId == null) return
        scope.launch(Dispatchers.IO) {
            playStatsDao.recordPlayStarted(trackId, System.currentTimeMillis())
        }
    }

    /**
     * Arms the count for the remainder of the threshold, or cancels it when playback stops.
     *
     * The wait is measured from where the track already is rather than restarted, so resuming after
     * a pause does not start the clock again - the same arithmetic Fossify uses.
     */
    private fun schedulePlayCount(isPlaying: Boolean) {
        playCountJob?.cancel()
        if (!isPlaying) return
        val mediaController = controller ?: return
        val trackId = mediaController.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        if (trackId == countedTrackId) return

        val remainingMs = (PLAY_COUNT_THRESHOLD_MS - mediaController.currentPosition).coerceAtLeast(0L)
        playCountJob = scope.launch {
            delay(remainingMs)
            countedTrackId = trackId
            withContext(Dispatchers.IO) {
                playStatsDao.recordPlayCounted(trackId, System.currentTimeMillis())
            }
        }
    }

    private fun persistQueueState(mediaController: MediaController) {
        val currentItem = mediaController.currentMediaItem ?: return
        val currentId = currentItem.mediaId.toLongOrNull() ?: return
        val timeline = mediaController.currentTimeline
        if (timeline.isEmpty) return
        val position = mediaController.currentPosition
        val window = Timeline.Window()
        val items = (0 until timeline.windowCount).mapNotNull { index ->
            val id = timeline.getWindow(index, window).mediaItem.mediaId.toLongOrNull()
                ?: return@mapNotNull null
            QueueItemEntity(
                trackId = id,
                trackOrder = index,
                isCurrent = id == currentId,
                lastPositionMs = if (id == currentId) position else 0L,
            )
        }
        if (items.isEmpty()) return
        scope.launch(Dispatchers.IO) { queueItemDao.resetQueue(items) }
    }

    // Ported from Fossify's Player.maybePreparePlayer: restore is triggered by the first
    // MediaController to connect, guarded so it only ever runs once per process and only when
    // nothing is already loaded (e.g. the service survived and already has a queue).
    private fun restoreQueueIfNeeded(mediaController: MediaController) {
        if (mediaController.currentMediaItem != null) return
        if (!hasRestoredQueue.compareAndSet(false, true)) return
        scope.launch {
            val items = withContext(Dispatchers.IO) { queueItemDao.getAll() }
            if (items.isEmpty()) return@launch
            // Track ids are derived from file paths and so survive a rescan. Before that, a scan
            // reassigned every id, and a restored queue silently resolved to nothing after the
            // first relaunch.
            val tracksById = withContext(Dispatchers.IO) {
                repository.tracksByIds(items.map { it.trackId }).associateBy { it.id }
            }
            val restored = items.mapNotNull { queueItem ->
                tracksById[queueItem.trackId]?.let { track -> queueItem to track }
            }
            if (restored.isEmpty()) return@launch
            val currentIndex = restored.indexOfFirst { it.first.isCurrent }.takeIf { it >= 0 } ?: 0
            val startPositionMs = restored[currentIndex].first.lastPositionMs
            val mediaItems = restored.map { it.second.toMediaItem() }
            mediaController.setMediaItems(mediaItems, currentIndex, startPositionMs)
            mediaController.prepare()
        }
    }

    private fun currentDurationMsOrElse(fallback: Long): Long {
        val duration = controller?.duration ?: return fallback
        return if (duration == C.TIME_UNSET) fallback else duration
    }

    private fun startPositionPolling() {
        if (positionPollJob?.isActive == true) return
        positionPollJob = scope.launch {
            while (isActive) {
                controller?.let { mediaController ->
                    _playbackState.update { it.copy(positionMs = mediaController.currentPosition) }
                }
                delay(500)
            }
        }
    }

    private fun stopPositionPolling() {
        positionPollJob?.cancel()
        positionPollJob = null
    }

}
