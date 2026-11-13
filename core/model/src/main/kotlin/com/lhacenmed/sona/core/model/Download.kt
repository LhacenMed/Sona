package com.lhacenmed.sona.core.model

/** What a download keeps of what it fetches: the video with its sound, the sound alone, or the video's captions alone. */
enum class DownloadKind {
    VIDEO,
    AUDIO,

    /** Its captions alone, as files - saved where videos are - with nothing of its sound or picture fetched. */
    SUBTITLES,
    ;

    /** Whether it saves sound or picture: what the library holds, and a player plays. */
    val isMedia: Boolean get() = this != SUBTITLES
}

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

/** Which of a video's codecs is preferred - Seal's preferred video format. */
enum class VideoFormat {
    /** H.264 in MP4, which every app plays - for sharing. */
    LEGACY,

    /** AV1, VP9 or H.265, smaller at the same quality - for watching in an app that plays them. */
    QUALITY,
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
    val videoFormat: VideoFormat = VideoFormat.QUALITY,
    val audioFormat: AudioFormat = AudioFormat.ORIGINAL,
    /** A video's captions - in the device's language and English, unless [DownloadOptions.subtitleLanguages] picks others. */
    val captions: Boolean = true,
    /** The parts [DownloaderPreferences.sponsorSegments] names cut out, as SponsorBlock's community marks them. */
    val skipSponsors: Boolean = false,
    /**
     * The language of the sound kept, as its site names it - "es" - where a video has its sound in more than one: dubbed, or
     * described. The one it was recorded in while null, or where it has none in this language.
     */
    val audioLanguage: String? = null,
    /** Captions saved as files beside the video, rather than as tracks in it. */
    val subtitlesAsFile: Boolean = false,
    /** Sound saved as the song it is - found in the music catalogues, and tagged, covered and given lyrics as that song. */
    val savesAsSong: Boolean = true,
    /**
     * The cover kept as a picture of its own too, beside the file - the one in it: a sound always has its cover in its file, its
     * song's where it is saved as one, else the video's picture; a video is its own picture.
     */
    val savesCover: Boolean = false,
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
    /**
     * The captions picked from the video's own, by their codes as its read names them - none while empty. Null leaves it to the
     * captions [DownloadChoices.captions] would take.
     */
    val subtitleLanguages: List<String>? = null,
    /**
     * The song picked for a sound [DownloadChoices.savesAsSong] - its tags, cover and lyrics, as the tag editor keeps them. Null
     * leaves it to the song the file's own names surely are.
     */
    val song: String? = null,
    /** Saved even where it is already in the library - asked for once told so. See `LibraryCopies`. */
    val downloadsAgain: Boolean = false,
)

/** How every file is named, yt-dlp's way: its title, cut to fit, and its id. */
const val DEFAULT_FILE_NAME_TEMPLATE = "%(title).150B [%(id)s].%(ext)s"

/**
 * The folder downloads are saved in until another is picked - as every folder is kept: its path on the device's own storage,
 * or, on another - a memory card - its whole path.
 */
const val DEFAULT_DOWNLOAD_FOLDER = "Download/Sona"

/**
 * A service that transcribes a video's speech into captions, by the user's own free key - [title] its name, as it names itself.
 * Supadata reads a video by its link, the site's own transcript first; Groq hears its sound, uploaded.
 */
enum class TranscriptionService(val title: String) {
    SUPADATA("Supadata"),
    GROQ("Groq"),
}

/** How downloading works, whatever is downloaded - the Downloads settings. */
data class DownloaderPreferences(
    val downloadsAtOnce: Int = 2,
    /** Downloads wait for Wi-Fi, or any network that is not metered. */
    val wifiOnly: Boolean = false,
    /** The folder sound is saved in - "Music", "Download/Sona" - see [DEFAULT_DOWNLOAD_FOLDER]. */
    val audioFolder: String = DEFAULT_DOWNLOAD_FOLDER,
    /** The folder videos are saved in. */
    val videoFolder: String = DEFAULT_DOWNLOAD_FOLDER,
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
    /** The user's own key to Supadata's transcript API - what transcribes a video's speech first; none while blank. */
    val supadataKey: String = "",
    /** The user's own key to Groq's speech-to-text API; none while blank. */
    val groqKey: String = "",
    /** The service a video's speech is transcribed by first - see [transcribers]. */
    val transcriptionService: TranscriptionService = TranscriptionService.SUPADATA,
) {
    /** The services a video's speech is transcribed by, in the order they are tried: the one chosen, then the other - each with its key given. */
    val transcribers: List<TranscriptionService>
        get() = (listOf(transcriptionService) + TranscriptionService.entries).distinct().filter { keyFor(it).isNotBlank() }

    /** Whether a video's speech can be transcribed into captions: a key to either is given. */
    val transcribes: Boolean get() = transcribers.isNotEmpty()

    /** The user's key to [service] - blank while none is given. */
    fun keyFor(service: TranscriptionService): String = when (service) {
        TranscriptionService.SUPADATA -> supadataKey
        TranscriptionService.GROQ -> groqKey
    }

    /** The folder [kind] is saved in. */
    fun folderFor(kind: DownloadKind): String = if (kind == DownloadKind.AUDIO) audioFolder else videoFolder

    /** These preferences, [kind] saved in [folder] - see [DEFAULT_DOWNLOAD_FOLDER] - the default where it names none. */
    fun withFolder(kind: DownloadKind, folder: String): DownloaderPreferences {
        val path = folder.trim().trimEnd('/').ifEmpty { DEFAULT_DOWNLOAD_FOLDER }
        return if (kind == DownloadKind.AUDIO) copy(audioFolder = path) else copy(videoFolder = path)
    }
}

/** Where a download stands. */
enum class DownloadStatus {
    QUEUED,
    RUNNING,

    /** Stopped by the user, to carry on later from its last byte. */
    PAUSED,
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

    /** It is in the library already - downloaded before, or found there - and was not asked for again. */
    ALREADY_SAVED,
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
    /** The playlist or watched channel it was queued from, with the rest of its videos - null for one link alone. */
    val batch: DownloadBatch? = null,
)

/**
 * Downloads queued together - a playlist's videos, or what is new on a watched channel - listed as one, by [key], as [title].
 */
data class DownloadBatch(val key: String, val title: String)

/**
 * A saved set of yt-dlp arguments - YTDLnis's command templates: given to a download that picks it, and by itself - beside the
 * one picked - to every download of the kinds it [addsToAudio] and [addsToVideo], and to every read of a link with yt-dlp it
 * [addsToReading]. One [isPreferred] is picked for every download to begin with. Each is only for the links its
 * [urlPatterns] match - every link while it has none.
 */
data class DownloadCommand(
    val id: Long,
    val name: String,
    val arguments: String,
    val addsToAudio: Boolean = false,
    val addsToVideo: Boolean = false,
    val addsToReading: Boolean = false,
    val isPreferred: Boolean = false,
    val urlPatterns: List<String> = emptyList(),
) {
    /** Whether it is for [url]. */
    fun isFor(url: String): Boolean = urlPatterns.isEmpty() || urlPatterns.any { runCatching { Regex(it).containsMatchIn(url) }.getOrDefault(false) }

    /** Whether a download of [url] as [kind] is given it by itself. */
    fun addsTo(kind: DownloadKind, url: String): Boolean = when (kind) {
        DownloadKind.AUDIO -> addsToAudio
        DownloadKind.VIDEO -> addsToVideo
        DownloadKind.SUBTITLES -> false
    } && isFor(url)
}

/** The command a download of [url] is given to begin with: the first preferred one that is for it. */
fun List<DownloadCommand>.preferredFor(url: String): DownloadCommand? = firstOrNull { it.isPreferred && it.isFor(url) }

/** A snippet of yt-dlp arguments, inserted while a command is written - YTDLnis's template shortcuts. */
data class DownloadCommandShortcut(val id: Long, val content: String)

/** A channel or playlist whose new videos are downloaded as they appear - YTDLnis's observed sources. */
data class DownloadWatch(
    val id: Long,
    val url: String,
    val title: String,
    /** The ids of the videos it held when last looked at - only one not among them is new. */
    val knownIds: Set<String>,
    val createdAt: Long,
)
