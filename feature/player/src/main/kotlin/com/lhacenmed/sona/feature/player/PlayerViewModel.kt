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
import com.lhacenmed.sona.core.vault.VaultRepository
import com.lhacenmed.sona.feature.playback.PlaybackController
import com.lhacenmed.sona.feature.playback.PlaybackSpace
import com.lhacenmed.sona.feature.playback.PlaybackUiState
import com.lhacenmed.sona.feature.playback.QueueEntry
import com.lhacenmed.sona.feature.playback.SleepTimerState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

/**
 * How long a track must keep loading before the player says so. A local file loads in a moment, and a
 * loader shown at once flashed on every skip; one that takes longer than this still shows it - the delay
 * Android's `ContentLoadingProgressBar` waits.
 */
private const val LoaderDelayMillis = 500L

/**
 * Playback resolved against the library, for the mini player, the full player and its queue.
 *
 * [queue] holds the tracks in the order they play; [currentQueueIndex] is where the current track sits
 * in it, or -1 while nothing is playing.
 */
data class PlayerUiState(
    /**
     * The playback state, save for its position - read that through [PlayerViewModel.currentPositionMs] -
     * and buffering only once it has lasted [LoaderDelayMillis], so every loader the player draws waits alike.
     */
    val playback: PlaybackUiState = PlaybackUiState(),
    val currentTrack: Track? = null,
    val queue: List<QueueTrack> = emptyList(),
    val currentQueueIndex: Int = -1,
    val isCurrentTrackFavorite: Boolean = false,
    /** Whether the playing track can be a favourite - a library track's, never a Private Folder item's. */
    val canFavorite: Boolean = false,
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

/**
 * The player of one [space]: it shows only that space's playback, and none while a video is being watched -
 * the video player's - so the library's player and the Private Folder's never show each other's tracks.
 */
@HiltViewModel(assistedFactory = PlayerViewModel.Factory::class)
class PlayerViewModel @AssistedInject constructor(
    @Assisted private val space: PlaybackSpace,
    private val repository: LibraryRepository,
    private val vault: VaultRepository,
    private val playbackController: PlaybackController,
    playerStyleSettings: PlayerStyleSettings,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(space: PlaybackSpace): PlayerViewModel
    }

    // The library's tracks, or the Private Folder's - unknown, rather than empty, until read.
    private val catalog: StateFlow<Map<Long, Track>?> = when (space) {
        PlaybackSpace.Library -> combine(repository.tracksById, repository.isReady) { tracks, isReady -> tracks.takeIf { isReady } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, repository.tracksById.value.takeIf { repository.isReady.value })
        PlaybackSpace.Private -> vault.tracksById.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    }

    private val tracksById: Flow<Map<Long, Track>> = catalog.map { it.orEmpty() }

    // The position is the one part of the playback state that moves on its own, ticking twice a second
    // while playing, and nothing drawn from this state shows it: the seek bar and the lyrics follow the
    // player far closer than that, reading it straight through [currentPositionMs]. Carried in, every
    // tick would rebuild the state and recompose the player and every row of the queue - under a
    // reorder drag included, where a list rebuilt twice a second is what the drag stutters against.
    // Buffering, as the loaders show it: only once it has lasted [LoaderDelayMillis]. Not buffering is told
    // at once - first thing, so the state below is never held up waiting for the delay.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val isLoaderShown = playbackController.playbackState
        .map { it.isBuffering }
        .distinctUntilChanged()
        .transformLatest { isBuffering ->
            if (isBuffering) {
                emit(false)
                delay(LoaderDelayMillis)
            }
            emit(isBuffering)
        }

    private val steadyPlaybackState = combine(playbackController.playbackState, isLoaderShown) { playback, isLoaderShown ->
        playback.withoutPosition().copy(isBuffering = isLoaderShown)
    }.distinctUntilChanged()

    // Narrowed to the queue itself, so it is not resolved again for a queue that has not changed.
    private val queue = combine(
        steadyPlaybackState.map { it.takeIfShown()?.queue.orEmpty() }.distinctUntilChanged(),
        tracksById,
        ::resolveQueue,
    )

    // Started from what is already in memory, so a screen opened mid-playback draws the player on its
    // first frame rather than one frame later.
    val uiState: StateFlow<PlayerUiState> = combine(
        steadyPlaybackState,
        queue,
        catalog,
        repository.favoriteTrackIds,
        ::resolveUiState,
    ).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        playbackController.playbackState.value.withoutPosition().copy(isBuffering = false).let { playback ->
            resolveUiState(
                playback = playback,
                queue = resolveQueue(playback.takeIfShown()?.queue.orEmpty(), catalog.value.orEmpty()),
                catalog = catalog.value,
                favoriteTrackIds = repository.favoriteTrackIds.value,
            )
        },
    )

    /**
     * What the queue is playing from, for the line under "Now Playing" - renamed as its collection is.
     * Started from the library already in memory, so the line is right on the player's first frame.
     */
    internal val playingFrom: StateFlow<PlayingFrom?> = if (space == PlaybackSpace.Private) {
        // The Private Folder plays from none of the library's collections.
        MutableStateFlow(null)
    } else combine(
        playbackController.playbackState.map { it.parent }.distinctUntilChanged(),
        repository.albums,
        repository.artists,
        repository.genres,
        combine(repository.playlists, repository.folders, repository.videoFolders, ::Triple),
    ) { parent, albums, artists, genres, (playlists, folders, videoFolders) ->
        resolvePlayingFrom(parent, albums, artists, genres, playlists, folders, videoFolders)
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
            videoFolders = repository.videoFolders.value,
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
        if (!uiState.value.canFavorite) return
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
        if (space == PlaybackSpace.Library) {
            playbackController.playNext(listOf(item.track))
            return
        }
        // A Private Folder item is moved there within its own queue - the library's ways of adding know only its tracks.
        val state = uiState.value
        val fromPosition = state.queue.indexOfFirst { it.entry.key == item.entry.key }
        if (fromPosition < 0 || state.currentQueueIndex < 0) return
        val toPosition = if (fromPosition > state.currentQueueIndex) state.currentQueueIndex + 1 else state.currentQueueIndex
        playbackController.moveQueueItem(fromPosition, toPosition)
    }

    fun onRemoveQueueItem(item: QueueTrack) {
        playbackController.removeQueueItem(item.entry.mediaItemIndex)
    }

    /** Puts [item] back where it was removed from: at [playPosition] of [PlayerUiState.queue], shuffled or not. */
    fun onRestoreQueueItem(item: QueueTrack, playPosition: Int) {
        playbackController.restoreQueueItem(item.track, item.entry.mediaItemIndex, playPosition)
    }

    /** The player swiped away: the Private Folder's playback ends, giving the library's queue back; the library's is cleared. */
    fun onStopAndClearQueue() {
        when (space) {
            PlaybackSpace.Library -> playbackController.stopAndClearQueue()
            PlaybackSpace.Private -> playbackController.endPrivatePlayback()
        }
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
        videoFolders: LibraryContent<Folder>,
    ): PlayingFrom? =
        playingFromOf(
            parent = parent,
            albums = albums.itemsOrEmpty,
            artists = artists.itemsOrEmpty,
            genres = genres.itemsOrEmpty,
            playlists = playlists.itemsOrEmpty,
            // A folder of the Videos tab is named from its list: one path can hold music and videos alike.
            folders = (if ((parent as? PlaybackParent.Folder)?.isVideo == true) videoFolders else folders).itemsOrEmpty,
        )

    private fun resolveUiState(
        playback: PlaybackUiState,
        queue: List<QueueTrack>,
        catalog: Map<Long, Track>?,
        favoriteTrackIds: Set<Long>,
    ): PlayerUiState {
        val shown = playback.takeIfShown()
        val track = shown?.currentTrackId?.let { catalog?.get(it) }
        val currentEntryKey = shown?.queue?.getOrNull(shown.currentQueueIndex)?.key
        val canFavorite = space == PlaybackSpace.Library
        return PlayerUiState(
            playback = playback,
            currentTrack = track,
            queue = queue,
            currentQueueIndex = queue.indexOfFirst { it.entry.key == currentEntryKey },
            isCurrentTrackFavorite = canFavorite && track != null && track.id in favoriteTrackIds,
            canFavorite = canFavorite,
            isResolved = playback.isReady && catalog != null,
        )
    }

    /** [this], where it is this player's to show: its own space's, and listened to rather than watched. */
    private fun PlaybackUiState.takeIfShown(): PlaybackUiState? = takeIf { it.space == space && !it.isWatching }
}

/** This playback state with its ticking position dropped - see [PlayerViewModel.steadyPlaybackState]. */
private fun PlaybackUiState.withoutPosition(): PlaybackUiState = copy(positionMs = 0L)
