package com.lhacenmed.sona.feature.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.common.sort.nameComparator
import com.lhacenmed.sona.core.database.dao.TrackDao
import com.lhacenmed.sona.core.database.entity.toDomain
import com.lhacenmed.sona.core.datastore.LibrarySettings
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.PlaybackController
import com.lhacenmed.sona.feature.playback.PlaybackUiState
import com.lhacenmed.sona.feature.scanner.MediaScanner
import com.lhacenmed.sona.feature.scanner.hasScannerPermission
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class TracksViewModel @Inject constructor(
    @ApplicationContext context: Context,
    trackDao: TrackDao,
    librarySettings: LibrarySettings,
    mediaScanner: MediaScanner,
    private val playbackController: PlaybackController,
) : ViewModel() {

    // null means "not read from the database yet", which is deliberately distinct from an empty
    // list: it lets the screen render nothing for the few milliseconds before the first row
    // arrives, instead of flashing an "empty library" message that is not actually true yet.
    val tracks: StateFlow<List<Track>?> = combine(
        trackDao.observeAll().map { entities -> entities.map { it.toDomain() } },
        librarySettings.intelligentSortingEnabled,
    ) { tracks, intelligentSortingEnabled ->
        tracks.sortedWith(compareBy(nameComparator(intelligentSortingEnabled)) { it.title })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val playbackState: StateFlow<PlaybackUiState> = playbackController.playbackState

    val isScanning: StateFlow<Boolean> = mediaScanner.isScanning

    val hasPermission: StateFlow<Boolean> = mediaScanner.isScanning
        .map { context.hasScannerPermission() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), context.hasScannerPermission())

    fun onTrackClick(index: Int) {
        playbackController.playTracks(tracks.value.orEmpty(), index)
    }

    fun onTogglePlayPause() {
        playbackController.togglePlayPause()
    }
}
