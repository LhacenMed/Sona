package com.lhacenmed.sona.core.downloader

import com.lhacenmed.sona.core.model.AudioFormat
import com.lhacenmed.sona.core.model.DownloadChoices
import com.lhacenmed.sona.core.model.DownloadKind
import com.lhacenmed.sona.core.model.VideoFormat
import com.lhacenmed.sona.core.model.VideoQuality

/**
 * About how large this video downloads with [choices] - or as [picked], a format of its own - as its formats say: the picture
 * yt-dlp takes at the quality chosen, in the codec the format chosen prefers, with the sound it is downloaded with. Null where
 * a format says no size, or the sound is converted into another.
 */
fun MediaInfo.estimatedSizeBytes(choices: DownloadChoices, picked: MediaFormat? = null): Long? {
    val language = audioLanguageFor(choices.audioLanguage)
    val sound = formats.filter { !it.hasVideo && it.hasAudio && (language == null || it.audioTrack?.language == language) }.maxByOrNull { it.bitrateKbps ?: 0 }
    val picture = when {
        choices.kind == DownloadKind.SUBTITLES -> return null
        picked != null -> picked
        choices.kind == DownloadKind.AUDIO -> return sound?.sizeBytes?.takeIf { choices.audioFormat == AudioFormat.ORIGINAL || choices.audioFormat == AudioFormat.M4A }
        else -> pictureFor(choices) ?: return null
    }
    val pictureSize = picture.sizeBytes ?: return null
    return if (picture.hasAudio || !picture.hasVideo) pictureSize else sound?.sizeBytes?.plus(pictureSize)
}

/** The qualities this video has a picture at, the best first - each as tall as the formats say, or near it. */
val MediaInfo.videoQualities: List<VideoQuality>
    get() {
        val heights = formats.mapNotNull { it.height }
        return VideoQuality.entries.filter { quality -> quality.height?.let { height -> heights.any { it in (height * 9 / 10)..height } } ?: heights.isNotEmpty() }
    }

/** The picture yt-dlp takes for [choices]: the tallest within the quality, in the codec its format prefers where it has it. */
private fun MediaInfo.pictureFor(choices: DownloadChoices): MediaFormat? {
    val limit = choices.videoQuality.height ?: Int.MAX_VALUE
    val pictures = formats.filter { it.hasVideo && (it.height ?: 0) <= limit }
    val tallest = pictures.maxOfOrNull { it.height ?: 0 } ?: return null
    val candidates = pictures.filter { (it.height ?: 0) == tallest }
    val prefersH264 = choices.videoFormat == VideoFormat.LEGACY
    return candidates.firstOrNull { it.videoCodec?.startsWith("avc") == prefersH264 } ?: candidates.first()
}
