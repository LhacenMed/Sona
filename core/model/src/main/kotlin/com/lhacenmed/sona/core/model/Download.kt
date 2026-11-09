package com.lhacenmed.sona.core.model

/** What a download keeps of what it fetches: the video with its sound, or the sound alone. */
enum class DownloadKind { VIDEO, AUDIO }

/** The tallest picture a video is downloaded at - the best there is while [height] is null. */
enum class VideoQuality(val height: Int?) {
    BEST(null),
    P2160(2160),
    P1440(1440),
    P1080(1080),
    P720(720),
    P480(480),
    P360(360),
}

/** What a download's sound is kept as - as it was published while [ORIGINAL], otherwise converted. */
enum class AudioFormat(val extension: String?) {
    ORIGINAL(null),
    M4A("m4a"),
    MP3("mp3"),
    OPUS("opus"),
}

/**
 * How something is downloaded - one choice of each. The settings hold the user's own, which a Quick download takes as they
 * stand and the download screen starts from.
 */
data class DownloadChoices(
    val kind: DownloadKind = DownloadKind.VIDEO,
    val videoQuality: VideoQuality = VideoQuality.P1080,
    val audioFormat: AudioFormat = AudioFormat.ORIGINAL,
    /** A video's captions in the device's language and English, saved beside it, where it has any. */
    val captions: Boolean = true,
    /** Sponsor segments, self-promotion and non-music stretches cut out, as SponsorBlock's community marks them. */
    val skipSponsors: Boolean = false,
)

/** Where a download stands. */
enum class DownloadStatus {
    QUEUED,
    RUNNING,
    DONE,
    FAILED,
    CANCELED,
    ;

    /** Whether it is over, for good or not - what a list's clearing takes away. */
    val isFinished: Boolean get() = this == DONE || this == FAILED || this == CANCELED
}

/** One download asked for, as it stands. */
data class Download(
    val id: Long,
    val url: String,
    /** What it is called - its address until that is known. */
    val title: String?,
    val choices: DownloadChoices,
    val status: DownloadStatus,
    /** Why it failed, in the downloader's own words. */
    val error: String?,
    val createdAt: Long,
)
