package com.lhacenmed.sona.core.data.lyrics

import android.content.Context
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.common.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where the Japanese dictionary stands on this device. */
sealed interface DictionaryState {
    data object Missing : DictionaryState

    /** [receivedBytes] of the [totalBytes] it takes, so far. */
    data class Downloading(val receivedBytes: Long, val totalBytes: Long) : DictionaryState {
        val progress: Float get() = (receivedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
    }

    data object Installed : DictionaryState

    data object Failed : DictionaryState
}

/**
 * The dictionary Japanese lyrics are romanized with - Kuromoji's IPADIC, which reads kanji as Japanese is
 * read. At 13 MB it is two thirds of the app, so it is not shipped in it: it is downloaded here, once, by
 * whoever wants Japanese romanized, and romanizing Japanese is on exactly while it is here.
 *
 * It is the very archive the app used to ship, fetched from Maven Central and checked against the digest
 * pinned below before it is used, so a romanization reads the same as it always did. It is kept in the app's
 * own storage under its version's name, where an update of the app leaves it: it is downloaded again only if
 * a later app moves to another version of it.
 */
@Singleton
class JapaneseDictionary @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val directory = File(context.filesDir, "romanization")
    private val archive = File(directory, ArchiveName)
    private val partial = File(directory, "$ArchiveName.part")

    private val _state = MutableStateFlow<DictionaryState>(DictionaryState.Missing)
    val state: StateFlow<DictionaryState> = _state.asStateFlow()

    private var download: Job? = null

    init {
        // A download the process did not live to finish, or a dictionary an earlier app used, is let go - two
        // looks at one small folder, done before anything can start another download into it.
        directory.listFiles()?.filter { it != archive }?.forEach(File::delete)
        if (archive.length() == ArchiveBytes) {
            JapaneseTokenizer.use(archive)
            _state.value = DictionaryState.Installed
        }
    }

    /** Downloads the dictionary, unless it is here already or on its way. */
    fun download() {
        if (_state.value == DictionaryState.Installed || download?.isActive == true) return
        _state.value = DictionaryState.Downloading(0, ArchiveBytes)
        download = scope.launch(ioDispatcher) {
            try {
                fetch()
                check(partial.renameTo(archive)) { "The dictionary could not be kept" }
                JapaneseTokenizer.use(archive)
                _state.value = DictionaryState.Installed
            } catch (e: CancellationException) {
                partial.delete()
                throw e
            } catch (e: Exception) {
                partial.delete()
                _state.value = DictionaryState.Failed
            }
        }
    }

    /** Stops a download under way, leaving nothing of it. */
    fun cancel() {
        download?.cancel()
        download = null
        partial.delete()
        if (_state.value is DictionaryState.Downloading) _state.value = DictionaryState.Missing
    }

    /** Takes the dictionary off the device, which turns romanizing Japanese off. */
    fun remove() {
        cancel()
        JapaneseTokenizer.release()
        archive.delete()
        _state.value = DictionaryState.Missing
    }

    /** Streams the archive into [partial], checking its digest as it arrives. */
    private suspend fun fetch() {
        directory.mkdirs()
        val connection = URL(ArchiveUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = TimeoutMillis
        connection.readTimeout = TimeoutMillis
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${connection.responseCode}" }
            val digest = MessageDigest.getInstance("SHA-256")
            var received = 0L
            var reported = 0L
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(BufferBytes)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        received += read
                        // A few dozen updates over the whole download: enough to move smoothly, not to flood.
                        if (received - reported >= ReportEveryBytes) {
                            reported = received
                            _state.value = DictionaryState.Downloading(received, ArchiveBytes)
                        }
                    }
                }
            }
            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            check(received == ArchiveBytes && sha256 == ArchiveSha256) { "The dictionary did not arrive whole" }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val ArchiveName = "kuromoji-ipadic-0.9.0.jar"
        const val ArchiveUrl = "https://repo1.maven.org/maven2/com/atilika/kuromoji/kuromoji-ipadic/0.9.0/$ArchiveName"
        const val ArchiveBytes = 13_343_016L
        const val ArchiveSha256 = "24909fd751c0b439f7af5131b080eb65bc85062f0c4977ca4a71c76abe74e0b6"
        const val TimeoutMillis = 20_000
        const val BufferBytes = 64 * 1024
        const val ReportEveryBytes = 256 * 1024L
    }
}
