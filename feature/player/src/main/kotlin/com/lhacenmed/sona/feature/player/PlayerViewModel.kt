package com.lhacenmed.sona.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.model.RepeatMode
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.PlaybackController
import com.lhacenmed.sona.feature.playback.PlaybackUiState
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
 * Combined playback + current-track state exposed to the mini player and the expandable
 * [com.lhacenmed.sona.feature.player.PlayerScreen]. [queueTracks] resolves
 * [PlaybackUiState.queue] (ordered track ids) to full [Track]s, in queue order, for the artwork
 * pager - kept reactive (re-derived from [LibraryRepository.tracksById]) rather than fetched once,
 * so a favourite toggle or library rescan is reflected immediately, but without ever re-reading the
 * database: the repository already holds the library, and this only indexes into it.
 */
data class PlayerUiState(
    val playback: PlaybackUiState = PlaybackUiState(),
    val currentTrack: Track? = null,
    val queueTracks: List<Track> = emptyList(),
    val isCurrentTrackFavorite: Boolean = false,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val playbackController: PlaybackController,
) : ViewModel() {

    // The playback state ticks roughly twice a second while playing (it carries the position), so
    // anything expensive keyed off it must be narrowed to the field it actually depends on first -
    // otherwise resolving the queue is redone on every tick for a queue that has not changed.
    private val currentTrack = combine(
        playbackController.playbackState.map { it.currentTrackId }.distinctUntilChanged(),
        repository.tracksById,
    ) { trackId, tracksById -> tracksById[trackId] }

    private val queueTracks = combine(
        playbackController.playbackState.map { it.queue }.distinctUntilChanged(),
        repository.tracksById,
    ) { queue, tracksById -> queue.mapNotNull { tracksById[it] } }

    val uiState: StateFlow<PlayerUiState> = combine(
        playbackController.playbackState,
        currentTrack,
        queueTracks,
        repository.favoriteTrackIds,
    ) { playback, track, queue, favoriteIds ->
        PlayerUiState(
            playback = playback,
            currentTrack = track,
            queueTracks = queue,
            isCurrentTrackFavorite = track != null && track.id in favoriteIds,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerUiState())

    fun onTogglePlayPause() {
        playbackController.togglePlayPause()
    }

    fun onSkipNext() {
        playbackController.skipToNext()
    }

    fun onSkipPrevious() {
        playbackController.skipToPrevious()
    }

    fun onSeek(positionMs: Long) {
        playbackController.seekTo(positionMs)
    }

    fun onSetShuffleEnabled(enabled: Boolean) {
        playbackController.setShuffleEnabled(enabled)
    }

    fun onSetRepeatMode(mode: RepeatMode) {
        playbackController.setRepeatMode(mode)
    }

    /** Cycles OFF -> ALL -> ONE -> OFF, matching Fossify's repeat-button tap behavior. */
    fun onCycleRepeatMode() {
        val next = when (uiState.value.playback.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        onSetRepeatMode(next)
    }

    fun onToggleFavorite() {
        val track = uiState.value.currentTrack ?: return
        val isFavorite = uiState.value.isCurrentTrackFavorite
        viewModelScope.launch(Dispatchers.IO) {
            repository.setFavorite(track.id, !isFavorite)
        }
    }

    /**
     * Jumps playback directly to [index] within the current queue. Used when the artwork pager
     * settles on a page that isn't adjacent to the currently playing track (e.g. a multi-page
     * fling) - an adjacent settle instead goes through [onSkipNext]/[onSkipPrevious] so the
     * queue's actual next/previous semantics (shuffle, repeat) are respected.
     */
    fun onJumpToQueueIndex(index: Int) {
        val tracks = uiState.value.queueTracks
        if (index !in tracks.indices) return
        playbackController.playTracks(tracks, index)
    }
}
