package com.lhacenmed.sona.feature.playback

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.lhacenmed.sona.core.database.dao.QueueItemDao
import com.lhacenmed.sona.core.datastore.PlaybackSettings
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Foreground [MediaSessionService] that owns the [ExoPlayer] instance and its [MediaSession].
 *
 * Hosting the player here (rather than in the UI process) gives us, for free, a system media
 * notification, lock screen transport controls, and headset button handling via
 * `androidx.media3.session` - none of that is hand-built (mirrors Fossify: no custom
 * `MediaNotification.Provider` is installed, media3's `DefaultMediaNotificationProvider` builds
 * the notification entirely from the session/player state).
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var queueItemDao: QueueItemDao

    @Inject
    lateinit var playbackSettings: PlaybackSettings

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var forwardingPlayer: PlaybackForwardingPlayer
    private lateinit var mediaSession: MediaSession

    // Live-updated snapshots read synchronously from Player.Listener / the forwarding player -
    // DataStore is Flow/suspend-based, so runtime changes are mirrored into these volatile fields
    // rather than read on every call.
    @Volatile private var forwardingSettings = PlaybackForwardingPlayer.Snapshot(
        rememberPause = false,
        rewindBeforeSkipBack = true,
    )

    @Volatile private var pauseOnRepeatEnabled = false

    @Volatile private var headsetAutoplayEnabled = false
    private var initialHeadsetPlugEventHandled = false

    private val playerListener = object : Player.Listener {
        override fun onRepeatModeChanged(repeatMode: Int) {
            updatePauseOnRepeat()
        }
    }

    private val headsetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                AudioManager.ACTION_HEADSET_PLUG -> {
                    when (intent.getIntExtra("state", -1)) {
                        0 -> pauseFromHeadsetPlug()
                        1 -> playFromHeadsetPlug()
                    }
                    initialHeadsetPlugEventHandled = true
                }

                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> pauseFromHeadsetPlug()
            }
        }
    }

    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(PlaybackSessionCommands.closeSessionCommand)
                .build()
            return MediaSession.ConnectionResult.accept(
                sessionCommands,
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS,
            )
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == PlaybackSessionCommands.ACTION_CLOSE) {
                stopPlaybackAndService()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    override fun onCreate() {
        super.onCreate()

        // One-time synchronous read of the persisted settings needed before the player/session
        // are built (mirrors Fossify's PlayerInit.initializeSessionAndPlayer, which reads its
        // SharedPreferences-backed Config synchronously at the same point) - DataStore has no
        // synchronous API, so a short blocking read at startup is the closest equivalent.
        val initialShuffleEnabled: Boolean
        val initialRepeatMode: Int
        runBlocking {
            forwardingSettings = PlaybackForwardingPlayer.Snapshot(
                rememberPause = playbackSettings.rememberPause.first(),
                rewindBeforeSkipBack = playbackSettings.rewindBeforeSkipBack.first(),
            )
            pauseOnRepeatEnabled = playbackSettings.pauseOnRepeat.first()
            headsetAutoplayEnabled = playbackSettings.headsetAutoplay.first()
            initialShuffleEnabled = playbackSettings.shuffleEnabled.first()
            initialRepeatMode = playbackSettings.repeatMode.first().toPlayerRepeatMode()
        }

        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .apply {
                shuffleModeEnabled = initialShuffleEnabled
                repeatMode = initialRepeatMode
                pauseAtEndOfMediaItems = repeatMode == Player.REPEAT_MODE_ONE && pauseOnRepeatEnabled
                addListener(playerListener)
            }

        forwardingPlayer = PlaybackForwardingPlayer(exoPlayer) { forwardingSettings }

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setCallback(sessionCallback)
            .setSessionActivity(buildSessionActivityPendingIntent())
            .setCustomLayout(listOf(buildCloseCommandButton()))
            .build()

        registerHeadsetReceiver()
        collectRuntimeSettings()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onDestroy() {
        unregisterReceiver(headsetReceiver)
        serviceScope.cancel()
        mediaSession.release()
        exoPlayer.release()
        super.onDestroy()
    }

    private fun collectRuntimeSettings() {
        serviceScope.launch {
            combine(
                playbackSettings.rememberPause,
                playbackSettings.rewindBeforeSkipBack,
            ) { rememberPause, rewindBeforeSkipBack ->
                PlaybackForwardingPlayer.Snapshot(rememberPause, rewindBeforeSkipBack)
            }.collect { forwardingSettings = it }
        }
        serviceScope.launch {
            playbackSettings.pauseOnRepeat.collect {
                pauseOnRepeatEnabled = it
                updatePauseOnRepeat()
            }
        }
        serviceScope.launch {
            playbackSettings.headsetAutoplay.collect { headsetAutoplayEnabled = it }
        }
    }

    private fun updatePauseOnRepeat() {
        exoPlayer.pauseAtEndOfMediaItems =
            exoPlayer.repeatMode == Player.REPEAT_MODE_ONE && pauseOnRepeatEnabled
    }

    private fun registerHeadsetReceiver() {
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        }
        ContextCompat.registerReceiver(this, headsetReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    private fun playFromHeadsetPlug() {
        // ACTION_HEADSET_PLUG fires once immediately on receiver registration with the current
        // plug state - drop that first call, otherwise a headset already plugged in when the
        // service starts would auto-resume playback unexpectedly (ported from Auxio's
        // `initialHeadsetPlugEventHandled` guard).
        if (headsetAutoplayEnabled && exoPlayer.currentMediaItem != null && initialHeadsetPlugEventHandled) {
            exoPlayer.play()
        }
    }

    private fun pauseFromHeadsetPlug() {
        // Unlike the resume-on-plug-in behavior above, pause-on-unplug/noisy is unconditional -
        // it is not gated by any setting, matching Auxio.
        if (exoPlayer.currentMediaItem != null) {
            exoPlayer.pause()
        }
    }

    private fun stopPlaybackAndService() {
        exoPlayer.pause()
        exoPlayer.clearMediaItems()
        serviceScope.launch(Dispatchers.IO) { queueItemDao.clear() }
        stopSelf()
    }

    private fun buildCloseCommandButton(): CommandButton =
        CommandButton.Builder()
            .setDisplayName(getString(R.string.playback_action_close))
            .setSessionCommand(PlaybackSessionCommands.closeSessionCommand)
            .setIconResId(R.drawable.ic_close)
            .build()

    // feature:playback has no dependency on :app (that dependency runs the other way), so the
    // notification's tap target is resolved generically via the launcher intent for our own
    // package rather than referencing MainActivity directly - the one deliberate deviation from
    // Fossify's PlayerInit.getSessionActivityIntent(), which can reference its app's MainActivity
    // directly since notification wiring and the activity live in the same module there.
    private fun buildSessionActivityPendingIntent(): PendingIntent {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName)
        return PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
