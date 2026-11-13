package com.lhacenmed.sona.core.downloader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import com.lhacenmed.sona.core.model.AudioFormat
import com.lhacenmed.sona.core.model.DownloadChoices
import com.lhacenmed.sona.core.model.DownloadKind
import com.lhacenmed.sona.core.model.DownloadOptions
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

/** How often a direct download says how far it is. */
private const val ReportEveryMs = 500L

/** The longest side of a cover written into a file. */
private const val CoverSize = 1200

/**
 * A song or a video downloaded straight from where its site serves it, in one piece - no Python, no FFmpeg - and tagged by
 * the app itself: what a download whose choices need nothing assembled or converted takes, on a device with or without the
 * downloader. Read in pieces of [ChunkBytes], as YouTube serves a long read slowly, and carried on from where it stopped.
 */
@Singleton
class DirectTransfer @Inject constructor() {
    private val client = OkHttpClient()

    /**
     * The format [info] is downloaded as directly with [choices] and [options] - null where they ask for what only yt-dlp
     * does: a merge of picture and sound, a conversion, captions, a cut, a command.
     */
    fun formatFor(info: MediaInfo, choices: DownloadChoices, options: DownloadOptions): MediaFormat? {
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

            DownloadKind.SUBTITLES -> null
        }
    }

    /**
     * Downloads [format] of [info] into [outputDir] by way of its download's [workspace] - see [DownloadWorkspaces] - tagging
     * it, a song with its cover, and says the file saved to [onEvent].
     */
    suspend fun download(
        info: MediaInfo,
        format: MediaFormat,
        workspace: File,
        outputDir: File,
        onEvent: (DownloadEvent) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val part = fetch(format, workspace, onEvent)
        val total = part.length()
        outputDir.mkdirs()
        val name = "${info.title.toFileName()} [${info.id.orEmpty()}].${format.extension}"
        val file = File(outputDir, name)
        part.copyTo(file, overwrite = true)
        tag(file, info, withCover = !format.hasVideo)
        onEvent(DownloadEvent.Progress(total, total, null))
        onEvent(DownloadEvent.Saved(file, heatmapJson = null, chaptersJson = info.chaptersJson))
    }

    /**
     * Fetches [format] into its part in [workspace], carrying on from the last byte the part holds - only where it is the
     * start of this very file: the same format, the same size, and, asked with `If-Range`, unchanged on its site. Anything
     * else is fetched again from its start, never joined onto bytes of another file. The part.
     */
    private suspend fun fetch(format: MediaFormat, workspace: File, onEvent: (DownloadEvent) -> Unit): File {
        val url = checkNotNull(format.directUrl)
        val part = File(workspace, "direct.part")
        val recordFile = File(workspace, "direct.json")
        var record = recordFile.takeIf(File::isFile)?.readText()?.let(PartRecord::parse)?.takeIf { it.formatId == format.id }
        var reportedAt = 0L
        // Whole only once the download is over: it is fetched in place.
        onEvent(DownloadEvent.Part(part, part))
        onEvent(DownloadEvent.Log("[direct] Downloading format ${format.id} (${format.extension}) from ${url.toHttpUrlOrNull()?.host.orEmpty()}"))
        RandomAccessFile(part, "rw").use { output ->
            if (record == null) output.setLength(0)
            if (output.length() > 0) onEvent(DownloadEvent.Log("[direct] Resuming at byte ${output.length()}"))
            var reportedBytes = output.length()
            while (true) {
                val total = record?.totalBytes
                if (total != null && output.length() >= total) break
                val offset = output.length()
                val request = Request.Builder().url(url).apply { format.headers.forEach { (name, value) -> header(name, value) } }
                    .header("Range", "bytes=$offset-${offset + ChunkBytes - 1}")
                    .apply { if (offset > 0) record?.validator?.let { header("If-Range", it) } }
                    .build()
                val isWhole = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP Error ${response.code}")
                    val sentTotal = response.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                        ?: response.body?.contentLength()?.takeIf { response.code == 200 && it > 0 }
                    if (!continuesPart(response.code, offset, total, sentTotal)) {
                        // Not the file the part is the start of - changed, or sent whole: fetched again from its start.
                        onEvent(DownloadEvent.Log("[direct] Not the file it began as: starting over"))
                        output.setLength(0)
                        record = null
                        if (response.code != 200) return@use false
                    }
                    if (record == null && sentTotal != null) {
                        record = PartRecord(format.id, sentTotal, validatorOf(response)).also { recordFile.writeText(it.toJson()) }
                    }
                    output.seek(output.length())
                    val body = checkNotNull(response.body).byteStream()
                    val buffer = ByteArray(64 * 1024)
                    var received = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = body.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        received += read
                        val now = SystemClock.elapsedRealtime()
                        if (now - reportedAt >= ReportEveryMs) {
                            val speed = if (reportedAt > 0) (output.length() - reportedBytes) * 1000 / (now - reportedAt) else null
                            onEvent(DownloadEvent.Progress(output.length(), record?.totalBytes, speed))
                            onEvent(DownloadEvent.Log(progressLine(output.length(), record?.totalBytes, speed)))
                            reportedAt = now
                            reportedBytes = output.length()
                        }
                    }
                    // On the disk itself, not only in the system's memory, before more is asked: what is held survives
                    // even the device going off.
                    output.fd.sync()
                    // A file of no told size is over once a response is sent whole, or short of what was asked.
                    sentTotal == null && (response.code == 200 || received < ChunkBytes)
                }
                if (isWhole) break
            }
        }
        return part
    }

    /**
     * Writes [info]'s title, artist and year into [file] - the song's own where its site knows them - and, [withCover], its
     * picture as a square cover: a video is its own picture.
     */
    private fun tag(file: File, info: MediaInfo, withCover: Boolean) {
        val cover = info.thumbnailUrl?.takeIf { withCover }?.let(::squareCover)
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
    private fun squareCover(url: String): ByteArray? = runCatching {
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

    /** What a part being fetched is the start of, to tell a file it may carry on from one it may not - see [fetch]. */
    internal data class PartRecord(val formatId: String, val totalBytes: Long, val validator: String?) {
        fun toJson(): String = JSONObject().put("formatId", formatId).put("totalBytes", totalBytes).put("validator", validator).toString()

        companion object {
            fun parse(text: String): PartRecord? = runCatching {
                val json = JSONObject(text)
                PartRecord(json.getString("formatId"), json.getLong("totalBytes"), json.optString("validator").ifEmpty { null })
            }.getOrNull()
        }
    }

    companion object {
        /** How much of a file one request asks for. */
        const val ChunkBytes = 10L * 1024 * 1024
    }
}

/**
 * Whether a response to a request for the bytes from [offset] carries on the part held, of [heldTotal] bytes - rather than
 * being the whole sent again, or another file: a part is carried on only by a range of a file of its very size.
 */
internal fun continuesPart(code: Int, offset: Long, heldTotal: Long?, sentTotal: Long?): Boolean =
    offset == 0L || (code == 206 && heldTotal != null && sentTotal == heldTotal)

/** How far a direct download is, as yt-dlp writes its own: "[download] 45.3% of 10.00MiB at 1.00MiB/s". */
private fun progressLine(done: Long, total: Long?, bytesPerSecond: Long?): String = buildString {
    append("[download] ")
    if (total != null && total > 0) append(String.format(Locale.US, "%.1f%% of %s", done * 100.0 / total, mebibytes(total))) else append(mebibytes(done))
    bytesPerSecond?.let { append(" at ${mebibytes(it)}/s") }
}

private fun mebibytes(bytes: Long): String = String.format(Locale.US, "%.2fMiB", bytes / 1_048_576.0)

/** What tells this file from a changed one on its site, as `If-Range` asks it: a strong ETag, else when it last changed. */
private fun validatorOf(response: Response): String? =
    response.header("ETag")?.takeUnless { it.startsWith("W/") } ?: response.header("Last-Modified")

/** A title as a file can be named: without what a file system forbids, cut to fit. */
private fun String.toFileName(): String = replace(Regex("""[\\/:*?"<>|\x00-\x1f]"""), "_").trim().take(150).ifEmpty { "download" }
