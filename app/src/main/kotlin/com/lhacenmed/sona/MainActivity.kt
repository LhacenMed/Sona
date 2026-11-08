package com.lhacenmed.sona

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.lhacenmed.sona.core.common.entry.AppEntry
import com.lhacenmed.sona.core.common.network.NetworkMonitor
import com.lhacenmed.sona.core.common.permission.AppPermission
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.datastore.LibraryTab
import com.lhacenmed.sona.core.designsystem.MaxFirstFrameHoldMillis
import com.lhacenmed.sona.core.designsystem.SonaActivity
import com.lhacenmed.sona.core.designsystem.component.NetworkStatusHost
import com.lhacenmed.sona.core.designsystem.theme.AppCoverStyle
import com.lhacenmed.sona.core.designsystem.theme.AppFastScrollTouchArea
import com.lhacenmed.sona.core.designsystem.theme.AppTheme
import com.lhacenmed.sona.core.designsystem.theme.SonaTheme
import com.lhacenmed.sona.core.navigation.AppPrompts
import com.lhacenmed.sona.core.navigation.IntentNavigator
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.PlayerOverlay
import com.lhacenmed.sona.feature.library.LibraryTabRequest
import com.lhacenmed.sona.feature.library.PlayingList
import com.lhacenmed.sona.feature.library.playingListOf
import com.lhacenmed.sona.feature.playback.PlaybackController
import com.lhacenmed.sona.feature.scanner.MediaScanner
import com.lhacenmed.sona.feature.settings.backup.BackupScreen
import com.lhacenmed.sona.feature.settings.screen.StorageScreen
import com.lhacenmed.sona.feature.settings.updates.UpdatesScreen
import com.lhacenmed.sona.feature.tageditor.autotag.AutoTagScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@AndroidEntryPoint
class MainActivity : SonaActivity() {

    companion object {
        private const val EXTRA_LIBRARY_TAB = "com.lhacenmed.sona.extra.LIBRARY_TAB"

        /** What takes the app back to the library - closing every screen over it - and on to [tab]. */
        fun libraryTabIntent(context: Context, tab: LibraryTab): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_LIBRARY_TAB, tab.name)
    }

    @Inject
    lateinit var mediaScanner: MediaScanner

    @Inject
    lateinit var libraryRepository: LibraryRepository

    @Inject
    lateinit var appTheme: AppTheme

    @Inject
    lateinit var appCoverStyle: AppCoverStyle

    @Inject
    lateinit var appFastScrollTouchArea: AppFastScrollTouchArea

    @Inject
    lateinit var playerOverlay: PlayerOverlay

    @Inject
    lateinit var appPrompts: AppPrompts

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    @Inject
    lateinit var playbackController: PlaybackController

    /** The library's tab a tap on the player from outside the app opened it on - see [arriveAtPlaying]. */
    private var playingTab by mutableStateOf<LibraryTab?>(null)

    /** The library's tab last asked for from within the app - see [libraryTabIntent]. */
    private var tabRequest by mutableStateOf<LibraryTabRequest?>(null)

    private val launchPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (AppPermission.AUDIO_LIBRARY.isGranted(this)) mediaScanner.requestScan()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Only as the activity is first created: one recreated after a rotation or a process death
        // still carries the intent it was opened with, and must not go there a second time.
        val entry = if (savedInstanceState == null) AppEntry.of(intent) else null
        // Before the first composition, so the player is drawn open from the first frame there is. A queue
        // not known in time is arrived at all the same when it is, over the library by then on screen.
        val opening = lifecycleScope.launch {
            if (entry == AppEntry.NOW_PLAYING) {
                withTimeoutOrNull(MaxFirstFrameHoldMillis) { arriveAtPlaying().join() }
            } else {
                arriveAtScreenOf(entry)
            }
            showContent()
        }

        // The first frame is held until the library is in memory and the theme is worked out, so the
        // first thing the user sees is a populated list in their own font and colours, rather than an
        // empty one in the defaults that fills in and restyles itself a moment later.
        holdFirstFrameUntil {
            libraryRepository.isReady.first { it }
            appTheme.awaitReady()
            opening.join()
        }

        if (AppPermission.AUDIO_LIBRARY.isGranted(this)) {
            // Fire-and-forget on the application scope: a scan is process-wide work, and tying it
            // to this activity meant every configuration change restarted it from the beginning.
            // It also no longer blocks anything - the library on screen comes from the database,
            // and the scan only reconciles what changed since last time (usually nothing).
            mediaScanner.requestScan()
        }

        // Every launch asks, in one dialog, for whatever the app is hardly usable without and has not
        // been granted. Android stops showing it for a permission the user keeps declining, so this
        // never nags; Settings > Permissions is where such a permission is granted after that. Not on
        // a recreation, which would stack a second dialog over the first.
        if (savedInstanceState == null) {
            val missingPermissions = AppPermission.needed
                .filter { it.isAskedAtLaunch && !it.isGranted(this) }
                .mapNotNull { it.runtimePermission }
            if (missingPermissions.isNotEmpty()) launchPermissionsLauncher.launch(missingPermissions.toTypedArray())
        }

        // Only as the activity is first created: one recreated after a rotation or a process death
        // still carries the intent it was opened with, and must not play a second time.
        if (savedInstanceState == null && intent.action == QuickPlayShortcut.ACTION) {
            playbackController.quickPlay()
        }
    }

    private fun showContent() {
        setSonaContent {
            val themeConfig by appTheme.config.collectAsStateWithLifecycle()
            val coverStyle by appCoverStyle.style.collectAsStateWithLifecycle()
            val fastScrollTouchArea by appFastScrollTouchArea.touchArea.collectAsStateWithLifecycle()
            val networkBanner by networkMonitor.banner.collectAsStateWithLifecycle()

            SonaTheme(
                config = themeConfig,
                coverStyle = coverStyle,
                fastScrollTouchArea = fastScrollTouchArea,
            ) {
                // The library is no screen of its own, so every screen is one it can go to.
                val navigator = remember { IntentNavigator(this, currentScreen = null) }
                CompositionLocalProvider(LocalNavigator provides navigator) {
                    NetworkStatusHost(networkBanner) {
                        AppShell(playerOverlay = playerOverlay, playingTab = playingTab, tabRequest = tabRequest)
                        appPrompts.Content()
                    }
                }
            }
        }
    }

    /** A notification, tapped while the app is already open - or a tab of the library asked for from within it. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_LIBRARY_TAB)?.let { tab -> tabRequest = LibraryTabRequest(LibraryTab.valueOf(tab)) }
        arriveAtScreenOf(AppEntry.of(intent))
    }

    /**
     * Opens the list the queue plays from - its own screen, or the library's tab - at its playing row, the full
     * player over it. Read once the queue and the tracks are known, so a process the tap started opens the list the
     * restored queue plays from, and the player can draw its track at once.
     */
    private fun arriveAtPlaying() = lifecycleScope.launch {
        libraryRepository.loadedTracksById.first { it != null }
        val playback = playbackController.playbackState.first { it.isReady }
        if (!playback.opensNowPlaying) return@launch
        when (val list = playingListOf(playback.parent)) {
            is PlayingList.OnScreen -> IntentNavigator(this@MainActivity, currentScreen = null).arriveAt(list.screen, atPlaying = true)
            is PlayingList.OnTab -> playingTab = list.tab
        }
    }

    /** Opens the screen [entry] names, where it names one. */
    private fun arriveAtScreenOf(entry: AppEntry?) {
        val screen = when (entry) {
            AppEntry.UPDATES -> UpdatesScreen
            AppEntry.BACKUP -> BackupScreen
            AppEntry.STORAGE -> StorageScreen
            AppEntry.AUTO_TAG -> AutoTagScreen()
            AppEntry.NOW_PLAYING, AppEntry.AS_LEFT, null -> return
        }
        IntentNavigator(this, currentScreen = null).arriveAt(screen)
    }
}
