package com.lhacenmed.sona

import android.app.Application
import com.lhacenmed.sona.core.data.LibraryRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import javax.inject.Provider

@HiltAndroidApp
class SonaApplication : Application() {

    // A Provider, so constructing the repository is not part of Hilt's own graph setup.
    @Inject
    lateinit var libraryRepository: Provider<LibraryRepository>

    override fun onCreate() {
        super.onCreate()
        // Touching the repository here starts its eager database read at the earliest moment the
        // process has a Context - typically well before the first activity is created, and always
        // before the first frame. By the time anything asks for the track list, the query has
        // either finished or is already in flight; nothing waits for it to be started on demand.
        libraryRepository.get()
    }
}
