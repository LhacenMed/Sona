package com.lhacenmed.sona.core.downloader.sites

import org.json.JSONArray
import org.json.JSONObject

/** One file a post's video is downloaded as: whole, over HTTPS. */
internal class VideoFormat(
    /** yt-dlp's name for it, which picks it: a letter or digit, then letters, digits, `_`, `.` and `-`. */
    val id: String,
    val url: String,
    val width: Int?,
    /** Its picture's height - required of a file with a picture, which is told from one without by it. */
    val height: Int?,
    /** "none" for a file with no picture. */
    val videoCodec: String,
    /** "none" for a file with no sound. */
    val audioCodec: String,
    val extension: String = "mp4",
    val bitrateKbps: Double? = null,
    val sizeBytes: Long? = null,
    /** What its address is fetched with besides the browser it is fetched as. */
    val headers: Map<String, String> = emptyMap(),
)

/** One caption file of a post's video: in [language], at [url], as [extension] - vtt, srt - where its address does not say. */
internal class CaptionFile(
    /** As a language tag - "en", "pt-BR" - see [languageTagOf]. */
    val language: String,
    val url: String,
    /** Made by its site from the speech, rather than by people. */
    val isAutomatic: Boolean,
    val extension: String? = null,
)

/**
 * [raw], a site's name for a language, as a language tag: "en_US" and "eng-US" both "en-US", "cmn-Hans-CN" "zh-Hans-CN" - so
 * captions read from every site are named, compared and picked alike. "und" where it names none.
 */
internal fun languageTagOf(raw: String?): String {
    val parts = raw?.trim()?.replace('_', '-')?.split('-')?.filter(String::isNotEmpty).orEmpty()
    val language = parts.firstOrNull()?.lowercase() ?: return "und"
    val short = when {
        language.length != 3 -> language
        language in ChineseCodes -> "zh"
        else -> TwoLetterCodes[language] ?: language
    }
    return (listOf(short) + parts.drop(1)).joinToString("-")
}

/** The three-letter codes Chinese is spoken under - Mandarin, Cantonese - which name no two-letter code of their own. */
private val ChineseCodes = setOf("zho", "chi", "cmn", "yue")

/** Every two-letter language, by its three-letter code. */
private val TwoLetterCodes: Map<String, String> by lazy {
    java.util.Locale.getISOLanguages().associateBy { runCatching { java.util.Locale(it).isO3Language }.getOrDefault(it) }
}

/** What every post's video says, for [videoInfoJson]. */
internal class VideoPost(
    val id: String,
    val title: String?,
    val uploader: String?,
    val thumbnail: String?,
    val durationSeconds: Double?,
    /** The post's own page - what yt-dlp would read it from. */
    val webpageUrl: String,
    /** The site, as yt-dlp names its extractor - Twitter, Instagram. */
    val extractorKey: String,
    val formats: List<VideoFormat>,
    /** Its captions, where its site gives any - see [CaptionFile]. */
    val captions: List<CaptionFile> = emptyList(),
)

/**
 * [post] as yt-dlp's info JSON for one video - null where none of its files can be named in full: a file with a picture but
 * no height is left out, and two files with one id, or an id a format selector would misread, make the whole of it null.
 */
internal fun videoInfoJson(post: VideoPost, extra: JSONObject.() -> Unit = {}): String? {
    val formats = post.formats.filter { it.videoCodec == "none" || (it.height ?: 0) > 0 }
    if (formats.none { it.videoCodec != "none" } && formats.none { it.audioCodec != "none" }) return null
    val ids = formats.map { it.id }
    if (ids.toSet().size != ids.size || !ids.all(FormatId::matches) || ids.any { it in SelectorWords }) return null
    return JSONObject()
        .put("_type", "video")
        .put("id", post.id)
        .put("title", post.title ?: post.id)
        .put("uploader", post.uploader)
        .put("thumbnail", post.thumbnail)
        .put("duration", post.durationSeconds?.takeIf { it > 0 })
        .put("webpage_url", post.webpageUrl)
        .put("extractor_key", post.extractorKey)
        .put("formats", JSONArray(formats.map { format ->
            JSONObject()
                .put("format_id", format.id)
                .put("url", format.url)
                .put("protocol", "https")
                .put("ext", format.extension)
                .put("vcodec", format.videoCodec)
                .put("acodec", format.audioCodec)
                .put("width", format.width?.takeIf { it > 0 })
                .put("height", format.height?.takeIf { it > 0 })
                .put("tbr", format.bitrateKbps?.takeIf { it > 0 })
                .put("filesize", format.sizeBytes?.takeIf { it > 0 })
                .put("http_headers", JSONObject(mapOf("User-Agent" to SiteHttp.DesktopUserAgent) + format.headers))
        }))
        .put("subtitles", captionsJson(post.captions.filterNot(CaptionFile::isAutomatic)))
        .put("automatic_captions", captionsJson(post.captions.filter(CaptionFile::isAutomatic)))
        .apply(extra)
        .toString()
}

/** [captions] as yt-dlp lists them: by language, each language's files. */
private fun captionsJson(captions: List<CaptionFile>): JSONObject = JSONObject().apply {
    captions.groupBy(CaptionFile::language).forEach { (language, files) ->
        put(language, JSONArray(files.map { file -> JSONObject().put("url", file.url).apply { file.extension?.let { put("ext", it) } } }))
    }
}

/** [raw]'s first line with words in it, its spaces made one, cut to [max] characters - a post's text as a title. */
internal fun firstLine(raw: String?, max: Int = 200): String? {
    val line = raw?.lineSequence()?.firstOrNull { it.isNotBlank() } ?: return null
    val text = line.replace(Regex("\\s+"), " ").trim()
    return if (text.codePointCount(0, text.length) <= max) text else text.substring(0, text.offsetByCodePoints(0, max)).trimEnd() + "…"
}

/** An ISO 8601 length, `PT1H2M3.5S`, in seconds - null for anything else. */
internal fun isoDurationSeconds(raw: String): Double? {
    val match = IsoDuration.matchEntire(raw.trim())?.takeIf { it.value != "PT" } ?: return null
    val (hours, minutes, seconds) = match.destructured
    return (hours.toDoubleOrNull() ?: 0.0) * 3600 + (minutes.toDoubleOrNull() ?: 0.0) * 60 + (seconds.toDoubleOrNull() ?: 0.0)
}

private val IsoDuration = Regex("PT(?:([0-9]+)H)?(?:([0-9]+)M)?(?:([0-9.]+)S)?")

/** A whole number a JSON value is - null for any other value, or a negative one. */
internal fun Any?.wholeNumber(): Long? = when (this) {
    is Int -> toLong()
    is Long -> this
    is java.math.BigInteger -> takeIf { it.bitLength() < 64 }?.toLong()
    is String -> takeIf { it.length in 1..18 && it.all(Char::isDigit) }?.toLong()
    else -> null
}?.takeIf { it >= 0 }

/** [name] of this object as a non-blank string - null where it is anything else. */
internal fun JSONObject.text(name: String): String? = (opt(name) as? String)?.takeIf { it.isNotBlank() }

private val FormatId = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}")

/** What yt-dlp's format selector reads as a word of its own rather than a format's id. */
private val SelectorWords = setOf("b", "w", "best", "worst", "bv", "ba", "wv", "wa", "bestvideo", "bestaudio", "worstvideo",
    "worstaudio", "all", "mergeall")

/** This object's names, in the order it holds them - Android's org.json has no `keySet`. */
internal fun JSONObject.keyList(): List<String> = keys().asSequence().toList()
