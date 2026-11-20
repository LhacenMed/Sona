package com.lhacenmed.sona.core.model

import java.util.Locale

/**
 * What a download keeps of what it fetches: the video with its sound, the sound alone, the video's captions alone - or, from a
 * link that serves a file of any other kind, that file as it is.
 */
enum class DownloadKind {
    VIDEO,
    AUDIO,

    /** Its captions alone, as files - saved where videos are - with nothing of its sound or picture fetched. */
    SUBTITLES,

    /** A file that is not media - an archive, a document, an app - saved as it is served, in the folder for files. */
    FILE,
    ;

    /** Whether it saves sound or picture: what the library holds, and a player plays. */
    val isMedia: Boolean get() = this == VIDEO || this == AUDIO
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
    /**
     * What the browser it was found in sent for it - its cookies, who it said it was, the page it came from - sent with every
     * request for it, so a site that serves a file only to the page that links it serves it here too. None for a link given.
     */
    val requestHeaders: Map<String, String> = emptyMap(),
    /** The name the user gave its file, without its extension - see [toFileName]. Null names it by the file name template. */
    val fileName: String? = null,
    /**
     * The video's further sounds embedded beside the one [DownloadChoices.audioLanguage] picks, by their language codes - a track
     * each, which a player switches between. None for the one sound.
     */
    val audioLanguages: List<String> = emptyList(),
)

/** [this] as a file can be named: without what a file system forbids, cut to fit - "download" for nothing left. */
fun String.toFileName(): String = replace(Regex("""[\\/:*?"<>|\x00-\x1f]"""), "_").trim().take(150).ifEmpty { "download" }

/** What every file name is built from, yt-dlp's way: its title, cut to fit - which every template keeps. */
const val FILE_NAME_BASENAME = "%(title).150B"

/** What every file name ends in, yt-dlp's way: its extension - which every template keeps. */
const val FILE_NAME_EXTENSION = ".%(ext)s"

/** A file named by its title alone. */
const val TITLE_FILE_NAME_TEMPLATE = FILE_NAME_BASENAME + FILE_NAME_EXTENSION

/** How every file is named unless another template is chosen: its title, and its id. */
const val DEFAULT_FILE_NAME_TEMPLATE = "$FILE_NAME_BASENAME [%(id)s]$FILE_NAME_EXTENSION"

/** What a video's captions are saved as - [extension] as yt-dlp's `--convert-subs` names it - or, [ORIGINAL], as its site serves them. */
enum class SubtitleFormat(val extension: String?) {
    ORIGINAL(null),
    SRT("srt"),
    ASS("ass"),
    LRC("lrc"),
    VTT("vtt"),
}

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

/** Where yt-dlp is brought up to date from - [id] as `--update-to` names it. */
enum class YtDlpChannel(val id: String) {
    STABLE("stable"),
    NIGHTLY("nightly"),
}

/** How often something is done by itself - every [days]. */
enum class UpdateInterval(val days: Long) {
    DAY(1),
    WEEK(7),
    MONTH(30),
}

/** How downloading works, whatever is downloaded - the Downloads settings. */
data class DownloaderPreferences(
    val downloadsAtOnce: Int = 2,
    /** Downloads wait for Wi-Fi, or any network that is not metered. */
    val wifiOnly: Boolean = false,
    /** The folder sound is saved in - "Music", "Download/Sona" - see [DEFAULT_DOWNLOAD_FOLDER]. */
    val audioFolder: String = DEFAULT_DOWNLOAD_FOLDER,
    /** The folder videos are saved in - and their captions, saved alone. */
    val videoFolder: String = DEFAULT_DOWNLOAD_FOLDER,
    /** The folder files that are not media are saved in. */
    val fileFolder: String = DEFAULT_DOWNLOAD_FOLDER,
    val fileNameTemplate: String = DEFAULT_FILE_NAME_TEMPLATE,
    val sponsorSegments: Set<SponsorSegment> = setOf(
        SponsorSegment.SPONSOR,
        SponsorSegment.SELF_PROMOTION,
        SponsorSegment.INTERACTION,
        SponsorSegment.MUSIC_OFFTOPIC,
    ),
    /** Every request goes through [proxy] - see [activeProxy]. */
    val usesProxy: Boolean = false,
    /** A proxy - `socks5://host:port`, `http://host:port` - kept while [usesProxy] is off, for when it is on again. */
    val proxy: String = "",
    /** Downloads take no more of the connection than [rateLimit] - see [activeRateLimit]. */
    val limitsRate: Boolean = false,
    /** The most a download takes of the connection a second - `2M`, `500K` - kept while [limitsRate] is off. */
    val rateLimit: String = "1000K",
    /** Every connection is made over IPv4 - yt-dlp's `--force-ipv4`. */
    val forcesIpv4: Boolean = false,
    /** How many parts of a stream in fragments - HLS, DASH - yt-dlp fetches at once: 1, 8, 16 or 24. */
    val concurrentFragments: Int = 8,
    /** Sites are read signed in, as the app's browser is signed in to them - see `BrowserCookies`. */
    val usesCookies: Boolean = true,
    /** Signed in, requests also say they come from the browser the cookies were made in - [userAgent]. */
    val sendsUserAgent: Boolean = false,
    /** What the browser cookies are sent as calls itself - the app's browser's own while empty. */
    val userAgent: String = "",
    /** The folder what downloads with a command - or is run in the terminal - is saved in: each kind's own while blank. */
    val commandFolder: String = "",
    /**
     * The languages captions are wanted in, by their codes, comma-separated - `en, fr` - the device's and English while blank.
     * See [wantedCaptionLanguages].
     */
    val subtitleLanguages: String = "",
    /** What captions are saved as. */
    val subtitleFormat: SubtitleFormat = SubtitleFormat.SRT,
    /** Captions a site made from the speech are taken where people made none. */
    val writesAutoSubtitles: Boolean = true,
    /** Captions embedded in a video are kept beside it too, as files of their own. */
    val keepsSubtitleFiles: Boolean = false,
    /** The services not to transcribe with, though their keys are kept - turned off by the user. */
    val disabledTranscriptionServices: Set<TranscriptionService> = emptySet(),
    /** The thumbnail is also saved beside what it was downloaded with, as a file of its own. */
    val savesThumbnail: Boolean = false,
    /** Incognito: what is downloaded leaves the Downloads list once it is saved - its files are kept. */
    val isIncognito: Boolean = false,
    /** A link's thumbnail is not shown while it is read and its download set up. */
    val hidesPreview: Boolean = false,
    /** The library shows the button that opens Downloads. */
    val showsLibraryButton: Boolean = true,
    /** Where yt-dlp is brought up to date from. */
    val ytDlpChannel: YtDlpChannel = YtDlpChannel.NIGHTLY,
    /** How often yt-dlp brings itself up to date - never while null. */
    val ytDlpAutoUpdate: UpdateInterval? = UpdateInterval.WEEK,
    /** What was downloaded once is not downloaded again, by its id at its site. */
    val usesArchive: Boolean = false,
    /** The user's own key to Supadata's transcript API - what transcribes a video's speech first; none while blank. */
    val supadataKey: String = "",
    /** The user's own key to Groq's speech-to-text API; none while blank. */
    val groqKey: String = "",
    /** The service a video's speech is transcribed by first - see [transcribers]. */
    val transcriptionService: TranscriptionService = TranscriptionService.SUPADATA,
) {
    /** The proxy every request goes through - null while it is off, or none is given. */
    val activeProxy: String? get() = proxy.trim().takeIf { usesProxy && it.isNotEmpty() }

    /** The most a download takes of the connection a second - null while there is no limit. */
    val activeRateLimit: String? get() = rateLimit.trim().takeIf { limitsRate && it.isNotEmpty() }

    /** The services a video's speech is transcribed by, in the order they are tried: the one chosen, then the other - each with its key given. */
    val transcribers: List<TranscriptionService>
        get() = (listOf(transcriptionService) + TranscriptionService.entries).distinct().filter(::isTranscribingWith)

    /** Whether [service] transcribes: its key is given, and it is not turned off. */
    fun isTranscribingWith(service: TranscriptionService): Boolean = keyFor(service).isNotBlank() && service !in disabledTranscriptionServices

    /** These preferences, [service] turned on - or, not [on], off, its key kept. */
    fun transcribingWith(service: TranscriptionService, on: Boolean): DownloaderPreferences =
        copy(disabledTranscriptionServices = if (on) disabledTranscriptionServices - service else disabledTranscriptionServices + service)

    /** The languages captions are wanted in - those [subtitleLanguages] names, else the device's and English. */
    val wantedCaptionLanguages: List<String>
        get() = subtitleLanguages.split(',').map(String::trim).filter(String::isNotEmpty)
            .ifEmpty { listOf(Locale.getDefault().language, "en") }

    /** Whether a video's speech can be transcribed into captions: a key to either is given. */
    val transcribes: Boolean get() = transcribers.isNotEmpty()

    /** The user's key to [service] - blank while none is given. */
    fun keyFor(service: TranscriptionService): String = when (service) {
        TranscriptionService.SUPADATA -> supadataKey
        TranscriptionService.GROQ -> groqKey
    }

    /** These preferences, with [key] as the user's key to [service]. */
    fun withKey(service: TranscriptionService, key: String): DownloaderPreferences = when (service) {
        TranscriptionService.SUPADATA -> copy(supadataKey = key)
        TranscriptionService.GROQ -> copy(groqKey = key)
    }

    /** The folder [kind] is saved in - the [commandFolder], where one is set, for what [runsCommand]. */
    fun folderFor(kind: DownloadKind, runsCommand: Boolean): String =
        commandFolder.takeIf { runsCommand && it.isNotBlank() } ?: folderFor(kind)

    /** The folder [kind] is saved in. */
    fun folderFor(kind: DownloadKind): String = when (kind) {
        DownloadKind.AUDIO -> audioFolder
        DownloadKind.VIDEO, DownloadKind.SUBTITLES -> videoFolder
        DownloadKind.FILE -> fileFolder
    }

    /** These preferences, [kind] saved in [folder] - see [DEFAULT_DOWNLOAD_FOLDER] - the default where it names none. */
    fun withFolder(kind: DownloadKind, folder: String): DownloaderPreferences {
        val path = folder.trim().trimEnd('/').ifEmpty { DEFAULT_DOWNLOAD_FOLDER }
        return when (kind) {
            DownloadKind.AUDIO -> copy(audioFolder = path)
            DownloadKind.VIDEO, DownloadKind.SUBTITLES -> copy(videoFolder = path)
            DownloadKind.FILE -> copy(fileFolder = path)
        }
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
        DownloadKind.SUBTITLES, DownloadKind.FILE -> false
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
