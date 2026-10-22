package com.lhacenmed.sona.feature.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.common.permission.AppPermission
import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.data.plus
import com.lhacenmed.sona.core.data.quickplay.QuickPlay
import com.lhacenmed.sona.core.data.quickplay.QuickPlaySourceRepository
import com.lhacenmed.sona.core.data.search.SearchIndex
import com.lhacenmed.sona.core.data.sort.LibrarySortOrders
import com.lhacenmed.sona.core.datastore.LibrarySettings
import com.lhacenmed.sona.core.datastore.LibraryTab
import com.lhacenmed.sona.core.datastore.QuickPlayMode
import com.lhacenmed.sona.core.datastore.QuickPlaySettings
import com.lhacenmed.sona.core.model.Album
import com.lhacenmed.sona.core.model.Artist
import com.lhacenmed.sona.core.model.Folder
import com.lhacenmed.sona.core.model.Genre
import com.lhacenmed.sona.core.model.PlaybackParent
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.core.model.sort.SortableList
import com.lhacenmed.sona.feature.library.selection.SelectionKey
import com.lhacenmed.sona.feature.library.sort.SortControl
import com.lhacenmed.sona.feature.library.sort.control
import com.lhacenmed.sona.feature.playback.PlaybackController
import com.lhacenmed.sona.feature.scanner.MediaScanner
import com.lhacenmed.sona.feature.tageditor.LyricsMatches
import com.lhacenmed.sona.feature.tageditor.LyricsSearchIndex
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

private val CANONICAL_TAB_ORDER = listOf(
    LibraryTab.TRACKS,
    LibraryTab.ARTISTS,
    LibraryTab.ALBUMS,
    LibraryTab.GENRES,
    LibraryTab.FOLDERS,
    LibraryTab.VIDEOS,
)

private fun Set<LibraryTab>.inCanonicalOrder(): List<LibraryTab> = CANONICAL_TAB_ORDER.filter { it in this }

/** How long typing pauses before the search looks in the lyrics - see [LibraryViewModel]'s lyrics matches. */
private const val LYRICS_SEARCH_DELAY_MS = 150L

/** The Tracks tab as a search leaves it, and the lyric line each track found by its lyrics alone shows. */
private class SearchedTracks(val content: LibraryContent<Track>, val lyricLines: Map<Long, String> = emptyMap())

private fun LibraryTab.sortableList(): SortableList = when (this) {
    LibraryTab.TRACKS -> SortableList.TRACKS
    LibraryTab.ARTISTS -> SortableList.ARTISTS
    LibraryTab.ALBUMS -> SortableList.ALBUMS
    LibraryTab.GENRES -> SortableList.GENRES
    LibraryTab.FOLDERS -> SortableList.FOLDERS
    LibraryTab.VIDEOS -> SortableList.VIDEOS
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
    quickPlaySettings: QuickPlaySettings,
    private val quickPlaySources: QuickPlaySourceRepository,
    private val mediaScanner: MediaScanner,
    private val playbackController: PlaybackController,
    private val lyricsSearchIndex: LyricsSearchIndex,
    sortOrders: LibrarySortOrders,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow<String?>(null)

    /** What the bar is being searched for, or null while its search is closed. */
    val searchQuery: StateFlow<String?> = _searchQuery.asStateFlow()

    /**
     * What the lyrics hold of the search - see [LyricsSearchIndex] - asked once typing pauses, so a word
     * still being typed is not looked for in every song's lyrics on each letter.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val lyricsMatches: StateFlow<LyricsMatches> = _searchQuery
        .mapLatest { query ->
            delay(LYRICS_SEARCH_DELAY_MS)
            lyricsSearchIndex.search(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LyricsMatches(query = null, lines = emptyMap()))

    /**
     * The Tracks tab as the search leaves it, with the lyric line of each track it found by its lyrics alone.
     * Those come after every track found by its title, artist or album, as the lyrics are searched once
     * typing pauses: the rows found first never move for them.
     */
    private val searchedTracks: StateFlow<SearchedTracks> =
        combine(repository.tracks.indexedForSearch { listOf(it.title, it.artist, it.album) }, _searchQuery, lyricsMatches) { (content, index), query, lyrics ->
            // Lyrics found for an earlier query are never shown under a later one.
            val lyricLines = if (lyrics.query == query) lyrics.lines else emptyMap()
            if (query.isNullOrBlank() || content !is LibraryContent.Ready) {
                SearchedTracks(content)
            } else {
                val result = index.search(query) { it.id in lyricLines }
                SearchedTracks(LibraryContent.Ready(result.items), result.foundOtherwise.associate { it.id to lyricLines.getValue(it.id) })
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchedTracks(repository.tracks.value))

    val tracks: StateFlow<LibraryContent<Track>> = searchedTracks
        .map { it.content }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), searchedTracks.value.content)

    /** The lyric line each track the search found by its lyrics alone shows in place of its artist and album. */
    val trackLyricLines: StateFlow<Map<Long, String>> = searchedTracks
        .map { it.lyricLines }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val albums: StateFlow<LibraryContent<Album>> =
        repository.albums.narrowedBySearch { listOf(it.title, it.artistName) }
    val artists: StateFlow<LibraryContent<Artist>> =
        repository.artists.narrowedBySearch { listOf(it.name) }
    val genres: StateFlow<LibraryContent<Genre>> =
        repository.genres.narrowedBySearch { listOf(it.name) }
    /** The Folders tab: the folders of music, then those of videos, each kind a section of its own. */
    val folders: StateFlow<LibraryContent<Folder>> =
        combine(repository.folders, repository.videoFolders, LibraryContent<Folder>::plus)
            .stateIn(viewModelScope, SharingStarted.Eagerly, repository.folders.value + repository.videoFolders.value)
            .narrowedBySearch { listOf(it.name) }
    val videos: StateFlow<LibraryContent<Track>> =
        repository.videos.narrowedBySearch { listOf(it.title, it.artist, it.album) }

    /** Where each video was left - see [LibraryRepository.resumePositionsMs]. */
    val resumePositionsMs: StateFlow<Map<Long, Long>> = repository.resumePositionsMs

    /** The section each tab's rows sit in under its sort - what the fast scroller's popup names - see [whileNotSearched]. */
    val trackSections: StateFlow<((Track) -> String?)?> = repository.trackSections.shownWhileNotSearched()
    val videoSections: StateFlow<((Track) -> String?)?> = repository.videoSections.shownWhileNotSearched()
    val albumSections: StateFlow<((Album) -> String?)?> = repository.albumSections.shownWhileNotSearched()
    val artistSections: StateFlow<((Artist) -> String?)?> = repository.artistSections.shownWhileNotSearched()
    val genreSections: StateFlow<((Genre) -> String?)?> = repository.genreSections.shownWhileNotSearched()
    val folderSections: StateFlow<((Folder) -> String?)?> = repository.folderSections.shownWhileNotSearched()

    /** A tab's rows, each with its [texts] indexed for search - once per change to the tab, not per keystroke. */
    private fun <T> StateFlow<LibraryContent<T>>.indexedForSearch(
        texts: (T) -> List<String>,
    ): Flow<Pair<LibraryContent<T>, SearchIndex<T>>> = map { content -> content to SearchIndex(content.itemsOrEmpty, texts) }

    /**
     * A tab's rows as the search leaves them: those it finds, best first - see [SearchIndex].
     *
     * One query searches every tab at once, so swiping across them shows the same search answered five
     * ways. The lists are already in memory, so this is a search of them rather than a query - which is
     * what makes every tab answer a keystroke on the same frame.
     *
     * Because the tabs read these rather than the repository's, everything downstream follows the
     * search for free: what a row plays from, and what Select all selects.
     */
    private fun <T> StateFlow<LibraryContent<T>>.narrowedBySearch(
        texts: (T) -> List<String>,
    ): StateFlow<LibraryContent<T>> = combine(indexedForSearch(texts), _searchQuery) { (content, index), query ->
        if (query.isNullOrBlank() || content !is LibraryContent.Ready) content else LibraryContent.Ready(index.search(query).items)
    }
        // Off the main thread: a large library is tens of thousands of rows, and a keystroke searches
        // every tab at once. The initial value is still read synchronously, so the first frame is
        // unaffected.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), value)

    private fun <T> StateFlow<(T) -> String?>.shownWhileNotSearched(): StateFlow<((T) -> String?)?> =
        combine(this, _searchQuery) { sections, query -> sections.whileNotSearched(query) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), value.whileNotSearched(_searchQuery.value))

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

    /** Whether pulling a tab's list down past its top rescans the library - see [rescanLibrary]. */
    val isPullToRefreshEnabled: StateFlow<Boolean> = librarySettings.pullToRefreshEnabled.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, librarySettings.pullToRefreshEnabled.value)

    /**
     * Reads every file on the device again, whatever the last scan found - a pull to refresh - and says
     * whether it worked. The scan itself runs on the application's scope, so a screen closing mid-way
     * only stops the waiting.
     */
    suspend fun rescanLibrary(): Boolean =
        try {
            mediaScanner.rescan(force = true)
            true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            false
        }

    /**
     * The quick play button as it shows - what it plays and how, its icon and its menu following - or null
     * while the user has turned it off. One state, so the button never shows one of them changed before the other.
     */
    val quickPlay: StateFlow<QuickPlay?> = combine(
        quickPlaySettings.showButton.flow,
        quickPlaySettings.mode.flow,
        quickPlaySources.source,
    ) { isShown, mode, source -> if (isShown) QuickPlay(mode, source) else null }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            if (quickPlaySettings.showButton.value) QuickPlay(quickPlaySettings.mode.value, quickPlaySources.source.value) else null,
        )

    /** Favorites, which the quick play button's menu always offers. */
    val favoritesPlaylistId: Long get() = repository.favoritesPlaylistId

    /**
     * Whether the library holds any track to shuffle - whatever a search narrows the tab to. A flag
     * rather than the list, so the pager that reads it is not recomposed by every library change.
     */
    val hasTracks: StateFlow<Boolean> = repository.tracks
        .map { it.itemsOrEmpty.isNotEmpty() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.tracks.value.itemsOrEmpty.isNotEmpty())

    /** What every tab marks as playing - see [LibraryPlayback]. */
    val playback: StateFlow<LibraryPlayback> = libraryPlayback(playbackController, repository)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryPlayback())

    // Permission state has no dedicated change broadcast; re-checking it whenever a scan
    // starts/stops (the moment it would actually change, since granting it is what unblocks the
    // first scan) is enough to avoid a stale "permission needed" message without any polling.
    val hasPermission: StateFlow<Boolean> = mediaScanner.isScanning
        .map { AppPermission.AUDIO_LIBRARY.isGranted(context) }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AppPermission.AUDIO_LIBRARY.isGranted(context),
        )

    /**
     * Reads the videos in, now that Sona may - a scan the device's unchanged media would otherwise skip,
     * which the permission is part of the reckoning of.
     */
    fun onVideoAccessGranted() {
        mediaScanner.requestScan()
    }

    /** Starts playback of the whole tracks tab, beginning at [track] - see [play]. */
    fun onTrackClick(track: Track) {
        play(track, tracks.value.itemsOrEmpty, parent = null)
    }

    /** Starts playback of the whole Videos tab, beginning at [video] - see [play]. */
    fun onVideoClick(video: Track) {
        play(video, videos.value.itemsOrEmpty, PlaybackParent.Videos)
    }

    /**
     * Plays [list] - a tab's rows, as [parent] - beginning at [track]. The index is resolved here rather
     * than passed down so the list can stay keyed by identity rather than by position.
     *
     * A track already playing from this list pauses or resumes instead of starting the queue over -
     * see [LibraryPlayback.isReselection]. A video playing as audio is watched again instead, in the video
     * player; one started opens there - see [PlaybackController.playTracks].
     */
    private fun play(track: Track, list: List<Track>, parent: PlaybackParent?) {
        if (playback.value.isReselection(track, listParent = parent)) {
            if (track.isVideo) playbackController.watchCurrent() else playbackController.togglePlayPause()
            return
        }
        val index = list.indexOfFirst { it.id == track.id }
        if (index >= 0) playbackController.playTracks(list, index, parent)
    }

    /** Plays [quickPlay] as the button shows it - see [PlaybackController.quickPlay]. */
    fun onQuickPlay(quickPlay: QuickPlay) {
        playbackController.quickPlay(quickPlay.source.parent, quickPlay.mode)
    }

    /** Makes [parent] - every track, while null - what quick play plays, and plays it now as [mode]. */
    fun onQuickPlayFrom(parent: PlaybackParent?, mode: QuickPlayMode) {
        quickPlaySources.choose(parent)
        playbackController.quickPlay(parent, mode)
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
        LibraryTab.FOLDERS -> folders.value.itemsOrEmpty.map { SelectionKey.Folder(it.path, it.isVideo) }
        LibraryTab.VIDEOS -> videos.value.itemsOrEmpty.map { SelectionKey.Track(it.id) }
    }
}
