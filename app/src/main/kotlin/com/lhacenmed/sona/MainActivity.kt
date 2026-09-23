package com.lhacenmed.sona

import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.designsystem.SonaActivity
import com.lhacenmed.sona.core.designsystem.theme.AppCoverStyle
import com.lhacenmed.sona.core.designsystem.theme.AppThemeSeed
import com.lhacenmed.sona.core.designsystem.theme.SonaTheme
import com.lhacenmed.sona.core.navigation.IntentNavigator
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.PlayerOverlay
import com.lhacenmed.sona.feature.scanner.MediaScanner
import com.lhacenmed.sona.feature.scanner.hasScannerPermission
import com.lhacenmed.sona.feature.scanner.scannerRequiredPermission
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
    lateinit var playerOverlay: PlayerOverlay

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) mediaScanner.requestScan()
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

        if (hasScannerPermission()) {
            // Fire-and-forget on the application scope: a scan is process-wide work, and tying it
            // to this activity meant every configuration change restarted it from the beginning.
            // It also no longer blocks anything - the library on screen comes from the database,
            // and the scan only reconciles what changed since last time (usually nothing).
            mediaScanner.requestScan()
        } else {
            requestPermissionLauncher.launch(scannerRequiredPermission())
        }

        setContent {
            val themeColor by themeSeed.color.collectAsStateWithLifecycle()
            val coverStyle by appCoverStyle.style.collectAsStateWithLifecycle()

            SonaTheme(themeColor = themeColor, coverStyle = coverStyle) {
                val navigator = remember { IntentNavigator(this) }
                CompositionLocalProvider(LocalNavigator provides navigator) {
                    AppShell(playerOverlay = playerOverlay)
                }
            }
        }
    }
}
