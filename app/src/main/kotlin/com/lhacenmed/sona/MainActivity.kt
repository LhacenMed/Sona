package com.lhacenmed.sona

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.lhacenmed.sona.core.designsystem.theme.SonaTheme
import com.lhacenmed.sona.feature.scanner.MediaScanner
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var mediaScanner: MediaScanner

    private val audioPermission =
        if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startScan()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, audioPermission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            startScan()
        } else {
            requestPermissionLauncher.launch(audioPermission)
        }

        setContent {
            SonaTheme {
                SonaApp()
            }
        }
    }

    private fun startScan() {
        lifecycleScope.launch { mediaScanner.scan() }
    }
}
