package com.lhacenmed.sona.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.database.dao.TrackDao
import com.lhacenmed.sona.core.database.entity.toDomain
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Combined playback + current-track state exposed to the mini player and the expandable
 * [com.lhacenmed.sona.feature.player.PlayerScreen]. [queueTracks] resolves
 * [PlaybackUiState.queue] (ordered track ids) to full [Track]s, in queue order, for the artwork
 * pager - kept reactive (re-derived from [TrackDao.observeAll]) rather than fetched once, so a
 * favorite toggle or library rescan is reflected immediately.
 */
data class PlayerUiState(
    val playback: PlaybackUiState = PlaybackUiState(),
    val currentTrack: Track? = null,
    val queueTracks: List<Track> = emptyList(),
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val trackDao: TrackDao,
    private val playbackController: PlaybackController,
) : ViewModel() {

    val uiState: StateFlow<PlayerUiState> = combine(
        playbackController.playbackState,
        trackDao.observeAll(),
    ) { playback, entities ->
        val tracksById = entities.associateBy { it.id }
        PlayerUiState(
            playback = playback,
            currentTrack = tracksById[playback.currentTrackId]?.toDomain(),
            queueTracks = playback.queue.mapNotNull { id -> tracksById[id]?.toDomain() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayerUiState())

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
        viewModelScope.launch(Dispatchers.IO) {
            trackDao.setFavorite(track.id, !track.isFavorite)
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
