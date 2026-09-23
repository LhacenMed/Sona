package com.lhacenmed.sona

import android.app.Application
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.datastore.SettingsLoader
import com.lhacenmed.sona.feature.update.UpdateManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
class SonaApplication : Application() {

    @Inject
    lateinit var settingsLoader: SettingsLoader

    // A Provider, so constructing the repository is not part of Hilt's own graph setup.
    @Inject
    lateinit var libraryRepository: Provider<LibraryRepository>

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // First, and blocking: every setting is in memory before the first activity, the playback
        // service or a media button is created, so each of them starts with the user's choices
        // already in place - the way it would start with defaults - instead of correcting itself
        // once they arrive. The files are small and load concurrently, once per process.
        runBlocking { settingsLoader.load() }
        // Touching the repository here starts its eager database read at the earliest moment the
        // process has a Context - typically well before the first activity is created, and always
        // before the first frame. By the time anything asks for the track list, the query has
        // either finished or is already in flight; nothing waits for it to be started on demand.
        libraryRepository.get()
        // Whatever the last session left of an update - a found version, a finished download - is
        // put back, so its prompt is there again even offline. Off the main thread: it reads disk.
        applicationScope.launch { UpdateManager.restore(this@SonaApplication) }
    }
}
