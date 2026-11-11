package com.lhacenmed.sona.core.downloader

import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.ContentAvailability
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream

/** The most videos of a playlist read here - past it, yt-dlp reads the rest. */
private const val MaxPlaylistEntries = 500

/** The largest answer YouTube is let give: a page is far smaller, and anything larger is not one. */
private const val MaxResponseBytes = 16L * 1024 * 1024

/** The browser NewPipe's own app tells YouTube it is, where a stream names no client of its own. */
private const val BrowserUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

/** How long YouTube's visitor data is used before it is asked for again - as a browser keeps it, across videos. */
private const val VisitorDataKeptMs = 30 * 60 * 1000L

/** How many videos lately read have their watch page's data kept, waiting on a download. */
private const val KeptNextAnswers = 8

/** A watch page's data with nothing in it - what a read is answered while the real one is fetched; NewPipe takes no shorter. */
private const val EmptyWatchPage = """{"responseContext":{},"contents":{},"engagementPanels":[],"frameworkUpdates":{}}"""

/**
 * YouTube, read in the app itself by NewPipe's extractor - Nezzel's fast path: no process started, no player script run where
 * a stream needs none - into yt-dlp's own info JSON, so the rest of the downloader cannot tell it from yt-dlp's.
 *
 * A video's read waits only on the answers that say what it is and how it is downloaded: the watch page's data - YouTube's
 * `next`, half a megabyte and the slowest of them - is fetched beside it, and added only for a download: see [complete].
 *
 * Only what it reads in full is answered - a video with its streams fetched in one piece each, a playlist's videos - and null
 * for anything else, which yt-dlp then reads. Each call blocks its thread.
 */
@Singleton
internal class NewPipeSource @Inject constructor() {
    private val network = YouTubeOnlyDownloader()

    private val installed by lazy {
        synchronized(NewPipe::class.java) { if (NewPipe.getDownloader() == null) NewPipe.init(network) }
    }

    fun accepts(url: String): Boolean = runCatching {
        ServiceList.YouTube.streamLHFactory.acceptUrl(url) || ServiceList.YouTube.playlistLHFactory.acceptUrl(url)
    }.getOrDefault(false)

    /**
     * Readies a read before a link is there: NewPipe started, YouTube's hosts connected to, and the version of YouTube's web
     * client its reads name - what NewPipe asks YouTube once a run - known.
     */
    fun warmUp() {
        installed
        network.connect()
        YoutubeParsingHelper.getClientVersion()
    }

    /** [url] as yt-dlp's info JSON - null where it is not read in full here. */
    fun read(url: String): String? = runCatching {
        installed
        when {
            ServiceList.YouTube.streamLHFactory.acceptUrl(url) -> network.deferringNext { videoJson(StreamInfo.getInfo(ServiceList.YouTube, url)) }
            ServiceList.YouTube.playlistLHFactory.acceptUrl(url) -> playlistJson(url)
            else -> null
        }
    }.getOrNull()

    /**
     * [json], a video's read, completed with what the watch page's data adds - its Most replayed graph, its chapters - as
     * yt-dlp's read of it has them: what it is downloaded with. Without the watch page's data, its chapters are its
     * description's.
     */
    fun complete(json: String): String {
        installed
        val read = JSONObject(json)
        val next = runCatching { network.next(read.getString("id")) }.getOrNull()?.let(::JSONObject)
        next?.let(YouTubeExtras::heatmapOf)?.let { read.put("heatmap", it) }
        YouTubeExtras.chaptersOf(next, read.optString("description"), read.optDouble("duration"))?.let { read.put("chapters", it) }
        return read.toString()
    }

    private fun videoJson(info: StreamInfo): String? {
        if (info.streamType != StreamType.VIDEO_STREAM || info.contentAvailability == ContentAvailability.UPCOMING) return null
        val formats = (info.videoOnlyStreams + info.videoStreams).mapNotNull(::videoFormat) + info.audioStreams.mapNotNull(::audioFormat)
        // Without both a picture and a sound in one piece each, yt-dlp's assembling is needed.
        if (formats.none { it.optString("acodec") == "none" } || formats.none { it.optString("vcodec") == "none" }) return null
        val ids = formats.map { it.getString("format_id") }
        if (ids.toSet().size != ids.size) return null
        val (made, automatic) = captionsOf(info.subtitles)
        return JSONObject()
            .put("_type", "video")
            .put("id", info.id)
            .put("title", info.name)
            .put("uploader", info.uploaderName)
            .put("duration", info.duration.takeIf { it > 0 })
            .put("thumbnail", info.thumbnails.largest())
            .put("description", info.description?.content)
            .put("webpage_url", info.url)
            .put("extractor_key", "Youtube")
            .put("formats", JSONArray(formats))
            .put("subtitles", made)
            .put("automatic_captions", automatic)
            // Known from the description now; the watch page's own, where it has them, as the read is completed - see [complete].
            .put("chapters", YouTubeExtras.chaptersOf(null, info.description?.content, info.duration.toDouble()))
            .toString()
    }

    /**
     * The video's captions as yt-dlp lists them - see [captionLanguagesOf]: those made by people by their language; those
     * made from the speech by theirs, and again as "-orig". Not their translations: YouTube refuses them at these addresses.
     */
    private fun captionsOf(tracks: List<SubtitlesStream>): Pair<JSONObject, JSONObject> {
        val made = JSONObject()
        val automatic = JSONObject()
        fun JSONObject.add(code: String, url: String) = put(code, JSONArray().put(JSONObject().put("ext", "vtt").put("url", url)))
        tracks.filter { it.isUrl }.forEach { track ->
            // Listed in one format; any is had by asking for it.
            val url = track.content.replace(Regex("&fmt=[^&]*"), "") + "&fmt=vtt"
            if (track.isAutoGenerated) {
                automatic.add("${track.languageTag}-orig", url)
                automatic.add(track.languageTag, url)
            } else {
                made.add(track.languageTag, url)
            }
        }
        return made to automatic
    }

    private fun playlistJson(url: String): String {
        val first = PlaylistInfo.getInfo(ServiceList.YouTube, url)
        val items = first.relatedItems.toMutableList()
        var page = first.nextPage
        while (page != null && items.size < MaxPlaylistEntries) {
            val more = PlaylistInfo.getMoreItems(ServiceList.YouTube, url, page)
            items += more.items
            page = more.nextPage
        }
        val entries = items.take(MaxPlaylistEntries).mapNotNull { item ->
            val id = runCatching { ServiceList.YouTube.streamLHFactory.getId(item.url) }.getOrNull() ?: return@mapNotNull null
            JSONObject()
                .put("id", id)
                .put("url", item.url)
                .put("title", item.name)
                .put("uploader", item.uploaderName)
                .put("duration", item.duration.takeIf { it > 0 })
                .put("thumbnail", item.thumbnails.largest())
        }
        return JSONObject()
            .put("_type", "playlist")
            .put("id", first.id)
            .put("title", first.name)
            .put("uploader", first.uploaderName)
            .put("thumbnail", first.thumbnails.largest())
            .put("webpage_url", url)
            .put("extractor_key", "YoutubeTab")
            .put("entries", JSONArray(entries))
            .toString()
    }

    private fun videoFormat(stream: VideoStream): JSONObject? = base(stream, stream.itag.toString())?.apply {
        val codecs = stream.codec?.split(',')?.map(String::trim).orEmpty()
        put("vcodec", codecs.firstOrNull() ?: "unknown")
        put("acodec", if (stream.isVideoOnly) "none" else codecs.getOrNull(1) ?: "unknown")
        if (stream.width > 0) put("width", stream.width)
        if (stream.height > 0) put("height", stream.height)
        if (stream.fps > 0) put("fps", stream.fps)
        stream.bitrate.takeIf { it > 0 }?.let { put("tbr", it / 1000.0) }
    }

    private fun audioFormat(stream: AudioStream): JSONObject? {
        // A dubbed or described track is the same itag as the original: kept apart by its track, or left out.
        val track = stream.audioTrackId?.takeIf { it.isNotBlank() }?.let { "-" + it.replace(Regex("[^a-zA-Z0-9_.-]"), "_") }.orEmpty()
        return base(stream, "${stream.itag}$track")?.apply {
            put("vcodec", "none")
            put("acodec", stream.codec ?: "unknown")
            (stream.bitrate.takeIf { it > 0 }?.div(1000.0) ?: stream.averageBitrate.takeIf { it > 0 }?.toDouble())?.let { put("abr", it) }
            // Marked as yt-dlp marks YouTube's: the sound recorded is what yt-dlp takes unless another is asked for, so a
            // dubbed one never stands in for it.
            stream.audioLocale?.toLanguageTag()?.takeIf { it != "und" }?.let { language ->
                val isDescriptive = stream.audioTrackType == AudioTrackType.DESCRIPTIVE
                put("language", if (isDescriptive) "$language-desc" else language)
                put("language_preference", when (stream.audioTrackType) {
                    AudioTrackType.ORIGINAL -> OriginalLanguagePreference
                    AudioTrackType.DESCRIPTIVE -> DescriptiveLanguagePreference
                    else -> DubbedLanguagePreference
                })
            }
            stream.audioTrackName?.let { put("format_note", it) }
        }
    }

    /** What every format of [stream] says - null for one not fetched in one piece over HTTPS. */
    private fun base(stream: Stream, id: String): JSONObject? {
        val url = stream.content.takeIf { stream.isUrl && stream.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP } ?: return null
        if (url.toHttpUrlOrNull()?.isHttps != true) return null
        val extension = stream.format?.suffix?.takeIf(String::isNotBlank) ?: return null
        return JSONObject()
            .put("format_id", id)
            .put("url", url)
            .put("protocol", "https")
            .put("ext", extension)
            .put("http_headers", JSONObject().put("User-Agent", userAgentFor(url)))
            // YouTube slows a stream read in one long request: yt-dlp, and the direct transfer, read it in pieces.
            .put("downloader_options", JSONObject().put("http_chunk_size", DirectTransfer.ChunkBytes))
            .apply {
                stream.itagItem?.contentLength?.takeIf { it > 0 }?.let { put("filesize", it) }
            }
    }

    private fun userAgentFor(url: String): String = when {
        YoutubeParsingHelper.isAndroidStreamingUrl(url) -> YoutubeParsingHelper.getAndroidUserAgent(null)
        YoutubeParsingHelper.isIosStreamingUrl(url) -> YoutubeParsingHelper.getIosUserAgent(null)
        else -> BrowserUserAgent
    }

    private fun List<Image>.largest(): String? = maxByOrNull { it.width.toLong() * it.height }?.url
}

/**
 * NewPipe's way out to the network: YouTube's own hosts alone, no cookies kept, every answer held to a size. What a read of a
 * video waits on is kept to what it needs: the visitor data each of its clients asks for first is asked once a while, not
 * once a read; and the watch page's data, asked for last, is fetched beside the read - see [deferringNext].
 */
private class YouTubeOnlyDownloader : Downloader() {
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    private class Kept(val response: Response, val at: Long)

    private val visitorData = mutableMapOf<String, Kept>()

    /** The watch page's data being fetched for each video lately read, by its id. */
    private val nextAnswers = object : LinkedHashMap<String, CompletableFuture<String?>>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CompletableFuture<String?>>?) = size > KeptNextAnswers
    }

    private val defersNext = ThreadLocal.withInitial { false }

    /** Runs [read], its watch page's data fetched beside it, answered to it as none: a video's read needs nothing of it. */
    fun <T> deferringNext(read: () -> T): T {
        defersNext.set(true)
        try {
            return read()
        } finally {
            defersNext.set(false)
        }
    }

    /**
     * The watch page's data of the video [id] - that fetched beside its read, or fetched now, asked for as NewPipe last asked
     * for another's. Null where YouTube gave none.
     */
    fun next(id: String): String? {
        synchronized(nextAnswers) { nextAnswers.remove(id) }?.let { return it.get() }
        val (address, body) = lastNext ?: return null
        val request = okhttp3.Request.Builder().url(address).header("User-Agent", BrowserUserAgent)
            .post(JSONObject(body).put("videoId", id).toString().toRequestBody("application/json".toMediaType()))
            .build()
        return send(request).takeIf { it.responseCode() == 200 }?.responseBody()
    }

    /** The address and body NewPipe last asked for a video's watch page data with. */
    @Volatile
    private var lastNext: Pair<HttpUrl, String>? = null

    /** Connects to YouTube's hosts, so the first read does not wait on it. */
    fun connect() {
        listOf("https://www.youtube.com/generate_204", "https://youtubei.googleapis.com/generate_204").forEach { address ->
            client.newCall(okhttp3.Request.Builder().url(address).head().build()).enqueue(
                object : Callback {
                    override fun onResponse(call: Call, response: okhttp3.Response) = response.close()
                    override fun onFailure(call: Call, e: IOException) = Unit
                },
            )
        }
    }

    override fun execute(request: Request): Response {
        val url = request.url().toHttpUrlOrNull() ?: throw IOException("Not a web address")
        val host = url.host
        if (!url.isHttps || !(host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtubei.googleapis.com" || host.endsWith(".googlevideo.com"))) {
            throw IOException("Not YouTube")
        }
        val builder = okhttp3.Request.Builder().url(url).header("User-Agent", BrowserUserAgent)
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }
        val body = request.dataToSend()
        builder.method(request.httpMethod(), body?.toRequestBody())
        val call = builder.build()
        when {
            url.encodedPath.endsWith("/youtubei/v1/visitor_id") -> {
                val key = "$url ${body?.decodeToString()}"
                synchronized(visitorData) { visitorData[key]?.takeIf { System.currentTimeMillis() - it.at < VisitorDataKeptMs } }?.let { return it.response }
                return send(call).also { if (it.responseCode() == 200) synchronized(visitorData) { visitorData[key] = Kept(it, System.currentTimeMillis()) } }
            }

            url.encodedPath.endsWith("/youtubei/v1/next") && defersNext.get() -> {
                val id = body?.let { runCatching { JSONObject(it.decodeToString()).optString("videoId") }.getOrNull() }
                if (!id.isNullOrEmpty()) {
                    lastNext = url to body.decodeToString()
                    val answer = CompletableFuture<String?>()
                    client.newCall(call).enqueue(
                        object : Callback {
                            override fun onResponse(call: Call, response: okhttp3.Response) {
                                answer.complete(response.use { it.body?.takeIf { _ -> it.isSuccessful }?.string() })
                            }

                            override fun onFailure(call: Call, e: IOException) {
                                answer.complete(null)
                            }
                        },
                    )
                    synchronized(nextAnswers) { nextAnswers[id] = answer }
                    return Response(200, "OK", emptyMap(), EmptyWatchPage, url.toString())
                }
                return send(call)
            }

            else -> return send(call)
        }
    }

    private fun send(request: okhttp3.Request): Response = client.newCall(request).execute().use { response ->
        val body = response.body ?: throw IOException("No answer")
        if (body.contentLength() > MaxResponseBytes) throw IOException("Answer too large")
        Response(response.code, response.message, response.headers.toMultimap(), body.string(), response.request.url.toString())
    }
}
