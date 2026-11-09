package com.lhacenmed.sona.core.downloader

import android.content.Context
import android.os.Build
import com.lhacenmed.sona.core.common.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.CRC32
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

/** Where the downloader stands on this device. */
sealed interface RuntimeState {
    data object Missing : RuntimeState

    /** [receivedBytes] of the [totalBytes] it takes, so far. */
    data class Downloading(val receivedBytes: Long, val totalBytes: Long) : RuntimeState {
        val progress: Float get() = if (totalBytes > 0) (receivedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    }

    data object Installed : RuntimeState

    data object Failed : RuntimeState
}

/** What runs yt-dlp, once the downloader is here - see [DownloaderRuntime.files]. */
internal class RuntimeFiles(
    val python: File,
    val ffmpeg: File,
    val quickJs: File,
    val ytDlp: File,
    val pythonHome: File,
    val libraryDirs: List<File>,
)

/** About how much a part of the downloader's files a download reports itself after. */
private const val ReportEveryBytes = 512 * 1024L

/** yt-dlp as its own project publishes it, the newest: kept up to date in place from then on - see [YtDlp.update]. */
private const val YtDlpUrl = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp"

/**
 * The libraries Python and FFmpeg run with - each in youtubedl-android's archive on Maven Central, one zip per processor
 * inside it: the very artifacts whose programs the app carries, at the version it carries them at.
 */
private enum class RuntimeLibrary(val artifact: String, val zipName: String) {
    PYTHON("library", "libpython.zip.so"),
    FFMPEG("ffmpeg", "libffmpeg.zip.so"),
    ;

    val url: String get() = "https://repo1.maven.org/maven2/io/github/junkfood02/youtubedl-android/$artifact/$RuntimeVersion/$artifact-$RuntimeVersion.aar"

    /** Where it is unpacked: the folder its `usr` stands in. */
    fun dir(versionDir: File) = File(versionDir, name.lowercase())
}

private const val RuntimeVersion = BuildConfig.RUNTIME_VERSION

/**
 * What downloading takes beyond the app: yt-dlp, and the libraries Python and FFmpeg run with - some 50 MB, downloaded
 * here, once, by whoever downloads. The programs that run them are in the app, for Android runs no program an app
 * downloads; the rest is what they read and load, which may come from anywhere.
 *
 * Only this device's processor's share of each library is fetched - read straight out of youtubedl-android's archives on
 * Maven Central, the version the app's programs came from, every file checked against the archive's own checksum. Kept
 * under that version's name, where an update of the app leaves it: downloaded again only once the app moves to another.
 */
@Singleton
class DownloaderRuntime @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val home = File(context.noBackupFilesDir, "downloader")
    private val abi = Build.SUPPORTED_ABIS.first()
    private val versionDir = File(home, "$RuntimeVersion-$abi")
    private val installedMark = File(versionDir, ".installed")

    /** yt-dlp, kept apart from the libraries: it outlives a move to another version of them, kept up to date in place. */
    internal val ytDlpFile = File(home, "yt-dlp")

    /** Where yt-dlp keeps what it caches - the scripts YouTube is read with among it. */
    internal val cacheDir = File(home, "cache")

    private val client = OkHttpClient()

    private val _state = MutableStateFlow<RuntimeState>(RuntimeState.Missing)
    val state: StateFlow<RuntimeState> = _state.asStateFlow()

    private var download: Job? = null

    init {
        // An earlier version's libraries, or one this process did not live to finish, are let go.
        home.listFiles()?.filter { it.isDirectory && it != versionDir && it != cacheDir }?.forEach(File::deleteRecursively)
        if (installedMark.exists() && ytDlpFile.exists()) _state.value = RuntimeState.Installed else versionDir.deleteRecursively()
    }

    /** What runs yt-dlp - null until the downloader is here. */
    internal fun files(): RuntimeFiles? {
        if (_state.value != RuntimeState.Installed) return null
        val programs = File(context.applicationInfo.nativeLibraryDir)
        val python = RuntimeLibrary.PYTHON.dir(versionDir)
        return RuntimeFiles(
            python = File(programs, "libpython.so"),
            ffmpeg = File(programs, "libffmpeg.so"),
            quickJs = File(programs, "libqjs.so"),
            ytDlp = ytDlpFile,
            pythonHome = File(python, "usr"),
            libraryDirs = listOf(File(python, "usr/lib"), File(RuntimeLibrary.FFMPEG.dir(versionDir), "usr/lib"), programs),
        )
    }

    /** Downloads the downloader, unless it is here already or on its way. */
    fun download() {
        if (_state.value == RuntimeState.Installed || download?.isActive == true) return
        _state.value = RuntimeState.Downloading(0, 0)
        download = scope.launch(Dispatchers.IO) {
            try {
                fetch()
                installedMark.createNewFile()
                _state.value = RuntimeState.Installed
            } catch (e: CancellationException) {
                versionDir.deleteRecursively()
                throw e
            } catch (e: Exception) {
                versionDir.deleteRecursively()
                _state.value = RuntimeState.Failed
            }
        }
    }

    /** Stops a download under way, leaving nothing of it. */
    fun cancel() {
        download?.cancel()
        download = null
        if (_state.value is RuntimeState.Downloading) _state.value = RuntimeState.Missing
    }

    /** Takes the downloader off the device - downloading needs it downloaded again. */
    fun remove() {
        cancel()
        home.deleteRecursively()
        _state.value = RuntimeState.Missing
    }

    private suspend fun fetch() {
        versionDir.deleteRecursively()
        versionDir.mkdirs()
        val libraries = RuntimeLibrary.entries.map { library ->
            val archive = HttpZipSource(client, library.url)
            val zip = archive.entries().firstOrNull { it.name == "jni/$abi/${library.zipName}" }
                ?: throw IOException("No ${library.zipName} for $abi")
            Triple(library, archive, zip)
        }
        val ytDlp = client.newCall(Request.Builder().url(YtDlpUrl).build()).execute()
        try {
            if (!ytDlp.isSuccessful) throw IOException("HTTP ${ytDlp.code} for yt-dlp")
            val ytDlpBody = checkNotNull(ytDlp.body)
            val progress = Progress(libraries.sumOf { (_, _, zip) -> zip.compressedSize } + ytDlpBody.contentLength().coerceAtLeast(0))

            for ((library, archive, zip) in libraries) {
                // The zip of the library, out of its archive, checked against the archive's checksum, then unpacked.
                val unpacked = File(versionDir, library.zipName)
                val crc = CRC32()
                archive.contentOf(zip, progress).use { input ->
                    unpacked.outputStream().use { output -> input.copyChecking(output, crc) }
                }
                if (crc.value != zip.crc) throw IOException("${library.zipName} arrived damaged")
                unpacked.unzipTo(library.dir(versionDir))
                unpacked.delete()
            }

            val partial = File(home, "yt-dlp.part")
            ytDlpBody.byteStream().counted(progress).use { input -> partial.outputStream().use { input.copyChecking(it, null) } }
            if (!partial.renameTo(ytDlpFile)) throw IOException("yt-dlp could not be kept")
        } finally {
            ytDlp.close()
        }
    }

    /** How much of the whole has arrived, reported as it moves on - see [RuntimeState.Downloading]. */
    private inner class Progress(private val totalBytes: Long) {
        private var received = 0L
        private var reported = 0L

        fun add(bytes: Int) {
            received += bytes
            if (received - reported < ReportEveryBytes) return
            reported = received
            _state.value = RuntimeState.Downloading(received, totalBytes)
        }
    }

    /** [entry]'s content, its arrival counted - as stored, before it is unzipped, so the count meets the total. */
    private fun ZipSource.contentOf(entry: ZipEntryInfo, progress: Progress): InputStream {
        val counted = object : ZipSource by this {
            override fun open(offset: Long, length: Long): InputStream = this@contentOf.open(offset, length).counted(progress)
        }
        return counted.contentOf(entry)
    }

    private fun InputStream.counted(progress: Progress): InputStream = object : FilterInputStream(this) {
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = super.read(buffer, offset, length).also { if (it > 0) progress.add(it) }
    }
}

/** Copies this to [output], into [crc] too where one is given - stopping should the download be cancelled. */
private suspend fun InputStream.copyChecking(output: java.io.OutputStream, crc: CRC32?) {
    val buffer = ByteArray(65_536)
    while (true) {
        currentCoroutineContext().ensureActive()
        val read = read(buffer)
        if (read < 0) break
        output.write(buffer, 0, read)
        crc?.update(buffer, 0, read)
    }
}
