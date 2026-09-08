package com.lhacenmed.sona.feature.library

import android.content.Context
import com.lhacenmed.sona.core.common.sort.nameComparator
import com.lhacenmed.sona.core.database.dao.ArtistDao
import com.lhacenmed.sona.core.database.entity.toDomain
import com.lhacenmed.sona.core.datastore.LibrarySettings
import com.lhacenmed.sona.core.model.Artist
import com.lhacenmed.sona.feature.scanner.MediaScanner
import com.lhacenmed.sona.feature.scanner.hasScannerPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ArtistsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    artistDao: ArtistDao,
    librarySettings: LibrarySettings,
    mediaScanner: MediaScanner,
) : ViewModel() {

    // null means "not read from the database yet" - see TracksViewModel for why that is distinct
    // from an empty list.
    val artists: StateFlow<List<Artist>?> = combine(
        artistDao.observeAll().map { entities -> entities.map { it.toDomain() } },
        librarySettings.intelligentSortingEnabled,
    ) { artists, intelligentSortingEnabled ->
        artists.sortedWith(compareBy(nameComparator(intelligentSortingEnabled)) { it.name })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isScanning: StateFlow<Boolean> = mediaScanner.isScanning

    // Permission state has no dedicated change broadcast; re-checking it whenever a scan
    // starts/stops (the moment it would actually change, since granting it is what unblocks the
    // first scan) is enough to avoid a stale "permission needed" message without any active polling.
    val hasPermission: StateFlow<Boolean> = mediaScanner.isScanning
        .map { context.hasScannerPermission() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), context.hasScannerPermission())
}
