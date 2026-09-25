package com.lhacenmed.sona.feature.library.options

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.datastore.LibrarySettings
import com.lhacenmed.sona.core.model.PlaybackParent
import com.lhacenmed.sona.core.model.Playlist
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.library.readM3uTrackIds
import com.lhacenmed.sona.feature.library.writeM3u
import com.lhacenmed.sona.core.common.coroutines.launchOperation
import com.lhacenmed.sona.feature.library.selection.SelectionKey
import com.lhacenmed.sona.feature.library.selection.tracksOf
import com.lhacenmed.sona.feature.playback.PlaybackController
import com.lhacenmed.sona.feature.scanner.MediaScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What an options sheet's rows do - playing, queueing, adding to a playlist, sharing, and the changes
 * that cannot be taken back: excluding folders, removing tracks from a playlist, deleting playlists and
 * deleting tracks' files from the device. Shared by every entity's sheet
 * and every selection bar, so a track, an album, an artist, a genre, a folder and a playlist all reach
 * the queue, the playlist table and the library the same way.
 *
 * Scoped to the screen rather than the sheet, so work started from a row - resolving a collection's
 * tracks to share them - finishes even though the sheet has already slid away.
 */
@HiltViewModel
class OptionsActionsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: LibraryRepository,
    private val playbackController: PlaybackController,
    private val librarySettings: LibrarySettings,
    private val mediaScanner: MediaScanner,
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

    /**
     * Keeps [folderPaths] out of the library, then waits for the rescan that takes their tracks out, so
     * [onFinished] is told only once the folders are really gone from every list.
     */
    fun excludeFolders(folderPaths: List<String>, onFinished: (succeeded: Boolean) -> Unit) {
        viewModelScope.launchOperation(onFinished) {
            folderPaths.forEach { librarySettings.addExcludedFolder(it) }
            mediaScanner.rescan()
        }
    }

    /** Drops [trackIds] out of the playlist [playlistId]. The files themselves are untouched. */
    fun removeFromPlaylist(playlistId: Long, trackIds: List<Long>, onFinished: (succeeded: Boolean) -> Unit) {
        viewModelScope.launchOperation(onFinished) { repository.removeTracksFromPlaylist(playlistId, trackIds) }
    }

    /**
     * Deletes [tracks]' files itself - with the storage permission below Android 11, or with access to
     * manage all files from 11 on, as a file manager deletes - then lets go of them as
     * [forgetDeletedTracks] does. It fails if any file is still there afterwards; one already gone counts
     * as deleted.
     *
     * From Android 11 on a file and its MediaStore row are one: deleting the file removes the row. Below
     * it they are apart, so the row is deleted as well, or the next refresh would bring the track back.
     */
    fun deleteTracks(tracks: List<Track>, onFinished: (succeeded: Boolean) -> Unit) {
        viewModelScope.launchOperation(onFinished) {
            withContext(Dispatchers.IO) {
                tracks.forEach { track ->
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && !track.isManuallyScanned) {
                        context.contentResolver.delete(track.contentUri(), null, null)
                    }
                    val file = File(track.path)
                    file.delete()
                    check(!file.exists()) { "${track.path} could not be deleted" }
                }
            }
            settleDeletedTracks(tracks)
        }
    }

    /**
     * Lets go of [tracks] once their files are deleted - by Android's own request, or by [deleteTracks]:
     * out of the queue and out of the library at once, so [onFinished] is told as they leave every list,
     * playlists included.
     */
    fun forgetDeletedTracks(tracks: List<Track>, onFinished: (succeeded: Boolean) -> Unit) {
        viewModelScope.launchOperation(onFinished) { settleDeletedTracks(tracks) }
    }

    private suspend fun settleDeletedTracks(tracks: List<Track>) {
        val trackIds = tracks.mapTo(HashSet()) { it.id }
        playbackController.removeFromQueue(trackIds)
        mediaScanner.forgetTracks(trackIds)
    }

    /** Deletes the playlists [playlistIds] name. The tracks they hold are untouched. */
    fun deletePlaylists(playlistIds: List<Long>, onFinished: (succeeded: Boolean) -> Unit) {
        viewModelScope.launchOperation(onFinished) { playlistIds.forEach { repository.deletePlaylist(it) } }
    }

    /**
     * Adds an M3U file's tracks to [playlistId]. [onResult] reports whether anything was imported - a
     * file that cannot be opened and one naming no music this device has both leave the playlist as it was.
     */
    fun importIntoPlaylist(playlistId: Long, openStream: () -> InputStream?, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val trackIds = readM3uTrackIds(openStream, repository.tracks.value.itemsOrEmpty)
            if (trackIds.isNotEmpty()) repository.addTracksToPlaylist(playlistId, trackIds)
            onResult(trackIds.isNotEmpty())
        }
    }

    /** Writes [target]'s own tracks as an M3U file - nothing, for one holding no tracks. */
    fun exportTracks(target: OptionsTarget, openStream: () -> OutputStream?) {
        viewModelScope.launch {
            val tracks = entityTracks(target)
            if (tracks.isEmpty()) return@launch
            withContext(Dispatchers.IO) { openStream()?.use { stream -> writeM3u(stream, tracks) } }
        }
    }

    /** Hands [target]'s own tracks to [onLoaded] once they are known - what sharing a selection needs. */
    fun loadTracks(target: OptionsTarget, onLoaded: (List<Track>) -> Unit) {
        viewModelScope.launch { onLoaded(entityTracks(target)) }
    }

    /**
     * Turns a selection into the tracks it stands for - see [tracksOf] - and hands them to [onResolved]
     * as the selection's sheet target. Nothing is handed over for a selection that stands for no tracks.
     */
    fun resolveSelection(keys: List<SelectionKey>, onResolved: (OptionsTarget.ForSelection) -> Unit) {
        viewModelScope.launch {
            val tracks = repository.tracksOf(keys)
            if (tracks.isNotEmpty()) onResolved(OptionsTarget.ForSelection(tracks))
        }
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
        is OptionsTarget.ForFolder -> readyTracks(repository.folderTracks(target.folder.path))
        is OptionsTarget.ForSelection -> target.tracks
    }

    /** What [target] plays as - null for a selection, which Auxio plays from no collection at all. */
    private fun parentOf(target: OptionsTarget): PlaybackParent? = when (target) {
        is OptionsTarget.ForTrack -> target.queueParent
        is OptionsTarget.ForAlbum -> PlaybackParent.Album(target.album.id)
        is OptionsTarget.ForArtist -> PlaybackParent.Artist(target.artist.id)
        is OptionsTarget.ForGenre -> PlaybackParent.Genre(target.genre.id)
        is OptionsTarget.ForPlaylist -> PlaybackParent.Playlist(target.playlist.id)
        is OptionsTarget.ForFolder -> PlaybackParent.Folder(target.folder.path)
        is OptionsTarget.ForSelection -> null
    }

    /** Waits for the query to actually have rows rather than [LibraryContent.Loading]'s empty first emission. */
    private suspend fun readyTracks(query: Flow<LibraryContent<Track>>): List<Track> =
        query.first { it is LibraryContent.Ready }.itemsOrEmpty
}
