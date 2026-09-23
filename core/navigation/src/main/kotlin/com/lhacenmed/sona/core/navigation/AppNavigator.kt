package com.lhacenmed.sona.core.navigation

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/** Navigation surface exposed to every screen composable, regardless of which Activity hosts it. */
interface AppNavigator {
    fun go(screen: Screen)
    fun back()

    /**
     * Leaves this screen outright, whatever it is showing - unlike [back], which an open search or a
     * selection answers first. For a screen whose work is done.
     */
    fun close()

    /** Calls [listener] each time [go] opens another screen, until it is removed. */
    fun addOnNavigateListener(listener: () -> Unit)

    fun removeOnNavigateListener(listener: () -> Unit)
}

val LocalNavigator = staticCompositionLocalOf<AppNavigator> {
    error("No AppNavigator provided")
}

/**
 * Runs [onNavigate] whenever this screen opens another one - Auxio's `DialogAwareNavigationListener`.
 * A sheet or dialog opening over the screen is not navigating; only [AppNavigator.go] is.
 */
@Composable
fun NavigateAwayEffect(onNavigate: () -> Unit) {
    val navigator = LocalNavigator.current
    val latestOnNavigate by rememberUpdatedState(onNavigate)
    DisposableEffect(navigator) {
        val listener = { latestOnNavigate() }
        navigator.addOnNavigateListener(listener)
        onDispose { navigator.removeOnNavigateListener(listener) }
    }
}

/** Pushes [Screen]s as new [HostActivity] instances, relying on the platform's Activity back stack. */
class IntentNavigator(private val activity: ComponentActivity) : AppNavigator {
    private val onNavigateListeners = mutableListOf<() -> Unit>()

    override fun go(screen: Screen) {
        onNavigateListeners.toList().forEach { it() }
        activity.startActivity(
            Intent(activity, HostActivity::class.java).putExtra(HostActivity.EXTRA_SCREEN, screen),
        )
    }

    override fun back() {
        activity.onBackPressedDispatcher.onBackPressed()
    }

    override fun close() {
        activity.finish()
    }

    override fun addOnNavigateListener(listener: () -> Unit) {
        onNavigateListeners += listener
    }

    override fun removeOnNavigateListener(listener: () -> Unit) {
        onNavigateListeners -= listener
    }
}
