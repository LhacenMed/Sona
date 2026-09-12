package com.lhacenmed.sona.feature.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.database.dao.QueueItemDao
import com.lhacenmed.sona.core.datastore.PlaybackSettings
import com.lhacenmed.sona.core.model.RepeatMode
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
import kotlinx.coroutines.withContext

/**
 * Foreground [MediaSessionService] that owns the [ExoPlayer] instance and its [MediaSession].
 *
 * Hosting the player here (rather than in the UI process) gives us, for free, lock screen transport
 * controls and headset button handling via `androidx.media3.session`.
 *
 * The notification itself is still rendered by media3, but through
 * [SonaMediaNotificationProvider] rather than the bare default, so it carries Sona's icon and its
 * own action buttons. Those buttons follow ArchiveTune's design: [updateNotification] rebuilds the
 * whole layout from current state, and it is called from the player's state listeners rather than
 * from the button handlers - a press only changes state, and the notification follows. That
 * indirection is what keeps a button's icon from ever disagreeing with the player.
 */
@AndroidEntryPoint
@UnstableApi
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var libraryRepository: LibraryRepository

    @Inject
    lateinit var playbackSettings: PlaybackSettings

    @Inject
    lateinit var queueItemDao: QueueItemDao

    @Inject
    lateinit var equalizer: SonaEqualizer

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

    // The player's repeat int cannot tell RepeatMode.ONE from STOP_AFTER_CURRENT apart, so the
    // stored mode is what both the player and the notification are driven from.
    @Volatile private var repeatMode: RepeatMode = RepeatMode.OFF

    @Volatile private var headsetAutoplayEnabled = false

    // Mirrored into a field because the notification is rebuilt synchronously and cannot suspend to
    // ask whether the playing track is a favourite.
    @Volatile private var favoriteTrackIds: Set<Long> = emptySet()
    private var initialHeadsetPlugEventHandled = false

    private val playerListener = object : Player.Listener {
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            updateNotification()
        }

        // The heart belongs to the track, so it has to be redrawn when the track changes.
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateNotification()
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
                .add(PlaybackSessionCommands.toggleFavoriteCommand)
                .add(PlaybackSessionCommands.toggleShuffleCommand)
                .add(PlaybackSessionCommands.toggleRepeatModeCommand)
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
            // Each branch only changes state. Nothing here touches the notification - the
            // resulting player callback does, via updateNotification().
            when (customCommand.customAction) {
                PlaybackSessionCommands.ACTION_TOGGLE_FAVORITE -> toggleFavorite()

                PlaybackSessionCommands.ACTION_TOGGLE_SHUFFLE -> toggleShuffle()

                PlaybackSessionCommands.ACTION_TOGGLE_REPEAT_MODE -> toggleRepeatMode()

                else -> return super.onCustomCommand(session, controller, customCommand, args)
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        // Ported from Fossify's MediaSessionCallback.onPlaybackResumption: lets a media button
        // (e.g. a headset's play button) start the service and resume the last queue even when
        // no MediaController - and so no PlaybackController - is around to restore it, because
        // nothing in the app process runs before this fires. Reads the same queue table
        // PlaybackController restores from on a normal app launch.
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            serviceScope.launch {
                val items = withContext(Dispatchers.IO) { queueItemDao.getAll() }
                val tracksById = withContext(Dispatchers.IO) {
                    libraryRepository.tracksByIds(items.map { it.trackId }).associateBy { it.id }
                }
                val restored = items.mapNotNull { queueItem ->
                    tracksById[queueItem.trackId]?.let { track -> queueItem to track }
                }
                if (restored.isEmpty()) {
                    future.setException(UnsupportedOperationException())
                    return@launch
                }
                val currentIndex = restored.indexOfFirst { it.first.isCurrent }.takeIf { it >= 0 } ?: 0
                val startPositionMs = restored[currentIndex].first.lastPositionMs
                val mediaItems = restored.map { it.second.toMediaItem() }
                future.set(MediaSession.MediaItemsWithStartPosition(mediaItems, currentIndex, startPositionMs))
            }
            return future
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // One-time synchronous read of the persisted settings needed before the player/session
        // are built (mirrors Fossify's PlayerInit.initializeSessionAndPlayer, which reads its
        // SharedPreferences-backed Config synchronously at the same point) - DataStore has no
        // synchronous API, so a short blocking read at startup is the closest equivalent.
        val initialShuffleEnabled: Boolean
        runBlocking {
            forwardingSettings = PlaybackForwardingPlayer.Snapshot(
                rememberPause = playbackSettings.rememberPause.first(),
                rewindBeforeSkipBack = playbackSettings.rewindBeforeSkipBack.first(),
            )
            headsetAutoplayEnabled = playbackSettings.headsetAutoplay.first()
            initialShuffleEnabled = playbackSettings.shuffleEnabled.first()
            repeatMode = playbackSettings.repeatMode.first()
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
                addListener(playerListener)
            }

        // Bound here rather than in the UI because the session id is the player's, and the
        // curve has to keep applying while no screen is open.
        equalizer.attach(exoPlayer.audioSessionId)

        forwardingPlayer = PlaybackForwardingPlayer(exoPlayer) { forwardingSettings }

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setCallback(sessionCallback)
            .setSessionActivity(buildSessionActivityPendingIntent())
            .build()

        setMediaNotificationProvider(
            SonaMediaNotificationProvider(
                context = this,
                smallIconResId = R.drawable.ic_notification,
            ),
        )

        updateNotification()
        applyRepeatMode(repeatMode)
        registerHeadsetReceiver()
        collectRuntimeSettings()
    }

    // Swipe-to-dismiss is routed here by SonaMediaNotificationProvider so the queue can be dropped
    // rather than left behind for a session the user has just dismissed, then the original media3
    // delete intent is forwarded so its own teardown still happens.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_MEDIA_NOTIFICATION_DISMISSED) {
            handleMediaNotificationDismissed(intent)
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun handleMediaNotificationDismissed(intent: Intent) {
        val originalDeleteIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_MEDIA_NOTIFICATION_DELETE_INTENT, PendingIntent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_MEDIA_NOTIFICATION_DELETE_INTENT)
        }
        runCatching { originalDeleteIntent?.send() }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.playback_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    /**
     * Rebuilds the whole notification layout from current player state.
     *
     * Ported from ArchiveTune's `updateNotification`: the same three buttons, with no slot assigned
     * to any of them - only each button's icon and label change from one call to the next.
     * `setCustomLayout` (not `setMediaButtonPreferences`) is what ArchiveTune uses, and media3 fits
     * play/pause plus as much of this list as the platform allows around it.
     *
     * The one deviation: while repeating the current track (`ONE`/`STOP_AFTER_CURRENT`), favourite
     * moves to the front of the list. media3 backfills a missing transport button - previous, or
     * next when repeating - from whichever button is first, so this is what keeps repeat and
     * shuffle from being the one pulled into that spot while there is nowhere to go next.
     */
    private fun updateNotification() {
        val repeatButton = CommandButton.Builder()
            .setDisplayName(
                getString(
                    when (repeatMode) {
                        RepeatMode.STOP_AFTER_CURRENT -> R.string.playback_action_repeat_one_stop
                        RepeatMode.ONE -> R.string.playback_action_repeat_one
                        RepeatMode.ALL -> R.string.playback_action_repeat_all
                        RepeatMode.OFF -> R.string.playback_action_repeat_off
                    },
                ),
            )
            .setIconResId(
                when (repeatMode) {
                    RepeatMode.STOP_AFTER_CURRENT -> R.drawable.repeat_one_stop
                    RepeatMode.ONE -> R.drawable.repeat_one_on
                    RepeatMode.ALL -> R.drawable.repeat_on
                    RepeatMode.OFF -> R.drawable.repeat
                },
            )
            .setSessionCommand(PlaybackSessionCommands.toggleRepeatModeCommand)
            .build()
        val shuffleButton = CommandButton.Builder()
            .setDisplayName(
                getString(
                    if (exoPlayer.shuffleModeEnabled) {
                        R.string.playback_action_shuffle_off
                    } else {
                        R.string.playback_action_shuffle_on
                    },
                ),
            )
            .setIconResId(
                if (exoPlayer.shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle,
            )
            .setSessionCommand(PlaybackSessionCommands.toggleShuffleCommand)
            .build()
        val favoriteButton = buildFavoriteCommandButton()

        val repeatsCurrentTrack = repeatMode == RepeatMode.ONE || repeatMode == RepeatMode.STOP_AFTER_CURRENT
        val customLayout = if (repeatsCurrentTrack) {
            listOf(favoriteButton, repeatButton, shuffleButton)
        } else {
            listOf(repeatButton, shuffleButton, favoriteButton)
        }
        mediaSession.setCustomLayout(customLayout)
    }

    // Deviation from ArchiveTune, which mutates the player and nothing else: Sona persists shuffle
    // and repeat, so a toggle from the notification has to reach PlaybackSettings too or the choice
    // would be forgotten on the next launch.
    private fun toggleShuffle() {
        val enabled = !exoPlayer.shuffleModeEnabled
        exoPlayer.shuffleModeEnabled = enabled
        serviceScope.launch { playbackSettings.setShuffleEnabled(enabled) }
    }

    private fun toggleRepeatMode() {
        // Only the stored mode is written; the collector above puts it on the player and redraws
        // the notification, so this path is the same one the player screen takes.
        serviceScope.launch { playbackSettings.setRepeatMode(repeatMode.next) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onDestroy() {
        unregisterReceiver(headsetReceiver)
        serviceScope.cancel()
        mediaSession.release()
        // Before the player, while the audio session the effect is attached to still exists.
        equalizer.release()
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
            playbackSettings.repeatMode.collect { mode ->
                repeatMode = mode
                applyRepeatMode(mode)
                updateNotification()
            }
        }
        serviceScope.launch {
            playbackSettings.headsetAutoplay.collect { headsetAutoplayEnabled = it }
        }
        serviceScope.launch {
            libraryRepository.favoriteTrackIds.collect {
                favoriteTrackIds = it
                updateNotification()
            }
        }
    }

    /**
     * Puts a repeat mode on the player.
     *
     * `pauseAtEndOfMediaItems` is what separates "repeat this track" from "play it once more, then
     * stop" - media3 already knows how to pause at the end of an item, so unlike the reference app
     * this needs no hand-written seek-and-pause when the track comes round.
     */
    private fun applyRepeatMode(mode: RepeatMode) {
        exoPlayer.repeatMode = mode.toPlayerRepeatMode()
        exoPlayer.pauseAtEndOfMediaItems = mode.stopsAfterCurrentTrack()
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

    private fun currentTrackId(): Long? = exoPlayer.currentMediaItem?.mediaId?.toLongOrNull()

    private fun toggleFavorite() {
        val trackId = currentTrackId() ?: return
        val isFavorite = trackId in favoriteTrackIds
        // Only the write happens here; the icon follows from the favourites flow re-emitting, the
        // same way the shuffle and repeat icons follow their player callbacks.
        serviceScope.launch { libraryRepository.setFavorite(trackId, !isFavorite) }
    }

    /**
     * The heart, always present and always in the same place.
     *
     * Ported from ArchiveTune's like button: it carries no slot, and is disabled rather than hidden
     * while nothing is loaded, matching `.setEnabled(currentSong.value != null)`.
     */
    private fun buildFavoriteCommandButton(): CommandButton {
        val trackId = currentTrackId()
        val isFavorite = trackId?.let { it in favoriteTrackIds } == true
        return CommandButton.Builder()
            .setDisplayName(
                getString(
                    if (isFavorite) {
                        R.string.playback_action_remove_favorite
                    } else {
                        R.string.playback_action_add_favorite
                    },
                ),
            )
            .setSessionCommand(PlaybackSessionCommands.toggleFavoriteCommand)
            .setIconResId(if (isFavorite) R.drawable.favorite else R.drawable.favorite_border)
            .setEnabled(trackId != null)
            .build()
    }

    // feature:playback has no dependency on :app (that dependency runs the other way), so the
    // notification's tap target is resolved generically via the launcher intent for our own
    // package rather than referencing MainActivity directly - the one deliberate deviation from
    // Fossify's PlayerInit.getSessionActivityIntent(), which can reference its app's MainActivity
    // directly since notification wiring and the activity live in the same module there.
    companion object {
        const val CHANNEL_ID = "sona_playback_channel"
        const val NOTIFICATION_ID = 888
        const val ACTION_MEDIA_NOTIFICATION_DISMISSED =
            "com.lhacenmed.sona.playback.action.MEDIA_NOTIFICATION_DISMISSED"
        const val EXTRA_MEDIA_NOTIFICATION_DELETE_INTENT =
            "com.lhacenmed.sona.playback.extra.MEDIA_NOTIFICATION_DELETE_INTENT"
    }

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
