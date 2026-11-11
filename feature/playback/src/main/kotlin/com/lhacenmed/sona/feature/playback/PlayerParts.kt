package com.lhacenmed.sona.feature.playback

import android.content.Context
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.video.VideoRendererEventListener
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegAudioRenderer
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegLibrary
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegVideoRenderer

/**
 * Every format the device decodes itself, and FFmpeg behind it - dav1d for AV1 - for the rest: the audio it cannot decode, a
 * film's AC-3, DTS or TrueHD track, and every picture it would otherwise decode in its own software, many times slower, which
 * leaves a heavy video's frames behind its sound. A decoder that fails to start hands over to the next that can play it.
 */
@OptIn(UnstableApi::class)
internal fun sonaRenderersFactory(context: Context): DefaultRenderersFactory =
    SonaRenderersFactory(context)
        .setMediaCodecSelector(HardwarePictureCodecSelector)
        .setEnableDecoderFallback(true)

/** The device's decoders - but for a picture FFmpeg decodes, only those that decode it in hardware. */
@OptIn(UnstableApi::class)
private val HardwarePictureCodecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
    val decoders = MediaCodecUtil.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
    if (MimeTypes.isVideo(mimeType) && FfmpegLibrary.supportsFormat(mimeType)) decoders.filterNot { it.softwareOnly } else decoders
}

/** Media3's renderers with FFmpeg's after them - leaving the subtitles to Media3's own, which the subtitles are read from. */
@OptIn(UnstableApi::class)
private class SonaRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>,
    ) {
        super.buildVideoRenderers(
            context,
            extensionRendererMode,
            mediaCodecSelector,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            allowedVideoJoiningTimeMs,
            out,
        )
        out += FfmpegVideoRenderer(allowedVideoJoiningTimeMs, eventHandler, eventListener, MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY)
    }

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>,
    ) {
        super.buildAudioRenderers(context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback, audioSink, eventHandler, eventListener, out)
        out += FfmpegAudioRenderer(eventHandler, eventListener, audioSink)
    }
}

/** How a video's file is read - through [trackFiles], knowing which frames nothing depends on, so a seek decodes only those it needs. */
@OptIn(UnstableApi::class)
internal fun videoMediaSourceFactory(context: Context, trackFiles: TrackFiles): DefaultMediaSourceFactory =
    DefaultMediaSourceFactory(trackFiles.dataSourceFactory(DefaultDataSource.Factory(context)))
        .experimentalSetCodecsToParseWithinGopSampleDependencies(C.VIDEO_CODEC_FLAG_H264 or C.VIDEO_CODEC_FLAG_H265)

/** The queue in the order it plays - shuffle order while shuffling - the way ArchiveTune's `getQueueWindows` walks it. */
internal fun Player.queueEntries(): List<QueueEntry> {
    val timeline = currentTimeline
    if (timeline.isEmpty) return emptyList()
    val window = Timeline.Window()
    val occurrences = HashMap<Long, Int>()
    val queue = ArrayList<QueueEntry>(timeline.windowCount)
    var mediaItemIndex = timeline.getFirstWindowIndex(shuffleModeEnabled)
    while (mediaItemIndex != C.INDEX_UNSET) {
        timeline.getWindow(mediaItemIndex, window).mediaItem.idInSpace?.let { trackId ->
            val occurrence = occurrences.merge(trackId, 1, Int::plus)
            queue += QueueEntry(key = "$trackId:$occurrence", mediaItemIndex = mediaItemIndex, trackId = trackId)
        }
        mediaItemIndex = timeline.getNextWindowIndex(mediaItemIndex, Player.REPEAT_MODE_OFF, shuffleModeEnabled)
    }
    return queue
}

/** The files this player holds a track of: the one playing, and the next, which it opens ahead of time - the only ones it reads. */
internal fun Player.heldFileUris(): Set<String> {
    // The current item by itself, not by its index: an empty queue still reports index 0, with nothing there.
    val next = nextMediaItemIndex.takeIf { it != C.INDEX_UNSET }?.let(::getMediaItemAt)
    return listOfNotNull(currentMediaItem, next).mapNotNullTo(HashSet()) { it.localConfiguration?.uri?.toString() }
}
