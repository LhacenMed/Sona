package com.lhacenmed.sona.feature.settings.screen

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import com.lhacenmed.sona.core.common.coroutines.launchOperation
import com.lhacenmed.sona.feature.scanner.MediaScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The library database's file, as Room names it, and the journal files beside it. */
private const val DatabaseFileName = "sona.db"
private val DatabaseFileSuffixes = listOf("", "-wal", "-shm")

/**
 * What the app keeps on disk - the [StorageScreen]'s sizes, and the ways to reclaim or rebuild them. Of
 * ArchiveTune's storage settings only the image cache applies: Sona downloads no music and streams nothing.
 */
@HiltViewModel
class StorageViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaScanner: MediaScanner,
) : ViewModel() {

    private val _databaseBytes = MutableStateFlow<Long?>(null)

    /** The library database's size, or null until measured. */
    val databaseBytes: StateFlow<Long?> = _databaseBytes.asStateFlow()

    private val _imageCacheBytes = MutableStateFlow<Long?>(null)

    /** The image cache's size, or null until measured. */
    val imageCacheBytes: StateFlow<Long?> = _imageCacheBytes.asStateFlow()

    val isScanning: StateFlow<Boolean> = mediaScanner.isScanning

    init {
        measure()
    }

    /** Reads every track again, whatever the last scan found, then measures the database it rebuilt. */
    fun rescan() {
        viewModelScope.launch {
            mediaScanner.rescan()
            measure()
        }
    }

    /** Empties the image cache - on disk and in memory - reporting how it went to [onFinished]. */
    fun clearImageCache(onFinished: (succeeded: Boolean) -> Unit) {
        viewModelScope.launchOperation(onFinished) {
            val imageLoader = SingletonImageLoader.get(context)
            imageLoader.memoryCache?.clear()
            withContext(Dispatchers.IO) { imageLoader.diskCache?.clear() }
            measure()
        }
    }

    private fun measure() {
        viewModelScope.launch(Dispatchers.IO) {
            val database = context.getDatabasePath(DatabaseFileName)
            _databaseBytes.value = DatabaseFileSuffixes.sumOf { File(database.path + it).length() }
            _imageCacheBytes.value = SingletonImageLoader.get(context).diskCache?.size ?: 0L
        }
    }
}
