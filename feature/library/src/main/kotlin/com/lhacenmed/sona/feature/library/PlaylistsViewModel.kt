package com.lhacenmed.sona.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.common.cover.rankedCoverArtUris
import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.data.sort.LibrarySortOrders
import com.lhacenmed.sona.core.model.Playlist
import com.lhacenmed.sona.core.model.PlaylistCover
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.core.model.sort.SortableList
import com.lhacenmed.sona.feature.library.sort.SortControl
import com.lhacenmed.sona.feature.library.sort.control
import com.lhacenmed.sona.feature.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val repository: LibraryRepository,
    sortOrders: LibrarySortOrders,
    playbackController: PlaybackController,
) : ViewModel() {

    /** Already shared from the application scope, so this only hands it on. */
    val playlists: StateFlow<LibraryContent<Playlist>> = repository.playlists

    /** What the rows mark as playing - see [LibraryPlayback]. */
    val playback: StateFlow<LibraryPlayback> = libraryPlayback(playbackController, repository)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryPlayback())

    /** The covers Most played composes its own from, ranked the way any collection's are. */
    val mostPlayedCoverArtUris: StateFlow<List<String>> = repository.mostPlayedTracks().rankedCovers()

    val sort: SortControl = sortOrders.control(SortableList.PLAYLISTS)

    val mostPlayedCount: StateFlow<Int> = repository.mostPlayedCount

    val favoritesPlaylistId: Long get() = repository.favoritesPlaylistId

    /** Favorites' own cover, as its row shows it - see [playlistCover]. */
    val favoritesCover: StateFlow<ShortcutCover?> = playlistCover { it.id == favoritesPlaylistId }

    /**
     * The cover of the playlist the chosen sort puts first, as its row shows it - so re-sorting, or the
     * top playlist changing, changes the Playlists card with it. Favorites is left out: it keeps the top
     * whatever the sort, and has a card of its own. So are Recent and Most played, listening histories
     * rather than playlists, which are not among [playlists] at all.
     */
    val playlistsCover: StateFlow<ShortcutCover?> = playlistCover { !it.isBuiltIn }

    /** The cover of the track played last, or null when nothing has been played. */
    val recentlyPlayedCover: StateFlow<ShortcutCover?> = repository.recentlyPlayedTracks().topCover()

    fun renamePlaylist(playlistId: Long, name: String) {
        viewModelScope.launch { repository.renamePlaylist(playlistId, name) }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch { repository.createPlaylist(name) }
    }

    /** Creates a playlist holding everything the library already knows about under [folderPath]. */
    fun createPlaylistFromFolder(name: String, folderPath: String) {
        viewModelScope.launch {
            val playlistId = repository.createPlaylist(name) ?: return@launch
            repository.addTracksToPlaylist(playlistId, tracksUnder(folderPath))
        }
    }

    /**
     * Creates a playlist from an M3U file.
     *
     * The file is read before the playlist is created, so an import that resolves nothing leaves
     * nothing behind - an empty playlist named after a failed import is worse than no playlist.
     */
    fun importIntoNewPlaylist(
        name: String,
        openStream: () -> InputStream?,
        onResult: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            val trackIds = readTrackIds(openStream)
            if (trackIds.isEmpty()) {
                onResult(false)
                return@launch
            }
            val playlistId = repository.createPlaylist(name)
            if (playlistId == null) {
                onResult(false)
                return@launch
            }
            repository.addTracksToPlaylist(playlistId, trackIds)
            onResult(true)
        }
    }

    /** The tracks an M3U file names, as ids - see [readM3uTrackIds]. */
    private suspend fun readTrackIds(openStream: () -> InputStream?): List<Long> =
        readM3uTrackIds(openStream, repository.tracks.value.itemsOrEmpty)

    /**
     * Tracks the library holds anywhere beneath [folderPath].
     *
     * Filtered from the list already in memory rather than walking the filesystem as the reference
     * app does: everything under that folder was found by the last scan, so the answer is already
     * here and the picker does not have to wait on I/O.
     */
    private fun tracksUnder(folderPath: String): List<Long> =
        repository.tracks.value.itemsOrEmpty
            .filter { it.folderPath == folderPath || it.folderPath.startsWith("$folderPath/") }
            .map { it.id }

    /**
     * The shortcut cover of the first playlist [matches] picks - null while there is none, or it has
     * nothing to show - kept current as the playlists change.
     *
     * Read from the playlists the library loads as the app starts, and started from the ones already in
     * memory rather than from nothing, so a card whose cover is known draws it on its first frame: nothing
     * waits on a query of its own, or on this being collected.
     */
    private fun playlistCover(matches: (Playlist) -> Boolean): StateFlow<ShortcutCover?> {
        fun coverIn(content: LibraryContent<Playlist>) = content.itemsOrEmpty.firstOrNull(matches)?.shortcutCover()
        return repository.playlists
            .map(::coverIn)
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), coverIn(repository.playlists.value))
    }

    /** Every cover in the list, most shared first, kept current as the list changes. */
    private fun Flow<LibraryContent<Track>>.rankedCovers(): StateFlow<List<String>> =
        map { content -> rankedCoverArtUris(content.itemsOrEmpty.map { it.coverArtUri }) }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The cover of whichever track a list shows first, kept current as the list changes. */
    private fun Flow<LibraryContent<Track>>.topCover(): StateFlow<ShortcutCover?> =
        map { content -> content.itemsOrEmpty.firstOrNull()?.let { ShortcutCover.Track(it.coverArtUri) } }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * How a shortcut card previews this playlist, as its row shows its cover: stacked, when its cover is the
 * stack, and otherwise the one cover it was given or its sort puts first. Null while it has none to show.
 */
private fun Playlist.shortcutCover(): ShortcutCover? = when {
    coverArtUris.isEmpty() -> null
    cover == PlaylistCover.Stacked -> ShortcutCover.Playlist(coverArtUris = coverArtUris, seed = id.hashCode())
    else -> ShortcutCover.Track(coverArtUris.first())
}

/** What a shortcut card previews of its list. */
sealed interface ShortcutCover {
    /**
     * The cover of the track the list shows first. Held apart from the list being empty, so a first
     * track without artwork still previews the default cover.
     */
    data class Track(val coverArtUri: String?) : ShortcutCover

    /** A playlist's own cover: [coverArtUris] stacked as its row stacks them, [seed] keeping the pile. */
    data class Playlist(val coverArtUris: List<String>, val seed: Int) : ShortcutCover
}
