package com.lhacenmed.sona.core.downloader.sites.snapchat

import com.lhacenmed.sona.core.downloader.sites.CaptionFile
import com.lhacenmed.sona.core.downloader.sites.HttpsUrl
import com.lhacenmed.sona.core.downloader.sites.SiteHttp
import com.lhacenmed.sona.core.downloader.sites.SiteReader
import com.lhacenmed.sona.core.downloader.sites.VideoFormat
import com.lhacenmed.sona.core.downloader.sites.VideoPost
import com.lhacenmed.sona.core.downloader.sites.firstLine
import com.lhacenmed.sona.core.downloader.sites.isoDurationSeconds
import com.lhacenmed.sona.core.downloader.sites.text
import com.lhacenmed.sona.core.downloader.sites.videoInfoJson
import com.lhacenmed.sona.core.downloader.sites.wholeNumber
import okhttp3.HttpUrl
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

/** A Spotlight page runs to about half a megabyte. */
private const val MaxBytes = 2 * 1024 * 1024

/** A public Snapchat Spotlight link, `snapchat.com/spotlight/<id>` or `snapchat.com/@<user>/spotlight/<id>`. */
internal class SnapchatSpotlightIdentity private constructor(val id: String) {
    /** The one page read of it: www.snapchat.com, no query - which leads on to its creator's own. */
    val fetchUrl: String get() = "https://www.snapchat.com/spotlight/$id"

    companion object {
        private val hosts = setOf("snapchat.com", "www.snapchat.com")
        private val path = Regex("(?:/@[A-Za-z0-9._-]{1,32})?/spotlight/([A-Za-z0-9_-]{16,128})/?")

        fun parse(raw: String): SnapchatSpotlightIdentity? {
            val url = HttpsUrl.parse(raw) ?: return null
            if (url.host !in hosts) return null
            return path.matchEntire(url.encodedPath)?.let { SnapchatSpotlightIdentity(it.groupValues[1]) }
        }
    }
}

/**
 * Snapchat Spotlight, read from a post's logged-out page: its JSON-LD VideoObject - the one naming the post - gives its one
 * MP4 and, where Snapchat made them from its speech, its captions as WebVTT. yt-dlp's extractor reads the page's Next.js data,
 * which Snapchat no longer serves.
 */
internal object SnapchatReader : SiteReader {
    override fun accepts(url: String): Boolean = SnapchatSpotlightIdentity.parse(url) != null

    override fun read(url: String, http: SiteHttp): String? {
        val identity = SnapchatSpotlightIdentity.parse(url) ?: return null
        // Its creator's page is the same post: followed there, and nowhere else.
        val follow = { _: HttpUrl, to: HttpUrl -> to.takeIf { SnapchatSpotlightIdentity.parse(it.toString())?.id == identity.id } }
        val page = http.get(identity.fetchUrl, MaxBytes, SiteHttp.BrowserPage, follow) ?: return null
        return parse(page.text, identity.id, page.url.toString())
    }

    /** The video of the Spotlight [id] on [html] as info JSON - null where none of its VideoObjects names that very post. */
    fun parse(html: String, id: String, webpageUrl: String): String? = runCatching {
        val document = Jsoup.parse(html, "", Parser.htmlParser().setMaxDepth(64))
        val video = document.select("script[type=application/ld+json]")
            .mapNotNull { runCatching { JSONObject(it.data()) }.getOrNull() }
            .singleOrNull { it.opt("@type") == "VideoObject" && it.text("url")?.let(SnapchatSpotlightIdentity::parse)?.id == id } ?: return null
        val url = video.text("contentUrl")?.takeIf(::isOnSnapchat) ?: return null
        val caption = video.optJSONObject("caption")?.takeIf { it.text("encodingFormat") == "text/vtt" }?.text("contentUrl")?.takeIf(::isOnSnapchat)
        val creator = video.optJSONObject("creator")
        videoInfoJson(
            VideoPost(
                id = id,
                title = firstLine(video.text("name")) ?: firstLine(video.text("description")),
                uploader = creator?.text("name") ?: creator?.text("alternateName"),
                thumbnail = video.text("thumbnailUrl")?.takeIf(::isOnSnapchat),
                durationSeconds = video.text("duration")?.let(::isoDurationSeconds),
                webpageUrl = webpageUrl,
                extractorKey = "SnapchatSpotlight",
                // The file as served: what it is encoded in is not said.
                formats = listOf(
                    VideoFormat("mp4", url, video.opt("width").wholeNumber()?.toInt(), video.opt("height").wholeNumber()?.toInt(), "unknown", "unknown"),
                ),
                // Made from its speech, in the language spoken - which it does not name.
                captions = listOfNotNull(caption?.let { CaptionFile("und", it, isAutomatic = true, extension = "vtt") }),
            ),
        )
    }.getOrNull()

    private fun isOnSnapchat(raw: String): Boolean = HttpsUrl.parse(raw)?.host?.endsWith(".sc-cdn.net") == true
}
