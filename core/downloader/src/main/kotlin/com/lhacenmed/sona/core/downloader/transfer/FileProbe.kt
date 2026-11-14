package com.lhacenmed.sona.core.downloader.transfer

import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** How many bytes a probe asks for: enough to know what a file is by how it begins. */
private const val ProbeBytes = 256L

/** What a file's server may say it is while saying nothing of what it holds. */
private val GenericTypes = setOf(
    "application/octet-stream",
    "binary/octet-stream",
    "application/download",
    "application/x-download",
    "application/force-download",
)

/** What a page is served as. */
private val PageTypes = setOf("text/html", "application/xhtml+xml")

/**
 * What every request for a file is made with - AB Download Manager's: a desktop browser, as file hosts serve best, and the
 * bytes as they are, never compressed, so their length is the file's.
 */
internal val FileRequestHeaders = mapOf(
    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept-Encoding" to "identity",
)

/** What a link serves, told by its first bytes alone - see [probe]. */
internal data class ServedFile(
    val name: String?,
    /** The size of the whole file - null where its server does not say. */
    val totalBytes: Long?,
    /** Whether it is served in ranges, of a known size: fetched in parts at once, and carried on where it stopped. */
    val isResumable: Boolean,
    /** What tells this file from a changed one, as `If-Range` asks it - see [validatorOf]. */
    val validator: String?,
    /** Whether it is what yt-dlp reads rather than a file to keep: a page, a feed, the playlist of a stream. */
    val isForDownloader: Boolean,
)

/** A `Content-Range` - `bytes 0-255/1000` - read: where the bytes sent begin, and the size of the whole, each null where unsaid. */
internal data class ContentRange(val start: Long?, val total: Long?)

/**
 * What [url] serves, asked as AB Download Manager asks: for its first bytes alone, with a GET - a HEAD is answered unreliably -
 * which tells its size, its name and kind, and whether it is served in ranges. A server that will not send so few bytes, or
 * fails on being asked to, is asked for the whole, read no further than its first bytes. Throws where the link fails.
 */
internal fun OkHttpClient.probe(url: String, headers: Map<String, String>): ServedFile {
    runCatching { probeOnce(url, headers, ranged = true) }.getOrNull()
        ?.takeIf { (code, file) -> code in 200..299 && file.totalBytes != ProbeBytes }
        ?.let { return it.second }
    val (code, file) = probeOnce(url, headers, ranged = false)
    if (code !in 200..299) throw IOException("HTTP Error $code")
    return file
}

private fun OkHttpClient.probeOnce(url: String, headers: Map<String, String>, ranged: Boolean): Pair<Int, ServedFile> {
    val request = Request.Builder().url(url)
        .apply {
            headers.forEach { (name, value) -> header(name, value) }
            if (ranged) header("Range", "bytes=0-${ProbeBytes - 1}")
        }
        .build()
    return newCall(request).execute().use { response ->
        val length = response.header("Content-Length")?.toLongOrNull()?.takeIf { it >= 0 }
        val range = contentRangeOf(response.header("Content-Range"))
        val isPartial = response.code == 206
        val declaredType = response.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val first = response.body?.source()?.let { source ->
            source.request(ProbeBytes)
            source.buffer.copy().readByteArray(minOf(source.buffer.size, ProbeBytes))
        } ?: ByteArray(0)
        val type = declaredType?.takeUnless { it in GenericTypes } ?: mediaTypeOf(first)
        response.code to ServedFile(
            name = fileNameOf(response.request.url.toString(), response.header("Content-Disposition"), type),
            totalBytes = length?.let { if (isPartial) range?.total ?: it else it },
            isResumable = isPartial && length != null && range?.total != null,
            validator = validatorOf(response),
            isForDownloader = declaredType in PageTypes || beginsMarkupOrPlaylist(first),
        )
    }
}

/** A `Content-Range` header read as AB Download Manager reads it - with or without its `bytes ` - null where it is no range. */
internal fun contentRangeOf(header: String?): ContentRange? {
    val (range, size) = header?.removePrefix("bytes ")?.takeIf { it.isNotBlank() }?.split('/')?.takeIf { it.size >= 2 } ?: return null
    val start = if (range == "*") null else range.substringBefore('-').toLongOrNull() ?: return null
    val total = if (size == "*") null else size.toLongOrNull() ?: return null
    return ContentRange(start, total)
}

/** What tells this file from a changed one on its site, as `If-Range` asks it: a strong ETag, else when it last changed. */
internal fun validatorOf(response: Response): String? =
    response.header("ETag")?.takeUnless { it.startsWith("W/") } ?: response.header("Last-Modified")

/**
 * Whether [bytes] begin a page, a feed or a manifest - markup - or an HLS playlist: what a stream is published as, whatever
 * its server calls it, and yt-dlp downloads the stream of.
 */
internal fun beginsMarkupOrPlaylist(bytes: ByteArray): Boolean {
    val text = String(bytes, Charsets.ISO_8859_1).removePrefix("\u00EF\u00BB\u00BF").trimStart()
    return text.startsWith("<") || text.startsWith("#EXTM3U")
}

/**
 * The kind of media file [bytes] begin - an MP4 or M4A, Matroska or WebM, Ogg, FLAC, MP3, WAV - by the mark each begins with:
 * what a file its server calls only a stream of bytes is. Null for any other.
 */
internal fun mediaTypeOf(bytes: ByteArray): String? {
    fun at(offset: Int, mark: String) = bytes.size >= offset + mark.length && String(bytes, offset, mark.length, Charsets.ISO_8859_1) == mark
    return when {
        at(4, "ftypM4A") -> "audio/mp4"
        at(4, "ftyp") -> "video/mp4"
        at(0, "\u001AEß£") -> if ("webm" in String(bytes, Charsets.ISO_8859_1)) "video/webm" else "video/x-matroska"
        at(0, "OggS") -> "audio/ogg"
        at(0, "fLaC") -> "audio/flac"
        at(0, "RIFF") && at(8, "WAVE") -> "audio/x-wav"
        at(0, "ID3") || (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1].toInt() and 0xE0 == 0xE0) -> "audio/mpeg"
        else -> null
    }
}
