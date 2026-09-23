package com.lhacenmed.sona.feature.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.data.sort.LibrarySortOrders
import com.lhacenmed.sona.core.datastore.LibrarySettings
import com.lhacenmed.sona.core.datastore.LibraryTab
import com.lhacenmed.sona.core.model.Album
import com.lhacenmed.sona.core.model.Artist
import com.lhacenmed.sona.core.model.Folder
import com.lhacenmed.sona.core.model.Genre
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.core.model.sort.SortableList
import com.lhacenmed.sona.feature.library.selection.SelectionKey
import com.lhacenmed.sona.feature.library.sort.SortControl
import com.lhacenmed.sona.feature.library.sort.control
import com.lhacenmed.sona.feature.playback.PlaybackController
import com.lhacenmed.sona.feature.scanner.MediaScanner
import com.lhacenmed.sona.feature.scanner.hasScannerPermission
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val CANONICAL_TAB_ORDER = listOf(
    LibraryTab.TRACKS,
    LibraryTab.ARTISTS,
    LibraryTab.ALBUMS,
    LibraryTab.GENRES,
    LibraryTab.FOLDERS,
)

private fun Set<LibraryTab>.inCanonicalOrder(): List<LibraryTab> = CANONICAL_TAB_ORDER.filter { it in this }

private fun LibraryTab.sortableList(): SortableList = when (this) {
    LibraryTab.TRACKS -> SortableList.TRACKS
    LibraryTab.ARTISTS -> SortableList.ARTISTS
    LibraryTab.ALBUMS -> SortableList.ALBUMS
    LibraryTab.GENRES -> SortableList.GENRES
    LibraryTab.FOLDERS -> SortableList.FOLDERS
}

/**
 * One ViewModel for the entire library pager, replacing the five near-identical ones it used to
 * create up front.
 *
 * Those five each queried the database, mapped every row and sorted the result independently - two
 * of them (tracks and folders) over the very same table - and each kept its own copy of the scan and
 * permission state. Every one of those pipelines re-ran on every library write. Now the lists come
 * pre-computed from [LibraryRepository], so this class holds no library work at all: it is tab
 * visibility, scan/permission state, and click handling.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: LibraryRepository,
    private val librarySettings: LibrarySettings,
    private val mediaScanner: MediaScanner,
    private val playbackController: PlaybackController,
    sortOrders: LibrarySortOrders,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow<String?>(null)

    /** What the bar is being searched for, or null while its search is closed. */
    val searchQuery: StateFlow<String?> = _searchQuery.asStateFlow()

    val tracks: StateFlow<LibraryContent<Track>> =
        repository.tracks.narrowedBySearch { track, query -> matchesSearch(query, track.title, track.artist) }
    val albums: StateFlow<LibraryContent<Album>> =
        repository.albums.narrowedBySearch { album, query -> matchesSearch(query, album.title, album.artistName) }
    val artists: StateFlow<LibraryContent<Artist>> =
        repository.artists.narrowedBySearch { artist, query -> matchesSearch(query, artist.name) }
    val genres: StateFlow<LibraryContent<Genre>> =
        repository.genres.narrowedBySearch { genre, query -> matchesSearch(query, genre.name) }
    val folders: StateFlow<LibraryContent<Folder>> =
        repository.folders.narrowedBySearch { folder, query -> matchesSearch(query, folder.name) }

    /** The section each tab's rows sit in under its sort - what the fast scroller's popup names. */
    val trackSections: StateFlow<(Track) -> String?> = repository.trackSections
    val albumSections: StateFlow<(Album) -> String?> = repository.albumSections
    val artistSections: StateFlow<(Artist) -> String?> = repository.artistSections
    val genreSections: StateFlow<(Genre) -> String?> = repository.genreSections
    val folderSections: StateFlow<(Folder) -> String?> = repository.folderSections

    /**
     * A tab's rows as the search leaves them.
     *
     * One query narrows every tab at once, so swiping across them shows the same search answered five
     * ways. The lists are already in memory and pre-sorted, so this is a filter rather than a query -
     * which is what lets a tab keep its own order while being searched, and what makes every tab
     * answer a keystroke on the same frame.
     *
     * Because the tabs read these rather than the repository's, everything downstream follows the
     * search for free: what a row plays from, and what Select all selects.
     */
    private fun <T> StateFlow<LibraryContent<T>>.narrowedBySearch(
        matches: (T, String?) -> Boolean,
    ): StateFlow<LibraryContent<T>> = combine(this, _searchQuery) { content, query ->
        if (query.isNullOrBlank()) content else content.filterItems { matches(it, query) }
    }
        // Off the main thread: a large library is tens of thousands of rows, and a keystroke narrows
        // every tab at once. The initial value is still read synchronously, so the first frame is
        // unaffected.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), value)

    private val tabSorts: Map<LibraryTab, SortControl> =
        LibraryTab.entries.associateWith { tab -> sortOrders.control(tab.sortableList()) }

    /** How [tab] is sorted - what the bar's sort button opens over whichever tab is showing. */
    fun sort(tab: LibraryTab): SortControl = tabSorts.getValue(tab)

    /**
     * Starts from the stored tabs, so the strip is already right on its first frame rather than
     * showing every tab until the setting arrives and then dropping the hidden ones.
     */
    val visibleTabs: StateFlow<List<LibraryTab>> = librarySettings.visibleTabs.flow
        .map { it.inCanonicalOrder() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, librarySettings.visibleTabs.value.inCanonicalOrder())

    val isScanning: StateFlow<Boolean> = mediaScanner.isScanning

    /** What every tab marks as playing - see [LibraryPlayback]. */
    val playback: StateFlow<LibraryPlayback> = libraryPlayback(playbackController, repository)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryPlayback())

    // Permission state has no dedicated change broadcast; re-checking it whenever a scan
    // starts/stops (the moment it would actually change, since granting it is what unblocks the
    // first scan) is enough to avoid a stale "permission needed" message without any polling.
    val hasPermission: StateFlow<Boolean> = mediaScanner.isScanning
        .map { context.hasScannerPermission() }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            context.hasScannerPermission(),
        )

    /**
     * Starts playback of the whole tracks tab, beginning at [track]. The index is resolved here
     * rather than passed down so the list can stay keyed by identity rather than by position.
     *
     * A track already playing from this list pauses or resumes instead of starting the queue over -
     * see [LibraryPlayback.isReselection].
     */
    fun onTrackClick(track: Track) {
        if (playback.value.isReselection(track, listParent = null)) {
            playbackController.togglePlayPause()
            return
        }
        val all = tracks.value.itemsOrEmpty
        val index = all.indexOfFirst { it.id == track.id }
        if (index >= 0) playbackController.playTracks(all, index)
    }

    /** An explicit user-initiated rescan, which bypasses the unchanged-library skip. */
    fun onRescan() {
        mediaScanner.requestScan(force = true)
    }

    /** Opens the bar's search with an empty query, narrows it, or - with null - closes it. */
    fun onSearchQueryChange(query: String?) {
        _searchQuery.value = query
    }

    /**
     * Every row's selection key on the given tab, which is what "select all" selects - an artist with
     * no tracks left out, as its row cannot be selected.
     *
     * Read from the flows at the moment it is called rather than collected in composition: the
     * pager deliberately reads no library data, and subscribing it to all five lists just to
     * populate a menu action would undo that.
     */
    fun selectableKeys(tab: LibraryTab): List<SelectionKey> = when (tab) {
        LibraryTab.TRACKS -> tracks.value.itemsOrEmpty.map { SelectionKey.Track(it.id) }
        LibraryTab.ALBUMS -> albums.value.itemsOrEmpty.map { SelectionKey.Album(it.id) }
        LibraryTab.ARTISTS -> artists.value.itemsOrEmpty.filter { it.trackCount > 0 }.map { SelectionKey.Artist(it.id) }
        LibraryTab.GENRES -> genres.value.itemsOrEmpty.map { SelectionKey.Genre(it.id) }
        LibraryTab.FOLDERS -> folders.value.itemsOrEmpty.map { SelectionKey.Folder(it.path) }
    }
}
