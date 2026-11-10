package com.lhacenmed.sona.core.downloader.sites.reddit

import com.lhacenmed.sona.core.downloader.sites.HttpsUrl
import com.lhacenmed.sona.core.downloader.sites.SiteHttp
import com.lhacenmed.sona.core.downloader.sites.SiteReader
import com.lhacenmed.sona.core.downloader.sites.VideoFormat
import com.lhacenmed.sona.core.downloader.sites.VideoPost
import com.lhacenmed.sona.core.downloader.sites.firstLine
import com.lhacenmed.sona.core.downloader.sites.videoInfoJson
import com.lhacenmed.sona.core.downloader.sites.wholeNumber
import okhttp3.HttpUrl
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

/** A logged-out post page runs to about a megabyte. */
private const val MaxPageBytes = 3 * 1024 * 1024

/** A video's DASH manifest is a few kilobytes. */
private const val MaxManifestBytes = 256 * 1024

/** What a post page names of its video. */
internal class RedditVideo(val videoId: String, val title: String?, val author: String?, val thumbnail: String?, val durationSeconds: Long?)

/**
 * Reddit, read from a post's logged-out page - yt-dlp's reddit extractor needs Reddit's JSON API, which refuses logged-out
 * apps - and its video's DASH manifest on v.redd.it: each picture and each sound a file of its own, which yt-dlp merges. The
 * page's check is answered by fetching it again, at most twice. A link, photo, text or adult post is not read.
 */
internal object RedditReader : SiteReader {
    override fun accepts(url: String): Boolean = RedditPostIdentity.parse(url) != null

    override fun read(url: String, http: SiteHttp): String? {
        val identity = RedditPostIdentity.parse(url) ?: return null
        val page = page(identity, http) ?: return null
        val postId = RedditPostIdentity.postHop(page.url)?.postId ?: return null
        val video = parsePage(page.text, postId, identity.videoId) ?: return null
        val manifest = http.get("https://v.redd.it/${video.videoId}/DASHPlaylist.mpd", MaxManifestBytes, SiteHttp.File) ?: return null
        return infoJson(video, manifest.text, page.url.toString())
    }

    /** The post page [identity] leads to: its redirects kept on www pages of the one post, its check answered. */
    private fun page(identity: RedditPostIdentity, http: SiteHttp): SiteHttp.Page? {
        var post = identity.postId
        val follow = { _: HttpUrl, to: HttpUrl ->
            val hop = RedditPostIdentity.postHop(to)?.takeUnless { RedditPostIdentity.isLoginWall(to) }
            if (hop == null || (post != null && hop.postId != post)) null else {
                post = hop.postId
                HttpsUrl.parse(hop.fetchUrl)
            }
        }
        var page = http.get(identity.fetchUrl, MaxPageBytes, SiteHttp.BrowserPage, follow) ?: return null
        repeat(2) {
            val answer = RedditJsChallenge.answer(page.text, page.url) ?: return@repeat
            page = http.get(answer.toString(), MaxPageBytes, SiteHttp.BrowserPage, follow) ?: return null
        }
        val landed = RedditPostIdentity.postHop(page.url)?.postId ?: return null
        return page.takeIf { post == null || landed == post }
    }

    /** The video of the post [postId] on [html] - null for any other post, or a page that names none it can be watched by. */
    fun parsePage(html: String, postId: String, expectedVideoId: String?): RedditVideo? = runCatching {
        val document = Jsoup.parse(html, "", Parser.htmlParser().setMaxDepth(128))
        val post = document.select("shreddit-post").singleOrNull { it.attr("id") == "t3_$postId" } ?: return null
        if (post.attr("post-type") != "video") return null
        // An adult post asks a logged-out reader to sign in.
        if (post.attributes().any { "nsfw" in it.key.lowercase() }) return null
        val id = HttpsUrl.parse(post.attr("content-href"))?.takeIf { it.host == "v.redd.it" }?.pathSegments?.singleOrNull()
            ?.takeIf(VideoId::matches) ?: return null
        if (expectedVideoId != null && id != expectedVideoId) return null
        // A logged-out reader is given the player only for a post it may watch.
        val player = post.select("shreddit-player, shreddit-player-2").firstOrNull { player ->
            player.attr("post-id") == "t3_$postId" &&
                HttpsUrl.parse(player.attr("src"))?.let { it.host == "v.redd.it" && it.pathSegments.firstOrNull() == id } == true
        } ?: return null
        val thumbnail = player.attr("poster").takeIf { raw ->
            HttpsUrl.parse(raw)?.host?.let { it == "preview.redd.it" || it == "external-preview.redd.it" } == true
        }
        val duration = runCatching {
            JSONObject(player.attr("packaged-media-json").takeIf { it.length in 1..64 * 1024 } ?: "{}")
                .optJSONObject("playbackMp4s")?.opt("duration").wholeNumber()?.takeIf { it > 0 }
        }.getOrNull()
        RedditVideo(id, firstLine(post.attr("post-title")), post.attr("author").takeIf(String::isNotBlank), thumbnail, duration)
    }.getOrNull()

    /** [video] and its DASH manifest as info JSON: each representation's one file, a format of its own. */
    fun infoJson(video: RedditVideo, manifest: String, webpageUrl: String): String? {
        val document = runCatching { Jsoup.parse(manifest, "", Parser.xmlParser()) }.getOrNull() ?: return null
        val base = HttpsUrl.parse("https://v.redd.it/${video.videoId}/") ?: return null
        val formats = document.getAllElements().filter { it.tagName().equals("Representation", ignoreCase = true) }.map { representation ->
            val set = representation.parent()
            fun attribute(name: String) = representation.attr(name).ifBlank { set?.attr(name).orEmpty() }
            val file = representation.children().firstOrNull { it.tagName().equals("BaseURL", ignoreCase = true) }?.text()?.trim() ?: return null
            val url = HttpsUrl.resolve(base, file)?.takeIf {
                it.host == "v.redd.it" && it.pathSegments.firstOrNull() == video.videoId && !it.encodedPath.contains("..")
            } ?: return null
            val mime = attribute("mimeType")
            val audio = mime.startsWith("audio/") || attribute("contentType") == "audio"
            if (!audio && !mime.startsWith("video/")) return null
            val name = url.pathSegments.last().substringBeforeLast('.').lowercase().replace('_', '-')
            VideoFormat(
                id = name,
                url = url.toString(),
                width = if (audio) null else attribute("width").toIntOrNull(),
                height = if (audio) null else attribute("height").toIntOrNull(),
                videoCodec = if (audio) "none" else attribute("codecs").ifBlank { "h264" },
                audioCodec = if (audio) attribute("codecs").ifBlank { "aac" } else "none",
                extension = if (audio) "m4a" else "mp4",
                bitrateKbps = attribute("bandwidth").toDoubleOrNull()?.div(1000),
            )
        }
        if (formats.none { it.videoCodec != "none" }) return null
        val root = document.getAllElements().firstOrNull { it.tagName().equals("MPD", ignoreCase = true) }
        return videoInfoJson(
            VideoPost(
                id = video.videoId,
                title = video.title,
                uploader = video.author,
                thumbnail = video.thumbnail,
                durationSeconds = video.durationSeconds?.toDouble() ?: root?.let(::duration),
                webpageUrl = webpageUrl,
                extractorKey = "Reddit",
                formats = formats,
            ),
        )
    }

    /** The manifest's `mediaPresentationDuration`, `PT1H2M3.5S`, in seconds. */
    private fun duration(root: Element): Double? {
        val match = IsoDuration.matchEntire(root.attr("mediaPresentationDuration")) ?: return null
        val (hours, minutes, seconds) = match.destructured
        return (hours.toDoubleOrNull() ?: 0.0) * 3600 + (minutes.toDoubleOrNull() ?: 0.0) * 60 + (seconds.toDoubleOrNull() ?: 0.0)
    }

    private val VideoId = Regex("[a-z0-9]{8,24}")
    private val IsoDuration = Regex("PT(?:([0-9]+)H)?(?:([0-9]+)M)?(?:([0-9.]+)S)?")
}

/**
 * Reddit's logged-out page check: a small page whose script doubles one hex string, `await(async e=>e+e)("<hex>")`, puts it
 * in a hidden GET form's `solution` field and sends the form to the same page. Anything but that very shape is not answered.
 */
internal object RedditJsChallenge {
    /** The check page is about 8 KB; a post page is never read as one. */
    private const val MaxPage = 32 * 1024
    private val doubled = Regex("""await\s*\(\s*async\s+(\w+)\s*=>\s*\1\s*\+\s*\1\s*\)\s*\(\s*"([0-9a-f]{8,256})"\s*\)""")
    private val fieldName = Regex("[a-z_]{1,32}")
    private val fieldValue = Regex("[A-Za-z0-9_-]{0,512}")

    /** The address that answers [html]'s check of [page] - null where it is no check, or one that cannot be answered. */
    fun answer(html: String, page: HttpUrl): HttpUrl? {
        if (html.length !in 1..MaxPage) return null
        return runCatching {
            val document = Jsoup.parse(html, "", Parser.htmlParser().setMaxDepth(32))
            val seed = document.select("script").mapNotNull { doubled.find(it.data())?.groupValues?.get(2) }.singleOrNull() ?: return null
            val form = document.select("form").singleOrNull() ?: return null
            if (!form.attr("method").equals("get", ignoreCase = true)) return null
            val action = HttpsUrl.resolve(page, form.attr("action")) ?: return null
            // The form goes back to the page asked for, never anywhere else.
            if (action.host != page.host || action.encodedPath != page.encodedPath || action.encodedQuery != null) return null
            val inputs = form.select("input")
            if (inputs.isEmpty() || inputs.size > 8) return null
            val names = inputs.map { it.attr("name") }
            if ("solution" !in names || "jsc_token" !in names || names.toSet().size != names.size) return null
            val builder = page.newBuilder().query(null)
            for (input in inputs) {
                val name = input.attr("name").takeIf(fieldName::matches) ?: return null
                val value = if (name == "solution") seed + seed else input.attr("value").takeIf(fieldValue::matches) ?: return null
                builder.addQueryParameter(name, value)
            }
            builder.build()
        }.getOrNull()
    }
}
