package com.lhacenmed.sona.feature.playback

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.lhacenmed.sona.core.database.dao.QueueItemDao
import com.lhacenmed.sona.core.database.dao.TrackDao
import com.lhacenmed.sona.core.database.entity.QueueItemEntity
import com.lhacenmed.sona.core.database.entity.toDomain
import com.lhacenmed.sona.core.datastore.PlaybackSettings
import com.lhacenmed.sona.core.model.RepeatMode
import com.lhacenmed.sona.core.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
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
@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queueItemDao: QueueItemDao,
    private val trackDao: TrackDao,
    private val playbackSettings: PlaybackSettings,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var controller: MediaController? = null
    private var positionPollJob: Job? = null
    private val hasRestoredQueue = AtomicBoolean(false)

    private val _playbackState = MutableStateFlow(PlaybackUiState())
    val playbackState: StateFlow<PlaybackUiState> = _playbackState.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) startPositionPolling() else stopPositionPolling()
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
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
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

    /** Builds a fresh queue from [tracks] and starts playback at [startIndex]. */
    fun playTracks(tracks: List<Track>, startIndex: Int) {
        val mediaController = controller ?: return
        val mediaItems = tracks.map(::toMediaItem)
        mediaController.setMediaItems(mediaItems, startIndex, 0L)
        mediaController.prepare()
        mediaController.play()
    }

    fun togglePlayPause() {
        val mediaController = controller ?: return
        if (mediaController.isPlaying) mediaController.pause() else mediaController.play()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun skipToNext() {
        controller?.seekToNext()
    }

    fun skipToPrevious() {
        controller?.seekToPrevious()
    }

    fun setShuffleEnabled(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
        scope.launch { playbackSettings.setShuffleEnabled(enabled) }
    }

    fun setRepeatMode(mode: RepeatMode) {
        controller?.repeatMode = mode.toPlayerRepeatMode()
        scope.launch { playbackSettings.setRepeatMode(mode) }
    }

    private fun updateUiState(mediaController: MediaController) {
        _playbackState.update {
            it.copy(
                isPlaying = mediaController.isPlaying,
                currentTrackId = mediaController.currentMediaItem?.mediaId?.toLongOrNull(),
                positionMs = mediaController.currentPosition,
                durationMs = currentDurationMsOrElse(it.durationMs),
                shuffleEnabled = mediaController.shuffleModeEnabled,
                repeatMode = mediaController.repeatMode.toRepeatMode(),
                queue = currentQueueIds(mediaController),
            )
        }
    }

    private fun currentQueueIds(mediaController: MediaController): List<Long> {
        val timeline = mediaController.currentTimeline
        if (timeline.isEmpty) return emptyList()
        val window = Timeline.Window()
        return (0 until timeline.windowCount).mapNotNull { index ->
            timeline.getWindow(index, window).mediaItem.mediaId.toLongOrNull()
        }
    }

    // Ported from Fossify's AudioHelper.resetQueue: a full delete-and-reinsert of the queue table
    // on every save, keeping it trivially consistent with the player's current timeline.
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
            val tracksById = withContext(Dispatchers.IO) {
                trackDao.getByIds(items.map { it.trackId }).associateBy { it.id }
            }
            val restored = items.mapNotNull { queueItem ->
                tracksById[queueItem.trackId]?.let { entity -> queueItem to entity.toDomain() }
            }
            if (restored.isEmpty()) return@launch
            val currentIndex = restored.indexOfFirst { it.first.isCurrent }.takeIf { it >= 0 } ?: 0
            val startPositionMs = restored[currentIndex].first.lastPositionMs
            val mediaItems = restored.map { toMediaItem(it.second) }
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

    private fun toMediaItem(track: Track): MediaItem {
        val uri: Uri = if (track.isManuallyScanned) {
            Uri.fromFile(File(track.path))
        } else {
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track.mediaStoreId)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .apply {
                track.coverArtUri?.let { setArtworkUri(Uri.parse(it)) }
            }
            .build()
        return MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }
}
