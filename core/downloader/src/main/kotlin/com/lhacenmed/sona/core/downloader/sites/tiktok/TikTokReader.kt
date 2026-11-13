package com.lhacenmed.sona.core.downloader.sites.tiktok

import com.lhacenmed.sona.core.downloader.objects
import com.lhacenmed.sona.core.downloader.sites.CaptionFile
import com.lhacenmed.sona.core.downloader.sites.HttpsUrl
import com.lhacenmed.sona.core.downloader.sites.keyList
import com.lhacenmed.sona.core.downloader.sites.languageTagOf
import com.lhacenmed.sona.core.downloader.sites.ShortLinks
import com.lhacenmed.sona.core.downloader.sites.SiteHttp
import com.lhacenmed.sona.core.downloader.sites.SiteReader
import com.lhacenmed.sona.core.downloader.sites.VideoFormat
import com.lhacenmed.sona.core.downloader.sites.VideoPost
import com.lhacenmed.sona.core.downloader.sites.firstLine
import com.lhacenmed.sona.core.downloader.sites.text
import com.lhacenmed.sona.core.downloader.sites.videoInfoJson
import com.lhacenmed.sona.core.downloader.sites.wholeNumber
import java.security.MessageDigest
import java.util.Base64
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

/** A post page runs to a megabyte or two. */
private const val MaxBytes = 3 * 1024 * 1024

/** A public TikTok post link, `tiktok.com/@<user>/video|photo/<id>`; its query never reaches the page read. */
internal class TikTokPostIdentity private constructor(val username: String, val postId: String) {
    val videoUrl: String get() = "https://www.tiktok.com/@$username/video/$postId"

    companion object {
        private val hosts = setOf("www.tiktok.com", "tiktok.com", "m.tiktok.com")
        private val path = Regex("/@([A-Za-z0-9_.]{1,64})/(?:photo|video)/([1-9][0-9]{0,24})/?")

        fun parse(raw: String): TikTokPostIdentity? {
            val url = HttpsUrl.parse(raw) ?: return null
            if (url.host !in hosts) return null
            val match = path.matchEntire(url.encodedPath) ?: return null
            return TikTokPostIdentity(match.groupValues[1], match.groupValues[2])
        }
    }
}

/**
 * TikTok, read from a post's desktop page: its rehydration JSON names the video's files, each quality one MP4 with its sound,
 * served only with the `tt_chain_token` cookie the page set - which every format carries. The page's «Please wait…» check is
 * answered once. A short `vm.`/`vt.tiktok.com` link is followed to its post first. A photo post is not read.
 */
internal object TikTokReader : SiteReader {
    override fun accepts(url: String): Boolean = TikTokPostIdentity.parse(url) != null || isShortLink(url)

    override fun read(url: String, http: SiteHttp): String? {
        val target = if (isShortLink(url)) http.landing(url, ShortLinks::isShortLink)?.toString() ?: return null else url
        val identity = TikTokPostIdentity.parse(target) ?: return null
        // Its redirects stay on www pages of the same post.
        val follow = { _: okhttp3.HttpUrl, to: okhttp3.HttpUrl ->
            TikTokPostIdentity.parse(to.toString())?.takeIf { to.host == "www.tiktok.com" && it.postId == identity.postId }
                ?.let { HttpsUrl.parse(it.videoUrl) }
        }
        var page = http.get(identity.videoUrl, MaxBytes, SiteHttp.BrowserPage, follow) ?: return null
        val cookies = page.cookies.toMutableMap()
        TikTokWafChallenge.solve(page.text)?.let { answer ->
            page = http.get(identity.videoUrl, MaxBytes, SiteHttp.BrowserPage + ("Cookie" to answer), follow) ?: return null
            cookies += page.cookies
        }
        if (TikTokPostIdentity.parse(page.url.toString())?.postId != identity.postId) return null
        return parse(page.text, identity.postId, identity.videoUrl, cookies["tt_chain_token"] ?: return null)
    }

    private fun isShortLink(url: String): Boolean =
        HttpsUrl.parse(url)?.let { it.host in setOf("vm.tiktok.com", "vt.tiktok.com") && ShortLinks.isShortLink(it) } == true

    /** The video of the post [postId] on [html], each format fetched with [chainToken] - null for a photo post or another post. */
    fun parse(html: String, postId: String, webpageUrl: String, chainToken: String): String? {
        val item = runCatching {
            val document = Jsoup.parse(html, "", Parser.htmlParser().setMaxDepth(64))
            val script = document.select("script[id=__UNIVERSAL_DATA_FOR_REHYDRATION__]").singleOrNull() ?: return null
            val detail = JSONObject(script.data()).optJSONObject("__DEFAULT_SCOPE__")?.optJSONObject("webapp.video-detail") ?: return null
            if (detail.opt("statusCode").wholeNumber().let { it != null && it != 0L }) return null
            detail.optJSONObject("itemInfo")?.optJSONObject("itemStruct")
        }.getOrNull() ?: return null
        if (item.opt("id")?.toString() != postId) return null
        if (item.has("imagePost") && !item.isNull("imagePost")) return null
        val video = item.optJSONObject("video") ?: return null
        val headers = mapOf("Referer" to "https://www.tiktok.com/", "Cookie" to "tt_chain_token=$chainToken")
        val formats = video.optJSONArray("bitrateInfo")?.objects().orEmpty().mapIndexedNotNull { index, rate ->
            val address = rate.optJSONObject("PlayAddr") ?: return@mapIndexedNotNull null
            val url = address.optJSONArray("UrlList")?.let { list -> (0 until list.length()).mapNotNull { list.opt(it) as? String } }
                ?.firstOrNull(::isVideo) ?: return@mapIndexedNotNull null
            val codec = rate.text("CodecType").orEmpty()
            VideoFormat(
                id = rate.text("GearName")?.replace(Regex("[^A-Za-z0-9_.-]"), "_") ?: "bitrate-$index",
                url = url,
                width = address.opt("Width").wholeNumber()?.toInt(),
                height = address.opt("Height").wholeNumber()?.toInt(),
                videoCodec = if (codec.startsWith("h265") || codec.startsWith("bytevc1")) "h265" else "h264",
                audioCodec = "aac",
                bitrateKbps = rate.opt("Bitrate").wholeNumber()?.div(1000.0),
                sizeBytes = address.opt("DataSize").wholeNumber(),
                headers = headers,
            )
        }.ifEmpty {
            // No list of qualities: the one file the player plays.
            listOfNotNull(video.text("playAddr")?.takeIf(::isVideo)?.let { url ->
                VideoFormat(
                    id = "play_addr",
                    url = url,
                    width = video.opt("width").wholeNumber()?.toInt(),
                    height = video.opt("height").wholeNumber()?.toInt(),
                    videoCodec = if (video.text("codecType")?.startsWith("h265") == true) "h265" else "h264",
                    audioCodec = "aac",
                    headers = headers,
                )
            })
        }
        val author = item.optJSONObject("author")
        return videoInfoJson(
            VideoPost(
                id = postId,
                title = firstLine(item.text("desc")) ?: author?.text("nickname")?.let { "TikTok video by $it" },
                uploader = author?.text("nickname") ?: author?.text("uniqueId"),
                thumbnail = video.text("cover")?.takeIf { HttpsUrl.parse(it) != null },
                durationSeconds = video.opt("duration").wholeNumber()?.toDouble(),
                webpageUrl = webpageUrl,
                extractorKey = "TikTok",
                formats = formats,
                captions = captionsOf(video),
            ),
        )
    }

    /**
     * The video's captions, as yt-dlp reads them from its page: `subtitleInfos`, each in WebVTT or SubRip - made from the speech
     * (`ASR`) or translated from those (`MT`) by TikTok, else by the creator. Its creator's own JSON captions no player reads.
     */
    private fun captionsOf(video: JSONObject): List<CaptionFile> = video.optJSONArray("subtitleInfos")?.objects().orEmpty().mapNotNull { info ->
        val url = info.text("Url")?.takeIf { HttpsUrl.parse(it) != null } ?: return@mapNotNull null
        val extension = when (info.text("Format")) {
            "webvtt" -> "vtt"
            "srt" -> "srt"
            else -> return@mapNotNull null
        }
        CaptionFile(languageTagOf(info.text("LanguageCodeName")), url, isAutomatic = info.text("Source") in setOf("ASR", "MT"), extension)
    }

    private fun isVideo(raw: String): Boolean {
        val url = HttpsUrl.parse(raw) ?: return false
        return VideoHost.matches(url.host) && !url.encodedPath.contains("..") && VideoPath.matches(url.encodedPath)
    }

    private val VideoHost = Regex(
        "[a-z0-9][a-z0-9.-]*\\.(?:tiktokcdn(?:-us)?\\.com|tiktokcdn-eu\\.com|tiktokv\\.com|byteoversea\\.com|ibytedtos\\.com|ibyteimg\\.com|muscdn\\.com)" +
            "|v[0-9]{1,3}-webapp(?:-prime)?\\.(?:tiktok|us\\.tiktok)\\.com",
    )
    private val VideoPath = Regex("/[A-Za-z0-9_./%=~-]+")
}

/**
 * TikTok's «Please wait…» page: a SHA-256 proof of work, its answer sent back as one cookie on the next fetch of the page -
 * as yt-dlp's `_solve_challenge_and_set_cookies` answers it.
 */
internal object TikTokWafChallenge {
    /** The check page is about 1.5 KB; a post page is never read as one. */
    private const val MaxPage = 32 * 1024
    private const val MaxAttempts = 1_000_000
    private val cookieName = Regex("[A-Za-z0-9_-]{1,64}")
    private val cookieValue = Regex("[A-Za-z0-9+/=_.-]{1,4096}")

    /** The `Cookie` header that answers [html]'s check - null where it is no check, or one that cannot be solved. */
    fun solve(html: String): String? {
        if (html.length !in 1..MaxPage) return null
        return runCatching {
            val document = Jsoup.parse(html, "", Parser.htmlParser().setMaxDepth(16))
            fun attribute(id: String) = document.getElementById(id)?.attr("class")?.trim()?.takeIf { it.isNotEmpty() }
            val name = attribute("wci")?.takeIf(cookieName::matches) ?: return null
            val encoded = attribute("cs")?.takeIf { it.length <= 4096 } ?: return null
            val challenge = JSONObject(String(decode(encoded)))
            val values = challenge.optJSONObject("v") ?: return null
            val base = decode(values.text("a") ?: return null)
            val expected = decode(values.text("c") ?: return null)
            if (expected.size != 32) return null
            val digest = MessageDigest.getInstance("SHA-256")
            val answer = (0..MaxAttempts).firstOrNull { number ->
                digest.update(base)
                digest.digest(number.toString().toByteArray()).contentEquals(expected)
            } ?: return null
            val solved = Base64.getEncoder().encodeToString(answer.toString().toByteArray())
            val value = Base64.getEncoder().encodeToString((compact(challenge, solved) ?: return null).toByteArray())
            val original = attribute("rci")?.takeIf(cookieName::matches)?.let { rci ->
                attribute("rs")?.takeIf(cookieValue::matches)?.let { "; $rci=$it" }
            }.orEmpty()
            "$name=$value$original"
        }.getOrNull()
    }

    /**
     * [challenge] with `d` set to [solved], written as yt-dlp's `json.dumps(separators=(',', ':'))` writes it: `v`'s `a`, `b`
     * and `c`, then `s`, then `d`. A challenge holding anything else is not answered.
     */
    private fun compact(challenge: JSONObject, solved: String): String? {
        val values = challenge.getJSONObject("v")
        if (challenge.keyList().toSet() !in setOf(setOf("v", "s"), setOf("v"))) return null
        if (!values.keyList().all { it in setOf("a", "b", "c") }) return null
        fun write(value: Any): String? = when (value) {
            is String -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
            is Int, is Long -> value.toString()
            else -> null
        }
        val inner = listOf("a", "b", "c").filter(values::has).map { "\"$it\":" + (write(values.get(it)) ?: return null) }
        val outer = listOf("\"v\":{" + inner.joinToString(",") + "}") +
            listOfNotNull(challenge.opt("s")?.let { "\"s\":" + (write(it) ?: return null) }) + "\"d\":\"$solved\""
        return "{" + outer.joinToString(",") + "}"
    }

    /** The page leaves base64's padding out; it is put back, as yt-dlp does. */
    private fun decode(value: String): ByteArray =
        Base64.getDecoder().decode(value.trimEnd('=').let { it + "=".repeat((4 - it.length % 4) % 4) })
}
