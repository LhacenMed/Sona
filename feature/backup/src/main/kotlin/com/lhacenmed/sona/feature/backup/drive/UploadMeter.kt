package com.lhacenmed.sona.feature.backup.drive

import com.lhacenmed.sona.feature.backup.BackupProgress
import com.lhacenmed.sona.feature.backup.BackupStep
import com.lhacenmed.sona.feature.backup.Transfer

/** How long the speed is measured over before it is said again. */
private const val SPEED_WINDOW_NANOS = 1_000_000_000L

/**
 * Keeps count of the library's files going to Drive, several at once, byte by byte: how many files and bytes Drive
 * has - counted from [filesDone] and [bytesDone] it had before, so a backup carried on after a pause goes on from
 * where it was - how fast they go, and how much this run sent. Each change goes to [onChange].
 */
internal class UploadMeter(
    private var filesDone: Int,
    private val filesTotal: Int,
    private var bytesDone: Long,
    private val bytesTotal: Long,
    private val onChange: (BackupProgress) -> Unit,
) {
    /** How far each file under way has gone, by name. */
    private val positions = HashMap<String, Long>()
    private var sent = 0L
    private var windowStartedAt = System.nanoTime()
    private var windowStartSent = 0L
    private var bytesPerSecond = 0L

    /**
     * [name] has gone as far as [position]. Its first position is where it starts - 0, or where an earlier run left
     * it - so only what this run sends counts as sent; one going back, to send again, is sent again as it moves on.
     */
    @Synchronized
    fun moved(name: String, position: Long) {
        val last = positions.put(name, position)
        if (last != null && position > last) sent += position - last
        report()
    }

    /** [name], of [size] bytes, is in Drive. */
    @Synchronized
    fun finished(name: String, size: Long) {
        positions.remove(name)
        bytesDone += size
        filesDone++
        report()
    }

    /** [name] did not go, this time: what it had sent is no longer on its way. */
    @Synchronized
    fun failed(name: String) {
        positions.remove(name)
        report()
    }

    @Synchronized
    fun report() {
        val now = System.nanoTime()
        val elapsed = now - windowStartedAt
        if (elapsed >= SPEED_WINDOW_NANOS) {
            bytesPerSecond = (sent - windowStartSent) * SPEED_WINDOW_NANOS / elapsed
            windowStartedAt = now
            windowStartSent = sent
        }
        val transfer = Transfer(done = bytesDone + positions.values.sum(), total = bytesTotal, bytesPerSecond = bytesPerSecond, sent = sent)
        onChange(BackupProgress(BackupStep.UPLOADING_FILES, filesDone, filesTotal, transfer))
    }
}
