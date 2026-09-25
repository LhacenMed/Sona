package com.lhacenmed.sona

import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lhacenmed.sona.core.common.permission.AppPermission
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.datastore.UpdateSettings
import com.lhacenmed.sona.core.designsystem.SonaActivity
import com.lhacenmed.sona.core.designsystem.theme.AppCoverStyle
import com.lhacenmed.sona.core.designsystem.theme.AppFastScrollTouchArea
import com.lhacenmed.sona.core.designsystem.theme.AppThemeSeed
import com.lhacenmed.sona.core.designsystem.theme.SonaTheme
import com.lhacenmed.sona.core.navigation.IntentNavigator
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.PlayerOverlay
import com.lhacenmed.sona.feature.playback.PlaybackController
import com.lhacenmed.sona.feature.scanner.MediaScanner
import com.lhacenmed.sona.feature.update.NetworkMonitor
import com.lhacenmed.sona.feature.update.UpdateChecker
import com.lhacenmed.sona.feature.update.UpdateRegistry
import com.lhacenmed.sona.feature.update.UpdateState
import com.lhacenmed.sona.feature.update.UpdateStore
import com.lhacenmed.sona.feature.update.ui.UpdateGate
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * How long the launch screen may be held waiting for the library.
 *
 * It is a safety valve, not a budget: with the library shared from the application scope the wait is
 * normally a few milliseconds. If something is genuinely slow (a first run with no cache, a device
 * under memory pressure), showing the app with its loading placeholders beats holding a frozen
 * launch screen indefinitely.
 */
private const val MAX_SPLASH_WAIT_MS = 1_200L

@AndroidEntryPoint
class MainActivity : SonaActivity() {

    @Inject
    lateinit var mediaScanner: MediaScanner

    @Inject
    lateinit var libraryRepository: LibraryRepository

    @Inject
    lateinit var themeSeed: AppThemeSeed

    @Inject
    lateinit var appCoverStyle: AppCoverStyle

    @Inject
    lateinit var appFastScrollTouchArea: AppFastScrollTouchArea

    @Inject
    lateinit var playerOverlay: PlayerOverlay

    @Inject
    lateinit var updateSettings: UpdateSettings

    @Inject
    lateinit var playbackController: PlaybackController

    private val launchPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (AppPermission.AUDIO_LIBRARY.isGranted(this)) mediaScanner.requestScan()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The first frame is held until the library is in memory, so the first thing the user sees
        // is a populated list rather than an empty one that fills in a moment later. On Android 12+
        // the system launch screen stays up for as long as the frame is held.
        var isLibraryPending = true
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (isLibraryPending) return false
                content.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
        lifecycleScope.launch {
            withTimeoutOrNull(MAX_SPLASH_WAIT_MS) { libraryRepository.isReady.first { it } }
            isLibraryPending = false
            // A held frame never draws, so nothing else asks for the next one.
            content.invalidate()
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

        checkForUpdate()

        // Only as the activity is first created: one recreated after a rotation or a process death
        // still carries the intent it was opened with, and must not shuffle a second time.
        if (savedInstanceState == null && intent.action == ShuffleAllShortcut.ACTION) {
            playbackController.shuffleAll()
        }

        setSonaContent {
            val themeColor by themeSeed.color.collectAsStateWithLifecycle()
            val coverStyle by appCoverStyle.style.collectAsStateWithLifecycle()
            val fastScrollTouchArea by appFastScrollTouchArea.touchArea.collectAsStateWithLifecycle()
            val autoPromptUpdates by updateSettings.autoPrompt.flow
                .collectAsStateWithLifecycle(updateSettings.autoPrompt.value)

            SonaTheme(
                themeColor = themeColor,
                coverStyle = coverStyle,
                fastScrollTouchArea = fastScrollTouchArea,
            ) {
                // The library is no screen of its own, so every screen is one it can go to.
                val navigator = remember { IntentNavigator(this, currentScreen = null) }
                CompositionLocalProvider(LocalNavigator provides navigator) {
                    AppShell(playerOverlay = playerOverlay)
                    UpdateGate(autoPrompt = autoPromptUpdates)
                }
            }
        }
    }

    /**
     * Connectivity-driven update check; populates [UpdateRegistry] (and persists via [UpdateStore])
     * so [UpdateGate] can prompt — now and on any later launch, even offline. Runs whenever the
     * device is online: at launch if already connected, and again the moment connectivity returns
     * for a user who opened the app offline. Skips re-checking while a download is mid-flight or a
     * finished APK is awaiting install. Skipped for debug builds, whose `.debug` applicationId would
     * side-load the release APK as a separate app rather than update in place.
     */
    private fun checkForUpdate() {
        if (BuildConfig.DEBUG) return
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                NetworkMonitor.online(applicationContext).collect { online ->
                    if (!online || UpdateRegistry.isActive) return@collect
                    if (UpdateRegistry.stateOf() is UpdateState.Downloaded) return@collect
                    UpdateChecker.check(applicationContext)?.let {
                        UpdateStore.save(applicationContext, it)
                        UpdateRegistry.setAvailable(it)
                    }
                }
            }
        }
    }
}
