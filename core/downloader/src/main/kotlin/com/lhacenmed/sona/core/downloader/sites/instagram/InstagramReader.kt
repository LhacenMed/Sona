package com.lhacenmed.sona.core.downloader.sites.instagram

import com.lhacenmed.sona.core.downloader.objects
import com.lhacenmed.sona.core.downloader.sites.HttpsUrl
import com.lhacenmed.sona.core.downloader.sites.keyList
import com.lhacenmed.sona.core.downloader.sites.ShortLinks
import com.lhacenmed.sona.core.downloader.sites.SiteHttp
import com.lhacenmed.sona.core.downloader.sites.SiteReader
import com.lhacenmed.sona.core.downloader.sites.VideoFormat
import com.lhacenmed.sona.core.downloader.sites.VideoPost
import com.lhacenmed.sona.core.downloader.sites.firstLine
import com.lhacenmed.sona.core.downloader.sites.text
import com.lhacenmed.sona.core.downloader.sites.videoInfoJson
import com.lhacenmed.sona.core.downloader.sites.wholeNumber
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

/** A logged-out post page runs to a megabyte or two. */
private const val MaxBytes = 3 * 1024 * 1024

/**
 * A public Instagram post link: `instagram.com/p/<code>/`, `/reel/<code>/` (or `/reels/`), `/tv/<code>/`, with or without a
 * user before it; its query is left out. A `/share/…` link carries a share id, not a shortcode, so it - like any other of
 * Instagram's own first segments - is never read as a user.
 */
internal class InstagramPostIdentity private constructor(val code: String) {
    /** The one page read of it, a reel's as a post's: canonical, no query. */
    val fetchUrl: String get() = "https://www.instagram.com/p/$code/"

    companion object {
        private val hosts = setOf("instagram.com", "www.instagram.com", "m.instagram.com", "instagr.am", "www.instagr.am")
        private const val Code = "[A-Za-z0-9_-]{5,64}"
        private const val Reserved = "(?i:share|stories|explore|accounts|direct|about|legal|developer|p|reels?|tv)/"
        private val path = Regex("/(?:(?!$Reserved)[A-Za-z0-9_.]{1,30}/)?(?:p|reels?|tv)/($Code)/?")

        fun parse(url: String): InstagramPostIdentity? {
            val parsed = HttpsUrl.parse(url) ?: return null
            if (parsed.host.lowercase() !in hosts) return null
            return path.matchEntire(parsed.encodedPath)?.let { InstagramPostIdentity(it.groupValues[1]) }
        }
    }
}

/**
 * Instagram, read from a post's logged-out page: its `data-sjs` Relay results name the post as `xig_polaris_media`, and a
 * video's progressive MP4s as its `video_versions`. Only a post that is one video, not gated for logged-out readers, is read:
 * a photo or a carousel is not. A `/share/` link is followed to its post first.
 */
internal object InstagramReader : SiteReader {
    override fun accepts(url: String): Boolean =
        InstagramPostIdentity.parse(url) != null || HttpsUrl.parse(url)?.let { ShortLinks.isInstagramShare(it) } == true

    override fun read(url: String, http: SiteHttp): String? {
        val share = HttpsUrl.parse(url)?.takeIf { ShortLinks.isInstagramShare(it) }
        val target = if (share != null) http.landing(url, ShortLinks::isShortLink)?.toString() ?: return null else url
        val identity = InstagramPostIdentity.parse(target) ?: return null
        // No redirect is followed: a logged-out reader is sent to sign in.
        val page = http.get(identity.fetchUrl, MaxBytes, SiteHttp.BrowserPage) ?: return null
        return parse(page.text, identity.code, identity.fetchUrl)
    }

    /** The video of the post [code] on [html] as info JSON - null for anything but one video, or for copies that disagree. */
    fun parse(html: String, code: String, webpageUrl: String): String? {
        val found = runCatching {
            val document = Jsoup.parse(html, "", Parser.htmlParser().setMaxDepth(512))
            document.select("script[type=application/json][data-sjs]").map { it.data() }
                .filter { it.contains("RelayPrefetchedStreamCache") && it.contains(Polaris) }
                .flatMap { text -> results(JSONObject(text), 0) }
                .mapNotNull { it.optJSONObject(Polaris)?.takeIf { polaris -> polaris.opt("code") == code } }
        }.getOrNull().orEmpty()
        val polaris = found.firstOrNull() ?: return null
        // Copies of it repeated across the page must agree.
        if (found.any { it.toString() != polaris.toString() }) return null
        // Gated for logged-out readers - age, region, a private account: not public.
        if (polaris.has("gating_ruling") && !polaris.isNull("gating_ruling")) return null
        val media = polaris.optJSONObject("if_not_gated_logged_out")?.takeIf { it.opt("code") == code } ?: return null
        if (media.opt("media_type").wholeNumber() != 2L) return null
        val versions = media.optJSONArray("video_versions")?.objects().orEmpty().mapNotNull { version ->
            version.text("url")?.takeIf(::isVideo)?.let { version to it }
        }.distinctBy { it.second }
        val formats = versions.mapIndexed { index, (version, url) ->
            VideoFormat(
                id = "v$index",
                url = url,
                width = version.opt("width").wholeNumber()?.toInt(),
                height = version.opt("height").wholeNumber()?.toInt(),
                videoCodec = "h264",
                audioCodec = if (media.opt("has_audio") == false) "none" else "aac",
            )
        }
        val user = media.optJSONObject("user")
        val username = user?.text("username")
        return videoInfoJson(
            VideoPost(
                id = code,
                title = firstLine(media.optJSONObject("caption")?.text("text")) ?: username?.let { "Video by $it" },
                uploader = user?.text("full_name") ?: username,
                thumbnail = media.optJSONObject("image_versions2")?.optJSONArray("candidates")?.objects()?.firstOrNull()?.text("url")
                    ?.takeIf { HttpsUrl.parse(it) != null },
                durationSeconds = media.opt("video_duration")?.let { (it as? Number)?.toDouble() ?: (it as? String)?.toDoubleOrNull() },
                webpageUrl = webpageUrl,
                extractorKey = "Instagram",
                formats = formats,
            ),
        )
    }

    /** Every `__bbox.result.data` object nested anywhere in [value]. */
    private fun results(value: Any?, depth: Int): List<JSONObject> {
        if (depth > MaxDepth) return emptyList()
        return when (value) {
            is JSONObject -> listOfNotNull(value.optJSONObject("__bbox")?.optJSONObject("result")?.optJSONObject("data")) +
                value.keyList().flatMap { results(value.opt(it), depth + 1) }
            is JSONArray -> (0 until value.length()).flatMap { results(value.opt(it), depth + 1) }
            else -> emptyList()
        }
    }

    private fun isVideo(raw: String): Boolean {
        val url = HttpsUrl.parse(raw) ?: return false
        val host = url.host
        val cdn = Regex("(?:video|scontent|instagram)[a-z0-9.-]*\\.fbcdn\\.net").matches(host) ||
            Regex("scontent[a-z0-9.-]*\\.cdninstagram\\.com").matches(host)
        return cdn && !url.encodedPath.contains("..") && Regex("/(?:o[0-9]+/)?v/[A-Za-z0-9_./%-]+\\.mp4").matches(url.encodedPath)
    }

    private const val Polaris = "xig_polaris_media"
    private const val MaxDepth = 192
}
