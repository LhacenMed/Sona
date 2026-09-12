package com.lhacenmed.sona.feature.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.datastore.LibrarySettings
import com.lhacenmed.sona.core.datastore.LibraryTab
import com.lhacenmed.sona.core.model.Album
import com.lhacenmed.sona.core.model.Artist
import com.lhacenmed.sona.core.model.Folder
import com.lhacenmed.sona.core.model.Genre
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.PlaybackController
import com.lhacenmed.sona.feature.scanner.MediaScanner
import com.lhacenmed.sona.feature.scanner.hasScannerPermission
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val CANONICAL_TAB_ORDER = listOf(
    LibraryTab.TRACKS,
    LibraryTab.ARTISTS,
    LibraryTab.ALBUMS,
    LibraryTab.GENRES,
    LibraryTab.FOLDERS,
)

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
) : ViewModel() {

    val tracks: StateFlow<LibraryContent<Track>> = repository.tracks
    val albums: StateFlow<LibraryContent<Album>> = repository.albums
    val artists: StateFlow<LibraryContent<Artist>> = repository.artists
    val genres: StateFlow<LibraryContent<Genre>> = repository.genres
    val folders: StateFlow<LibraryContent<Folder>> = repository.folders

    /**
     * Defaults to "all visible" so the tab strip never flashes empty before the first DataStore
     * emission lands.
     */
    val visibleTabs: StateFlow<List<LibraryTab>> = librarySettings.visibleTabs
        .map { visible -> CANONICAL_TAB_ORDER.filter { it in visible } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, CANONICAL_TAB_ORDER)

    val isScanning: StateFlow<Boolean> = mediaScanner.isScanning

    /**
     * Only the *identity* of the playing track, not the whole playback state.
     *
     * The track list highlights the current row, and it used to do that by collecting the entire
     * `PlaybackUiState` - which carries a playback position updated twice a second. Every one of
     * those ticks recomposed the whole list for a value the list does not display. Narrowing it to a
     * distinct-until-changed id means the list recomposes when the song changes, and not otherwise.
     */
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
     */
    fun onTrackClick(track: Track) {
        val all = tracks.value.itemsOrEmpty
        val index = all.indexOfFirst { it.id == track.id }
        if (index >= 0) playbackController.playTracks(all, index)
    }

    /** An explicit user-initiated rescan, which bypasses the unchanged-library skip. */
    fun onRescan() {
        mediaScanner.requestScan(force = true)
    }

    /**
     * Plays everything the current selection resolves to.
     *
     * Each tab selects whatever its own rows are - track ids on Tracks, album ids on Albums, folder
     * paths on Folders - so turning those keys back into tracks belongs here, with the tab, rather
     * than in the top bar. The bar only ever hands back the keys it was given.
     */
    fun playSelection(tab: LibraryTab, selectedKeys: Set<Any>) {
        if (selectedKeys.isEmpty()) return
        viewModelScope.launch {
            val selectedTracks = tracksForSelection(tab, selectedKeys)
            if (selectedTracks.isNotEmpty()) {
                playbackController.playTracks(selectedTracks, startIndex = 0)
            }
        }
    }

    /** Keeps the selected folders out of the library, then rescans so they leave it immediately. */
    fun excludeSelectedFolders(selectedKeys: Set<Any>) {
        if (selectedKeys.isEmpty()) return
        viewModelScope.launch {
            selectedKeys.filterIsInstance<String>().forEach { librarySettings.addExcludedFolder(it) }
            mediaScanner.requestScan()
        }
    }

    /**
     * Every row's selection key on the given tab, which is what "select all" selects.
     *
     * Read from the flows at the moment it is called rather than collected in composition: the
     * pager deliberately reads no library data, and subscribing it to all five lists just to
     * populate a menu action would undo that.
     */
    fun selectableKeys(tab: LibraryTab): List<Any> = when (tab) {
        LibraryTab.TRACKS -> tracks.value.itemsOrEmpty.map { it.id }
        LibraryTab.ALBUMS -> albums.value.itemsOrEmpty.map { it.id }
        LibraryTab.ARTISTS -> artists.value.itemsOrEmpty.map { it.id }
        LibraryTab.GENRES -> genres.value.itemsOrEmpty.map { it.id }
        LibraryTab.FOLDERS -> folders.value.itemsOrEmpty.map { it.path }
    }

    private suspend fun tracksForSelection(tab: LibraryTab, selectedKeys: Set<Any>): List<Track> =
        when (tab) {
            LibraryTab.TRACKS -> tracks.value.itemsOrEmpty.filter { it.id in selectedKeys }
            LibraryTab.ALBUMS -> selectedKeys.filterIsInstance<Long>()
                .flatMap { readyTracks(repository.albumTracks(it)) }
            LibraryTab.ARTISTS -> selectedKeys.filterIsInstance<Long>()
                .flatMap { readyTracks(repository.artistTracks(it)) }
            LibraryTab.GENRES -> selectedKeys.filterIsInstance<Long>()
                .flatMap { readyTracks(repository.genreTracks(it)) }
            LibraryTab.FOLDERS -> selectedKeys.filterIsInstance<String>()
                .flatMap { readyTracks(repository.folderTracks(it)) }
        }

    /**
     * Waits for the query to actually have rows rather than taking its first emission, which is
     * [LibraryContent.Loading] and would silently resolve a selection to nothing.
     */
    private suspend fun readyTracks(query: Flow<LibraryContent<Track>>): List<Track> =
        query.first { it is LibraryContent.Ready }.itemsOrEmpty
}
