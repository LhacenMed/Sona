package com.lhacenmed.sona.core.downloader

import android.content.Context
import com.lhacenmed.sona.core.model.DownloadChoices
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** How long yt-dlp is left before it is brought up to date: the sites it reads change under it within weeks. */
private val UpdateIntervalMs = TimeUnit.DAYS.toMillis(7)

/** What a link leads to, as yt-dlp reads it before downloading. */
data class MediaInfo(
    val title: String,
    val uploader: String?,
    val durationSeconds: Long?,
    val thumbnailUrl: String?,
    /** How many videos it holds - null for one video. */
    val playlistCount: Int?,
)

/** What a download says as it goes. */
sealed interface DownloadEvent {
    /** Another of its videos begins - [index] of [count] for a playlist's. */
    data class Item(val title: String, val index: Int?, val count: Int?) : DownloadEvent

    /** How far the file downloading is, from 0 to 1 - null while how large it is is unknown. */
    data class Progress(val fraction: Float?) : DownloadEvent

    /** A file saved where it belongs, with the Most replayed graph and the chapters its site gave it, as yt-dlp gives them - see [EmbeddedExtras]. */
    data class Saved(val file: File, val heatmapJson: String?, val chaptersJson: String?) : DownloadEvent
}

/** The downloader is not on this device: it has to be downloaded first - see [DownloaderRuntime]. */
class DownloaderMissingException : Exception("The downloader is not downloaded")

/** yt-dlp failed - with its own words why. */
class YtDlpException(message: String) : Exception(message)

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
    /** What [url] leads to. */
    suspend fun info(url: String): MediaInfo {
        updateIfDue()
        val json = JSONObject(run(listOf("-J", "--flat-playlist", "--no-playlist", url)))
        val entries = json.optJSONArray("entries")
        return MediaInfo(
            title = json.optString("title").ifBlank { url },
            uploader = json.stringOrNull("uploader") ?: json.stringOrNull("channel"),
            durationSeconds = json.optDouble("duration").takeUnless { it.isNaN() }?.toLong(),
            thumbnailUrl = json.stringOrNull("thumbnail") ?: entries?.optJSONObject(0)?.optJSONArray("thumbnails")?.lastThumbnail(),
            playlistCount = if (json.optString("_type") == "playlist") json.optInt("playlist_count", entries?.length() ?: 0) else null,
        )
    }

    /** Downloads [url] with [choices] into [outputDir], saying what it does to [onEvent] as it goes. */
    suspend fun download(url: String, choices: DownloadChoices, outputDir: File, onEvent: (DownloadEvent) -> Unit) {
        updateIfDue()
        val tempDir = File(context.cacheDir, "downloads").apply { mkdirs() }
        val arguments = choices.toArguments(Locale.getDefault().language) + downloadArguments(outputDir.path, tempDir.path) + url
        run(arguments) { line -> parseLine(line)?.let(onEvent) }
    }

    /** The version of yt-dlp in use - null while the downloader is not here. */
    suspend fun version(): String? = runCatching { run(listOf("--version")).trim() }.getOrNull()

    /** Brings yt-dlp up to date, saying what it is now. */
    suspend fun update(): String {
        run(listOf("--update-to", "stable"))
        // Not rewritten when already the newest: marked all the same, as checked now.
        runtime.ytDlpFile.setLastModified(System.currentTimeMillis())
        return version().orEmpty()
    }

    /** Brings yt-dlp up to date once it has gone a while without - quietly, a download going on with what it has otherwise. */
    private suspend fun updateIfDue() {
        if (System.currentTimeMillis() - runtime.ytDlpFile.lastModified() < UpdateIntervalMs) return
        runCatching { update() }
    }

    /**
     * Runs yt-dlp with [arguments], handing each line it writes to [onLine], and gives back all it wrote - ended, with its
     * process, as the calling coroutine is. Fails with yt-dlp's own words should it fail.
     */
    private suspend fun run(arguments: List<String>, onLine: (String) -> Unit = {}): String = withContext(Dispatchers.IO) {
        val files = runtime.files() ?: throw DownloaderMissingException()
        val command = listOf(files.python.path, files.ytDlp.path) + commonArguments(files) + arguments
        val process = ProcessBuilder(command).apply { environment().putAll(environmentOf(files)) }.start()
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
            if (exitCode != 0) throw YtDlpException(failureOf(errors))
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

    /** Where the programs find what they load and read. */
    private fun environmentOf(files: RuntimeFiles) = mapOf(
        "LD_LIBRARY_PATH" to files.libraryDirs.joinToString(":") { it.path },
        "SSL_CERT_FILE" to File(files.pythonHome, "etc/tls/cert.pem").path,
        "PYTHONHOME" to files.pythonHome.path,
        "HOME" to files.ytDlp.parent.orEmpty(),
        "TMPDIR" to context.cacheDir.path,
        "PATH" to "${files.python.parent}:${System.getenv("PATH") ?: "/system/bin"}",
    )
}

/** What a line yt-dlp wrote says, where it is one of Sona's own - see [YtDlpLines]. */
internal fun parseLine(line: String): DownloadEvent? {
    val (tag, rest) = line.split(' ', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    return when (tag) {
        YtDlpLines.ITEM -> {
            val (index, count, title) = rest.split(' ', limit = 3).let { Triple(it.getOrNull(0), it.getOrNull(1), it.getOrNull(2)) }
            DownloadEvent.Item(title.orEmpty(), index?.toIntOrNull(), count?.toIntOrNull())
        }

        YtDlpLines.PROGRESS -> {
            val (downloaded, total) = rest.split(' ').let { it.getOrNull(0)?.toDoubleOrNull() to it.getOrNull(1)?.toDoubleOrNull() }
            DownloadEvent.Progress(if (downloaded != null && total != null && total > 0.0) (downloaded / total).toFloat().coerceIn(0f, 1f) else null)
        }

        YtDlpLines.SAVED -> runCatching {
            val json = JSONObject(rest)
            DownloadEvent.Saved(File(json.getString("filepath")), json.optJSONArray("heatmap")?.toString(), json.optJSONArray("chapters")?.toString())
        }.getOrNull()

        else -> null
    }
}

/** Why yt-dlp failed: the errors it named, or the last of what it wrote. */
private fun failureOf(errors: String): String {
    val named = errors.lines().filter { it.startsWith("ERROR:") }.map { it.removePrefix("ERROR:").trim() }
    return named.joinToString("\n").ifBlank { errors.trim().takeLast(500) }
}

private fun JSONObject.stringOrNull(name: String): String? = optString(name).takeIf { has(name) && !isNull(name) && it.isNotBlank() }

private fun JSONArray.lastThumbnail(): String? = optJSONObject(length() - 1)?.stringOrNull("url")
