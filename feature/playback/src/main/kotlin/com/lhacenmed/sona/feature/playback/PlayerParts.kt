package com.lhacenmed.sona.feature.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/**
 * Every format the device decodes itself, and FFmpeg behind it for the audio it cannot - a film's AC-3, DTS or TrueHD
 * track. A decoder that fails to start hands over to the next that can play it.
 */
@OptIn(UnstableApi::class)
internal fun sonaRenderersFactory(context: Context): DefaultRenderersFactory =
    DefaultRenderersFactory(context)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        .setEnableDecoderFallback(true)

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
