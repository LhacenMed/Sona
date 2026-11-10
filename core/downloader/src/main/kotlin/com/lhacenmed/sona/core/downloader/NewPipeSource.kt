package com.lhacenmed.sona.core.downloader

import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
import org.schabi.newpipe.extractor.stream.ContentAvailability
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

/** The most videos of a playlist read here - past it, yt-dlp reads the rest. */
private const val MaxPlaylistEntries = 500

/** The largest answer YouTube is let give: a page is far smaller, and anything larger is not one. */
private const val MaxResponseBytes = 16L * 1024 * 1024

/** The browser NewPipe's own app tells YouTube it is, where a stream names no client of its own. */
private const val BrowserUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

/**
 * YouTube, read in the app itself by NewPipe's extractor - Nezzel's fast path: no process started, no player script run where
 * a stream needs none - into yt-dlp's own info JSON, so the rest of the downloader cannot tell it from yt-dlp's.
 *
 * Only what it reads in full is answered - a video with its streams fetched in one piece each, a playlist's videos - and null
 * for anything else, which yt-dlp then reads. Each call blocks its thread.
 */
@Singleton
internal class NewPipeSource @Inject constructor() {

    private val installed by lazy {
        synchronized(NewPipe::class.java) { if (NewPipe.getDownloader() == null) NewPipe.init(YouTubeOnlyDownloader()) }
    }

    fun accepts(url: String): Boolean = runCatching {
        ServiceList.YouTube.streamLHFactory.acceptUrl(url) || ServiceList.YouTube.playlistLHFactory.acceptUrl(url)
    }.getOrDefault(false)

    /** [url] as yt-dlp's info JSON - null where it is not read in full here. */
    fun read(url: String): String? = runCatching {
        installed
        when {
            ServiceList.YouTube.streamLHFactory.acceptUrl(url) -> videoJson(StreamInfo.getInfo(ServiceList.YouTube, url))
            ServiceList.YouTube.playlistLHFactory.acceptUrl(url) -> playlistJson(url)
            else -> null
        }
    }.getOrNull()

    private fun videoJson(info: StreamInfo): String? {
        if (info.streamType != StreamType.VIDEO_STREAM || info.contentAvailability == ContentAvailability.UPCOMING) return null
        val formats = (info.videoOnlyStreams + info.videoStreams).mapNotNull(::videoFormat) + info.audioStreams.mapNotNull(::audioFormat)
        // Without both a picture and a sound in one piece each, yt-dlp's assembling is needed.
        if (formats.none { it.optString("acodec") == "none" } || formats.none { it.optString("vcodec") == "none" }) return null
        val ids = formats.map { it.getString("format_id") }
        if (ids.toSet().size != ids.size) return null
        return JSONObject()
            .put("_type", "video")
            .put("id", info.id)
            .put("title", info.name)
            .put("uploader", info.uploaderName)
            .put("duration", info.duration.takeIf { it > 0 })
            .put("thumbnail", info.thumbnails.largest())
            .put("webpage_url", info.url)
            .put("extractor_key", "Youtube")
            .put("formats", JSONArray(formats))
            .put("chapters", info.streamSegments.takeIf { it.isNotEmpty() }?.let { segments ->
                JSONArray(segments.mapIndexed { index, segment ->
                    JSONObject()
                        .put("start_time", segment.startTimeSeconds)
                        .put("end_time", segments.getOrNull(index + 1)?.startTimeSeconds ?: info.duration)
                        .put("title", segment.title)
                })
            })
            .toString()
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
            stream.audioLocale?.toLanguageTag()?.takeIf { it != "und" }?.let { put("language", it) }
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

/** NewPipe's way out to the network: YouTube's own hosts alone, no cookies kept, every answer held to a size. */
private class YouTubeOnlyDownloader : Downloader() {
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

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
        builder.method(request.httpMethod(), request.dataToSend()?.toRequestBody())
        client.newCall(builder.build()).execute().use { response ->
            val body = response.body ?: throw IOException("No answer")
            if (body.contentLength() > MaxResponseBytes) throw IOException("Answer too large")
            return Response(response.code, response.message, response.headers.toMultimap(), body.string(), response.request.url.toString())
        }
    }
}
