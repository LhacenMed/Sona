package com.lhacenmed.sona.core.downloader

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build

/** The height "best" is checked at: the tallest a site serves. */
private const val BestHeight = 2160

/** The frame rate a decoder must keep up with: the fastest a site serves. */
private const val FastestFps = 60.0

/**
 * Whether this device decodes AV1 in hardware at [height] - null for the tallest - as Seal asks before preferring it: decoded in
 * software, as it is on many phones, its frames fall behind its sound.
 */
internal fun decodesAv1InHardware(height: Int?): Boolean {
    val tall = height ?: BestHeight
    val wide = tall * 16 / 9
    return MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { decoder ->
        val type = decoder.supportedTypes.firstOrNull { it.equals("video/av01", ignoreCase = true) }
        val video = type?.takeIf { !decoder.isEncoder && decoder.isHardware }?.let { decoder.getCapabilitiesForType(it).videoCapabilities }
        // Either way up: a video is as often tall as wide, and some decoders take only one.
        video != null && video.areSizeAndRateSupported(wide, tall, FastestFps) && video.areSizeAndRateSupported(tall, wide, FastestFps)
    }
}

private val MediaCodecInfo.isHardware: Boolean
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        isHardwareAccelerated
    } else {
        !name.startsWith("OMX.google.") && !name.startsWith("c2.android.")
    }
