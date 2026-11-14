package com.lhacenmed.sona.core.downloader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import com.lhacenmed.sona.core.downloader.transfer.ConnectionsPerDownload
import com.lhacenmed.sona.core.downloader.transfer.ContentRange
import com.lhacenmed.sona.core.downloader.transfer.FilePart
import com.lhacenmed.sona.core.downloader.transfer.FileParts
import com.lhacenmed.sona.core.downloader.transfer.MinPartBytes
import com.lhacenmed.sona.core.downloader.transfer.contentRangeOf
import com.lhacenmed.sona.core.downloader.transfer.probe
import com.lhacenmed.sona.core.downloader.transfer.rateThrottler
import com.lhacenmed.sona.core.downloader.transfer.splitToRange
import com.lhacenmed.sona.core.downloader.transfer.transferClient
import com.lhacenmed.sona.core.model.AudioFormat
import com.lhacenmed.sona.core.model.DownloadChoices
import com.lhacenmed.sona.core.model.DownloadKind
import com.lhacenmed.sona.core.model.DownloadOptions
import com.lhacenmed.sona.core.model.DownloaderPreferences
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Throttler
import okio.buffer
import org.json.JSONArray
import org.json.JSONObject

/** How often a direct download says how far it is - and records it, to carry on from. */
private const val ReportEveryMs = 500L

/** The longest side of a cover written into a file. */
private const val CoverSize = 1200

/** A file served differently than it was as its download began: changed, or no longer served in ranges. */
private class FileChangedException : IOException("The file changed on its site")

/**
 * A song, a video or any file downloaded straight from where it is served - no Python, no FFmpeg - a song or a video tagged by
 * the app itself: what a download whose choices need nothing assembled or converted takes, on a device with or without the
 * downloader. Asked for in pieces of no more than [ChunkBytes], as YouTube serves a long read slowly, and carried on from
 * where it stopped: a song or a video in order, so it plays as it downloads; any other file as AB Download Manager fetches
 * one - in parts, [ConnectionsPerDownload] at once. Through the downloader's proxy, held to its rate limit - see
 * `DownloaderPreferences`.
 */
@Singleton
class DirectTransfer @Inject constructor() {
    private val client = OkHttpClient()

    /**
     * The format [info] is downloaded as directly with [choices] and [options] - null where they ask for what only yt-dlp
     * does: a merge of picture and sound, a conversion, captions, a cut, a command. A file is always downloaded directly.
     */
    fun formatFor(info: MediaInfo, choices: DownloadChoices, options: DownloadOptions): MediaFormat? {
        if (choices.kind == DownloadKind.FILE) return info.formats.singleOrNull()
        val needsNothingElse = !choices.skipSponsors && options.clip == null && !options.splitChapters && options.commandArguments == null && !info.isPlaylist
        if (!needsNothingElse) return null
        return when (choices.kind) {
            DownloadKind.AUDIO -> {
                // Kept as published, in a container TagLib writes tags and a cover in; M4A asked for, M4A alone.
                val containers = when (choices.audioFormat) {
                    AudioFormat.ORIGINAL -> setOf("m4a", "mp3", "flac", "ogg", "opus")
                    AudioFormat.M4A -> setOf("m4a")
                    else -> return null
                }
                // In the language asked for, where the video has more than one sound - else the one recorded.
                val language = info.audioLanguageFor(choices.audioLanguage)
                val candidates = info.formats.filter {
                    !it.hasVideo && it.hasAudio && it.directUrl != null && it.extension in containers && (language == null || it.audioTrack?.language == language)
                }
                options.formatId?.let { id -> candidates.firstOrNull { it.id == id } } ?: candidates.maxByOrNull { it.bitrateKbps ?: 0 }
            }

            DownloadKind.VIDEO -> {
                // Captions are embedded by yt-dlp - and made, picked to be transcribed or translated, before it.
                if (info.pickedCaptions(choices, options, Locale.getDefault().language, transcribes = false).isNotEmpty()) return null
                val tallest = choices.videoQuality.height ?: Int.MAX_VALUE
                val pictures = info.formats.filter { it.hasVideo && (it.height ?: 0) <= tallest }
                // One file with its sound, as a post's video is served: taken only where nothing assembled would be better.
                val whole = pictures.filter { it.hasAudio && it.directUrl != null && it.extension == "mp4" }
                options.formatId?.let { id -> return whole.firstOrNull { it.id == id } }
                val best = whole.maxWithOrNull(compareBy<MediaFormat> { it.height ?: 0 }.thenBy { it.bitrateKbps ?: 0 }) ?: return null
                best.takeIf { (it.height ?: 0) >= (pictures.maxOfOrNull { picture -> picture.height ?: 0 } ?: 0) }
            }

            DownloadKind.SUBTITLES, DownloadKind.FILE -> null
        }
    }

    /**
     * Downloads [format] of [info] into [outputDir] by way of its download's [workspace] - see [DownloadWorkspaces] - as
     * [preferences] say requests are made, tagging a song or a video, a song with its cover, and says the file saved to [onEvent].
     */
    suspend fun download(
        info: MediaInfo,
        format: MediaFormat,
        preferences: DownloaderPreferences,
        workspace: File,
        outputDir: File,
        onEvent: (DownloadEvent) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val client = preferences.transferClient(client)
        val part = fetch(format, client, preferences.rateThrottler(), workspace, onEvent)
        val total = part.length()
        outputDir.mkdirs()
        val base = info.title.toFileName()
        val extension = format.extension.takeIf(String::isNotEmpty)?.let { ".$it" }.orEmpty()
        // A song or a video is named as yt-dlp names them, the same one saved over itself; a file keeps the name it was served
        // with, beside any other of that name - never over it.
        val file = if (format.isMedia) {
            File(outputDir, "$base [${info.id.orEmpty()}]$extension")
        } else {
            generateSequence(0) { it + 1 }.map { File(outputDir, if (it == 0) "$base$extension" else "$base ($it)$extension") }.first { !it.exists() }
        }
        part.copyTo(file, overwrite = true)
        if (format.isMedia) tag(file, info, client, withCover = !format.hasVideo)
        onEvent(DownloadEvent.Progress(total, total, null))
        onEvent(DownloadEvent.Saved(file, heatmapJson = null, chaptersJson = info.chaptersJson))
    }

    /**
     * Fetches [format] into its part in [workspace], carried on from where it stopped where the part held is the start of this
     * very file, unchanged on its site. A song or a video is fetched in order, by one connection - so it plays as it downloads,
     * see [LiveDownloads]; any other file in parts, [ConnectionsPerDownload] at once. One served neither in ranges nor of a known
     * size is fetched whole, from its start. A file found changed is fetched again from its start, never joined onto bytes of
     * another - and whole, should its site keep answering as though it had changed.
     */
    private suspend fun fetch(format: MediaFormat, client: OkHttpClient, throttler: Throttler?, workspace: File, onEvent: (DownloadEvent) -> Unit): File {
        val url = checkNotNull(format.directUrl)
        val part = File(workspace, "direct.part")
        val recordFile = File(workspace, "direct.json")
        val connections = if (format.isMedia) 1 else ConnectionsPerDownload
        var startsOver = 0
        // Whole only once the download is over: it is fetched in place.
        onEvent(DownloadEvent.Part(part, part))
        onEvent(DownloadEvent.Log("[direct] Downloading format ${format.id} (${format.extension}) from ${url.toHttpUrlOrNull()?.host.orEmpty()}"))
        while (true) {
            // Carried on only where the part held is this format's, with every byte its record says it has.
            val held = recordFile.takeIf(File::isFile)?.readText()?.let(PartRecord::parse)
                ?.takeIf { it.formatId == format.id && part.length() >= if (connections == 1) FileParts(it.parts).doneBytes else it.totalBytes }
            val record = held ?: run {
                val served = client.probe(url, format.headers)
                val total = served.totalBytes?.takeIf { served.isResumable && it > 0 && startsOver < 2 }
                if (total == null) {
                    recordFile.delete()
                    fetchWhole(url, format, client, throttler, part, onEvent)
                    return part
                }
                PartRecord(format.id, total, served.validator, splitToRange(total, MinPartBytes, connections).map { FilePart(it.first, it.last) })
                    .also { recordFile.writeText(it.toJson()) }
            }
            if (held != null) onEvent(DownloadEvent.Log("[direct] Resuming at byte ${FileParts(held.parts).doneBytes}"))
            try {
                fetchInParts(url, format, client, throttler, record, connections, part, recordFile, onEvent)
                return part
            } catch (e: FileChangedException) {
                onEvent(DownloadEvent.Log("[direct] Not the file it began as: starting over"))
                recordFile.delete()
                part.delete()
                startsOver++
            }
        }
    }

    /**
     * Fetches [record]'s parts into [file] over [connections] at once. One connection fetches in order, the file growing as it
     * goes; several fetch AB Download Manager's way, into a file already its whole size: a connection done with its part takes
     * another, or half of what is left of the largest - and one that fails leaves its part to the others, the download failing
     * only where they could not finish it. How far it is said, and recorded to carry on from, every [ReportEveryMs] - recorded
     * only once on the disk itself, so a record never claims bytes the device could lose.
     */
    private suspend fun fetchInParts(
        url: String,
        format: MediaFormat,
        client: OkHttpClient,
        throttler: Throttler?,
        record: PartRecord,
        connections: Int,
        file: File,
        recordFile: File,
        onEvent: (DownloadEvent) -> Unit,
    ) = RandomAccessFile(file, "rw").use { output ->
        val parts = FileParts(record.parts)
        output.setLength(if (connections == 1) parts.doneBytes else record.totalBytes)
        val channel = output.channel
        fun save() {
            // As the parts stood before what they hold was put on the disk: never further than it.
            val held = parts.snapshot()
            channel.force(false)
            recordFile.writeText(record.copy(parts = held).toJson())
        }
        val failure = AtomicReference<IOException>()
        try {
            coroutineScope {
                val reporter = launch {
                    var reportedAt = SystemClock.elapsedRealtime()
                    var reportedBytes = parts.doneBytes
                    while (true) {
                        delay(ReportEveryMs)
                        val now = SystemClock.elapsedRealtime()
                        val done = parts.doneBytes
                        val speed = (done - reportedBytes) * 1000 / (now - reportedAt).coerceAtLeast(1)
                        onEvent(DownloadEvent.Progress(done, record.totalBytes, speed))
                        onEvent(DownloadEvent.Log(progressLine(done, record.totalBytes, speed)))
                        save()
                        reportedAt = now
                        reportedBytes = done
                    }
                }
                coroutineScope {
                    repeat(connections) {
                        launch {
                            var part = parts.next(done = null)
                            while (part != null) {
                                try {
                                    fetchPart(url, format, client, throttler, record, parts, part, channel)
                                } catch (e: FileChangedException) {
                                    throw e
                                } catch (e: IOException) {
                                    // A site that takes fewer connections than were opened refuses the rest: theirs to finish.
                                    failure.set(e)
                                    parts.release(part)
                                    return@launch
                                }
                                part = parts.next(done = part)
                            }
                        }
                    }
                }
                reporter.cancel()
            }
        } finally {
            withContext(NonCancellable) { runCatching { save() } }
        }
        if (!parts.isComplete) throw checkNotNull(failure.get())
    }

    /**
     * Fetches [part] into [channel] at its place, asking for no more than [ChunkBytes] at a time, until it is whole - or until
     * another connection has taken the rest of it. Each answer must be the range asked for, of this very file - see
     * [continuesPart] - or the file is taken to have changed.
     */
    private suspend fun fetchPart(
        url: String,
        format: MediaFormat,
        client: OkHttpClient,
        throttler: Throttler?,
        record: PartRecord,
        parts: FileParts,
        part: FilePart,
        channel: FileChannel,
    ) {
        val buffer = ByteArray(64 * 1024)
        while (!part.isComplete) {
            val offset = part.current
            val request = Request.Builder().url(url)
                .apply { format.headers.forEach { (name, value) -> header(name, value) } }
                .header("Range", "bytes=$offset-${minOf(part.to, offset + ChunkBytes - 1)}")
                .apply { record.validator?.let { header("If-Range", it) } }
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP Error ${response.code}")
                if (!continuesPart(response.code, contentRangeOf(response.header("Content-Range")), offset, record.totalBytes)) throw FileChangedException()
                val body = checkNotNull(response.body).source().let { source -> throttler?.source(source)?.buffer() ?: source }
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val allowed = parts.claim(part, buffer.size.toLong())
                    if (allowed <= 0) break
                    val read = body.read(buffer, 0, minOf(buffer.size.toLong(), allowed).toInt())
                    if (read < 0) break
                    var written = 0
                    while (written < read) written += channel.write(ByteBuffer.wrap(buffer, written, read - written), part.current + written)
                    part.current += read
                }
            }
        }
    }

    /** Fetches the file at [url] whole into [file], from its start: one served neither in ranges nor of a known size. */
    private suspend fun fetchWhole(url: String, format: MediaFormat, client: OkHttpClient, throttler: Throttler?, file: File, onEvent: (DownloadEvent) -> Unit) {
        val request = Request.Builder().url(url).apply { format.headers.forEach { (name, value) -> header(name, value) } }.build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP Error ${response.code}")
            val total = checkNotNull(response.body).contentLength().takeIf { it > 0 }
            val body = checkNotNull(response.body).source().let { source -> throttler?.source(source)?.buffer() ?: source }
            RandomAccessFile(file, "rw").use { output ->
                output.setLength(0)
                val buffer = ByteArray(64 * 1024)
                var reportedAt = 0L
                var reportedBytes = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = body.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    val now = SystemClock.elapsedRealtime()
                    if (now - reportedAt >= ReportEveryMs) {
                        val speed = if (reportedAt > 0) (output.length() - reportedBytes) * 1000 / (now - reportedAt) else null
                        onEvent(DownloadEvent.Progress(output.length(), total, speed))
                        onEvent(DownloadEvent.Log(progressLine(output.length(), total, speed)))
                        reportedAt = now
                        reportedBytes = output.length()
                    }
                }
                output.fd.sync()
            }
        }
    }

    /**
     * Writes [info]'s title, artist and year into [file] - the song's own where its site knows them - and, [withCover], its
     * picture as a square cover: a video is its own picture.
     */
    private fun tag(file: File, info: MediaInfo, client: OkHttpClient, withCover: Boolean) {
        val cover = info.thumbnailUrl?.takeIf { withCover }?.let { squareCover(it, client) }
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE).use { descriptor ->
            // TagLib takes the descriptor it is handed as its own and closes it: it is given a duplicate.
            val properties = TagLib.getMetadata(descriptor.dup().detachFd(), readPictures = false)?.propertyMap ?: return
            properties["TITLE"] = arrayOf(info.track ?: info.title)
            (info.artist ?: info.uploader)?.let { properties["ARTIST"] = arrayOf(it) }
            info.year?.let { properties["DATE"] = arrayOf(it) }
            TagLib.savePropertyMap(descriptor.dup().detachFd(), properties)
            cover?.let { TagLib.savePictures(descriptor.dup().detachFd(), arrayOf(Picture(data = it, description = "", pictureType = "Front Cover", mimeType = "image/jpeg"))) }
        }
    }

    /** The picture at [url], cut to a square from its middle - a music cover from a video's wide frame. Null where it cannot be had. */
    private fun squareCover(url: String, client: OkHttpClient): ByteArray? = runCatching {
        val bytes = client.newCall(Request.Builder().url(url).build()).execute().use { checkNotNull(it.body).bytes() }
        val picture = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        val side = minOf(picture.width, picture.height)
        val square = Bitmap.createBitmap(picture, (picture.width - side) / 2, (picture.height - side) / 2, side, side)
        val scaled = if (side > CoverSize) Bitmap.createScaledBitmap(square, CoverSize, CoverSize, true) else square
        ByteArrayOutputStream().use { output ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 90, output)
            output.toByteArray()
        }
    }.getOrNull()

    /**
     * What a file being fetched in parts is, and how far each part is: to tell a file it may carry on from one it may not - the
     * same format, the same size, unchanged on its site - and carry each part on from its last byte.
     */
    internal data class PartRecord(val formatId: String, val totalBytes: Long, val validator: String?, val parts: List<FilePart>) {
        fun toJson(): String = JSONObject()
            .put("formatId", formatId)
            .put("totalBytes", totalBytes)
            .put("validator", validator)
            .put("parts", JSONArray(parts.map { JSONArray(listOf(it.from, it.to, it.current)) }))
            .toString()

        companion object {
            fun parse(text: String): PartRecord? = runCatching {
                val json = JSONObject(text)
                val parts = json.getJSONArray("parts").let { array ->
                    (0 until array.length()).map { array.getJSONArray(it).let { part -> FilePart(part.getLong(0), part.getLong(1), part.getLong(2)) } }
                }
                PartRecord(json.getString("formatId"), json.getLong("totalBytes"), json.optString("validator").ifEmpty { null }, parts)
            }.getOrNull()
        }
    }

    companion object {
        /** How much of a file one request asks for. */
        const val ChunkBytes = 10L * 1024 * 1024
    }
}

/**
 * Whether an answer to a request for the bytes from [offset] of a file of [heldTotal] bytes carries that file on: the range
 * asked for, of a file of its very size - never the whole sent again, nor another file.
 */
internal fun continuesPart(code: Int, range: ContentRange?, offset: Long, heldTotal: Long): Boolean =
    code == 206 && range?.start == offset && range.total == heldTotal

/** How far a direct download is, as yt-dlp writes its own: "[download] 45.3% of 10.00MiB at 1.00MiB/s". */
private fun progressLine(done: Long, total: Long?, bytesPerSecond: Long?): String = buildString {
    append("[download] ")
    if (total != null && total > 0) append(String.format(Locale.US, "%.1f%% of %s", done * 100.0 / total, mebibytes(total))) else append(mebibytes(done))
    bytesPerSecond?.let { append(" at ${mebibytes(it)}/s") }
}

private fun mebibytes(bytes: Long): String = String.format(Locale.US, "%.2fMiB", bytes / 1_048_576.0)

/** A title as a file can be named: without what a file system forbids, cut to fit. */
private fun String.toFileName(): String = replace(Regex("""[\\/:*?"<>|\x00-\x1f]"""), "_").trim().take(150).ifEmpty { "download" }
