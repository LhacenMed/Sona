package com.lhacenmed.sona.open

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.lhacenmed.sona.core.designsystem.SonaActivity
import com.lhacenmed.sona.core.designsystem.theme.AppCoverStyle
import com.lhacenmed.sona.core.designsystem.theme.AppFastScrollTouchArea
import com.lhacenmed.sona.core.designsystem.theme.AppTheme
import com.lhacenmed.sona.core.designsystem.theme.SonaTheme
import com.lhacenmed.sona.feature.playback.PlaybackController
import com.lhacenmed.sona.feature.playback.VideoPlayback
import com.lhacenmed.sona.feature.video.openVideoPlayer
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Where another app's "Open with", or a share, lands with a track or a video: played over that app without opening Sona -
 * a track in [TrackPopup], on a player of its own; a video in the video player's floating window. Neither joins the
 * library, nor leaves anything in it.
 *
 * A task of its own, kept out of Recents, so it opens over the app it was opened from whatever of Sona is open, and goes
 * with it. A track's popup closes - and its track stops - as it leaves the screen; a file opened while it shows takes its
 * place.
 */
@AndroidEntryPoint
class OpenMediaActivity : SonaActivity() {

    @Inject
    lateinit var appTheme: AppTheme

    @Inject
    lateinit var appCoverStyle: AppCoverStyle

    @Inject
    lateinit var appFastScrollTouchArea: AppFastScrollTouchArea

    @Inject
    lateinit var playbackController: PlaybackController

    @Inject
    lateinit var videoPlayback: VideoPlayback

    private val popup: TrackPopupViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!open(intent)) return
        holdFirstFrameUntil(appTheme::awaitReady)
        setSonaContent {
            val themeConfig by appTheme.config.collectAsStateWithLifecycle()
            val coverStyle by appCoverStyle.style.collectAsStateWithLifecycle()
            val fastScrollTouchArea by appFastScrollTouchArea.touchArea.collectAsStateWithLifecycle()
            SonaTheme(config = themeConfig, coverStyle = coverStyle, fastScrollTouchArea = fastScrollTouchArea) {
                TrackPopup(viewModel = popup, onClose = ::finish)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        open(intent)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) finish()
    }

    /** Plays what [intent] opens: true where that is a track, for the popup to show - otherwise this closes. */
    private fun open(intent: Intent): Boolean {
        val media = openedMediaOf(intent)
        when {
            media == null -> finish()
            media.isVideo -> watch(media)
            else -> {
                popup.player.play(media.uri, media.title)
                return true
            }
        }
        return false
    }

    /** Has the video player float [media] over the app it came from, then steps out of its way. */
    private fun watch(media: OpenedMedia) {
        playbackController.watchOutsideVideo(media.uri, media.title)
        lifecycleScope.launch {
            openVideoPlayer(videoPlayback.openings.first(), sharedUri = media.uri)
            finish()
        }
    }
}
