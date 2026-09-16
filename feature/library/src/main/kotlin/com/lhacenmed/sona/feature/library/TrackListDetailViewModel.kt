package com.lhacenmed.sona.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.model.PlaybackParent
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.library.sort.SortControl
import com.lhacenmed.sona.feature.playback.PlaybackController
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    repository: LibraryRepository,
) : ViewModel() {

    abstract val tracks: StateFlow<LibraryContent<Track>>

    /** How this list is sorted, or null for one whose order is its content - Recent and Most played. */
    open val sort: SortControl? = null

    /**
     * The collection this screen is, which playing from here plays from.
     *
     * It is what lets this list mark the playing track only while the queue is its own - the same
     * track playing from somewhere else leaves the row alone, exactly as on Auxio's detail screens.
     */
    abstract val playbackParent: PlaybackParent

    /** What this list marks as playing - see [LibraryPlayback]. */
    val playback: StateFlow<LibraryPlayback> = libraryPlayback(playbackController, repository)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryPlayback())

    /**
     * Plays [track] from this list - or, when it is already playing from this very list, pauses or
     * resumes it rather than starting the queue over. See [LibraryPlayback.isReselection].
     */
    fun onTrackClick(track: Track) {
        if (playback.value.isReselection(track, playbackParent)) {
            playbackController.togglePlayPause()
            return
        }
        val all = tracks.value.itemsOrEmpty
        val index = all.indexOfFirst { it.id == track.id }
        if (index >= 0) playbackController.playTracks(all, index, playbackParent)
    }

    /**
     * Plays just the selected rows, keeping the order the list shows them in.
     *
     * The queue is then those rows rather than this collection, so it plays from no collection at all -
     * Auxio's selection playback sets no parent either.
     */
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
