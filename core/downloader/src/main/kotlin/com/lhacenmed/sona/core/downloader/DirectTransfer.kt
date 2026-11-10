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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

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
                val candidates = info.formats.filter { !it.hasVideo && it.hasAudio && it.directUrl != null && it.extension in containers }
                options.formatId?.let { id -> candidates.firstOrNull { it.id == id } } ?: candidates.maxByOrNull { it.bitrateKbps ?: 0 }
            }

            DownloadKind.VIDEO -> {
                if (choices.captions && info.hasCaptions) return null
                val tallest = choices.videoQuality.height ?: Int.MAX_VALUE
                val pictures = info.formats.filter { it.hasVideo && (it.height ?: 0) <= tallest }
                // One file with its sound, as a post's video is served: taken only where nothing assembled would be better.
                val whole = pictures.filter { it.hasAudio && it.directUrl != null && it.extension == "mp4" }
                options.formatId?.let { id -> return whole.firstOrNull { it.id == id } }
                val best = whole.maxWithOrNull(compareBy<MediaFormat> { it.height ?: 0 }.thenBy { it.bitrateKbps ?: 0 }) ?: return null
                best.takeIf { (it.height ?: 0) >= (pictures.maxOfOrNull { picture -> picture.height ?: 0 } ?: 0) }
            }
        }
    }

    /** Downloads [format] of [info] into [outputDir] by way of [partFile], tagging it, and says the file saved to [onEvent]. */
    suspend fun download(info: MediaInfo, format: MediaFormat, partFile: File, outputDir: File, onEvent: (DownloadEvent) -> Unit) = withContext(Dispatchers.IO) {
        val url = checkNotNull(format.directUrl)
        val total = fetch(url, format.headers, partFile, onEvent)
        outputDir.mkdirs()
        val name = "${info.title.toFileName()} [${info.id.orEmpty()}].${format.extension}"
        val file = File(outputDir, name)
        partFile.copyTo(file, overwrite = true)
        partFile.delete()
        tag(file, info, withCover = !format.hasVideo)
        onEvent(DownloadEvent.Progress(total, total, null))
        onEvent(DownloadEvent.Saved(file, heatmapJson = null, chaptersJson = info.chaptersJson))
    }

    /** Fetches [url] into [partFile], carrying on from what it holds. How long the whole is. */
    private suspend fun fetch(url: String, headers: Map<String, String>, partFile: File, onEvent: (DownloadEvent) -> Unit): Long {
        partFile.parentFile?.mkdirs()
        var total: Long? = null
        var reportedAt = 0L
        var reportedBytes = partFile.length()
        RandomAccessFile(partFile, "rw").use { output ->
            output.seek(output.length())
            while (total == null || output.length() < total!!) {
                val offset = output.length()
                val request = Request.Builder().url(url).apply { headers.forEach { (name, value) -> header(name, value) } }
                    .header("Range", "bytes=$offset-${offset + ChunkBytes - 1}")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.code == 403 || response.code == 410) throw IOException("HTTP Error ${response.code}")
                    if (!response.isSuccessful) throw IOException("HTTP Error ${response.code}")
                    if (response.code == 200 && offset > 0) {
                        // The range was not honoured: the whole is sent again, from its start.
                        output.setLength(0)
                    }
                    total = response.header("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: response.body?.contentLength()?.takeIf { it > 0 }
                    val body = checkNotNull(response.body).byteStream()
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = body.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        val now = SystemClock.elapsedRealtime()
                        if (now - reportedAt >= ReportEveryMs) {
                            val speed = if (reportedAt > 0) (output.length() - reportedBytes) * 1000 / (now - reportedAt) else null
                            onEvent(DownloadEvent.Progress(output.length(), total, speed))
                            reportedAt = now
                            reportedBytes = output.length()
                        }
                    }
                }
                if (total == null) break
            }
            return output.length()
        }
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

    companion object {
        /** How much of a file one request asks for. */
        const val ChunkBytes = 10L * 1024 * 1024
    }
}

/** A title as a file can be named: without what a file system forbids, cut to fit. */
private fun String.toFileName(): String = replace(Regex("""[\\/:*?"<>|\x00-\x1f]"""), "_").trim().take(150).ifEmpty { "download" }
