package com.lhacenmed.sona.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.PlaybackController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * What every "a heading, then a list of tracks you can play" screen needs: the album, artist, genre
 * and folder detail screens are the same screen with a different `WHERE` clause.
 *
 * Each of those used to observe the **entire** tracks table and filter it down to its own handful of
 * rows, on the main thread, every time anything in the library changed. Subclasses now supply a flow
 * that queries only their own rows (off an index added for exactly that), and everything else -
 * playback, the highlighted row - lives here once.
 */
abstract class TrackListDetailViewModel(
    private val playbackController: PlaybackController,
) : ViewModel() {

    abstract val tracks: StateFlow<LibraryContent<Track>>

    /** Just the playing track's id - see [LibraryViewModel.currentTrackId] for why not the state. */
    val currentTrackId: StateFlow<Long?> = playbackController.playbackState
        .map { it.currentTrackId }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onTrackClick(track: Track) {
        val all = tracks.value.itemsOrEmpty
        val index = all.indexOfFirst { it.id == track.id }
        if (index >= 0) playbackController.playTracks(all, index)
    }
}
