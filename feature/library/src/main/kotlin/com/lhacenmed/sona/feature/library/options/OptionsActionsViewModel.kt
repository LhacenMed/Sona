package com.lhacenmed.sona.feature.library.options

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.model.PlaybackParent
import com.lhacenmed.sona.core.model.Playlist
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * What an options sheet's Play, Shuffle, Play next, Add to queue, Add to playlist and Share rows do -
 * shared by every entity's sheet, so a track, an album, an artist, a genre and a playlist all reach
 * the queue and the playlist table the same way.
 *
 * Scoped to the screen rather than the sheet, so work started from a row - resolving a collection's
 * tracks to share them - finishes even though the sheet has already slid away.
 */
@HiltViewModel
class OptionsActionsViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val playbackController: PlaybackController,
) : ViewModel() {

    /** Every playlist there is to add to. */
    val playlists: StateFlow<LibraryContent<Playlist>> = repository.playlists

    /**
     * Plays [target] now with shuffle off - Auxio's `playExplicit`. A track plays the list it was opened
     * from, starting at itself; anything else plays its own tracks from the first, as their parent.
     */
    fun play(target: OptionsTarget) {
        startPlayback(target, shuffled = false)
    }

    /**
     * Shuffles [target] - Auxio's `shuffleExplicit`. The queue is the one Play builds, in the same order,
     * with shuffle on: a track plays first, a collection starts from a random one of its own tracks.
     */
    fun shuffle(target: OptionsTarget) {
        startPlayback(target, shuffled = true)
    }

    /** Plays [target]'s own tracks right after the current one. */
    fun playNext(target: OptionsTarget) {
        viewModelScope.launch {
            val tracks = entityTracks(target)
            if (tracks.isNotEmpty()) playbackController.playNext(tracks)
        }
    }

    /** Plays [target]'s own tracks after everything else queued. */
    fun addToQueue(target: OptionsTarget) {
        viewModelScope.launch {
            val tracks = entityTracks(target)
            if (tracks.isNotEmpty()) playbackController.addToQueue(tracks)
        }
    }

    /** Adds [target]'s own tracks to an existing playlist. */
    fun addToPlaylist(target: OptionsTarget, playlistId: Long) {
        viewModelScope.launch {
            val trackIds = entityTracks(target).map { it.id }
            if (trackIds.isNotEmpty()) repository.addTracksToPlaylist(playlistId, trackIds)
        }
    }

    /** Creates a playlist named [name], then adds [target]'s own tracks to it. */
    fun createPlaylistAndAddTo(target: OptionsTarget, name: String) {
        viewModelScope.launch {
            val trackIds = entityTracks(target).map { it.id }
            val playlistId = repository.createPlaylist(name) ?: return@launch
            if (trackIds.isNotEmpty()) repository.addTracksToPlaylist(playlistId, trackIds)
        }
    }

    /** Hands [target]'s own tracks to [onLoaded] once they are known - what sharing a collection needs. */
    fun loadTracks(target: OptionsTarget, onLoaded: (List<Track>) -> Unit) {
        viewModelScope.launch { onLoaded(entityTracks(target)) }
    }

    /** The playlists already holding every one of [target]'s own tracks - Auxio's `PlaylistChoice.alreadyAdded`. */
    suspend fun playlistIdsHoldingAll(target: OptionsTarget): Set<Long> {
        val trackIds = entityTracks(target).map { it.id }
        return playlists.value.itemsOrEmpty
            .filter { playlist -> readyTracks(repository.playlistTracks(playlist.id)).map { it.id }.containsAll(trackIds) }
            .mapTo(HashSet()) { it.id }
    }

    private fun startPlayback(target: OptionsTarget, shuffled: Boolean) {
        viewModelScope.launch {
            val tracks = if (target is OptionsTarget.ForTrack) target.queueSource else entityTracks(target)
            if (tracks.isEmpty()) return@launch
            val startIndex = when {
                target is OptionsTarget.ForTrack -> tracks.indexOfFirst { it.id == target.track.id }.coerceAtLeast(0)
                shuffled -> tracks.indices.random()
                else -> 0
            }
            playbackController.playTracks(tracks, startIndex, parentOf(target), shuffled)
        }
    }

    /** [target] on its own - a track by itself, or a collection's tracks. Never the track's surrounding list. */
    private suspend fun entityTracks(target: OptionsTarget): List<Track> = when (target) {
        is OptionsTarget.ForTrack -> listOf(target.track)
        is OptionsTarget.ForAlbum -> readyTracks(repository.albumTracks(target.album.id))
        is OptionsTarget.ForArtist -> readyTracks(repository.artistTracks(target.artist.id))
        is OptionsTarget.ForGenre -> readyTracks(repository.genreTracks(target.genre.id))
        is OptionsTarget.ForPlaylist -> readyTracks(repository.playlistTracks(target.playlist.id))
    }

    private fun parentOf(target: OptionsTarget): PlaybackParent? = when (target) {
        is OptionsTarget.ForTrack -> target.queueParent
        is OptionsTarget.ForAlbum -> PlaybackParent.Album(target.album.id)
        is OptionsTarget.ForArtist -> PlaybackParent.Artist(target.artist.id)
        is OptionsTarget.ForGenre -> PlaybackParent.Genre(target.genre.id)
        is OptionsTarget.ForPlaylist -> PlaybackParent.Playlist(target.playlist.id)
    }

    /** Waits for the query to actually have rows rather than [LibraryContent.Loading]'s empty first emission. */
    private suspend fun readyTracks(query: Flow<LibraryContent<Track>>): List<Track> =
        query.first { it is LibraryContent.Ready }.itemsOrEmpty
}
