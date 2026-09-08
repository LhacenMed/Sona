package com.lhacenmed.sona.core.navigation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import com.lhacenmed.sona.core.designsystem.theme.SonaTheme

/**
 * The single reusable host for every "pushed" screen in the app. Adding a new screen never
 * touches this class or the manifest - it only needs a [Screen] implementation and a call to
 * `LocalNavigator.current.go(...)`.
 */
class HostActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SCREEN = "extra_screen"
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val screen = IntentCompat.getSerializableExtra(intent, EXTRA_SCREEN, Screen::class.java)
        if (screen == null) {
            finish()
            return
        }

        setContent {
            SonaTheme {
                val navigator = remember { IntentNavigator(this) }
                Scaffold(
                    topBar = {
                        screen.title(LocalContext.current)?.let { title ->
                            TopAppBar(
                                title = { Text(title) },
                                navigationIcon = {
                                    IconButton(onClick = navigator::back) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                    }
                                },
                            )
                        }
                    },
                ) { innerPadding ->
                    CompositionLocalProvider(LocalNavigator provides navigator) {
                        Box(modifier = Modifier.padding(innerPadding)) {
                            screen.Content()
                        }
                    }
                }
            }
        }
    }
}
