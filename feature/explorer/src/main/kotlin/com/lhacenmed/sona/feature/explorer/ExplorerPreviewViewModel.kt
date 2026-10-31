package com.lhacenmed.sona.feature.explorer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.lhacenmed.sona.core.data.contentUri
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The file being listened to in the explorer - see [ExplorerPreviewViewModel] - and whether it plays now. */
data class ExplorerPreview(val trackId: Long, val isPlaying: Boolean)

/**
 * Files looked at from the explorer, without leaving it and without touching the queue: music on a player of its
 * own - which takes the sound, so the music playing pauses, its queue untouched - and a video in the video
 * player's floating window, over the explorer, the queue it was watched over coming back as the window closes.
 */
@HiltViewModel
class ExplorerPreviewViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val playbackController: PlaybackController,
) : ViewModel() {

    private val player = ExoPlayer.Builder(context)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
        .build()

    private val _preview = MutableStateFlow<ExplorerPreview?>(null)

    /** The music file listened to, or null while none is. */
    val preview: StateFlow<ExplorerPreview?> = _preview.asStateFlow()

    init {
        player.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _preview.update { it?.copy(isPlaying = isPlaying) }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) stop()
                }
            },
        )
    }

    /** Listens to [track] - or, listened to already, pauses or resumes it; a video, it is watched floating. */
    fun play(track: Track) {
        if (track.isVideo) {
            stop()
            playbackController.playTracks(listOf(track), startIndex = 0, opensFloating = true)
            return
        }
        if (_preview.value?.trackId == track.id) {
            if (player.isPlaying) player.pause() else player.play()
            return
        }
        player.setMediaItem(MediaItem.fromUri(track.contentUri))
        player.prepare()
        player.play()
        _preview.value = ExplorerPreview(track.id, isPlaying = true)
    }

    /** Stops listening - the explorer closed, or another file looked at. */
    fun stop() {
        player.stop()
        player.clearMediaItems()
        _preview.value = null
    }

    override fun onCleared() {
        player.release()
    }
}
