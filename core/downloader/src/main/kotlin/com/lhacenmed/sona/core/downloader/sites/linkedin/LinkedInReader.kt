package com.lhacenmed.sona.core.downloader.sites.linkedin

import com.lhacenmed.sona.core.downloader.objects
import com.lhacenmed.sona.core.downloader.sites.HttpsUrl
import com.lhacenmed.sona.core.downloader.sites.SiteHttp
import com.lhacenmed.sona.core.downloader.sites.SiteReader
import com.lhacenmed.sona.core.downloader.sites.VideoFormat
import com.lhacenmed.sona.core.downloader.sites.VideoPost
import com.lhacenmed.sona.core.downloader.sites.firstLine
import com.lhacenmed.sona.core.downloader.sites.text
import com.lhacenmed.sona.core.downloader.sites.videoInfoJson
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

/** A public video post's page runs to about four megabytes. */
private const val MaxBytes = 6 * 1024 * 1024

/**
 * LinkedIn, read from a public post's page, as yt-dlp's linkedin extractor reads it: the post's one video's `data-sources`,
 * each quality an MP4 - or, on the newer layout with a video-js player, its JSON-LD VideoObject's `contentUrl`. The page must
 * say it is this video post - one shown post container, or a page whose canonical, og:url and VideoObject all name it - so a
 * reshared or commented video never stands in. A `lnkd.in/p/` link is followed to its post.
 */
internal object LinkedInReader : SiteReader {
    override fun accepts(url: String): Boolean = LinkedInPostIdentity.parse(url) != null

    override fun read(url: String, http: SiteHttp): String? {
        val entry = HttpsUrl.parse(url)?.let(LinkedInPostIdentity::normalizeEntry) ?: return null
        val follow = { from: HttpUrl, to: HttpUrl ->
            val next = LinkedInPostIdentity.hopTarget(to)
            next.takeIf { !LinkedInPostIdentity.isLoginWall(it) && LinkedInPostIdentity.allowsHop(from, to) }
        }
        val page = http.get(entry.toString(), MaxBytes, SiteHttp.MobilePage, follow) ?: return null
        if (!LinkedInPostIdentity.isPost(page.url)) return null
        val resolved = LinkedInPostIdentity.parse(page.url.toString())?.takeUnless { it.isShortlink } ?: return null
        return parse(page.text, resolved)
    }

    /** The video of the post [resolved] on [html] as info JSON - null where the page does not prove it is that video post. */
    fun parse(html: String, resolved: LinkedInPostIdentity): String? = runCatching {
        val document = Jsoup.parse(html, "", Parser.htmlParser().setMaxDepth(512))
        val nodes = jsonLdNodes(document)
        videoPost(document, resolved, nodes) ?: playerVideoPost(document, resolved, nodes)
    }.getOrNull()

    /**
     * The older layout: one shown post-marked element, naming this post by its activity - or by its share or ugcPost, its
     * featured activity repeating the same activity - holding no image list and one shown `video[data-sources]` directly in its
     * `feed-native-video-content`; any canonical names the same post, and so does a VideoObject.
     */
    private fun videoPost(document: Element, resolved: LinkedInPostIdentity, nodes: List<JSONObject>): String? {
        val container = document.select("[data-activity-urn], [data-attributed-urn], [data-post-url]").singleOrNull()?.takeIf(::shown) ?: return null
        val activity = LinkedInPostIdentity.activityUrn(container.attr("data-activity-urn")) ?: return null
        val featured = container.attr("data-featured-activity-urn").takeIf { container.hasAttr("data-featured-activity-urn") }
        if (featured != null && LinkedInPostIdentity.activityUrn(featured) != activity) return null
        val identity = when {
            resolved.matchesActivity(activity) -> resolved
            resolved.activityId == null && featured != null && resolved.matchesAttributed(container.attr("data-attributed-urn")) ->
                resolved.withBridgedActivity(activity)
            else -> return null
        }
        if (container.hasAttr("data-post-url") && LinkedInPostIdentity.parse(container.attr("data-post-url"))?.sameAs(identity) != true) return null
        if (container.select("ul.feed-images-content").isNotEmpty()) return null
        val video = container.select("video").singleOrNull() ?: return null
        val media = video.parent() ?: return null
        if (!video.hasAttr("data-sources") || media.parent() !== container || media.attr("data-test-id") != "feed-native-video-content" || !shown(video)) return null
        val canonicals = document.select("link[rel=canonical]").map { it.attr("href") }.distinct()
        if (canonicals.size > 1) return null
        val canonical = canonicals.singleOrNull()?.let { href -> LinkedInPostIdentity.parse(href)?.takeIf { it.sameAs(identity) } ?: return null }
        val videoObject = nodes.firstOrNull { node -> isVideoObject(node) && named(node)?.let { it.isNotEmpty() && it.all { id -> id.sameAs(identity) } } == true }
            ?: return null
        val sources = JSONArray(video.attr("data-sources")).objects()
        val formats = sources.mapNotNull { source ->
            val url = source.text("src")?.takeIf(::isVideo) ?: return@mapNotNull null
            if (source.text("type")?.let { it == "video/mp4" } == false) return@mapNotNull null
            format(url, (source.opt("data-bitrate") as? Number)?.toDouble()?.div(1000))
        }
        val slug = resolved.slug ?: canonical?.slug
        val webpageUrl = slug?.let { "https://www.linkedin.com/posts/$it/" } ?: "https://www.linkedin.com/feed/update/urn:li:activity:$activity/"
        return infoJson(activity, videoObject, video.attr("data-poster-url").ifBlank { null }, formats, webpageUrl)
    }

    /**
     * The newer layout: one og:type, `video.*`; one canonical naming a public post, any og:url the same post; one VideoObject
     * in its JSON-LD naming that post, and no posting; no image list. The canonical is the landing post, or - a share or ugcPost
     * landing the page makes its activity's - one sharing no id kind with it and carrying the same author and title words.
     */
    private fun playerVideoPost(document: Element, resolved: LinkedInPostIdentity, nodes: List<JSONObject>): String? {
        val types = document.select("meta[property=og:type]").map { it.attr("content").trim() }.distinct()
        if (types.singleOrNull()?.startsWith("video.", ignoreCase = true) != true) return null
        val canonical = document.select("link[rel=canonical]").map { it.attr("href") }.distinct().singleOrNull()
            ?.let(LinkedInPostIdentity::parse)?.takeUnless { it.isShortlink } ?: return null
        val ogUrls = document.select("meta[property=og:url]").map { it.attr("content") }.distinct()
        if (ogUrls.size > 1 || ogUrls.any { LinkedInPostIdentity.parse(it)?.sameAs(canonical) != true }) return null
        val bound = canonical.sameAs(resolved) || (resolved.sharesNoIdKind(canonical) && resolved.activityId == null &&
            canonical.activityId != null && resolved.vanity != null && resolved.vanity == canonical.vanity)
        if (!bound) return null
        if (document.select("ul.feed-images-content").isNotEmpty()) return null
        if (nodes.any { node -> types(node).any { it == "SocialMediaPosting" || it == "DiscussionForumPosting" } }) return null
        val video = nodes.filter(::isVideoObject).singleOrNull() ?: return null
        val ids = named(video) ?: return null
        if (ids.isEmpty() || ids.any { !it.sameAs(canonical) }) return null
        val url = video.text("contentUrl")?.takeIf(::isVideo) ?: return null
        return infoJson(canonical.activityId ?: canonical.slug ?: return null, video, null, listOf(format(url, null)), canonical.cleanUrl ?: return null)
    }

    private fun infoJson(id: String, video: JSONObject, poster: String?, formats: List<VideoFormat>, webpageUrl: String): String? {
        val author = video.opt("author") ?: video.opt("creator")
        val authorName = ((author as? JSONArray)?.optJSONObject(0) ?: author as? JSONObject)?.text("name")
        return videoInfoJson(
            VideoPost(
                id = id,
                title = firstLine(video.text("name")) ?: firstLine(video.text("description")),
                uploader = authorName,
                thumbnail = (video.text("thumbnailUrl") ?: poster)?.takeIf { HttpsUrl.parse(it) != null },
                durationSeconds = video.text("duration")?.let(::seconds),
                webpageUrl = webpageUrl,
                extractorKey = "LinkedIn",
                formats = formats,
            ),
        )
    }

    /** An MP4 on dms.licdn.com, its height read from its `mp4-<height>p` name, as yt-dlp reads it. */
    private fun format(url: String, kbps: Double?): VideoFormat {
        val height = Regex("mp4-([0-9]{3,4})p").find(url)?.groupValues?.get(1)?.toIntOrNull()
        return VideoFormat(id = height?.let { "mp4-${it}p" } ?: "mp4", url = url, width = null, height = height,
            videoCodec = "h264", audioCodec = "aac", bitrateKbps = kbps)
    }

    /** `PT1M51S`, in seconds. */
    private fun seconds(text: String): Double? {
        val match = Regex("PT(?:([0-9]+)H)?(?:([0-9]+)M)?(?:([0-9.]+)S)?").matchEntire(text) ?: return null
        val (hours, minutes, secs) = match.destructured
        return (hours.toDoubleOrNull() ?: 0.0) * 3600 + (minutes.toDoubleOrNull() ?: 0.0) * 60 + (secs.toDoubleOrNull() ?: 0.0)
    }

    /** Every top-level and `@graph` node of the page's JSON-LD; broken JSON-LD adds none. */
    private fun jsonLdNodes(document: Element): List<JSONObject> = document.select("script")
        .filter { it.attr("type").trim().equals("application/ld+json", ignoreCase = true) }
        .flatMap { script ->
            when (val root = runCatching { org.json.JSONTokener(script.data()).nextValue() }.getOrNull()) {
                is JSONArray -> root.objects()
                is JSONObject -> listOf(root) + (root.optJSONArray("@graph")?.objects().orEmpty())
                else -> emptyList()
            }
        }

    private fun types(node: JSONObject): List<String> = when (val type = node.opt("@type")) {
        is String -> listOf(type)
        is JSONArray -> (0 until type.length()).mapNotNull { type.opt(it) as? String }
        else -> emptyList()
    }

    private fun isVideoObject(node: JSONObject): Boolean = "VideoObject" in types(node)

    /** The posts a node names by `url` and `@id` - null where one of them is no post. */
    private fun named(node: JSONObject): List<LinkedInPostIdentity>? =
        listOfNotNull(node.text("url"), node.text("@id")).map { LinkedInPostIdentity.parse(it) ?: return null }

    /** Neither it nor what holds it is hidden: not a template, `hidden`, aria-hidden, or styled away. */
    private fun shown(element: Element): Boolean = (listOf(element) + element.parents()).none {
        it.tagName() in HiddenContainers || it.hasAttr("hidden") || it.attr("aria-hidden").trim().equals("true", true) ||
            HiddenStyle.containsMatchIn(it.attr("style"))
    }

    private fun isVideo(raw: String): Boolean = HttpsUrl.parse(raw)?.host == "dms.licdn.com"

    private val HiddenStyle = Regex("(?i)(?:display\\s*:\\s*none|visibility\\s*:\\s*(?:hidden|collapse))")
    private val HiddenContainers = setOf("template", "noscript", "script", "style", "textarea")
}
