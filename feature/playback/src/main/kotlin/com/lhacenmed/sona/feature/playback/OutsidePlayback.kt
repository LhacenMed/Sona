package com.lhacenmed.sona.feature.playback

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.lhacenmed.sona.core.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/*
 * What another app asks Sona to play - a file opened with it from a file manager, or shared to it - played apart from the
 * library: it joins no list, and leaves no play count, history or resume position behind.
 */

/**
 * An outside file's item, known by its address alone: no library track or Private Folder item has an id like it, so
 * nothing that keeps a track's history - see [ListenHistory], [ResumePositions] - ever finds one.
 */
internal fun outsideMediaItem(uri: Uri, title: String, isVideo: Boolean): MediaItem = MediaItem.Builder()
    .setMediaId(uri.toString())
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setMediaType(if (isVideo) MediaMetadata.MEDIA_TYPE_VIDEO else MediaMetadata.MEDIA_TYPE_MUSIC)
            .build(),
    )
    .build()

/** An outside video as the video player shows it: by its name, with nothing of the library to it. */
internal fun outsideVideo(uri: Uri, title: String) = Track(
    id = 0L,
    mediaStoreId = 0L,
    title = title,
    artist = "",
    artistId = 0L,
    album = "",
    albumId = 0L,
    genre = null,
    genreId = null,
    path = uri.toString(),
    folderPath = "",
    durationMs = 0L,
    trackNumber = null,
    discNumber = null,
    year = null,
    dateAddedSeconds = 0L,
    coverArtUri = null,
    isManuallyScanned = false,
    isVideo = true,
)

/** An outside track as its popup shows it - see [OutsideTrackPlayer]. */
data class OutsideTrackState(
    /** The file's own title, or its name until its tags are read - or where it has none. */
    val title: String,
    val artist: String? = null,
    /** The picture embedded in the file, as it is stored there. */
    val artwork: ByteArray? = null,
    /** As the user asked for it rather than as heard: a track starting stays playing while it buffers. */
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val hasFailed: Boolean = false,
)

/**
 * An outside track, played on a player of its own for as long as its popup is open, apart from the library's: the music
 * playing pauses for it, as for any other app, and nothing of it is kept. Read with the decoders the app's own players
 * have, so it plays whatever they would. [release] once the popup has closed.
 */
@OptIn(UnstableApi::class)
class OutsideTrackPlayer(context: Context) {
    private val player = ExoPlayer.Builder(context, sonaRenderersFactory(context))
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    private val _state = MutableStateFlow(OutsideTrackState(title = ""))
    val state: StateFlow<OutsideTrackState> = _state.asStateFlow()

    /** Where the track is - read as it is drawn, since it moves on its own. */
    val positionMs: Long get() = player.currentPosition

    init {
        player.addListener(
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = publish()

                override fun onPlayerError(error: PlaybackException) = publish()
            },
        )
    }

    /** Plays [uri], called [title] until its tags say otherwise, in place of whatever this was playing. */
    fun play(uri: Uri, title: String) {
        _state.value = OutsideTrackState(title = title)
        player.setMediaItem(outsideMediaItem(uri, title, isVideo = false))
        player.prepare()
        player.play()
    }

    /** Plays or pauses - and, at the track's end, plays it again from its start. */
    fun togglePlayPause() {
        when {
            player.isPlaying || (player.playWhenReady && player.playbackState != Player.STATE_ENDED) -> player.pause()
            player.playbackState == Player.STATE_ENDED -> {
                player.seekTo(0L)
                player.play()
            }
            else -> player.play()
        }
    }

    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    fun release() = player.release()

    private fun publish() {
        val metadata = player.mediaMetadata
        _state.update {
            it.copy(
                title = metadata.title?.toString()?.takeIf(String::isNotBlank) ?: it.title,
                artist = metadata.artist?.toString()?.takeIf(String::isNotBlank),
                artwork = metadata.artworkData,
                isPlaying = player.playWhenReady && player.playbackState != Player.STATE_ENDED && player.playerError == null,
                durationMs = player.duration.takeUnless { duration -> duration == C.TIME_UNSET } ?: 0L,
                hasFailed = player.playerError != null,
            )
        }
    }
}
