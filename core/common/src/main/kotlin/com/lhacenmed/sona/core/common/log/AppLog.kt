package com.lhacenmed.sona.core.common.log

import android.os.Process

/**
 * What this process has written to Android's log - the debug tools' log, and what a bug report carries. An app may always
 * read its own lines, and only those are read. Each call blocks, so it is made off the main thread.
 */
object AppLog {
    /** The latest [count] lines, oldest first, each with its time. */
    fun recentLines(count: Int): List<String> = logcat("-d", "-v", "time", "-t", count.toString(), "--pid=${Process.myPid()}")

    /** Empties the log, so what is read next starts from now. */
    fun clear() {
        logcat("-c")
    }

    private fun logcat(vararg arguments: String): List<String> {
        val process = ProcessBuilder("logcat", *arguments).redirectErrorStream(true).start()
        return process.inputStream.bufferedReader().use { it.readLines() }
    }
}
