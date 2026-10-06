package com.lhacenmed.sona.feature.video

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.SonaActivity
import com.lhacenmed.sona.core.designsystem.theme.AppCoverStyle
import com.lhacenmed.sona.core.designsystem.theme.AppFastScrollTouchArea
import com.lhacenmed.sona.core.designsystem.theme.AppTheme
import com.lhacenmed.sona.core.designsystem.theme.SonaTheme
import com.lhacenmed.sona.core.model.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Opens the video player on whatever plays now - started just before, by the caller. */
fun Context.openVideoPlayer() {
    startActivity(Intent(this, VideoPlayerActivity::class.java))
}

/**
 * The video player's own window: full screen, turned as the player says, and drawn dark whatever the app's
 * theme - in the app's own colours and font.
 *
 * Its own activity rather than one of the app's pushed screens, which carry the music player over them: here the
 * picture is all there is. It plays nothing itself - the one player the music plays on shows its picture here,
 * so the queue, the notification and the sound go on exactly as they do for music.
 *
 * Left for another app, it pauses - nobody is watching the picture - unless it plays for its sound alone, which
 * is what that is for. Closed, it leaves what plays at its own speed and volume.
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Standing as kept from the first frame, rather than turning once the screen is composed.
        requestedOrientation = viewModel.session.value.orientation.activityOrientation
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
                VideoPlayerScreen(viewModel = viewModel, onClose = ::finish)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) viewModel.onScreenLeft()
    }

    override fun onDestroy() {
        if (isFinishing) viewModel.onPlayerClosed()
        super.onDestroy()
    }
}
