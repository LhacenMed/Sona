package com.lhacenmed.sona

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.designsystem.theme.SonaTheme
import com.lhacenmed.sona.core.navigation.IntentNavigator
import com.lhacenmed.sona.core.navigation.LocalNavigator
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
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var mediaScanner: MediaScanner

    @Inject
    lateinit var libraryRepository: LibraryRepository

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) mediaScanner.requestScan()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Held until the library is in memory, so the first frame the user sees is a populated
        // list rather than an empty one that fills in a moment later.
        var isLibraryPending = true
        installSplashScreen().setKeepOnScreenCondition { isLibraryPending }
        lifecycleScope.launch {
            withTimeoutOrNull(MAX_SPLASH_WAIT_MS) { libraryRepository.isReady.first { it } }
            isLibraryPending = false
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
            val themeViewModel: AppThemeViewModel = hiltViewModel()
            val themeColor by themeViewModel.themeColor.collectAsStateWithLifecycle()

            SonaTheme(themeColor = themeColor) {
                val navigator = remember { IntentNavigator(this) }
                CompositionLocalProvider(LocalNavigator provides navigator) {
                    AppShell()
                }
            }
        }
    }
}
