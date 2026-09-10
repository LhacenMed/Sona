package com.lhacenmed.sona.core.navigation

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.staticCompositionLocalOf

/** Navigation surface exposed to every screen composable, regardless of which Activity hosts it. */
interface AppNavigator {
    fun go(screen: Screen)
    fun back()
}

val LocalNavigator = staticCompositionLocalOf<AppNavigator> {
    error("No AppNavigator provided")
}

/** Pushes [Screen]s as new [HostActivity] instances, relying on the platform's Activity back stack. */
class IntentNavigator(private val activity: ComponentActivity) : AppNavigator {
    override fun go(screen: Screen) {
        activity.startActivity(
            Intent(activity, HostActivity::class.java).putExtra(HostActivity.EXTRA_SCREEN, screen),
        )
    }

    override fun back() {
        activity.onBackPressedDispatcher.onBackPressed()
    }
}
