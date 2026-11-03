package com.lhacenmed.sona.feature.debug

import androidx.lifecycle.ViewModel
import com.lhacenmed.sona.core.datastore.LibrarySettings
import com.lhacenmed.sona.core.datastore.ScanSettings
import com.lhacenmed.sona.feature.backup.BackupSettings
import com.lhacenmed.sona.feature.debug.database.DatabaseInspector
import com.lhacenmed.sona.feature.debug.preferences.PreferencesInspector
import com.lhacenmed.sona.feature.playback.PlaybackController
import com.lhacenmed.sona.feature.scanner.MediaScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** What the debug tools' screens look into: the very objects the app runs on, so what they show is what it is doing. */
@HiltViewModel
internal class DebugViewModel @Inject constructor(
    val databases: DatabaseInspector,
    val preferences: PreferencesInspector,
    val playback: PlaybackController,
    val scanner: MediaScanner,
    val scanSettings: ScanSettings,
    val librarySettings: LibrarySettings,
    val backupSettings: BackupSettings,
) : ViewModel()
