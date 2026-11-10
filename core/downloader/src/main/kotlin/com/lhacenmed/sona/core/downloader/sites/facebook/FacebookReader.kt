package com.lhacenmed.sona.core.downloader.sites.facebook

import com.lhacenmed.sona.core.downloader.sites.HttpsUrl
import com.lhacenmed.sona.core.downloader.sites.keyList
import com.lhacenmed.sona.core.downloader.sites.SiteHttp
import com.lhacenmed.sona.core.downloader.sites.SiteReader
import com.lhacenmed.sona.core.downloader.sites.VideoFormat
import com.lhacenmed.sona.core.downloader.sites.VideoPost
import com.lhacenmed.sona.core.downloader.sites.firstLine
import com.lhacenmed.sona.core.downloader.sites.text
import com.lhacenmed.sona.core.downloader.sites.videoInfoJson
import com.lhacenmed.sona.core.downloader.sites.wholeNumber
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

/** A logged-out post page runs to several megabytes. */
private const val MaxBytes = 10 * 1024 * 1024

/**
 * Facebook, read from a logged-out desktop page: its `data-sjs` Relay results name the video - the link's own, or the one
 * video that is a post's only attachment - and that Video node its two progressive MP4s, `browser_native_hd_url` and
 * `browser_native_sd_url`, each with its sound. A photo, an album, a story, or a post whose video cannot be bound to it is
 * not read. A share or fb.watch link is followed to its page, and a sign-in wall once to the page it names.
 */
internal object FacebookReader : SiteReader {
    override fun accepts(url: String): Boolean = FacebookPostIdentity.parse(url)?.takeIf { it.photoId == null } != null

    override fun read(url: String, http: SiteHttp): String? {
        val requested = FacebookPostIdentity.parse(url)?.takeIf { it.photoId == null } ?: return null
        var unwrapped = false
        val follow = follow@{ from: HttpUrl, to: HttpUrl ->
            if (FacebookPostIdentity.isStoryLink(to.toString())) return@follow null
            if (FacebookPostIdentity.isLoginWall(to)) {
                if (unwrapped) return@follow null
                unwrapped = true
                val target = to.queryParameterValues("next").singleOrNull()?.takeUnless(FacebookPostIdentity::isStoryLink)
                    ?.let(FacebookPostIdentity::parse)?.takeUnless { it.isShortlink } ?: return@follow null
                return@follow HttpsUrl.parse(target.fetchUrlOn(to.host))
            }
            val identity = FacebookPostIdentity.parse(to.toString()) ?: return@follow null
            if (identity.isShortlink) {
                // One regional hop may keep the same share link; the regional page must then resolve it.
                val previous = FacebookPostIdentity.parse(from.toString())
                if (from.host == "web.facebook.com" || to.host != "web.facebook.com" || previous == null ||
                    previous.fetchUrl.trimEnd('/') != identity.fetchUrl.trimEnd('/')) return@follow null
            }
            HttpsUrl.parse(identity.fetchUrlOn(to.host))
        }
        val page = http.get(requested.fetchUrl, MaxBytes, SiteHttp.BrowserPage, follow) ?: return null
        val landed = FacebookPostIdentity.parse(page.url.toString())?.takeUnless { it.isShortlink } ?: return null
        return parse(page.text, landed, requested, landed.fetchUrl)
    }

    /** The video [page] - reached from [requested] - names on [html], as info JSON. */
    fun parse(html: String, page: FacebookPostIdentity, requested: FacebookPostIdentity, webpageUrl: String): String? = runCatching {
        if (page.photoId != null) return null
        val document = Jsoup.parse(html, "", Parser.htmlParser().setMaxDepth(512))
        val scripts = document.select("script[data-sjs]").map { it.data() }
        // The page's route says "content isn't available", or asks to sign in, before any media is read.
        scripts.firstOrNull { it.contains("\"rootView\"") }?.let { text ->
            val route = routeName(JSONObject(text), 0)
            if (route == "CometErrorRoot.react" || route?.startsWith("CometLogin") == true) return null
        }
        val results = scripts.filter { it.contains("RelayPrefetchedStreamCache") }
            .mapNotNull { runCatching { JSONObject(it) }.getOrNull() }.flatMap { results(it, 0) }
        val postId = requested.postId ?: page.postId
        val stories = LinkedHashMap<String, JSONObject>()
        val conflicted = HashSet<String>()
        for (data in results) {
            val story = data.optJSONObject("node_v2") ?: data.optJSONObject("node") ?: continue
            if (story.opt("__typename") != "Story") continue
            val key = story.text("post_id") ?: story.text("id") ?: continue
            val existing = stories[key]
            when {
                existing == null || media(existing).isEmpty -> stories[key] = story
                media(story).isEmpty || media(story).signature == media(existing).signature -> Unit
                else -> conflicted += key
            }
        }
        // A known post id binds only the story naming it; an unknown one only a page's sole story.
        val boundKey = if (postId != null) stories.entries.singleOrNull { it.value.text("post_id") == postId }?.key else stories.keys.singleOrNull()
        val bound = boundKey?.takeUnless { it in conflicted }?.let { stories[it] }
        val boundMedia = bound?.let(::media)
        val videoId = page.videoId ?: requested.videoId ?: boundMedia?.let { media ->
            val videos = media.videos.distinct()
            when {
                requested.isVideoShare || page.isVideoShare -> videos.singleOrNull()
                (requested.isPostOrPhoto || page.isPostOrPhoto) && media.photos == 0 && media.others == 0 -> videos.singleOrNull()
                else -> null
            }
        } ?: return null
        val node = results.flatMap { videoNodes(it, videoId, 0) }.firstOrNull() ?: return null
        val fields = node.optJSONObject("videoDeliveryLegacyFields") ?: node
        val width = (node.opt("width") ?: node.opt("original_width")).wholeNumber()?.toInt()
        val height = (node.opt("height") ?: node.opt("original_height")).wholeNumber()?.toInt()
        // The node's size is its best file's: the high-definition one's, else the only one's.
        val hd = fields.text("browser_native_hd_url")?.takeIf(::isVideo)
        val sd = fields.text("browser_native_sd_url")?.takeIf(::isVideo)
        val format = when {
            hd != null -> VideoFormat("hd", hd, width, height, "h264", "aac")
            sd != null -> VideoFormat("sd", sd, width, height, "h264", "aac")
            else -> return null
        }
        val ogTitle = document.selectFirst("meta[property=og:title]")?.attr("content")
        videoInfoJson(
            VideoPost(
                id = videoId,
                title = bound?.let(::storyMessage) ?: firstLine(ogTitle),
                uploader = bound?.let(::storyActor) ?: node.optJSONObject("owner")?.text("name"),
                thumbnail = node.optJSONObject("preferred_thumbnail")?.optJSONObject("image")?.text("uri")?.takeIf { HttpsUrl.parse(it) != null },
                durationSeconds = node.opt("playable_duration_in_ms").wholeNumber()?.div(1000.0)
                    ?: (node.opt("length_in_second") as? Number)?.toDouble(),
                webpageUrl = webpageUrl,
                extractorKey = "Facebook",
                formats = listOf(format),
            ),
        )
    }.getOrNull()

    /** A story's attachments: its photos counted, one id per video attachment, and how many are anything else. */
    private class StoryMedia(val photos: Int, val videos: List<String>, val others: Int, val signature: String) {
        val isEmpty: Boolean get() = photos == 0 && videos.isEmpty() && others == 0
    }

    private fun media(story: JSONObject): StoryMedia {
        val content = story.optJSONObject("comet_sections")?.optJSONObject("content")?.optJSONObject("story")
        val attachments = story.optJSONArray("attachments")?.takeIf { it.length() > 0 } ?: content?.optJSONArray("attachments") ?: JSONArray()
        var photos = 0
        val videos = ArrayList<String>()
        var others = 0
        val signature = StringBuilder()
        for (index in 0 until minOf(attachments.length(), MaxCandidates)) {
            val entry = attachments.optJSONObject(index) ?: continue
            val styled = entry.optJSONObject("styles")?.optJSONObject("attachment")
            if ((styled?.optJSONObject("all_subattachments") ?: entry.optJSONObject("all_subattachments")) != null) {
                // An album is never one video.
                others++
                signature.append("album;")
                continue
            }
            val media = styled?.optJSONObject("media")
            if (media?.opt("__typename") == "Photo") {
                photos++
                signature.append("photo:").append(media.text("id")).append(';')
                continue
            }
            // Every Video node of the attachment must carry one same numeric id; anything else is something else.
            val ids = listOfNotNull(media, entry.optJSONObject("media")).filter { it.opt("__typename") == "Video" }.map { videoId(it) }
            if (ids.isNotEmpty() && ids.all { it != null } && ids.distinct().size == 1) {
                videos += ids.first()!!
                signature.append("video:").append(ids.first()).append(';')
            } else {
                others++
                signature.append("other;")
            }
        }
        if (attachments.length() > MaxCandidates) others++
        return StoryMedia(photos, videos, others, signature.toString())
    }

    private fun videoId(media: JSONObject): String? = media.text("id")?.takeIf { it.length <= 25 && it.all(Char::isDigit) }

    /** Every Video node with [videoId] anywhere in [value] that names its files. */
    private fun videoNodes(value: Any?, videoId: String, depth: Int): List<JSONObject> {
        if (depth > MaxDepth) return emptyList()
        return when (value) {
            is JSONObject -> {
                val fields = value.optJSONObject("videoDeliveryLegacyFields") ?: value
                val own = value.opt("id") == videoId && (fields.has("browser_native_hd_url") || fields.has("browser_native_sd_url"))
                listOfNotNull(value.takeIf { own }) + value.keyList().flatMap { videoNodes(value.opt(it), videoId, depth + 1) }
            }
            is JSONArray -> (0 until value.length()).flatMap { videoNodes(value.opt(it), videoId, depth + 1) }
            else -> emptyList()
        }
    }

    private fun storyMessage(story: JSONObject): String? = listOfNotNull(story, contentStory(story)).firstNotNullOfOrNull {
        firstLine(it.optJSONObject("message")?.text("text"))
    }

    private fun storyActor(story: JSONObject): String? = listOfNotNull(story, contentStory(story)).firstNotNullOfOrNull {
        it.optJSONArray("actors")?.optJSONObject(0)?.text("name")
    }

    private fun contentStory(story: JSONObject): JSONObject? =
        story.optJSONObject("comet_sections")?.optJSONObject("content")?.optJSONObject("story")

    /** Every `__bbox.result.data` object in one Relay block. */
    private fun results(value: Any?, depth: Int): List<JSONObject> {
        if (depth > MaxDepth) return emptyList()
        return when (value) {
            is JSONObject -> listOfNotNull(value.optJSONObject("__bbox")?.optJSONObject("result")?.optJSONObject("data")) +
                value.keyList().flatMap { results(value.opt(it), depth + 1) }
            is JSONArray -> (0 until value.length()).flatMap { results(value.opt(it), depth + 1) }
            else -> emptyList()
        }
    }

    /** The page route's `initialRouteInfo.route.rootView.resource.__dr`. */
    private fun routeName(value: Any?, depth: Int): String? {
        if (depth > MaxDepth) return null
        return when (value) {
            is JSONObject -> value.optJSONObject("initialRouteInfo")?.optJSONObject("route")?.optJSONObject("rootView")
                ?.optJSONObject("resource")?.text("__dr") ?: value.keyList().firstNotNullOfOrNull { routeName(value.opt(it), depth + 1) }
            is JSONArray -> (0 until value.length()).firstNotNullOfOrNull { routeName(value.opt(it), depth + 1) }
            else -> null
        }
    }

    private fun isVideo(raw: String): Boolean {
        val url = HttpsUrl.parse(raw) ?: return false
        return Regex("(?:video|scontent|instagram)[a-z0-9.-]*\\.fbcdn\\.net").matches(url.host) && !url.encodedPath.contains("..") &&
            Regex("/(?:o[0-9]+/)?v/[A-Za-z0-9_./%-]+\\.mp4").matches(url.encodedPath)
    }

    private const val MaxCandidates = 64
    private const val MaxDepth = 192
}
