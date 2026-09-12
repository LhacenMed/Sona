package com.lhacenmed.sona.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.PlaybackController
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    /**
     * Whether that track is actually playing, which is what the playing indicator animates on.
     * Split from [currentTrackId] for the same reason it exists: the two change at different
     * moments, and a row that took both as one value would recompose on each.
     */
    val isPlaying: StateFlow<Boolean> = playbackController.playbackState
        .map { it.isPlaying }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun onTrackClick(track: Track) {
        val all = tracks.value.itemsOrEmpty
        val index = all.indexOfFirst { it.id == track.id }
        if (index >= 0) playbackController.playTracks(all, index)
    }

    /** Plays just the selected rows, keeping the order the list shows them in. */
    fun playSelection(selectedKeys: Set<Any>) {
        val selected = tracks.value.itemsOrEmpty.filter { it.id in selectedKeys }
        if (selected.isNotEmpty()) playbackController.playTracks(selected, startIndex = 0)
    }

    /** Every row's selection key, which is what the context bar's "select all" selects. */
    fun selectableKeys(): List<Any> = tracks.value.itemsOrEmpty.map { it.id }

    /**
     * Writes what this screen is showing as an M3U file.
     *
     * Available to every track list, including the derived ones: exporting only reads, so "the
     * fifty things I played most" is as exportable as a playlist someone built by hand.
     */
    fun exportTo(openStream: () -> OutputStream?) {
        val exported = tracks.value.itemsOrEmpty
        if (exported.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            openStream()?.use { stream -> writeM3u(stream, exported) }
        }
    }
}
