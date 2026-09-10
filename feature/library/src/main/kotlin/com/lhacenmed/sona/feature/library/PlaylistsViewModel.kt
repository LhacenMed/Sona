package com.lhacenmed.sona.feature.library

import androidx.lifecycle.ViewModel
import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.model.Playlist
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    repository: LibraryRepository,
) : ViewModel() {

    /** Already shared from the application scope, so this only hands it on. */
    val playlists: StateFlow<LibraryContent<Playlist>> = repository.playlists
}
