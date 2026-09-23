package com.lhacenmed.sona.feature.update

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

/** Formats a live download log line: "↓ 8.3 MB/s · 45.1 MB / 120.0 MB" */
fun formatDownloadLog(speedBps: Long, received: Long, total: Long? = null): String {
    val speed = "${formatBytes(speedBps)}/s"
    val recv  = formatBytes(received)
    return if (total != null && total > 0) "Downloading files... $speed · $recv / ${formatBytes(total)}"
    else "Downloading files... $speed · $recv"
}

fun formatBytes(bytes: Long): String = when {
    bytes < 1_024L         -> "$bytes B"
    bytes < 1_048_576L     -> "%.1f KB".format(bytes / 1_024f)
    bytes < 1_073_741_824L -> "%.1f MB".format(bytes / 1_048_576f)
    else                   -> "%.2f GB".format(bytes / 1_073_741_824f)
}