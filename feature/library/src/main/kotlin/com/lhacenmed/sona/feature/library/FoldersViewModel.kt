package com.lhacenmed.sona.feature.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.common.sort.nameComparator
import com.lhacenmed.sona.core.database.dao.TrackDao
import com.lhacenmed.sona.core.database.entity.toDomain
import com.lhacenmed.sona.core.datastore.LibrarySettings
import com.lhacenmed.sona.core.model.Folder
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
class FoldersViewModel @Inject constructor(
    @ApplicationContext context: Context,
    trackDao: TrackDao,
    librarySettings: LibrarySettings,
    mediaScanner: MediaScanner,
) : ViewModel() {

    // Folder is a runtime aggregation over Track.folderPath, not a persisted entity - derive it
    // by grouping the already-loaded track list in-memory.
    // null means "not read from the database yet" - see TracksViewModel for why that is distinct
    // from an empty list.
    val folders: StateFlow<List<Folder>?> = combine(
        trackDao.observeAll().map { entities -> entities.map { it.toDomain() } },
        librarySettings.intelligentSortingEnabled,
    ) { tracks, intelligentSortingEnabled ->
        tracks.groupBy { it.folderPath }
            .map { (path, tracksInFolder) ->
                Folder(
                    path = path,
                    name = path.substringAfterLast('/'),
                    trackCount = tracksInFolder.size,
                )
            }
            .sortedWith(compareBy(nameComparator(intelligentSortingEnabled)) { it.name })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isScanning: StateFlow<Boolean> = mediaScanner.isScanning

    val hasPermission: StateFlow<Boolean> = mediaScanner.isScanning
        .map { context.hasScannerPermission() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), context.hasScannerPermission())
}
