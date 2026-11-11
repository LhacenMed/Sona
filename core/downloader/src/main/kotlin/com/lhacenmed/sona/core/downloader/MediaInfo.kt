package com.lhacenmed.sona.core.downloader

import org.json.JSONArray
import org.json.JSONObject

/** One way a link can be downloaded - a format of its own, as yt-dlp lists them. */
data class MediaFormat(
    /** Its id at its site - YouTube's itag - which picks it. */
    val id: String,
    val extension: String,
    /** Null for a format with no picture. */
    val height: Int?,
    val fps: Int?,
    val videoCodec: String?,
    val audioCodec: String?,
    val bitrateKbps: Int?,
    /** How large it is - estimated where [isSizeApproximate]. Null where its site says nothing. */
    val sizeBytes: Long?,
    val isSizeApproximate: Boolean,
    /** Where it is fetched from in one piece, over plain HTTPS - null for one yt-dlp has to assemble. */
    val directUrl: String?,
    val headers: Map<String, String>,
    /** Which of the video's sounds it holds, where its site names one. */
    val audioTrack: AudioTrack? = null,
) {
    /** Whether it has a picture - whose height a site may not say, as a media file's own link does not. */
    val hasVideo: Boolean get() = videoCodec != null
    val hasAudio: Boolean get() = audioCodec != null
}

/** What a sound of a video is: the one recorded, one dubbed into another language, or one describing what is seen. */
enum class AudioTrackKind { ORIGINAL, DUBBED, DESCRIPTIVE }

/**
 * One of a video's sounds: its [language] as its site names it - yt-dlp's, "es", and "en-desc" for one that describes - and
 * what it is. Both readers mark which is which as yt-dlp does, by its `language_preference`.
 */
data class AudioTrack(val language: String, val kind: AudioTrackKind)

/** One video a playlist or channel holds. */
data class PlaylistEntry(
    val id: String,
    val url: String,
    val title: String,
    val uploader: String?,
    val durationSeconds: Long?,
    val thumbnailUrl: String?,
)

/** What a link leads to: one video, with every way to download it, or a playlist, with its [entries]. */
data class MediaInfo(
    val id: String?,
    val title: String,
    val uploader: String?,
    val durationSeconds: Long?,
    val thumbnailUrl: String?,
    /** The site, as yt-dlp names it - Youtube, Soundcloud. */
    val site: String?,
    val formats: List<MediaFormat>,
    /** Null for one video. */
    val entries: List<PlaylistEntry>?,
    /** The song's own title and artist, where its site knows them - YouTube Music's. */
    val track: String?,
    val artist: String?,
    val year: String?,
    val chaptersJson: String?,
    /** The codes of its captions made by people, as its read names them - see [captionsToEmbed]. */
    val madeCaptions: List<String> = emptyList(),
    /** The codes of its captions its site made - from the speech, and translated. */
    val automaticCaptions: List<String> = emptyList(),
) {
    val isPlaylist: Boolean get() = entries != null

    /** How many chapters it has - what one file per chapter makes of it. */
    val chapterCount: Int get() = chaptersJson?.let { JSONArray(it).length() } ?: 0

    /** Whether it has captions - what downloading it with captions has to embed. */
    val hasCaptions: Boolean get() = madeCaptions.isNotEmpty() || automaticCaptions.isNotEmpty()

    /** Its sounds where it has more than one - the one recorded first - none where it has one. */
    val audioTracks: List<AudioTrack> get() = formats.mapNotNull { it.audioTrack }.distinct().sortedBy { it.kind }.takeIf { it.size > 1 }.orEmpty()

    /** The language of the sound kept for [wanted]: that one where it has it, else the one recorded - null where it has one sound. */
    fun audioLanguageFor(wanted: String?): String? =
        audioTracks.firstOrNull { it.language == wanted }?.language ?: audioTracks.firstOrNull { it.kind == AudioTrackKind.ORIGINAL }?.language
}

/**
 * A link, read: what it leads to, and the info JSON it was read into - yt-dlp's own format, whichever reader read it - which a
 * download is handed so as not to read the link again.
 */
class Extraction(
    val json: String,
    val info: MediaInfo,
    /**
     * Whether it holds all yt-dlp's own read would - false for NewPipe's, read without the watch page's data, whose Most
     * replayed graph and chapters a download adds: see [MediaResolver.resolveForDownload].
     */
    val isComplete: Boolean,
    /** When its stream addresses stop working - null where they do not say. */
    val expiresAt: Long?,
) {
    fun isFresh(now: Long = System.currentTimeMillis()): Boolean = expiresAt == null || now < expiresAt
}

/** [json], a reader's read of a link - [isComplete] where it holds all yt-dlp's own would: see [Extraction.isComplete]. */
internal fun extractionOf(json: String, isComplete: Boolean): Extraction {
    val parsed = JSONObject(json)
    return Extraction(json, mediaInfoOf(parsed), isComplete, expiryOf(parsed))
}

/** How long before its streams' own expiry a read is let go: a download started then has time to finish. */
private const val ExpiryMarginMs = 30 * 60 * 1000L

/** [json], yt-dlp's info JSON, read into what the app shows and downloads from. */
internal fun mediaInfoOf(json: JSONObject): MediaInfo {
    val entries = json.optJSONArray("entries")?.takeIf { json.optString("_type") == "playlist" }
    return MediaInfo(
        id = json.stringOrNull("id"),
        title = json.stringOrNull("title") ?: json.stringOrNull("webpage_url").orEmpty(),
        uploader = json.stringOrNull("uploader") ?: json.stringOrNull("channel"),
        durationSeconds = json.optDouble("duration").takeUnless(Double::isNaN)?.toLong(),
        thumbnailUrl = json.stringOrNull("thumbnail")
            ?: json.optJSONArray("thumbnails")?.lastUrl()
            ?: entries?.optJSONObject(0)?.optJSONArray("thumbnails")?.lastUrl(),
        site = json.stringOrNull("extractor_key"),
        formats = json.optJSONArray("formats")?.objects()?.mapNotNull(::formatOf).orEmpty(),
        entries = entries?.objects()?.mapNotNull(::entryOf),
        track = json.stringOrNull("track"),
        artist = json.stringOrNull("artist") ?: json.stringOrNull("creator"),
        year = json.stringOrNull("release_year") ?: json.stringOrNull("upload_date")?.take(4),
        chaptersJson = json.optJSONArray("chapters")?.takeIf { it.length() > 0 }?.toString(),
        madeCaptions = json.optJSONObject("subtitles")?.keys()?.asSequence()?.toList().orEmpty(),
        automaticCaptions = json.optJSONObject("automatic_captions")?.keys()?.asSequence()?.toList().orEmpty(),
    )
}

/** yt-dlp's marks on a YouTube sound, by its `language_preference`: the one recorded, one dubbed, one that describes what is seen. */
internal const val OriginalLanguagePreference = 10
internal const val DubbedLanguagePreference = -1
internal const val DescriptiveLanguagePreference = -10

/** The sound [format] holds, by yt-dlp's `language` and `language_preference` - null where it names no language. */
private fun audioTrackOf(format: JSONObject): AudioTrack? {
    val language = format.stringOrNull("language")?.takeIf { format.stringOrNull("acodec") != "none" } ?: return null
    val preference = format.optInt("language_preference", 0)
    val kind = when {
        preference >= OriginalLanguagePreference -> AudioTrackKind.ORIGINAL
        preference <= DescriptiveLanguagePreference -> AudioTrackKind.DESCRIPTIVE
        else -> AudioTrackKind.DUBBED
    }
    return AudioTrack(language, kind)
}

/** The earliest moment one of [json]'s stream addresses stops working, less a margin - null where none says. */
internal fun expiryOf(json: JSONObject): Long? {
    val urls = json.optJSONArray("formats")?.objects()?.mapNotNull { it.stringOrNull("url") }.orEmpty()
    return urls.mapNotNull { url ->
        Regex("""[?&](?:expire|x-expires)=(\d+)""").find(url)?.groupValues?.get(1)?.toLongOrNull()?.times(1000)
    }.minOrNull()?.minus(ExpiryMarginMs)
}

private fun formatOf(format: JSONObject): MediaFormat? {
    val id = format.stringOrNull("format_id") ?: return null
    // Storyboards are pictures of the video to seek over, not a way to download it.
    if (format.optString("format_note") == "storyboard" || format.optString("ext") == "mhtml") return null
    val videoCodec = format.stringOrNull("vcodec")?.takeUnless { it == "none" }
    val audioCodec = format.stringOrNull("acodec")?.takeUnless { it == "none" }
    val height = format.optInt("height").takeIf { it > 0 && videoCodec != null }
    val exactSize = format.optLong("filesize").takeIf { it > 0 }
    val approximateSize = format.optLong("filesize_approx").takeIf { it > 0 }
    val protocol = format.optString("protocol")
    val headers = format.optJSONObject("http_headers")?.let { headers -> headers.keys().asSequence().associateWith { headers.optString(it) } }.orEmpty()
    return MediaFormat(
        id = id,
        extension = format.optString("ext"),
        height = height,
        fps = format.optInt("fps").takeIf { it > 0 },
        videoCodec = videoCodec,
        audioCodec = audioCodec,
        bitrateKbps = (format.optDouble("tbr").takeUnless(Double::isNaN) ?: format.optDouble("abr").takeUnless(Double::isNaN))?.toInt(),
        sizeBytes = exactSize ?: approximateSize,
        isSizeApproximate = exactSize == null,
        directUrl = format.stringOrNull("url")?.takeIf { protocol == "https" || protocol == "http" },
        headers = headers,
        audioTrack = audioTrackOf(format),
    )
}

private fun entryOf(entry: JSONObject): PlaylistEntry? {
    val id = entry.stringOrNull("id") ?: return null
    return PlaylistEntry(
        id = id,
        url = entry.stringOrNull("url") ?: entry.stringOrNull("webpage_url") ?: return null,
        title = entry.stringOrNull("title") ?: id,
        uploader = entry.stringOrNull("uploader") ?: entry.stringOrNull("channel"),
        durationSeconds = entry.optDouble("duration").takeUnless(Double::isNaN)?.toLong(),
        thumbnailUrl = entry.stringOrNull("thumbnail") ?: entry.optJSONArray("thumbnails")?.lastUrl(),
    )
}

internal fun JSONObject.stringOrNull(name: String): String? = optString(name).takeIf { has(name) && !isNull(name) && it.isNotBlank() }

internal fun JSONArray.objects(): List<JSONObject> = List(length()) { optJSONObject(it) }.filterNotNull()

private fun JSONArray.lastUrl(): String? = objects().lastOrNull()?.stringOrNull("url")
