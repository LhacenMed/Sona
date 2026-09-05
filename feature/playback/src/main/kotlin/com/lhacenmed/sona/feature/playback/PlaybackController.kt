package com.lhacenmed.sona.feature.playback

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.lhacenmed.sona.core.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * UI-facing façade over the [PlaybackService]'s media session.
 *
 * Connects to the service via a [MediaController], forwards transport commands to it, and
 * republishes its state as a [StateFlow] of [PlaybackUiState] for observers (e.g. a ViewModel)
 * to collect.
 */
@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var controller: MediaController? = null
    private var positionPollJob: Job? = null

    private val _playbackState = MutableStateFlow(PlaybackUiState())
    val playbackState: StateFlow<PlaybackUiState> = _playbackState.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playbackState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) startPositionPolling() else stopPositionPolling()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _playbackState.update {
                it.copy(
                    currentTrackId = mediaItem?.mediaId?.toLongOrNull(),
                    positionMs = 0L,
                    durationMs = currentDurationMsOrElse(it.durationMs),
                )
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            _playbackState.update { it.copy(durationMs = currentDurationMsOrElse(it.durationMs)) }
        }
    }

    init {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener(
            {
                val mediaController = future.get()
                controller = mediaController
                mediaController.addListener(playerListener)
                _playbackState.update {
                    it.copy(
                        isPlaying = mediaController.isPlaying,
                        currentTrackId = mediaController.currentMediaItem?.mediaId?.toLongOrNull(),
                        positionMs = mediaController.currentPosition,
                        durationMs = currentDurationMsOrElse(0L),
                    )
                }
                if (mediaController.isPlaying) startPositionPolling()
            },
            MoreExecutors.directExecutor(),
        )
    }

    /** Builds a fresh queue from [tracks] and starts playback at [startIndex]. */
    fun playTracks(tracks: List<Track>, startIndex: Int) {
        val mediaController = controller ?: return
        val mediaItems = tracks.map(::toMediaItem)
        mediaController.setMediaItems(mediaItems, startIndex, 0L)
        mediaController.prepare()
        mediaController.play()
    }

    fun togglePlayPause() {
        val mediaController = controller ?: return
        if (mediaController.isPlaying) mediaController.pause() else mediaController.play()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun skipToNext() {
        controller?.seekToNext()
    }

    fun skipToPrevious() {
        controller?.seekToPrevious()
    }

    private fun currentDurationMsOrElse(fallback: Long): Long {
        val duration = controller?.duration ?: return fallback
        return if (duration == C.TIME_UNSET) fallback else duration
    }

    private fun startPositionPolling() {
        if (positionPollJob?.isActive == true) return
        positionPollJob = scope.launch {
            while (isActive) {
                controller?.let { mediaController ->
                    _playbackState.update { it.copy(positionMs = mediaController.currentPosition) }
                }
                delay(500)
            }
        }
    }

    private fun stopPositionPolling() {
        positionPollJob?.cancel()
        positionPollJob = null
    }

    private fun toMediaItem(track: Track): MediaItem {
        val uri: Uri = if (track.isManuallyScanned) {
            Uri.fromFile(File(track.path))
        } else {
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track.mediaStoreId)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .apply {
                track.coverArtUri?.let { setArtworkUri(Uri.parse(it)) }
            }
            .build()
        return MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }
}
