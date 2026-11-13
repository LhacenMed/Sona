package com.lhacenmed.sona.core.downloader

/**
 * How far one download is as a whole, across every file yt-dlp fetches for it - a video's sound, then its picture - rather
 * than each file's own: it fills once, from nothing to whole, against the size yt-dlp expects of them all together.
 */
internal class WholeProgress {
    /** What every file of the download comes to, as yt-dlp expects - null while it has not said, or cannot tell. */
    private var expectedBytes: Long? = null

    /** What the files fetched before the one fetching now came to. */
    private var finishedBytes = 0L
    private var current: DownloadEvent.Progress? = null

    /** Takes in [line], where it is what yt-dlp expects the download to come to - see [YtDlpLines.SIZE]. */
    fun read(line: String) {
        val sizes = line.substringAfter("${YtDlpLines.SIZE} ", "").takeIf { line.startsWith(YtDlpLines.SIZE) } ?: return
        val (sound, picture, whole) = sizes.split(' ').map { it.toDoubleOrNull()?.toLong() }.let { Triple(it.getOrNull(0), it.getOrNull(1), it.getOrNull(2)) }
        // Another of a playlist's videos begins, or the one download: counted from its start.
        expectedBytes = (if (sound != null || picture != null) (sound ?: 0) + (picture ?: 0) else whole)?.takeIf { it > 0 }
        finishedBytes = 0
        current = null
    }

    /** [event] as the download as a whole goes: a file's progress made the whole's. */
    fun of(event: DownloadEvent): DownloadEvent = when (event) {
        is DownloadEvent.Part -> event.also {
            finishedBytes += current?.let { it.totalBytes ?: it.downloadedBytes } ?: 0
            current = null
        }

        is DownloadEvent.Progress -> {
            current = event
            val done = finishedBytes + (event.downloadedBytes ?: 0)
            val total = maxOf(expectedBytes ?: 0, finishedBytes + (event.totalBytes ?: 0)).takeIf { it > 0 }
            DownloadEvent.Progress(done, total, event.bytesPerSecond, fileFraction = event.fraction)
        }

        else -> event
    }
}
