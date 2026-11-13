package com.lhacenmed.sona.core.downloader.sites.bluesky

import com.lhacenmed.sona.core.downloader.objects
import com.lhacenmed.sona.core.downloader.sites.CaptionFile
import com.lhacenmed.sona.core.downloader.sites.HttpsUrl
import com.lhacenmed.sona.core.downloader.sites.SiteHttp
import com.lhacenmed.sona.core.downloader.sites.SiteReader
import com.lhacenmed.sona.core.downloader.sites.VideoFormat
import com.lhacenmed.sona.core.downloader.sites.VideoPost
import com.lhacenmed.sona.core.downloader.sites.firstLine
import com.lhacenmed.sona.core.downloader.sites.languageTagOf
import com.lhacenmed.sona.core.downloader.sites.text
import com.lhacenmed.sona.core.downloader.sites.videoInfoJson
import com.lhacenmed.sona.core.downloader.sites.wholeNumber
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject

/** Each of the API's answers is a few kilobytes. */
private const val MaxBytes = 1024 * 1024

/**
 * A public Bluesky post link, `bsky.app/profile/<handle or DID>/post/<rkey>`; its likes, reposts and quotes views are the
 * post itself. A DID is never looked up as a handle.
 */
internal class BlueskyPostIdentity private constructor(val actor: String, val rkey: String) {
    val did: String? get() = actor.takeIf(::isDid)
    val resolveUrl: String get() = "$Api/com.atproto.identity.resolveHandle".toHttpUrl().newBuilder().addQueryParameter("handle", actor).build().toString()

    fun threadUrl(did: String): String = "$Api/app.bsky.feed.getPostThread".toHttpUrl().newBuilder()
        .addQueryParameter("uri", atUri(did)).addQueryParameter("depth", "0").build().toString()

    fun atUri(did: String): String = "at://$did/app.bsky.feed.post/$rkey"

    companion object {
        private const val Api = "https://public.api.bsky.app/xrpc"
        private val path = Regex("/profile/([^/]+)/post/([A-Za-z0-9_-]{1,64})(?:/(?:likes|reposted-by|quotes))?/?")
        private const val HostName = "(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z](?:[a-z0-9-]{0,61}[a-z0-9])?"
        /** did:plc, or did:web on a plain lowercase host name with no port. */
        private val did = Regex("did:plc:[a-z2-7]{24}|did:web:$HostName")
        private val handle = Regex("(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?")

        fun isDid(value: String): Boolean = value.length <= 253 && did.matches(value)

        fun parse(raw: String): BlueskyPostIdentity? {
            val url = HttpsUrl.parse(raw) ?: return null
            if (url.host != "bsky.app") return null
            val match = path.matchEntire(url.encodedPath) ?: return null
            val actor = match.groupValues[1]
            if (!isDid(actor) && !(actor.length <= 253 && handle.matches(actor))) return null
            return BlueskyPostIdentity(actor, match.groupValues[2])
        }
    }
}

/**
 * Bluesky, read from its public API: the post's thread names its video's blob, and the author's DID document the server that
 * holds it, which serves it as uploaded - the file yt-dlp's bluesky extractor calls its source. Only a blob on Bluesky's own
 * servers is read; a self-hosted one, or a post with no video of its own, is not.
 */
internal object BlueskyReader : SiteReader {
    override fun accepts(url: String): Boolean = BlueskyPostIdentity.parse(url) != null

    override fun read(url: String, http: SiteHttp): String? {
        val identity = BlueskyPostIdentity.parse(url) ?: return null
        val did = identity.did ?: http.get(identity.resolveUrl, MaxBytes, SiteHttp.Json)?.let { page ->
            runCatching { JSONObject(page.text).text("did") }.getOrNull()?.takeIf(BlueskyPostIdentity::isDid)
        } ?: return null
        val thread = http.get(identity.threadUrl(did), MaxBytes, SiteHttp.Json) ?: return null
        val video = parseThread(thread.text, identity.atUri(did)) ?: return null
        val server = http.get("https://plc.directory/$did", MaxBytes, SiteHttp.Json)?.let { serverOf(it.text) } ?: return null
        return infoJson(video, server, did, identity.rkey, url)
    }

    /** What a thread names of its post's own video - and of its captions, each blob's CID by its language. */
    class Video(val cid: String, val mimeType: String, val size: Long?, val width: Int?, val height: Int?,
        val thumbnail: String?, val text: String?, val author: String?, val captions: Map<String, String> = emptyMap())

    /** The video of the post [atUri] in its thread's JSON - null where it has none of its own. */
    fun parseThread(json: String, atUri: String): Video? {
        val post = runCatching { JSONObject(json) }.getOrNull()?.optJSONObject("thread")
            ?.takeIf { it.opt("\$type") == "app.bsky.feed.defs#threadViewPost" }?.optJSONObject("post") ?: return null
        val did = atUri.removePrefix("at://").substringBefore('/')
        if (post.opt("uri") != atUri || post.optJSONObject("author")?.opt("did") != did) return null
        var view = post.optJSONObject("embed") ?: return null
        var record = post.optJSONObject("record")?.optJSONObject("embed") ?: return null
        // A quote with a video of its own: the video is the post's, the quoted post is not.
        if (view.opt("\$type") == "app.bsky.embed.recordWithMedia#view") {
            view = view.optJSONObject("media") ?: return null
            record = record.optJSONObject("media") ?: return null
        }
        if (view.opt("\$type") != "app.bsky.embed.video#view") return null
        val blob = record.optJSONObject("video")?.takeIf { it.opt("\$type") == "blob" } ?: return null
        val cid = blob.optJSONObject("ref")?.text("\$link")?.takeIf(Cid::matches) ?: return null
        // The size the app recorded of the video on upload: its own.
        val ratio = view.optJSONObject("aspectRatio") ?: record.optJSONObject("aspectRatio")
        val author = post.optJSONObject("author")
        return Video(
            cid = cid,
            mimeType = blob.text("mimeType") ?: return null,
            size = blob.opt("size").wholeNumber(),
            width = ratio?.opt("width").wholeNumber()?.toInt(),
            height = ratio?.opt("height").wholeNumber()?.toInt(),
            thumbnail = view.text("thumbnail")?.takeIf { HttpsUrl.parse(it)?.host == "video.bsky.app" },
            text = post.optJSONObject("record")?.text("text"),
            author = author?.text("displayName") ?: author?.text("handle"),
            captions = captionsOf(record),
        )
    }

    /** The captions uploaded with a video's [record], as yt-dlp's bluesky extractor reads them: each WebVTT blob by its language. */
    private fun captionsOf(record: JSONObject): Map<String, String> = record.optJSONArray("captions")?.objects().orEmpty().mapNotNull { caption ->
        val cid = caption.optJSONObject("file")?.optJSONObject("ref")?.text("\$link")?.takeIf(Cid::matches) ?: return@mapNotNull null
        languageTagOf(caption.text("lang")) to cid
    }.toMap()

    /** The DID document's own data server, where it is one of Bluesky's - null for any other. */
    fun serverOf(json: String): String? {
        val services = runCatching { JSONObject(json) }.getOrNull()?.optJSONArray("service")?.objects().orEmpty()
        val endpoint = services.singleOrNull { it.opt("id") == "#atproto_pds" }?.text("serviceEndpoint") ?: return null
        val url = HttpsUrl.parse(endpoint)?.takeIf { it.host.endsWith(".host.bsky.network") && it.encodedPath == "/" } ?: return null
        return "https://${url.host}"
    }

    fun infoJson(video: Video, server: String, did: String, id: String, webpageUrl: String): String? {
        val extension = when (video.mimeType) {
            "video/mp4" -> "mp4"
            "video/quicktime" -> "mov"
            "video/webm" -> "webm"
            else -> return null
        }
        fun blobUrl(cid: String) = "$server/xrpc/com.atproto.sync.getBlob".toHttpUrl().newBuilder()
            .addQueryParameter("did", did).addQueryParameter("cid", cid).build().toString()
        return videoInfoJson(
            VideoPost(
                id = id,
                title = firstLine(video.text) ?: video.author?.let { "Video by $it" },
                uploader = video.author,
                thumbnail = video.thumbnail,
                durationSeconds = null,
                webpageUrl = webpageUrl,
                extractorKey = "Bluesky",
                // The file as uploaded: what it is encoded in is not said.
                formats = listOf(VideoFormat("source", blobUrl(video.cid), video.width, video.height, "unknown", "unknown", extension, sizeBytes = video.size)),
                captions = video.captions.map { (language, cid) -> CaptionFile(language, blobUrl(cid), isAutomatic = false, extension = "vtt") },
            ),
        )
    }

    private val Cid = Regex("[a-z2-7]{20,128}")
}
