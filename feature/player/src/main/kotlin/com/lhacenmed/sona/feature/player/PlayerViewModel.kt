package com.lhacenmed.sona.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.datastore.PlayerAppearance
import com.lhacenmed.sona.core.datastore.PlayerStyleSettings
import com.lhacenmed.sona.core.datastore.stateIn
import com.lhacenmed.sona.core.model.Album
import com.lhacenmed.sona.core.model.Artist
import com.lhacenmed.sona.core.model.Folder
import com.lhacenmed.sona.core.model.Genre
import com.lhacenmed.sona.core.model.PlaybackParent
import com.lhacenmed.sona.core.model.Playlist
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
    /** The playback state, save for its position - read that through [PlayerViewModel.currentPositionMs]. */
    val playback: PlaybackUiState = PlaybackUiState(),
    val currentTrack: Track? = null,
    val queue: List<QueueTrack> = emptyList(),
    val currentQueueIndex: Int = -1,
    val isCurrentTrackFavorite: Boolean = false,
    /**
     * Whether the playback and the library have both loaded, so a null [currentTrack] means nothing is
     * playing rather than that it is not known yet.
     */
    val isResolved: Boolean = false,
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

    // The position is the one part of the playback state that moves on its own, ticking twice a second
    // while playing, and nothing drawn from this state shows it: the seek bar and the lyrics follow the
    // player far closer than that, reading it straight through [currentPositionMs]. Carried in, every
    // tick would rebuild the state and recompose the player and every row of the queue - under a
    // reorder drag included, where a list rebuilt twice a second is what the drag stutters against.
    private val steadyPlaybackState = playbackController.playbackState
        .map { it.withoutPosition() }
        .distinctUntilChanged()

    // Narrowed to the queue itself, so it is not resolved again for a queue that has not changed.
    private val queue = combine(
        steadyPlaybackState.map { it.queue }.distinctUntilChanged(),
        repository.tracksById,
        ::resolveQueue,
    )

    // Started from what is already in memory, so a screen opened mid-playback draws the player on its
    // first frame rather than one frame later.
    val uiState: StateFlow<PlayerUiState> = combine(
        steadyPlaybackState,
        queue,
        repository.tracksById,
        repository.favoriteTrackIds,
        repository.isReady,
        ::resolveUiState,
    ).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        playbackController.playbackState.value.withoutPosition().let { playback ->
            resolveUiState(
                playback = playback,
                queue = resolveQueue(playback.queue, repository.tracksById.value),
                tracksById = repository.tracksById.value,
                favoriteTrackIds = repository.favoriteTrackIds.value,
                isLibraryReady = repository.isReady.value,
            )
        },
    )

    /**
     * What the queue is playing from, for the line under "Now Playing" - renamed as its collection is.
     * Started from the library already in memory, so the line is right on the player's first frame.
     */
    internal val playingFrom: StateFlow<PlayingFrom?> = combine(
        playbackController.playbackState.map { it.parent }.distinctUntilChanged(),
        repository.albums,
        repository.artists,
        repository.genres,
        combine(repository.playlists, repository.folders, ::Pair),
    ) { parent, albums, artists, genres, (playlists, folders) ->
        resolvePlayingFrom(parent, albums, artists, genres, playlists, folders)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        resolvePlayingFrom(
            parent = playbackController.playbackState.value.parent,
            albums = repository.albums.value,
            artists = repository.artists.value,
            genres = repository.genres.value,
            playlists = repository.playlists.value,
            folders = repository.folders.value,
        ),
    )

    val appearance: StateFlow<PlayerAppearance> = playerStyleSettings.appearance.stateIn(viewModelScope)

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

    /** Moves the track at [fromPosition] of [PlayerUiState.queue] to [toPosition] - shuffled or not. */
    fun onMoveQueueItem(fromPosition: Int, toPosition: Int) {
        playbackController.moveQueueItem(fromPosition, toPosition)
    }

    /** Plays [item] right after the track playing now - moved there, not repeated. */
    fun onPlayQueueItemNext(item: QueueTrack) {
        playbackController.playNext(listOf(item.track))
    }

    fun onRemoveQueueItem(item: QueueTrack) {
        playbackController.removeQueueItem(item.entry.mediaItemIndex)
    }

    /** Puts [item] back where it was removed from: at [playPosition] of [PlayerUiState.queue], shuffled or not. */
    fun onRestoreQueueItem(item: QueueTrack, playPosition: Int) {
        playbackController.restoreQueueItem(item.track, item.entry.mediaItemIndex, playPosition)
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

    private fun resolvePlayingFrom(
        parent: PlaybackParent?,
        albums: LibraryContent<Album>,
        artists: LibraryContent<Artist>,
        genres: LibraryContent<Genre>,
        playlists: LibraryContent<Playlist>,
        folders: LibraryContent<Folder>,
    ): PlayingFrom? =
        playingFromOf(
            parent = parent,
            albums = albums.itemsOrEmpty,
            artists = artists.itemsOrEmpty,
            genres = genres.itemsOrEmpty,
            playlists = playlists.itemsOrEmpty,
            folders = folders.itemsOrEmpty,
        )

    private fun resolveUiState(
        playback: PlaybackUiState,
        queue: List<QueueTrack>,
        tracksById: Map<Long, Track>,
        favoriteTrackIds: Set<Long>,
        isLibraryReady: Boolean,
    ): PlayerUiState {
        val track = playback.currentTrackId?.let(tracksById::get)
        val currentEntryKey = playback.queue.getOrNull(playback.currentQueueIndex)?.key
        return PlayerUiState(
            playback = playback,
            currentTrack = track,
            queue = queue,
            currentQueueIndex = queue.indexOfFirst { it.entry.key == currentEntryKey },
            isCurrentTrackFavorite = track != null && track.id in favoriteTrackIds,
            isResolved = playback.isReady && isLibraryReady,
        )
    }
}

/** This playback state with its ticking position dropped - see [PlayerViewModel.steadyPlaybackState]. */
private fun PlaybackUiState.withoutPosition(): PlaybackUiState = copy(positionMs = 0L)
