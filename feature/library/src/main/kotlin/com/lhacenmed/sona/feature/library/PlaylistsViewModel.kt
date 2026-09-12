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
     * Adds an M3U file's tracks to a playlist that already exists.
     *
     * [onResult] reports whether anything was imported, which is the only outcome the user is told
     * about: a file that cannot be opened and one that names no music this device has both leave the
     * playlist as it was.
     */
    fun importIntoPlaylist(
        playlistId: Long,
        openStream: () -> InputStream?,
        onResult: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            val trackIds = readTrackIds(openStream)
            if (trackIds.isEmpty()) {
                onResult(false)
                return@launch
            }
            repository.addTracksToPlaylist(playlistId, trackIds)
            onResult(true)
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

    /**
     * The tracks an M3U file names, as ids.
     *
     * Reading and matching happen off the main thread, but against the library already held in
     * memory rather than the database - the whole point of resolving by path is that it needs no
     * query per entry. Opening the stream is what can throw, the picked file having been moved or
     * its permission revoked between the pick and the import.
     */
    private suspend fun readTrackIds(openStream: () -> InputStream?): List<Long> {
        val library = repository.tracks.value.itemsOrEmpty
        return withContext(Dispatchers.IO) {
            runCatching {
                openStream()?.use { stream -> readM3u(stream, library).map { it.id } }
            }.getOrNull().orEmpty()
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
