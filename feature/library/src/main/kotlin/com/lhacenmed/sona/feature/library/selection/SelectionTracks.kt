package com.lhacenmed.sona.feature.library.selection

import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * The tracks a selection stands for - Auxio's `peekSelection`: each row's tracks in the order the rows
 * were selected, a collection's in the order its own list shows them.
 */
internal suspend fun LibraryRepository.tracksOf(keys: List<SelectionKey>): List<Track> =
    keys.flatMap { key ->
        when (key) {
            is SelectionKey.Track -> listOfNotNull(tracksById.value[key.trackId])
            is SelectionKey.Album -> readyTracks(albumTracks(key.albumId))
            is SelectionKey.Artist -> readyTracks(artistTracks(key.artistId))
            is SelectionKey.Genre -> readyTracks(genreTracks(key.genreId))
            is SelectionKey.Playlist -> readyTracks(playlistTracks(key.playlistId))
            is SelectionKey.Folder -> readyTracks(folderTracks(key.folderPath))
        }
    }

/** Waits for the query to actually have rows rather than [LibraryContent.Loading]'s empty first emission. */
private suspend fun readyTracks(query: Flow<LibraryContent<Track>>): List<Track> =
    query.first { it is LibraryContent.Ready }.itemsOrEmpty
