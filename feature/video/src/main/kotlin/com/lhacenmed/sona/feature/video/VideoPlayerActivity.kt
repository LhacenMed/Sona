package com.lhacenmed.sona.feature.video

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.viewModels
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
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val EXTRA_FLOATING = "floating"

/**
 * Opens the video player on whatever plays now - started just before, by the caller - full screen, or [floating]:
 * in its floating window, over the app, which goes on under it.
 */
fun Context.openVideoPlayer(floating: Boolean = false) {
    val intent = Intent(this, VideoPlayerActivity::class.java).putExtra(EXTRA_FLOATING, floating)
    if (floating && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && hasFloatingWindows()) {
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
 * picture is all there is. It plays nothing itself - the one player the music plays on shows its picture here,
 * so the queue, the notification and the sound go on exactly as they do for music.
 *
 * Left for another app, it pauses - nobody is watching the picture - unless it plays for its sound alone, which
 * is what that is for, or floats. Closed - its floating window too - it leaves what plays at its own speed and
 * volume: a video played as audio plays on in the mini player, and one watched gives way to the queue it replaced.
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

    /** The floating window as it would be now - see [FloatingWindow]. */
    private var floatingWindow: FloatingWindow? = null

    /** Opened to float where it could not open floating already: it goes into its window once on screen. */
    private var floatsOnResume = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Standing as kept from the first frame, rather than turning once the screen is composed.
        requestedOrientation = viewModel.session.value.orientation.activityOrientation
        // Opened from another app, the process may only just have started: the theme is worked out first.
        holdFirstFrameUntil(appTheme::awaitReady)
        isFloating = isInPictureInPictureMode
        floatsOnResume = savedInstanceState == null && intent.getBooleanExtra(EXTRA_FLOATING, false) && !isFloating
        // A Private Folder video is kept out of screenshots and the recent apps' preview.
        lifecycleScope.launch {
            viewModel.isPrivate.collect { isPrivate ->
                if (isPrivate) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        if (hasFloatingWindows()) {
            lifecycleScope.launch {
                combine(viewModel.uiState, viewModel.session, viewModel.isPrivate) { uiState, session, isPrivate ->
                    val playback = uiState.playback
                    FloatingWindow(
                        videoWidth = playback.videoWidth,
                        videoHeight = playback.videoHeight,
                        isPlaying = playback.isPlaying,
                        // A video watched, never one heard alone - and never the Private Folder's, out over the app.
                        canFloat = playback.isWatching && uiState.current?.isVideo == true && !session.isAudioOnly && !isPrivate,
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
                VideoPlayerScreen(
                    viewModel = viewModel,
                    onClose = ::finish,
                    isFloating = isFloating,
                    onFloat = if (hasFloatingWindows()) ::float else null,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (floatsOnResume) {
            floatsOnResume = false
            float()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Already open, it is asked to float for the next video looked at: it goes on floating, or starts to.
        if (intent.getBooleanExtra(EXTRA_FLOATING, false) && !isInPictureInPictureMode) float()
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
        if (!isChangingConfigurations) viewModel.onScreenLeft()
    }

    override fun onDestroy() {
        if (isFinishing) viewModel.onPlayerClosed()
        super.onDestroy()
    }

    private fun float() {
        if (hasFloatingWindows()) enterPictureInPictureMode(floatingWindowParams(floatingWindow))
    }
}
