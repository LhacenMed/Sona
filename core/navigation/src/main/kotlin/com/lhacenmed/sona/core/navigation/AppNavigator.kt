package com.lhacenmed.sona.core.navigation

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

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

/**
 * Pushes [Screen]s as new [HostActivity] instances, relying on the platform's Activity back stack.
 *
 * It knows the [currentScreen] its activity shows - null for the library, which is no [Screen] - so
 * going to that screen again stays where it is rather than stacking a copy of it. And once it has
 * opened a screen it opens no other until its activity is back in front: a second tap, landing before
 * the first screen has appeared, would otherwise open a second one on top of it.
 */
class IntentNavigator(
    private val activity: ComponentActivity,
    private val currentScreen: Screen?,
) : AppNavigator {
    private val onNavigateListeners = mutableListOf<() -> Unit>()

    private var isOpeningScreen = false

    init {
        activity.lifecycle.addObserver(
            LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) isOpeningScreen = false },
        )
    }

    override fun go(screen: Screen) {
        if (screen == currentScreen || isOpeningScreen) return
        isOpeningScreen = true
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
