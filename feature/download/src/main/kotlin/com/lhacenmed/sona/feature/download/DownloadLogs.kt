package com.lhacenmed.sona.feature.download

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** How often an open log is read again for what was written since. */
private const val FollowEveryMs = 500L

/**
 * What each download said as it ran - every line the downloader wrote, and Sona's own steps - YTDLnis's download logs: kept
 * with it in the history, each run of it after the last, until it is taken off.
 */
@Singleton
class DownloadLogs @Inject constructor(@ApplicationContext context: Context) {
    private val root = File(context.filesDir, "download-logs")

    private fun fileOf(id: Long) = File(root, "$id.log")

    /** [id]'s log, opened to carry on after what it holds. */
    fun open(id: Long): DownloadLogWriter {
        root.mkdirs()
        return DownloadLogWriter(BufferedWriter(FileWriter(fileOf(id), true)))
    }

    /** [id]'s log as it stands, and again as it grows. */
    fun follow(id: Long): Flow<String> = flow {
        val file = fileOf(id)
        while (true) {
            emit(if (file.isFile) file.readText() else "")
            delay(FollowEveryMs)
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    /** Deletes the logs of [ids]. */
    fun discard(ids: Collection<Long>) = ids.forEach { fileOf(it).delete() }

    /** Deletes every log but those of [kept] - the downloads still listed. */
    fun discardAllBut(kept: Set<Long>) {
        root.listFiles().orEmpty().filter { it.nameWithoutExtension.toLongOrNull() !in kept }.forEach(File::delete)
    }
}

/** One run's writing into its download's log - each line on the disk as it is written, so a run cut off keeps all it said. */
class DownloadLogWriter internal constructor(private val writer: BufferedWriter) {
    @Synchronized
    fun append(line: String) {
        writer.write(line)
        writer.newLine()
        writer.flush()
    }

    @Synchronized
    fun close() = writer.close()
}
