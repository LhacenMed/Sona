package com.lhacenmed.sona.core.downloader

import android.content.Context
import android.os.ParcelFileDescriptor
import com.kyant.taglib.TagLib
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * A format a file can be turned into - only those every part of the app reads, edits the tags of and plays, so a file turned into
 * one is the track it was everywhere. A sound's are encoded anew; a video's keep its streams as they are, put in another
 * container, re-encoding only a sound the container cannot hold. Each [encodings] is tried in turn: a build of FFmpeg without
 * an encoder fails it, and the next - FFmpeg's own - is tried.
 */
enum class FileFormat(val extension: String, val isVideo: Boolean, internal val encodings: List<List<String>>) {
    MP3("mp3", false, listOf(listOf("-c:a", "libmp3lame", "-q:a", "0"))),
    M4A("m4a", false, listOf(listOf("-c:a", "aac", "-b:a", "256k"))),
    OPUS("opus", false, listOf(listOf("-c:a", "libopus", "-b:a", "160k"), listOf("-c:a", "opus", "-strict", "-2", "-ar", "48000", "-b:a", "160k"))),
    OGG("ogg", false, listOf(listOf("-c:a", "libvorbis", "-q:a", "6"), listOf("-c:a", "vorbis", "-strict", "-2"))),
    FLAC("flac", false, listOf(listOf("-c:a", "flac"))),
    WAV("wav", false, listOf(listOf("-c:a", "pcm_s16le"))),
    MP4("mp4", true, listOf(listOf("-c", "copy"), listOf("-c:v", "copy", "-c:a", "aac", "-b:a", "192k"))),
    MKV("mkv", true, listOf(listOf("-c", "copy"))),
    WEBM("webm", true, listOf(listOf("-c", "copy"), listOf("-c:v", "copy", "-c:a", "libopus", "-b:a", "160k")));

    companion object {
        /** What [file] can be turned into: the formats of its kind but its own. */
        fun choicesFor(file: File, isVideo: Boolean): List<FileFormat> = entries.filter { it.isVideo == isVideo && it.extension != file.extension.lowercase() }
    }
}

/**
 * Turns a file into another [FileFormat] with the FFmpeg the downloader runs with - see [DownloaderRuntime] - every tag and cover
 * it has carried over by TagLib, as any tag editor writes them.
 */
@Singleton
class MediaConverter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtime: DownloaderRuntime,
) {
    /** Whether files can be converted: the downloader, which brings FFmpeg, is here - see [DownloaderRuntime.download]. */
    val runtimeState: StateFlow<RuntimeState> get() = runtime.state

    /** Writes [input] as [format] to [output]. Fails with FFmpeg's own last words where none of its encodings can. */
    suspend fun convert(input: File, output: File, format: FileFormat) = withContext(Dispatchers.IO) {
        val files = runtime.files() ?: throw DownloaderMissingException()
        val streams = if (format.isVideo) listOf("-map", "0") else listOf("-map", "0:a")
        var failure: String? = null
        for (encoding in format.encodings) {
            output.delete()
            val command = listOf(files.ffmpeg.path, "-y", "-i", input.path) + streams + listOf("-map_metadata", "0") + encoding + output.path
            val process = ProcessBuilder(command).redirectErrorStream(true).apply { environment().putAll(environmentOf(context, files)) }.start()
            val log = try {
                process.inputStream.bufferedReader().readText()
            } finally {
                if (process.waitFor() != 0) output.delete()
                process.destroy()
            }
            if (output.length() > 0) {
                carryTags(input, output)
                return@withContext
            }
            failure = log.lines().lastOrNull(String::isNotBlank)
        }
        throw IOException(failure ?: "Could not convert ${input.name}")
    }

    /** Writes [from]'s tags and pictures into [to] - left as FFmpeg wrote it where either cannot be read. */
    private fun carryTags(from: File, to: File) = runCatching {
        ParcelFileDescriptor.open(from, ParcelFileDescriptor.MODE_READ_ONLY).use { source ->
            val metadata = TagLib.getMetadata(source.dup().detachFd(), readPictures = true) ?: return@runCatching
            ParcelFileDescriptor.open(to, ParcelFileDescriptor.MODE_READ_WRITE).use { target ->
                // TagLib takes the descriptor it is handed as its own and closes it: it is given a duplicate.
                TagLib.savePropertyMap(target.dup().detachFd(), metadata.propertyMap)
                if (metadata.pictures.isNotEmpty()) TagLib.savePictures(target.dup().detachFd(), metadata.pictures)
            }
        }
    }
}
