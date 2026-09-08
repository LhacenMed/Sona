package com.lhacenmed.sona

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.lhacenmed.sona.core.datastore.LibrarySettings
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

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var mediaScanner: MediaScanner

    @Inject
    lateinit var librarySettings: LibrarySettings

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startScan()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (hasScannerPermission()) {
            startScan()
        } else {
            requestPermissionLauncher.launch(scannerRequiredPermission())
        }

        setContent {
            val themeViewModel: AppThemeViewModel = hiltViewModel()
            val themeColor by themeViewModel.themeColor.collectAsStateWithLifecycle()

            SonaTheme(themeColor = themeColor) {
                val navigator = remember { IntentNavigator(this) }
                CompositionLocalProvider(LocalNavigator provides navigator) {
                    SonaApp()
                }
            }
        }
    }

    private fun startScan() {
        lifecycleScope.launch {
            mediaScanner.scan(excludedFolders = librarySettings.excludedFolders.first())
        }
    }
}
