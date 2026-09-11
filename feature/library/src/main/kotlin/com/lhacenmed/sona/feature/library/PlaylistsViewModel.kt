package com.lhacenmed.sona.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.model.Playlist
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val repository: LibraryRepository,
) : ViewModel() {

    /** Already shared from the application scope, so this only hands it on. */
    val playlists: StateFlow<LibraryContent<Playlist>> = repository.playlists

    val recentlyPlayedCount: StateFlow<Int> = repository.recentlyPlayedCount
    val mostPlayedCount: StateFlow<Int> = repository.mostPlayedCount

    val favoritesPlaylistId: Long get() = repository.favoritesPlaylistId

    fun renamePlaylist(playlistId: Long, name: String) {
        viewModelScope.launch { repository.renamePlaylist(playlistId, name) }
    }

    fun deletePlaylists(playlistIds: List<Long>) {
        viewModelScope.launch { playlistIds.forEach { repository.deletePlaylist(it) } }
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
     * Reading and matching happen off the main thread, but against the library already held in
     * memory rather than the database - the whole point of resolving by path is that it needs no
     * query per entry.
     */
    fun importPlaylist(name: String, openStream: () -> InputStream?) {
        viewModelScope.launch {
            val library = repository.tracks.value.itemsOrEmpty
            val trackIds = withContext(Dispatchers.IO) {
                openStream()?.use { stream -> readM3u(stream, library).map { it.id } }
            } ?: return@launch

            val playlistId = repository.createPlaylist(name) ?: return@launch
            repository.addTracksToPlaylist(playlistId, trackIds)
        }
    }

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
}
