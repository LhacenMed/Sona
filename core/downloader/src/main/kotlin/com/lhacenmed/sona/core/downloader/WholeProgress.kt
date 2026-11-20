package com.lhacenmed.sona.core.downloader

import java.io.File

/**
 * How far one download is as a whole, across every file yt-dlp fetches for it - a video's sound, then its picture - rather
 * than each file's own: it fills once, from nothing to whole, against the size yt-dlp expects of them all together. Where it
 * [countsStreamsOnly], the captions fetched before them - again each time it is resumed - are no part of it: a file of a few
 * kilobytes, whole at once, would read as the download whole and being made ready, then start over.
 */
internal class WholeProgress(private val countsStreamsOnly: Boolean = true) {
    /** Whether the file fetching now is counted - see [countsStreamsOnly]. */
    private var isCounted = true

    /** What every file of the download comes to, as yt-dlp expects - null while it has not said, or cannot tell. */
    private var expectedBytes: Long? = null

    /** What the files fetched before the one fetching now came to. */
    private var finishedBytes = 0L
    private var current: DownloadEvent.Progress? = null

    /** Takes in [line], where it names the file fetched now, or is what yt-dlp expects the download to come to - see [YtDlpLines.SIZE]. */
    fun read(line: String) {
        fileBegunIn(line)?.let { isCounted = !countsStreamsOnly || it.isStream }
        val sizes = line.substringAfter("${YtDlpLines.SIZE} ", "").takeIf { line.startsWith(YtDlpLines.SIZE) } ?: return
        val (sound, picture, whole) = sizes.split(' ').map { it.toDoubleOrNull()?.toLong() }.let { Triple(it.getOrNull(0), it.getOrNull(1), it.getOrNull(2)) }
        // Another of a playlist's videos begins, or the one download: counted from its start.
        expectedBytes = (if (sound != null || picture != null) (sound ?: 0) + (picture ?: 0) else whole)?.takeIf { it > 0 }
        finishedBytes = 0
        current = null
    }

    /** [event] as the download as a whole goes: a file's progress made the whole's - null for one not counted. */
    fun of(event: DownloadEvent): DownloadEvent? = when (event) {
        is DownloadEvent.Part -> event.also {
            finishedBytes += current?.let { it.totalBytes ?: it.downloadedBytes } ?: 0
            // One fetched whole before - the download resumed - counted whole, as yt-dlp fetches it no more.
            current = event.whole.takeIf(File::exists)?.length()?.let { DownloadEvent.Progress(it, it, null) }
        }

        // One saying only its size - a stream fetched whole before, as yt-dlp passes it by - says nothing of how far it is.
        is DownloadEvent.Progress -> if (!isCounted || event.downloadedBytes == null) null else {
            current = event
            val done = finishedBytes + (event.downloadedBytes ?: 0)
            val total = maxOf(expectedBytes ?: 0, finishedBytes + (event.totalBytes ?: 0)).takeIf { it > 0 }
            DownloadEvent.Progress(done, total, event.bytesPerSecond, fileFraction = event.fraction)
        }

        else -> event
    }
}
