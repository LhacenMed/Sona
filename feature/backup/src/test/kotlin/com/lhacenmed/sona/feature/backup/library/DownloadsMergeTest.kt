package com.lhacenmed.sona.feature.backup.library

import com.lhacenmed.sona.core.database.entity.DownloadCommandEntity
import com.lhacenmed.sona.core.database.entity.DownloadCommandShortcutEntity
import com.lhacenmed.sona.core.database.entity.DownloadEntity
import com.lhacenmed.sona.core.database.entity.DownloadWatchEntity
import com.lhacenmed.sona.feature.backup.BackedUpDownloads
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadsMergeTest {
    private fun download(id: Long, url: String, status: String, createdAt: Long = 1) = DownloadEntity(
        id = id, url = url, title = null, kind = "VIDEO", videoQuality = "P1080", audioFormat = "ORIGINAL", captions = true,
        skipSponsors = false, status = status, error = null, createdAt = createdAt,
    )

    private val backup = BackedUpDownloads(
        history = listOf(download(7, "a", "DONE"), download(8, "b", "FAILED"), download(9, "c", "QUEUED"), download(10, "d", "PAUSED")),
        commands = listOf(DownloadCommandEntity(3, "Clip", "-x"), DownloadCommandEntity(4, "Clip", "-k")),
        shortcuts = listOf(DownloadCommandShortcutEntity(5, "--no-mtime")),
        watches = listOf(DownloadWatchEntity(6, "https://youtube.com/@x", "X", "1\n2", 1)),
    )

    @Test
    fun onlyWhatIsOverIsAddedAsNewRows() {
        val plan = planDownloadsMerge(backup, BackedUpDownloads())
        assertEquals(listOf("a", "b"), plan.history.map { it.url })
        assertEquals(listOf(0L, 0L), plan.history.map { it.id })
        assertEquals(listOf("-x", "-k"), plan.commands.map { it.arguments })
        assertEquals(1, plan.shortcuts.size)
        assertEquals("1\n2", plan.watches.single().knownIds)
    }

    @Test
    fun whatTheDeviceHasIsNeverAddedAgain() {
        val local = BackedUpDownloads(
            history = listOf(download(1, "a", "DONE")),
            commands = listOf(DownloadCommandEntity(1, "Clip", "-x")),
            shortcuts = listOf(DownloadCommandShortcutEntity(1, "--no-mtime")),
            watches = listOf(DownloadWatchEntity(1, "https://youtube.com/@x", "X", "", 1)),
        )
        val plan = planDownloadsMerge(backup, local)
        assertEquals(listOf("b"), plan.history.map { it.url })
        assertEquals(listOf("-k"), plan.commands.map { it.arguments })
        assertEquals(0, plan.shortcuts.size + plan.watches.size)
        // The same link asked for again at another time is another download.
        val later = BackedUpDownloads(history = listOf(download(1, "a", "DONE", createdAt = 5)))
        assertEquals(1, planDownloadsMerge(later, local).history.size)
    }
}
