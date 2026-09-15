package com.lhacenmed.sona.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.datastore.PlayerSliderStyle
import com.lhacenmed.sona.core.datastore.PlayerStyle
import com.lhacenmed.sona.core.datastore.PlayerStyleSettings
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.PlaybackController
import com.lhacenmed.sona.feature.playback.PlaybackUiState
import com.lhacenmed.sona.feature.playback.QueueEntry
import com.lhacenmed.sona.feature.playback.SleepTimerState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Playback resolved against the library, for the mini player, the full player and its queue.
 *
 * [queue] holds the tracks in the order they play; [currentQueueIndex] is where the current track sits
 * in it, or -1 while nothing is playing.
 */
data class PlayerUiState(
    val playback: PlaybackUiState = PlaybackUiState(),
    val currentTrack: Track? = null,
    val queue: List<QueueTrack> = emptyList(),
    val currentQueueIndex: Int = -1,
    val isCurrentTrackFavorite: Boolean = false,
)

/** A slot of the queue with the track it holds. */
data class QueueTrack(
    val entry: QueueEntry,
    val track: Track,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val playbackController: PlaybackController,
    playerStyleSettings: PlayerStyleSettings,
) : ViewModel() {

    // The playback state ticks roughly twice a second while playing (it carries the position), so
    // resolving the queue is narrowed to the queue itself - otherwise it is redone on every tick for a
    // queue that has not changed.
    private val queue = combine(
        playbackController.playbackState.map { it.queue }.distinctUntilChanged(),
        repository.tracksById,
        ::resolveQueue,
    )

    // Started from what is already in memory, so a screen opened mid-playback draws the player on its
    // first frame rather than one frame later.
    val uiState: StateFlow<PlayerUiState> = combine(
        playbackController.playbackState,
        queue,
        repository.tracksById,
        repository.favoriteTrackIds,
        ::resolveUiState,
    ).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        playbackController.playbackState.value.let { playback ->
            resolveUiState(
                playback = playback,
                queue = resolveQueue(playback.queue, repository.tracksById.value),
                tracksById = repository.tracksById.value,
                favoriteTrackIds = repository.favoriteTrackIds.value,
            )
        },
    )

    val playerStyle: StateFlow<PlayerStyle> = playerStyleSettings.playerStyle.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), playerStyleSettings.playerStyle.value)

    val sliderStyle: StateFlow<PlayerSliderStyle> = playerStyleSettings.sliderStyle.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), playerStyleSettings.sliderStyle.value)

    val sleepTimer: StateFlow<SleepTimerState> = playbackController.sleepTimer

    /** The player's position at this moment, for the seek bar to follow closer than [uiState] ticks. */
    fun currentPositionMs(): Long = playbackController.currentPositionMs()

    fun onTogglePlayPause() {
        playbackController.togglePlayPause()
    }

    fun onSkipNext() {
        playbackController.skipToNext()
    }

    fun onSkipPrevious() {
        playbackController.skipToPrevious()
    }

    /** A swipe back moves to the previous track, never rewinding the current one first. */
    fun onSkipToPreviousTrack() {
        playbackController.skipToPreviousTrack()
    }

    fun onSeek(positionMs: Long) {
        playbackController.seekTo(positionMs)
    }

    fun onToggleShuffle() {
        playbackController.setShuffleEnabled(!playbackController.playbackState.value.shuffleEnabled)
    }

    /** Steps the repeat button through its enabled modes, in Fossify's order. */
    fun onCycleRepeatMode() {
        playbackController.cycleRepeatMode()
    }

    fun onToggleFavorite() {
        val track = uiState.value.currentTrack ?: return
        val isFavorite = uiState.value.isCurrentTrackFavorite
        viewModelScope.launch(Dispatchers.IO) {
            repository.setFavorite(track.id, !isFavorite)
        }
    }

    fun onPlayQueueItem(item: QueueTrack) {
        playbackController.playQueueItem(item.entry.mediaItemIndex)
    }

    /** Moves [item] to the slot [target] holds. */
    fun onMoveQueueItem(item: QueueTrack, target: QueueTrack) {
        playbackController.moveQueueItem(item.entry.mediaItemIndex, target.entry.mediaItemIndex)
    }

    /** Taken out from the last slot back, so every slot still to go keeps the index it was given. */
    fun onRemoveQueueItems(items: List<QueueTrack>) {
        items.sortedByDescending { it.entry.mediaItemIndex }.forEach { item ->
            playbackController.removeQueueItem(item.entry.mediaItemIndex)
        }
    }

    /** Put back from the first slot on, so each lands at the index it was taken from. */
    fun onRestoreQueueItems(items: List<QueueTrack>) {
        items.sortedBy { it.entry.mediaItemIndex }.forEach { item ->
            playbackController.insertQueueItem(item.entry.mediaItemIndex, item.track)
        }
    }

    fun onStopAndClearQueue() {
        playbackController.stopAndClearQueue()
    }

    fun onStartSleepTimer(minutes: Int) {
        playbackController.startSleepTimer(minutes)
    }

    fun onStartSleepTimerAtEndOfTrack() {
        playbackController.startSleepTimerAtEndOfTrack()
    }

    fun onClearSleepTimer() {
        playbackController.clearSleepTimer()
    }

    private fun resolveQueue(entries: List<QueueEntry>, tracksById: Map<Long, Track>): List<QueueTrack> =
        entries.mapNotNull { entry -> tracksById[entry.trackId]?.let { track -> QueueTrack(entry, track) } }

    private fun resolveUiState(
        playback: PlaybackUiState,
        queue: List<QueueTrack>,
        tracksById: Map<Long, Track>,
        favoriteTrackIds: Set<Long>,
    ): PlayerUiState {
        val track = playback.currentTrackId?.let(tracksById::get)
        val currentEntryKey = playback.queue.getOrNull(playback.currentQueueIndex)?.key
        return PlayerUiState(
            playback = playback,
            currentTrack = track,
            queue = queue,
            currentQueueIndex = queue.indexOfFirst { it.entry.key == currentEntryKey },
            isCurrentTrackFavorite = track != null && track.id in favoriteTrackIds,
        )
    }
}
