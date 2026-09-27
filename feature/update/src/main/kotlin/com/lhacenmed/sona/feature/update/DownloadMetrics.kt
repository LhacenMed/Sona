package com.lhacenmed.sona.feature.update

import android.content.Context
import android.text.format.Formatter

/** Rolling-window download speed tracker. Thread-safe via synchronized. */
class SpeedTracker(private val windowMs: Long = 3_000L) {

    private data class Sample(val time: Long, val bytes: Long)
    private val samples = ArrayDeque<Sample>()

    @Synchronized
    fun add(bytes: Long) {
        val now = System.currentTimeMillis()
        samples.addLast(Sample(now, bytes))
        prune(now)
    }

    @Synchronized
    fun bytesPerSec(): Long {
        val now = System.currentTimeMillis()
        prune(now)
        if (samples.size < 2) return 0L
        val windowBytes = samples.sumOf { it.bytes }
        val elapsed     = (now - samples.first().time).coerceAtLeast(1L)
        return windowBytes * 1_000L / elapsed
    }

    private fun prune(now: Long) {
        val cutoff = now - windowMs
        while (samples.isNotEmpty() && samples.first().time < cutoff) samples.removeFirst()
    }
}

/** How much has arrived, of how much - "4.2 MB / 18.6 MB" - or only how much, while the size is unknown. */
fun UpdateState.Downloading.sizeText(context: Context): String {
    val received = Formatter.formatShortFileSize(context, receivedBytes)
    return totalBytes?.let { context.getString(R.string.update_download_size, received, Formatter.formatShortFileSize(context, it)) }
        ?: received
}

/** How fast it is arriving - "1.3 MB/s". */
fun UpdateState.Downloading.speedText(context: Context): String =
    context.getString(R.string.update_download_speed, Formatter.formatShortFileSize(context, bytesPerSecond))
