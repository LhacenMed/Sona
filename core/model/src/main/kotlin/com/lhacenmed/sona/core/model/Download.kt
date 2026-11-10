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

/** A part of a video SponsorBlock's community marks, which skipping sponsors cuts out - [id] as SponsorBlock names it. */
enum class SponsorSegment(val id: String) {
    SPONSOR("sponsor"),
    SELF_PROMOTION("selfpromo"),
    INTERACTION("interaction"),
    INTRO("intro"),
    OUTRO("outro"),
    PREVIEW("preview"),
    FILLER("filler"),
    MUSIC_OFFTOPIC("music_offtopic"),
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
    /** The parts [DownloaderPreferences.sponsorSegments] names cut out, as SponsorBlock's community marks them. */
    val skipSponsors: Boolean = false,
)

/** A stretch of a video downloaded alone, from [startSeconds] to [endSeconds]. */
data class ClipRange(val startSeconds: Long, val endSeconds: Long)

/** What one download asks beyond the [DownloadChoices] - picked for it alone, never kept as the user's own. */
data class DownloadOptions(
    /** What was picked from the link's own formats, in place of what the choices would pick - as yt-dlp's format selector. */
    val formatId: String? = null,
    val clip: ClipRange? = null,
    /** One file per chapter, where the video has chapters. */
    val splitChapters: Boolean = false,
    /** A saved command's yt-dlp arguments, given after everything else - see [DownloadCommand]. */
    val commandArguments: String? = null,
)

/** How every file is named, yt-dlp's way: its title, cut to fit, and its id. */
const val DEFAULT_FILE_NAME_TEMPLATE = "%(title).150B [%(id)s].%(ext)s"

/** How downloading works, whatever is downloaded - the Downloads settings. */
data class DownloaderPreferences(
    val downloadsAtOnce: Int = 2,
    /** Downloads wait for Wi-Fi, or any network that is not metered. */
    val wifiOnly: Boolean = false,
    /** The folder of the device's Download that files are saved in. */
    val folderName: String = "Sona",
    val fileNameTemplate: String = DEFAULT_FILE_NAME_TEMPLATE,
    val sponsorSegments: Set<SponsorSegment> = setOf(
        SponsorSegment.SPONSOR,
        SponsorSegment.SELF_PROMOTION,
        SponsorSegment.INTERACTION,
        SponsorSegment.MUSIC_OFFTOPIC,
    ),
    /** A proxy every request goes through - `socks5://host:port`, `http://host:port` - none while blank. */
    val proxy: String = "",
    /** The most a download takes of the connection a second - `2M`, `500K` - no limit while blank. */
    val rateLimit: String = "",
    /** Sites are read signed in, with the cookies saved from signing in on them. */
    val usesCookies: Boolean = false,
    /** What was downloaded once is not downloaded again, by its id at its site. */
    val usesArchive: Boolean = false,
)

/** Where a download stands. */
enum class DownloadStatus {
    QUEUED,
    RUNNING,
    DONE,
    FAILED,
    CANCELED,
    ;

    /** Whether it is over, for good or not - what the history lists. */
    val isFinished: Boolean get() = this == DONE || this == FAILED || this == CANCELED
}

/** Why a download failed, where it is something the user can act on - [FAILED] for anything else. */
enum class DownloadFailure {
    NO_NETWORK,
    UNAVAILABLE,
    LOGIN_REQUIRED,
    FORMAT_GONE,
    NO_SPACE,
    NO_DOWNLOADER,
    FAILED,
}

/** One download asked for, as it stands - and, once over, as the history keeps it. */
data class Download(
    val id: Long,
    val url: String,
    /** What it is called - its address until that is known. */
    val title: String?,
    val choices: DownloadChoices,
    val options: DownloadOptions,
    val status: DownloadStatus,
    /** Why it failed, in the downloader's own words. */
    val error: String?,
    val failure: DownloadFailure?,
    val createdAt: Long,
    val thumbnailUrl: String?,
    val uploader: String?,
    /** The site it came from, as yt-dlp names it - YouTube, SoundCloud. */
    val site: String?,
    val durationSeconds: Long?,
    /** Every file it saved, where it saved them. */
    val savedPaths: List<String>,
    val sizeBytes: Long,
    val finishedAt: Long?,
    /** When it is to start - null to start as soon as it can. */
    val startAt: Long?,
)

/** A saved set of yt-dlp arguments, given to a download that picks it - YTDLnis's command templates. */
data class DownloadCommand(val id: Long, val name: String, val arguments: String)

/** A channel or playlist whose new videos are downloaded as they appear - YTDLnis's observed sources. */
data class DownloadWatch(
    val id: Long,
    val url: String,
    val title: String,
    /** The ids of the videos it held when last looked at - only one not among them is new. */
    val knownIds: Set<String>,
    val createdAt: Long,
)
