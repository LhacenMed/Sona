package com.lhacenmed.sona.core.navigation

import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.SonaActivity
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.theme.AppCoverStyle
import com.lhacenmed.sona.core.designsystem.theme.AppFastScrollTouchArea
import com.lhacenmed.sona.core.designsystem.theme.AppThemeSeed
import com.lhacenmed.sona.core.designsystem.theme.SonaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The single reusable host for every "pushed" screen in the app. Adding a new screen never
 * touches this class or the manifest - it only needs a [Screen] implementation and a call to
 * `LocalNavigator.current.go(...)`.
 *
 * A Hilt entry point, because the screens it renders resolve their ViewModels with `hiltViewModel()`
 * - that walks up to the hosting Activity for the component, so without this every pushed screen
 * with a ViewModel would fail at runtime, however cleanly it compiled.
 */
@AndroidEntryPoint
class HostActivity : SonaActivity() {

    companion object {
        const val EXTRA_SCREEN = "extra_screen"
    }

    /**
     * The same process-wide seed the main activity uses, so a pushed screen is coloured by the
     * playing track's artwork exactly as the library behind it is.
     */
    @Inject
    lateinit var themeSeed: AppThemeSeed

    /** The same process-wide cover style, so a pushed screen draws covers exactly as the library does. */
    @Inject
    lateinit var appCoverStyle: AppCoverStyle

    /** The same process-wide fast scroll touch area, so a pushed screen's lists grab their thumb as the library's do. */
    @Inject
    lateinit var appFastScrollTouchArea: AppFastScrollTouchArea

    /** The same player the main activity lays over its content, so every pushed screen has it too. */
    @Inject
    lateinit var playerOverlay: PlayerOverlay

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val screen = IntentCompat.getSerializableExtra(intent, EXTRA_SCREEN, Screen::class.java)
        if (screen == null) {
            finish()
            return
        }

        setSonaContent {
            val themeColor by themeSeed.color.collectAsStateWithLifecycle()
            val coverStyle by appCoverStyle.style.collectAsStateWithLifecycle()
            val fastScrollTouchArea by appFastScrollTouchArea.touchArea.collectAsStateWithLifecycle()

            SonaTheme(
                themeColor = themeColor,
                coverStyle = coverStyle,
                fastScrollTouchArea = fastScrollTouchArea,
            ) {
                val navigator = remember { IntentNavigator(this, currentScreen = screen) }
                // Only screens that named a title get a bar from the host; the rest draw their own,
                // because a title alone cannot express a selection or an action.
                val hostedTitle = screen.title(LocalContext.current)
                CompositionLocalProvider(LocalNavigator provides navigator) {
                    playerOverlay.Content {
                        Scaffold(
                            topBar = {
                                if (hostedTitle != null) {
                                    SonaTopAppBar(title = hostedTitle, onNavigateBack = navigator::back)
                                }
                            },
                            // A screen drawing its own bar consumes the status bar inset there; letting the
                            // Scaffold add it as well would inset the screen twice. The bottom is never
                            // inset here: the screen's lists end clear of the navigation bar themselves,
                            // along with the mini player over it.
                            contentWindowInsets = if (hostedTitle == null) {
                                WindowInsets(0, 0, 0, 0)
                            } else {
                                ScaffoldDefaults.contentWindowInsets
                                    .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                            },
                        ) { innerPadding ->
                            Box(modifier = Modifier.padding(innerPadding)) {
                                screen.Content()
                            }
                        }
                    }
                }
            }
        }
    }
}
