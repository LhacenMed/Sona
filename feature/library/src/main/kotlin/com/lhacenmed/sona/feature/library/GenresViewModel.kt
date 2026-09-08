package com.lhacenmed.sona.feature.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.common.sort.nameComparator
import com.lhacenmed.sona.core.database.dao.GenreDao
import com.lhacenmed.sona.core.database.entity.toDomain
import com.lhacenmed.sona.core.datastore.LibrarySettings
import com.lhacenmed.sona.core.model.Genre
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
class GenresViewModel @Inject constructor(
    @ApplicationContext context: Context,
    genreDao: GenreDao,
    librarySettings: LibrarySettings,
    mediaScanner: MediaScanner,
) : ViewModel() {

    // null means "not read from the database yet" - see TracksViewModel for why that is distinct
    // from an empty list.
    val genres: StateFlow<List<Genre>?> = combine(
        genreDao.observeAll().map { entities -> entities.map { it.toDomain() } },
        librarySettings.intelligentSortingEnabled,
    ) { genres, intelligentSortingEnabled ->
        genres.sortedWith(compareBy(nameComparator(intelligentSortingEnabled)) { it.name })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isScanning: StateFlow<Boolean> = mediaScanner.isScanning

    val hasPermission: StateFlow<Boolean> = mediaScanner.isScanning
        .map { context.hasScannerPermission() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), context.hasScannerPermission())
}
