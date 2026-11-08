package com.lhacenmed.sona.feature.video

import android.app.ActivityOptions
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.lhacenmed.sona.core.designsystem.SonaActivity
import com.lhacenmed.sona.core.designsystem.theme.AppCoverStyle
import com.lhacenmed.sona.core.designsystem.theme.AppFastScrollTouchArea
import com.lhacenmed.sona.core.designsystem.theme.AppTheme
import com.lhacenmed.sona.core.designsystem.theme.SonaTheme
import com.lhacenmed.sona.core.model.ThemeMode
import com.lhacenmed.sona.core.navigation.IntentNavigator
import com.lhacenmed.sona.feature.equalizer.EqualizerScreen
import com.lhacenmed.sona.feature.playback.VideoScreenRequest
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val EXTRA_FLOATING = "floating"

/** Which opening of a video a screen is for - see [com.lhacenmed.sona.feature.playback.VideoPlaybackState.opening]. */
internal const val EXTRA_OPENING = "opening"

/**
 * Opens the video player as [request] says - full screen, or in its floating window, over the app, which goes on under it.
 *
 * [sharedUri] is a file another app let Sona read only for as long as what it opened stays open: handed on to the player's
 * own window, so the video can still be read once that has closed.
 */
fun Context.openVideoPlayer(request: VideoScreenRequest, sharedUri: Uri? = null) {
    val intent = Intent(this, VideoPlayerActivity::class.java)
        .putExtra(EXTRA_FLOATING, request.isFloating)
        .putExtra(EXTRA_OPENING, request.opening)
    sharedUri?.let { uri ->
        intent.clipData = ClipData.newRawUri(null, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    if (request.isFloating && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && hasFloatingWindows()) {
        // Straight into the window, never a moment full screen first.
        startActivity(intent, ActivityOptions.makeLaunchIntoPip(floatingWindowParams(null)).toBundle())
    } else {
        startActivity(intent)
    }
}

/**
 * The video player's own window: full screen, turned as the player says, and drawn dark whatever the app's
 * theme - in the app's own colours and font. Or floating: the picture alone, in a window over everything, play
 * and pause on it - asked for from the player, gone into as the player is left while a video plays, or opened in
 * straight away for a video looked at without leaving what is on screen.
 *
 * Its own activity rather than one of the app's pushed screens, which carry the music player over them: here the
 * picture is all there is. Left for another app, it pauses - nobody is watching the picture - unless it floats.
 *
 * Closing it closes the video there and then, in [finish], for the next one to start at once, while this one goes on showing
 * what it last showed on its way out. Whatever else ends the video - the Private Folder locked, music started, another video
 * started - closes this too.
 */
@AndroidEntryPoint
class VideoPlayerActivity : SonaActivity() {

    @Inject
    lateinit var appTheme: AppTheme

    @Inject
    lateinit var appCoverStyle: AppCoverStyle

    @Inject
    lateinit var appFastScrollTouchArea: AppFastScrollTouchArea

    private val viewModel: VideoPlayerViewModel by viewModels()

    private var isFloating by mutableStateOf(false)

    private val navigator = IntentNavigator(this, currentScreen = null)

    /** Whether the equalizer is open over the player: left for it, the video plays on, for its sound to be heard as it is shaped. */
    private var isEqualizerOpen = false

    /** The floating window as it would be now - see [FloatingWindow]. */
    private var floatingWindow: FloatingWindow? = null

    /** Opened to float where it could not open floating already: it goes into its window once on screen. */
    private var floatsOnResume = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Its video over by now - another tapped since, or the process gone and this brought back - there is nothing to show.
        if (!viewModel.isShowing.value) {
            finish()
            return
        }
        // Standing as it should from the first frame, rather than turning once the screen is composed.
        requestedOrientation = viewModel.session.value.orientation.activityOrientation
        // Opened from another app, the process may only just have started: the theme is worked out first.
        holdFirstFrameUntil(appTheme::awaitReady)
        isFloating = isInPictureInPictureMode
        floatsOnResume = savedInstanceState == null && intent.getBooleanExtra(EXTRA_FLOATING, false) && !isFloating
        // A Private Folder video is kept out of screenshots and the recent apps' preview.
        lifecycleScope.launch {
            viewModel.playback.map { it.isPrivate }.distinctUntilChanged().collect { showsPrivateContent = it }
        }
        if (hasFloatingWindows()) {
            lifecycleScope.launch {
                viewModel.playback.map { playback ->
                    FloatingWindow(
                        videoWidth = playback.videoWidth,
                        videoHeight = playback.videoHeight,
                        isPlaying = playback.isPlaying,
                        // Never the Private Folder's, out over the app.
                        canFloat = playback.current != null && !playback.isPrivate,
                    )
                }.distinctUntilChanged().collect { window ->
                    floatingWindow = window
                    setPictureInPictureParams(floatingWindowParams(window))
                }
            }
        }
        setSonaContent {
            val themeConfig by appTheme.config.collectAsStateWithLifecycle()
            val coverStyle by appCoverStyle.style.collectAsStateWithLifecycle()
            val fastScrollTouchArea by appFastScrollTouchArea.touchArea.collectAsStateWithLifecycle()
            SonaTheme(
                // Dark around the picture, but in the user's own colours - black surfaces only if they chose them.
                config = themeConfig.copy(mode = ThemeMode.DARK),
                coverStyle = coverStyle,
                fastScrollTouchArea = fastScrollTouchArea,
            ) {
                // No Surface stands behind the player, and a button's ripple takes its colour from here: black otherwise.
                CompositionLocalProvider(LocalContentColor provides VideoPlayerTokens.ContentColor) {
                    VideoPlayerScreen(
                        viewModel = viewModel,
                        onClose = ::finish,
                        isFloating = isFloating,
                        onFloat = if (hasFloatingWindows()) ::float else null,
                        onOpenEqualizer = ::openEqualizer,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isEqualizerOpen = false
        if (floatsOnResume) {
            floatsOnResume = false
            float()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // From Android 12, the window goes by itself - see FloatingWindow.canFloat.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && floatingWindow?.canFloat == true) float()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isFloating = isInPictureInPictureMode
        // Closed from its window - not opened back out to full screen, which comes back started: the player is done.
        if (!isInPictureInPictureMode && lifecycle.currentState == Lifecycle.State.CREATED) finish()
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing && !isChangingConfigurations && !isEqualizerOpen) viewModel.onScreenLeft()
    }

    override fun finish() {
        viewModel.onPlayerClosed()
        super.finish()
    }

    /** Whichever way it was closed - taken away with its task, it never passes through [finish]. */
    override fun onDestroy() {
        if (isFinishing) viewModel.onPlayerClosed()
        super.onDestroy()
    }

    private fun openEqualizer() {
        isEqualizerOpen = true
        navigator.go(EqualizerScreen)
    }

    private fun float() {
        if (hasFloatingWindows()) enterPictureInPictureMode(floatingWindowParams(floatingWindow))
    }
}
