package com.lhacenmed.sona.feature.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.common.sort.nameComparator
import com.lhacenmed.sona.core.database.dao.AlbumDao
import com.lhacenmed.sona.core.database.entity.toDomain
import com.lhacenmed.sona.core.datastore.LibrarySettings
import com.lhacenmed.sona.core.model.Album
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
class AlbumsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    albumDao: AlbumDao,
    librarySettings: LibrarySettings,
    mediaScanner: MediaScanner,
) : ViewModel() {

    // null means "not read from the database yet" - see TracksViewModel for why that is distinct
    // from an empty list.
    val albums: StateFlow<List<Album>?> = combine(
        albumDao.observeAll().map { entities -> entities.map { it.toDomain() } },
        librarySettings.intelligentSortingEnabled,
    ) { albums, intelligentSortingEnabled ->
        albums.sortedWith(compareBy(nameComparator(intelligentSortingEnabled)) { it.title })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isScanning: StateFlow<Boolean> = mediaScanner.isScanning

    val hasPermission: StateFlow<Boolean> = mediaScanner.isScanning
        .map { context.hasScannerPermission() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), context.hasScannerPermission())
}
