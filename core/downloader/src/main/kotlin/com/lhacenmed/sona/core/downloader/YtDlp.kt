package com.lhacenmed.sona.core.downloader

import android.content.Context
import com.lhacenmed.sona.core.model.DownloadChoices
import com.lhacenmed.sona.core.model.DownloadKind
import com.lhacenmed.sona.core.model.DownloadOptions
import com.lhacenmed.sona.core.model.DownloaderPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** What a download says as it goes. */
sealed interface DownloadEvent {
    /** Another of its videos begins - [index] of [count] for a playlist's. */
    data class Item(val title: String, val index: Int?, val count: Int?) : DownloadEvent

    /** How far the file downloading is - each null while unknown. */
    data class Progress(
        val downloadedBytes: Long?,
        val totalBytes: Long?,
        val bytesPerSecond: Long?,
        /** How far the one file fetching now is, where the rest is the download's whole - see [WholeProgress]. */
        val fileFraction: Float? = null,
    ) : DownloadEvent {
        val fraction: Float? get() = fractionOf(downloadedBytes, totalBytes)
    }

    /**
     * A file it begins to download into - one of a video's streams, or the whole - growing at [growing] until it is whole at
     * [whole], which it may be from the first: what is played as it downloads - see [LiveDownloads].
     */
    data class Part(val growing: File, val whole: File) : DownloadEvent

    /** A line of what the download does, in the downloader's own words - its log's, and its notification's. */
    data class Log(val line: String) : DownloadEvent

    /** A file saved where it belongs, with the Most replayed graph and the chapters its site gave it, as yt-dlp gives them - see [EmbeddedExtras]. */
    data class Saved(val file: File, val heatmapJson: String?, val chaptersJson: String?) : DownloadEvent
}

/** How far [downloadedBytes] of [totalBytes] is, from 0 to 1 - null while either is unknown. */
fun fractionOf(downloadedBytes: Long?, totalBytes: Long?): Float? =
    if (downloadedBytes != null && totalBytes != null && totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null

/** The downloader is not on this device: it has to be downloaded first - see [DownloaderRuntime]. */
class DownloaderMissingException : Exception("The downloader is not downloaded")

/** yt-dlp failed - with its own words why. */
class YtDlpException(message: String) : Exception(message)

/** Nothing saved: yt-dlp's download archive has it as downloaded before - see [DownloaderPreferences.usesArchive]. */
class DownloadedBeforeException : Exception("Downloaded before: it is recorded in the download archive")

/** How yt-dlp ends the line saying it let a video go, as the archive has it. */
private const val ArchivedLine = "has already been recorded in the archive"

/** What a download reads: the link itself, or [infoJson] - the link already read, so it is not read again. */
class DownloadSource(val url: String, val infoJson: File?)

/**
 * yt-dlp, run with the programs the app carries and what [DownloaderRuntime] downloaded for them - kept up to date in place.
 *
 * Each call runs one process, ended with the coroutine that called it.
 */
@Singleton
class YtDlp @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtime: DownloaderRuntime,
) {
    /**
     * [url], read into yt-dlp's info JSON - one video with its formats and every caption it has, or a playlist with its
     * videos - given [extraArguments] beside its own. YouTube's translated captions are listed only where yt-dlp is told it
     * may write them.
     */
    internal suspend fun infoJson(url: String, preferences: DownloaderPreferences, extraArguments: List<String>): JSONObject =
        JSONObject(run(listOf("-J", "--flat-playlist", "--no-playlist", "--write-auto-subs") + preferences.networkArguments(runtime) + extraArguments + url))

    /**
     * Downloads [source] with [choices], [options] and [preferences] into [outputDir] by way of its download's [workspace] - see
     * [DownloadWorkspaces] - saying what it does to [onEvent] as it goes. yt-dlp carries on there what it fetched before, from
     * its last byte.
     */
    suspend fun download(
        source: DownloadSource,
        choices: DownloadChoices,
        options: DownloadOptions,
        preferences: DownloaderPreferences,
        workspace: File,
        outputDir: File,
        onEvent: (DownloadEvent) -> Unit,
    ) {
        val target = source.infoJson?.let { listOf("--load-info-json", it.path) } ?: listOf(source.url)
        val read = source.infoJson?.let { JSONObject(it.readText()) }
        val subtitlesAlone = choices.kind == DownloadKind.SUBTITLES
        val wantsCaptions = subtitlesAlone || (choices.kind == DownloadKind.VIDEO && choices.captions)
        // The captions Sona makes - transcribed from the speech, translated - are made first, and listed in the read yt-dlp is
        // handed as files of their own.
        val made = File(workspace, "captions")
        val picked = options.subtitleLanguages?.takeIf { wantsCaptions }?.let { codes ->
            val transcribed = CaptionTranscription.make(read, codes, made, preferences) { onEvent(DownloadEvent.Log("[sona] $it")) }
            CaptionTranslation.make(read, transcribed, made)
        }
        val hasTranslations = read != null && picked != null && picked != options.subtitleLanguages
        if (hasTranslations) source.infoJson?.writeText(read.toString())
        val captionLanguages = when {
            !wantsCaptions -> null
            picked != null -> exactCaptionLanguages(picked)
            else -> captionLanguagesOf(read, Locale.getDefault().language)
        }
        // Captions alone are saved where Sona then finds them - yt-dlp says nothing of the files it only moves - and moves them
        // where they belong.
        val savesTo = if (subtitlesAlone) File(workspace, "subtitles").apply { deleteRecursively() } else outputDir
        // What yt-dlp says of captions, for why none were saved.
        val captionNotes = mutableListOf<String>()
        // Whether yt-dlp let a video go as downloaded before, by the archive - and whether it saved anything at all.
        var isInArchive = false
        var hasSaved = false
        suspend fun runWith(languages: String?, target: List<String>) {
            val arguments = choices.toArguments(options, preferences, languages, decodesAv1InHardware(choices.videoQuality.height)) +
                // The archive is of what was saved as sound or picture: captions alone are neither held back by it nor kept in it -
                // and one asked for again anyway is downloaded, as it is recorded there already.
                preferences.networkArguments(runtime, archives = choices.kind.isMedia && !options.downloadsAgain) +
                downloadArguments(savesTo.path, workspace.path, preferences.fileNameTemplate, options.splitChapters) +
                (if (hasTranslations) listOf("--enable-file-urls") else emptyList()) +
                options.commandArguments?.let(::splitArguments).orEmpty() +
                target
            // Its errors among the rest, in the order it wrote them: its log is all it said.
            val progress = WholeProgress()
            run(arguments, mergesErrors = true, onLine = { line ->
                progress.read(line)
                parseLine(line)?.let(progress::of)?.let { event ->
                    if (event is DownloadEvent.Saved) hasSaved = true
                    onEvent(event)
                }
                logLineOf(line)?.let { onEvent(DownloadEvent.Log(it)) }
                if (line.endsWith(ArchivedLine)) isInArchive = true
                if (subtitlesAlone && line.contains("subtitle", ignoreCase = true) && (line.startsWith("WARNING") || line.startsWith("ERROR"))) captionNotes += line
            })
        }
        if (!subtitlesAlone || captionLanguages != null) runWith(captionLanguages, target)
        if (!subtitlesAlone) {
            // Nothing saved as it was downloaded before: said so, rather than over with nothing to show.
            if (isInArchive && !hasSaved) throw DownloadedBeforeException()
            return
        }
        // None from the app's own read: yt-dlp reads the link itself - its own ways of asking the site for captions - and is asked
        // for the same languages, else those of the speech.
        if (source.infoJson != null && written(savesTo).isEmpty()) {
            onEvent(DownloadEvent.Log("[sona] No subtitles from the read: yt-dlp reads the link itself"))
            runWith(captionLanguages ?: captionLanguagesOf(null, Locale.getDefault().language), listOf(source.url))
        }
        val listed = read?.let { it.keysOf("subtitles") + it.keysOf("automatic_captions") }.orEmpty()
        val why = listOf(
            NoSubtitles,
            "Asked for: ${captionLanguages ?: "none"}",
            "The read lists: ${listed.take(20).joinToString(", ").ifEmpty { "none" }}${if (listed.size > 20) ", …" else ""}",
        ) + captionNotes
        withContext(Dispatchers.IO) { saveSubtitles(savesTo, outputDir, why.joinToString("\n"), onEvent) }
    }

    /** The files yt-dlp wrote into [folder] - those in folders the file name template names too. */
    private fun written(folder: File): List<File> = folder.walkTopDown().filter(File::isFile).toList()

    /** Moves every caption file written into [folder] to [outputDir], each said saved - failing with [why] where there is none. */
    private fun saveSubtitles(folder: File, outputDir: File, why: String, onEvent: (DownloadEvent) -> Unit) {
        val files = written(folder).ifEmpty { throw YtDlpException(why) }
        outputDir.mkdirs()
        files.forEach { file ->
            // Copied, not renamed: the workspace and the folder chosen may be on different storage.
            val saved = file.copyTo(File(outputDir, file.name), overwrite = true)
            file.delete()
            onEvent(DownloadEvent.Saved(saved, heatmapJson = null, chaptersJson = null))
        }
    }

    /** Runs yt-dlp with [arguments] as typed, in [workingDir], every line it writes handed to [onLine] - the terminal's. */
    suspend fun runTyped(arguments: List<String>, workingDir: File, onLine: (String) -> Unit) {
        workingDir.mkdirs()
        run(arguments, onLine, mergesErrors = true, workingDir = workingDir)
    }

    /** The version of yt-dlp in use - null while the downloader is not here. */
    suspend fun version(): String? = runCatching { run(listOf("--version")).trim() }.getOrNull()

    /** Brings yt-dlp up to date, saying what it is now. */
    suspend fun update(): String {
        run(listOf("--update-to", YtDlpChannel))
        return version().orEmpty()
    }

    /**
     * Runs yt-dlp with [arguments], handing each line it writes to [onLine], and gives back all it wrote - ended, with its
     * process, as the calling coroutine is. Fails with yt-dlp's own words should it fail. [mergesErrors] hands its errors
     * to [onLine] too, in the order it wrote them; [workingDir] is where what it is not told where to save is saved.
     */
    private suspend fun run(
        arguments: List<String>,
        onLine: (String) -> Unit = {},
        mergesErrors: Boolean = false,
        workingDir: File? = null,
    ): String = withContext(Dispatchers.IO) {
        val files = runtime.files() ?: throw DownloaderMissingException()
        val command = listOf(files.python.path, files.ytDlp.path) + commonArguments(files) + arguments
        val process = ProcessBuilder(command).redirectErrorStream(mergesErrors).directory(workingDir)
            .apply { environment().putAll(environmentOf(context, files)) }
            .start()
        val output = StringBuilder()
        try {
            val errors = coroutineScope {
                // Read beside the output, so neither fills and stalls the process. A stream the process's end closes
                // mid-read is simply over: what it said by then is all it said.
                val errors = async { runCatching { process.errorStream.bufferedReader().readText() }.getOrDefault("") }
                // Cancelled, the process goes: its output ends, and the read below with it.
                val killer = launch { try { awaitCancellation() } finally { process.destroy() } }
                runCatching {
                    process.inputStream.bufferedReader().forEachLine { line ->
                        output.appendLine(line)
                        onLine(line)
                    }
                }
                killer.cancel()
                errors.await()
            }
            val exitCode = process.waitFor()
            ensureActive()
            if (exitCode != 0) throw YtDlpException(failureOf(if (mergesErrors) output.toString() else errors))
            output.toString()
        } finally {
            process.destroy()
        }
    }

    /** What every run is told: to read no configuration but this, to write its lines plainly, and its runtime's tools. */
    private fun commonArguments(files: RuntimeFiles) = listOf(
        "--ignore-config",
        "--newline",
        "--no-colors",
        "--ffmpeg-location", files.ffmpeg.path,
        "--js-runtimes", "quickjs:${files.quickJs.path}",
        // The scripts YouTube's player is read with, fetched once and kept, should this yt-dlp carry none.
        "--remote-components", "ejs:github",
        "--cache-dir", runtime.cacheDir.path,
    )
}

/** Where the programs the downloader brings - yt-dlp, FFmpeg - find what they load and read. */
internal fun environmentOf(context: Context, files: RuntimeFiles) = mapOf(
    "LD_LIBRARY_PATH" to files.libraryDirs.joinToString(":") { it.path },
    "SSL_CERT_FILE" to File(files.pythonHome, "etc/tls/cert.pem").path,
    "OPENSSL_CONF" to files.tlsConfig.path,
    "PYTHONHOME" to files.pythonHome.path,
    "HOME" to files.ytDlp.parent.orEmpty(),
    "TMPDIR" to context.cacheDir.path,
    "PATH" to "${files.python.parent}:${System.getenv("PATH") ?: "/system/bin"}",
)

/** What a line yt-dlp wrote says, where it is one of Sona's own - see [YtDlpLines] - or names a chapter's file. */
internal fun parseLine(line: String): DownloadEvent? {
    line.substringAfter("[SplitChapters] ", "").substringAfter("Destination: ", "").takeIf(String::isNotEmpty)?.let {
        return DownloadEvent.Saved(File(it.trim()), heatmapJson = null, chaptersJson = null)
    }
    line.substringAfter("[download] Destination: ", "").takeIf(String::isNotEmpty)?.let {
        val whole = File(it.trim())
        // Its captions are downloaded the same way, and are no part of what plays.
        return if (whole.extension.lowercase() in MediaExtensions) DownloadEvent.Part(File(whole.path + ".part"), whole) else null
    }
    val (tag, rest) = line.split(' ', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    return when (tag) {
        YtDlpLines.ITEM -> {
            val (index, count, title) = rest.split(' ', limit = 3).let { Triple(it.getOrNull(0), it.getOrNull(1), it.getOrNull(2)) }
            DownloadEvent.Item(title.orEmpty(), index?.toIntOrNull(), count?.toIntOrNull())
        }

        YtDlpLines.PROGRESS -> {
            val values = rest.split(' ').map { it.toDoubleOrNull()?.toLong() }
            DownloadEvent.Progress(values.getOrNull(0), values.getOrNull(1), values.getOrNull(2))
        }

        YtDlpLines.SAVED -> runCatching {
            val json = JSONObject(rest)
            DownloadEvent.Saved(File(json.getString("filepath")), json.optJSONArray("heatmap")?.toString(), json.optJSONArray("chapters")?.toString())
        }.getOrNull()

        else -> null
    }
}

/** What [line] reads as in a download's log: yt-dlp's own words, its progress as it writes it untold - null for Sona's own lines. */
internal fun logLineOf(line: String): String? {
    val (tag, rest) = line.split(' ', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    return when (tag) {
        YtDlpLines.PROGRESS -> rest.split(' ', limit = 4).getOrNull(3)?.trim()?.replace(Spaces, " ")?.takeIf(String::isNotEmpty)?.let { "[download] $it" }
        YtDlpLines.ITEM, YtDlpLines.SAVED, YtDlpLines.SIZE -> null
        else -> line.takeIf(String::isNotBlank)
    }
}

private val Spaces = Regex(" +")

/** Why a download of captions alone saved nothing: the video has none in the languages picked. */
private const val NoSubtitles = "The video has no subtitles in the languages picked"

/** What a stream of sound or picture is saved as - not a caption, a description or a picture. */
private val MediaExtensions = setOf("mp4", "m4a", "webm", "mkv", "mov", "mp3", "opus", "ogg", "oga", "flac", "wav", "aac", "ts", "3gp", "flv")

/** [text] as the arguments a shell would make of it: split at spaces, outside quotes. */
fun splitArguments(text: String): List<String> =
    Regex(""""([^"]*)"|'([^']*)'|(\S+)""").findAll(text).map { match -> match.groupValues.drop(1).firstOrNull(String::isNotEmpty).orEmpty() }.toList()

/** Why yt-dlp failed: the errors it named, or the last of what it wrote. */
private fun failureOf(errors: String): String {
    val named = errors.lines().filter { it.startsWith("ERROR:") }.map { it.removePrefix("ERROR:").trim() }
    return named.joinToString("\n").ifBlank { errors.trim().takeLast(500) }
}
