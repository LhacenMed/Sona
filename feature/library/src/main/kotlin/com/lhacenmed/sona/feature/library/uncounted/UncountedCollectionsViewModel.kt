package com.lhacenmed.sona.feature.library.uncounted

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.datastore.PlaybackSettings
import com.lhacenmed.sona.core.model.Album
import com.lhacenmed.sona.core.model.Artist
import com.lhacenmed.sona.core.model.Folder
import com.lhacenmed.sona.core.model.Genre
import com.lhacenmed.sona.core.model.PlaybackParent
import com.lhacenmed.sona.core.model.Playlist
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One collection whose plays are kept out of Most played: what it plays as, and the library's own copy of it. */
internal class UncountedCollection(val parent: PlaybackParent, val item: Any, val name: String)

/** The collections whose plays are kept out of Most played, as the library has them, and the way to count them again. */
@HiltViewModel
internal class UncountedCollectionsViewModel @Inject constructor(
    repository: LibraryRepository,
    private val playbackSettings: PlaybackSettings,
) : ViewModel() {

    /**
     * Every such collection still in the library, by name - one gone from it is not listed. It starts from what the
     * library already holds, so the screen is whole from its first frame.
     */
    val collections: StateFlow<List<UncountedCollection>> = combine(
        playbackSettings.uncountedParents.flow,
        combine(repository.albums, repository.artists, repository.genres, ::Collections),
        combine(repository.playlists, repository.folders, repository.videoFolders, ::FolderCollections),
    ) { parents, collections, folderCollections -> uncountedOf(parents, collections, folderCollections) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            uncountedOf(
                playbackSettings.uncountedParents.value,
                Collections(repository.albums.value, repository.artists.value, repository.genres.value),
                FolderCollections(repository.playlists.value, repository.folders.value, repository.videoFolders.value),
            ),
        )

    /** Has plays from [parents] count towards Most played again. */
    fun countAgain(parents: Collection<PlaybackParent>) {
        viewModelScope.launch { playbackSettings.setPlaysCounted(parents, counted = true) }
    }
}

private class Collections(
    val albums: LibraryContent<Album>,
    val artists: LibraryContent<Artist>,
    val genres: LibraryContent<Genre>,
)

private class FolderCollections(
    val playlists: LibraryContent<Playlist>,
    val folders: LibraryContent<Folder>,
    val videoFolders: LibraryContent<Folder>,
)

private fun uncountedOf(
    parents: Set<PlaybackParent>,
    collections: Collections,
    folderCollections: FolderCollections,
): List<UncountedCollection> =
    parents.mapNotNull { parent ->
        when (parent) {
            is PlaybackParent.Album ->
                collections.albums.itemsOrEmpty.firstOrNull { it.id == parent.albumId }?.let { UncountedCollection(parent, it, it.title) }
            is PlaybackParent.Artist ->
                collections.artists.itemsOrEmpty.firstOrNull { it.id == parent.artistId }?.let { UncountedCollection(parent, it, it.name) }
            is PlaybackParent.Genre ->
                collections.genres.itemsOrEmpty.firstOrNull { it.id == parent.genreId }?.let { UncountedCollection(parent, it, it.name) }
            is PlaybackParent.Playlist ->
                folderCollections.playlists.itemsOrEmpty.firstOrNull { it.id == parent.playlistId }
                    ?.let { UncountedCollection(parent, it, it.name) }
            is PlaybackParent.Folder ->
                (if (parent.isVideo) folderCollections.videoFolders else folderCollections.folders).itemsOrEmpty
                    .firstOrNull { it.path == parent.folderPath }?.let { UncountedCollection(parent, it, it.name) }
            // Never kept out of Most played: a collection's options are where that is chosen, and these have none.
            PlaybackParent.Videos, PlaybackParent.RecentlyPlayed, PlaybackParent.MostPlayed -> null
        }
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
