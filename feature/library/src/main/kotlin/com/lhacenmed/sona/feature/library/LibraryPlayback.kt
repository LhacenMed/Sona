package com.lhacenmed.sona.feature.library

import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.model.Album
import com.lhacenmed.sona.core.model.Artist
import com.lhacenmed.sona.core.model.Folder
import com.lhacenmed.sona.core.model.Genre
import com.lhacenmed.sona.core.model.PlaybackParent
import com.lhacenmed.sona.core.model.Playlist
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.PlaybackController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * What each list marks as playing: Auxio's `updatePlayback` rules, in the one place every list reads.
 *
 * A track and a collection answer two different questions, so they are marked by different rules.
 *
 * A track row says "this is the track playing", which is true of the track wherever it is listed - the
 * same file in an album, a genre and three playlists is one playing track, and every list holding it
 * says so. A collection row says "this is the list playing", which is only true of the one playback
 * actually came *from*: an album lights up when the queue was built from that album, and stays dark
 * while the very same track plays from a genre instead. That is what keeps a lit collection meaningful
 * rather than lighting up every collection the track happens to belong to.
 *
 * A playlist is the one collection whose membership is not on the track, so being the parent is the
 * whole test there - the queue came from that playlist, which is what the row is saying.
 */
data class LibraryPlayback(
    /** The collection the queue was built from, or null when it stands for the whole library. */
    val parent: PlaybackParent? = null,
    val currentTrack: Track? = null,
    /** Whether playback is ongoing, which is what the indicator animates on. */
    val isPlaying: Boolean = false,
) {
    /** The playing track, in every list that holds it - see the rules above. */
    fun marks(track: Track): Boolean = currentTrack?.id == track.id

    fun marks(album: Album): Boolean =
        parent == PlaybackParent.Album(album.id) && currentTrack?.albumId == album.id

    fun marks(artist: Artist): Boolean =
        parent == PlaybackParent.Artist(artist.id) && currentTrack?.artistId == artist.id

    fun marks(genre: Genre): Boolean =
        parent == PlaybackParent.Genre(genre.id) && currentTrack?.genreId == genre.id

    fun marks(playlist: Playlist): Boolean =
        parent == PlaybackParent.Playlist(playlist.id) && currentTrack != null

    fun marks(folder: Folder): Boolean =
        parent == PlaybackParent.Folder(folder.path) && currentTrack?.folderPath == folder.path

    /** A collection with no library row of its own - Recent and Most played - as its own row. */
    fun marks(collection: PlaybackParent): Boolean = parent == collection && currentTrack != null

    /**
     * Whether tapping [track] in a list that plays from [listParent] means pause or resume rather than
     * starting a queue: it is already the playing track, and it is already playing from this very list.
     *
     * Chosen from anywhere else - the same track in another playlist, or in the library's own list -
     * it starts that list's queue instead, which is what makes the row that was tapped the one playing.
     */
    fun isReselection(track: Track, listParent: PlaybackParent?): Boolean =
        parent == listParent && currentTrack?.id == track.id
}

/**
 * The playing state every list reads, assembled once per change that a list can actually see.
 *
 * The playing position ticks several times a second and no row shows it, so only the three things a row
 * does show are taken from the playback state - anything else moving leaves every list alone. The track
 * itself is resolved from the library already in memory rather than queried, because the rules need the
 * album, artist, genre and folder it belongs to.
 */
internal fun libraryPlayback(
    playbackController: PlaybackController,
    repository: LibraryRepository,
): Flow<LibraryPlayback> = playbackController.playbackState
    .map { Triple(it.currentTrackId, it.parent, it.isPlaying) }
    .distinctUntilChanged()
    .combine(repository.tracksById) { (currentTrackId, parent, isPlaying), tracksById ->
        LibraryPlayback(
            parent = parent,
            currentTrack = currentTrackId?.let(tracksById::get),
            isPlaying = isPlaying,
        )
    }
