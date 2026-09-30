package com.lhacenmed.sona.feature.library.quickplay

import androidx.lifecycle.ViewModel
import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.data.quickplay.QuickPlaySource
import com.lhacenmed.sona.core.data.quickplay.QuickPlaySourceRepository
import com.lhacenmed.sona.core.model.Album
import com.lhacenmed.sona.core.model.Artist
import com.lhacenmed.sona.core.model.Folder
import com.lhacenmed.sona.core.model.Genre
import com.lhacenmed.sona.core.model.PlaybackParent
import com.lhacenmed.sona.core.model.Playlist
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** What [QuickPlaySourcePickerScreen] lists - the library's own lists, already in memory - and the source it marks. */
@HiltViewModel
class QuickPlaySourcePickerViewModel @Inject constructor(
    repository: LibraryRepository,
    private val quickPlaySources: QuickPlaySourceRepository,
) : ViewModel() {

    val playlists: StateFlow<LibraryContent<Playlist>> = repository.playlists
    val artists: StateFlow<LibraryContent<Artist>> = repository.artists
    val albums: StateFlow<LibraryContent<Album>> = repository.albums
    val genres: StateFlow<LibraryContent<Genre>> = repository.genres
    val folders: StateFlow<LibraryContent<Folder>> = repository.folders

    /** The section each list's rows sit in under its library tab's sort - what the fast scroller's popup names. */
    val artistSections: StateFlow<(Artist) -> String?> = repository.artistSections
    val albumSections: StateFlow<(Album) -> String?> = repository.albumSections
    val genreSections: StateFlow<(Genre) -> String?> = repository.genreSections
    val folderSections: StateFlow<(Folder) -> String?> = repository.folderSections

    val source: StateFlow<QuickPlaySource> = quickPlaySources.source

    /** Makes [parent] what quick play plays. Nothing plays yet: this is a setting, not the button. */
    fun choose(parent: PlaybackParent) {
        quickPlaySources.choose(parent)
    }
}
