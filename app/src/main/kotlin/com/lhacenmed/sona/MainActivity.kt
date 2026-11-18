package com.lhacenmed.sona

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.Choreographer
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.RequiresApi
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.lhacenmed.sona.core.common.entry.AppEntry
import com.lhacenmed.sona.core.common.network.NetworkMonitor
import com.lhacenmed.sona.core.common.permission.AppPermission
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.datastore.LibraryTab
import com.lhacenmed.sona.core.datastore.OnboardingSettings
import com.lhacenmed.sona.core.designsystem.MaxFirstFrameHoldMillis
import com.lhacenmed.sona.core.designsystem.SonaActivity
import com.lhacenmed.sona.core.designsystem.component.LaunchEntrance
import com.lhacenmed.sona.core.designsystem.component.LaunchEntranceHost
import com.lhacenmed.sona.core.designsystem.component.NetworkStatusHost
import com.lhacenmed.sona.core.designsystem.theme.AppCoverStyle
import com.lhacenmed.sona.core.designsystem.theme.AppFastScrollTouchArea
import com.lhacenmed.sona.core.designsystem.theme.AppTheme
import com.lhacenmed.sona.core.designsystem.theme.SonaTheme
import com.lhacenmed.sona.core.model.ThemeMode
import com.lhacenmed.sona.core.navigation.AppPrompts
import com.lhacenmed.sona.core.navigation.IntentNavigator
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.PlayerOverlay
import com.lhacenmed.sona.feature.download.DownloadScreen
import com.lhacenmed.sona.feature.download.linkIn
import com.lhacenmed.sona.feature.library.LibraryTabRequest
import com.lhacenmed.sona.feature.library.PlayingList
import com.lhacenmed.sona.feature.library.playingListOf
import com.lhacenmed.sona.feature.onboarding.OnboardingScreen
import com.lhacenmed.sona.feature.playback.PlaybackController
import com.lhacenmed.sona.feature.scanner.MediaScanner
import com.lhacenmed.sona.feature.settings.backup.BackupScreen
import com.lhacenmed.sona.feature.settings.screen.StorageScreen
import com.lhacenmed.sona.feature.settings.updates.UpdatesScreen
import com.lhacenmed.sona.feature.tageditor.autotag.AutoTagScreen
import com.google.android.material.color.MaterialColors
import dagger.hilt.android.AndroidEntryPoint
import java.time.Duration
import java.time.Instant
import kotlin.coroutines.resume
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

@AndroidEntryPoint
class MainActivity : SonaActivity() {

    companion object {
        private const val EXTRA_LIBRARY_TAB = "com.lhacenmed.sona.extra.LIBRARY_TAB"

        /** What takes the app back to the library - closing every screen over it - and on to [tab]. */
        fun libraryTabIntent(context: Context, tab: LibraryTab): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_LIBRARY_TAB, tab.name)
    }

    @Inject
    lateinit var mediaScanner: MediaScanner

    @Inject
    lateinit var libraryRepository: LibraryRepository

    @Inject
    lateinit var appTheme: AppTheme

    @Inject
    lateinit var appCoverStyle: AppCoverStyle

    @Inject
    lateinit var appFastScrollTouchArea: AppFastScrollTouchArea

    @Inject
    lateinit var playerOverlay: PlayerOverlay

    @Inject
    lateinit var appPrompts: AppPrompts

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    @Inject
    lateinit var playbackController: PlaybackController

    @Inject
    lateinit var onboardingSettings: OnboardingSettings

    /** The library's tab a tap on the player from outside the app opened it on - see [arriveAtPlaying]. */
    private var playingTab by mutableStateOf<LibraryTab?>(null)

    /** The library's tab last asked for from within the app - see [libraryTabIntent]. */
    private var tabRequest by mutableStateOf<LibraryTabRequest?>(null)

    private val launchPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    /** Whether the device's files could be read when the activity was last in front - see [onResume]. */
    private var hadStorageAccess = false

    /** Whether the first-launch setup is on screen in place of the library - until it is done. */
    private var isOnboarding by mutableStateOf(false)

    /** The library's entrance as it first shows - from the launch screen, or from the setup's last step - or none. */
    private var entrance by mutableStateOf<LaunchEntrance?>(null)

    /** The setup's entrance - from the launch screen, where the app opened on the setup - or none. */
    private var setupEntrance by mutableStateOf<LaunchEntrance?>(null)

    /** Whether the library is being built beneath the setup, as the setup ends - see [OnboardingScreen]. */
    private var isLibraryUnderSetup by mutableStateOf(false)

    /** Whether the setup has handed its shape to the library's entrance - gone from sight, if not yet taken apart. */
    private var isSetupHandedOver by mutableStateOf(false)

    /** Whether Android's launch screen has handed its icon over - see [handOffSplash]. */
    private var isSplashHandedOff = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Only as the activity is first created: one recreated after a rotation or a process death
        // still carries the intent it was opened with, and must not go there a second time.
        val entry = if (savedInstanceState == null) AppEntry.of(intent) else null
        isOnboarding = onboardingSettings.progress.value != null
        // Before the first composition, so the player is drawn open from the first frame there is. A queue
        // not known in time is arrived at all the same when it is, over the library by then on screen.
        val opening = lifecycleScope.launch {
            if (entry == AppEntry.NOW_PLAYING) {
                withTimeoutOrNull(MaxFirstFrameHoldMillis) { arriveAtPlaying().join() }
            } else {
                if (savedInstanceState == null) arriveAtScreenOf(intent, entry)
            }
            showContent()
        }
        // The launch screen's mark flies to its place as the app is opened from its icon - the library's bar, or the
        // setup's shape. Not on a recreation, nor where a tap opens something over the library.
        val splashEntrance = LaunchEntrance.fromSplash().takeIf { savedInstanceState == null && entry == null }
        if (isOnboarding) setupEntrance = splashEntrance else entrance = splashEntrance
        if (splashEntrance != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) handOffSplash(splashEntrance)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) keepSplashScreenInThemeMode()

        // The first frame is held until the library is in memory and the theme is worked out, so the first thing the
        // user sees is a populated list in their own font and colours, rather than an empty one in the defaults that
        // fills in and restyles itself a moment later. On Android 12+ the launch screen stays up meanwhile.
        holdFirstFrameUntil(
            awaitReady = {
                libraryRepository.isReady.first { it }
                appTheme.awaitReady()
                opening.join()
            },
            onReleased = {
                when {
                    splashEntrance == null -> Unit
                    // Before Android 12 there is no launch screen: the mark starts in the middle as the first frame shows.
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> splashEntrance.flyFromCentre()
                    // A start Android showed no launch screen for never hands its icon over: once the first frame is
                    // drawn and a while has passed with nothing handed over, the mark starts without it.
                    else -> afterFirstDraw {
                        lifecycleScope.launch {
                            delay(SplashHandOffWaitMillis)
                            if (!isSplashHandedOff) splashEntrance.flyFromCentre()
                        }
                    }
                }
            },
        )

        // Every launch asks, in one dialog, for whatever the app is hardly usable without and has not
        // been granted. Android stops showing it for a permission the user keeps declining, so this
        // never nags; Settings > Permissions is where such a permission is granted after that. Not on
        // a recreation, which would stack a second dialog over the first. Access Android grants only
        // from its settings is asked for by StorageAccessPrompt instead, which says why first. The setup
        // asks for each itself, saying why, so not while it is on screen.
        if (savedInstanceState == null && !isOnboarding) {
            val missingPermissions = AppPermission.needed
                .filter { it.isAskedAtLaunch && !it.isGranted(this) }
                .mapNotNull { it.runtimePermission }
            if (missingPermissions.isNotEmpty()) launchPermissionsLauncher.launch(missingPermissions.toTypedArray())
        }

        // Only as the activity is first created: one recreated after a rotation or a process death
        // still carries the intent it was opened with, and must not play a second time.
        if (savedInstanceState == null && intent.action == QuickPlayShortcut.ACTION) {
            playbackController.quickPlay()
        }
    }

    /**
     * Scans as the activity opens with access to the device's files, and again the moment it comes back
     * with access it did not have - granted in Android's dialog, or on its settings page.
     */
    override fun onResume() {
        super.onResume()
        val hasStorageAccess = AppPermission.STORAGE.isGranted(this)
        // Fire-and-forget on the application scope: a scan is process-wide work, and tying it
        // to this activity meant every configuration change restarted it from the beginning.
        // It also no longer blocks anything - the library on screen comes from the database,
        // and the scan only reconciles what changed since last time (usually nothing). Held while the app
        // opens, so what it changes is not re-sorted and redrawn under the launch's frames.
        if (hasStorageAccess && !hadStorageAccess) {
            val opening = entrance ?: setupEntrance
            if (opening == null) mediaScanner.requestScan() else lifecycleScope.launch { opening.awaitSettled(); mediaScanner.requestScan() }
        }
        hadStorageAccess = hasStorageAccess
    }

    /**
     * Takes the mark over from Android's launch screen: once its icon has finished animating, the app draws the launch
     * screen again beneath it - its background, and the mark still, exactly where the icon's cookie is and in its colours.
     * Only once that copy has been drawn is the launch screen taken away, and the mark set flying on that same frame - so
     * taking it away changes no pixel, and the flight's first frame starts from the very place the launch screen left it.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun handOffSplash(entrance: LaunchEntrance) {
        splashScreen.setOnExitAnimationListener { splash ->
            isSplashHandedOff = true
            val icon = splash.iconView
            if (icon == null || icon.width == 0) {
                splash.remove()
                entrance.flyFromCentre()
                return@setOnExitAnimationListener
            }
            val iconAt = IntArray(2).also(icon::getLocationInWindow)
            val contentAt = IntArray(2).also(findViewById<View>(android.R.id.content)::getLocationInWindow)
            // Drawn at the size Android gives an icon without a background - not the icon view's, which it overflows.
            val side = SplashIconSizeDp * resources.displayMetrics.density * SplashMarkFraction
            val left = iconAt[0] - contentAt[0] + (icon.width - side) / 2f
            val top = iconAt[1] - contentAt[1] + (icon.height - side) / 2f
            val cookieColor = Color(MaterialColors.getColor(icon, com.google.android.material.R.attr.colorPrimaryContainer))
            val noteColor = Color(MaterialColors.getColor(icon, com.google.android.material.R.attr.colorOnPrimaryContainer))
            val backdropColor = (splash.background as? ColorDrawable)?.color?.let(::Color)
            val iconAnimationLeft = splash.iconAnimationStart?.let { start ->
                Duration.between(Instant.now(), start.plus(splash.iconAnimationDuration ?: Duration.ZERO)).toMillis()
            } ?: 0L
            lifecycleScope.launch {
                delay(iconAnimationLeft.coerceAtLeast(0L))
                entrance.holdAt(Rect(left, top, left + side, top + side), cookieColor, noteColor, backdropColor)
                // The copy drawn, then on screen a frame later. A window that never draws it - left before it could - does
                // not keep the launch screen up for good.
                withTimeoutOrNull(MaxSplashHandOffMillis) { entrance.awaitHeldOnScreen() }
                awaitNextFrame()
                splash.remove()
                entrance.fly()
            }
        }
    }

    /** Runs [action] once, after the window next draws. */
    private fun afterFirstDraw(action: () -> Unit) {
        val decor = window.decorView
        var hasDrawn = false
        decor.viewTreeObserver.addOnDrawListener(object : ViewTreeObserver.OnDrawListener {
            override fun onDraw() {
                if (hasDrawn) return
                hasDrawn = true
                // A draw listener cannot remove itself while the window draws.
                decor.post {
                    decor.viewTreeObserver.removeOnDrawListener(this)
                    action()
                }
            }
        })
    }

    /**
     * Keeps Android's launch screen light or dark as the app is, where the app is set to one whatever the system is:
     * Android reads it as the app is next opened, before any of the app runs.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun keepSplashScreenInThemeMode() = lifecycleScope.launch {
        appTheme.config.map { it.mode to it.pureBlack }.distinctUntilChanged().collect { (mode, pureBlack) ->
            splashScreen.setSplashScreenTheme(
                when (mode) {
                    ThemeMode.SYSTEM -> Resources.ID_NULL
                    ThemeMode.LIGHT -> R.style.Theme_Sona_Splash_Light
                    ThemeMode.DARK -> if (pureBlack) R.style.Theme_Sona_Splash_Black else R.style.Theme_Sona_Splash_Dark
                },
            )
        }
    }

    private fun showContent() {
        setSonaContent {
            val themeConfig by appTheme.config.collectAsStateWithLifecycle()
            val coverStyle by appCoverStyle.style.collectAsStateWithLifecycle()
            val fastScrollTouchArea by appFastScrollTouchArea.touchArea.collectAsStateWithLifecycle()
            val networkBanner by networkMonitor.banner.collectAsStateWithLifecycle()

            SonaTheme(
                config = themeConfig,
                coverStyle = coverStyle,
                fastScrollTouchArea = fastScrollTouchArea,
            ) {
                // The library is no screen of its own, so every screen is one it can go to.
                val navigator = remember { IntentNavigator(this, currentScreen = null) }
                CompositionLocalProvider(LocalNavigator provides navigator) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // The library - built beneath the setup from the moment it starts to end, so it is ready by the time
                        // the setup's shape, as the mark, flies to it, and nothing is left to build as it does.
                        if (!isOnboarding || isLibraryUnderSetup) {
                            LaunchEntranceHost(entrance) {
                                NetworkStatusHost(networkBanner) {
                                    AppShell(playerOverlay = playerOverlay, playingTab = playingTab, tabRequest = tabRequest)
                                    // Nothing asks anything while the setup is still on screen above.
                                    if (!isOnboarding) {
                                        appPrompts.Content()
                                        StorageAccessPrompt()
                                    }
                                }
                            }
                        }
                        if (isOnboarding) {
                            // Once handed over, kept - unseen, and touched through to nothing - until the library has arrived,
                            // so taking it apart costs none of the entrance's frames.
                            Box(modifier = if (isSetupHandedOver) Modifier.fillMaxSize().graphicsLayer { alpha = 0f }.blockingTouches() else Modifier) {
                                LaunchEntranceHost(setupEntrance) {
                                    OnboardingScreen(
                                        onEndInSight = {
                                            if (!isLibraryUnderSetup) {
                                                entrance = LaunchEntrance.fromSetup()
                                                isLibraryUnderSetup = true
                                            }
                                        },
                                        onFinished = { markBounds, cookieColor, noteColor ->
                                            val libraryEntrance = entrance
                                            libraryEntrance?.holdAt(markBounds, cookieColor, noteColor)
                                            libraryEntrance?.fly()
                                            isSetupHandedOver = true
                                            lifecycleScope.launch {
                                                libraryEntrance?.awaitSettled()
                                                isOnboarding = false
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** A notification, tapped while the app is already open - or a tab of the library asked for from within it. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_LIBRARY_TAB)?.let { tab -> tabRequest = LibraryTabRequest(LibraryTab.valueOf(tab)) }
        arriveAtScreenOf(intent, AppEntry.of(intent))
    }

    /**
     * Opens the list the queue plays from - its own screen, or the library's tab - at its playing row, the full
     * player over it. Read once the queue and the tracks are known, so a process the tap started opens the list the
     * restored queue plays from, and the player can draw its track at once.
     */
    private fun arriveAtPlaying() = lifecycleScope.launch {
        libraryRepository.loadedTracksById.first { it != null }
        val playback = playbackController.playbackState.first { it.isReady }
        if (!playback.opensNowPlaying) return@launch
        when (val list = playingListOf(playback.parent)) {
            is PlayingList.OnScreen -> IntentNavigator(this@MainActivity, currentScreen = null).arriveAt(list.screen, atPlaying = true)
            is PlayingList.OnTab -> playingTab = list.tab
        }
    }

    /** Opens the screen [entry] names, where it names one - the download screen on the link shared, where one was. */
    private fun arriveAtScreenOf(intent: Intent, entry: AppEntry?) {
        val sharedLink = linkIn(intent.getStringExtra(Intent.EXTRA_TEXT))
        val screen = when (entry) {
            AppEntry.UPDATES -> UpdatesScreen
            AppEntry.BACKUP -> BackupScreen
            AppEntry.STORAGE -> StorageScreen
            AppEntry.AUTO_TAG -> AutoTagScreen()
            AppEntry.DOWNLOADS -> DownloadScreen(sharedLink, AppEntry.itemOf(intent))
            AppEntry.NOW_PLAYING, AppEntry.AS_LEFT, null -> return
        }
        IntentNavigator(this, currentScreen = null).arriveAt(screen)
    }
}

/** How big Android draws a launch screen's icon that has no background of its own, in dp: its splash screen guidelines' 288. */
private const val SplashIconSizeDp = 288f

/** The mark's share of the launch screen's icon: 202 of its 432 units - see `ic_splash_anim`. */
private const val SplashMarkFraction = 202f / 432f

/**
 * How long the launch screen waits at most for the app to draw its copy of it beneath. Long enough for a busy first frame:
 * taken away before the copy is drawn, a frame would show no mark at all. Only a window that never draws waits it out.
 */
private const val MaxSplashHandOffMillis = 2_000L

/** How long after the first frame is drawn the launch screen has to hand its icon over before the mark starts without it. */
private const val SplashHandOffWaitMillis = 500L

/** Waits for the display's next frame. */
private suspend fun awaitNextFrame() = suspendCancellableCoroutine { continuation ->
    Choreographer.getInstance().postFrameCallback { continuation.resume(Unit) }
}

/** Takes every touch, so nothing beneath - or within - is reached. */
private fun Modifier.blockingTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
    }
}
