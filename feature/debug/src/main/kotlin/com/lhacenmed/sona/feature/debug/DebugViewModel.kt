package com.lhacenmed.sona.feature.debug

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.datastore.OnboardingSettings
import com.lhacenmed.sona.core.datastore.LibrarySettings
import com.lhacenmed.sona.core.datastore.ScanSettings
import com.lhacenmed.sona.feature.backup.BackupSettings
import com.lhacenmed.sona.feature.debug.database.DatabaseInspector
import com.lhacenmed.sona.feature.debug.preferences.PreferencesInspector
import com.lhacenmed.sona.feature.playback.PlaybackController
import com.lhacenmed.sona.feature.scanner.MediaScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.launch

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
    private val onboardingSettings: OnboardingSettings,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** Starts the first-launch setup over, and the app with it - the way a fresh install opens. */
    fun showSetupAgain() {
        viewModelScope.launch {
            onboardingSettings.restart()
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return@launch
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
    }
}
