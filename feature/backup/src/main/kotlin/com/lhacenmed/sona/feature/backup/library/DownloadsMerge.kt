package com.lhacenmed.sona.feature.backup.library

import com.lhacenmed.sona.core.database.entity.DownloadCommandEntity
import com.lhacenmed.sona.core.database.entity.DownloadCommandShortcutEntity
import com.lhacenmed.sona.core.database.entity.DownloadEntity
import com.lhacenmed.sona.core.database.entity.DownloadWatchEntity
import com.lhacenmed.sona.core.model.DownloadStatus
import com.lhacenmed.sona.feature.backup.BackedUpDownloads

/** The downloader's rows a backup adds, each written as new - see [planDownloadsMerge]. */
data class DownloadsMergePlan(
    val history: List<DownloadEntity>,
    val commands: List<DownloadCommandEntity>,
    val shortcuts: List<DownloadCommandShortcutEntity>,
    val watches: List<DownloadWatchEntity>,
)

/**
 * Merges [backup] into what the device holds, [local] - each row one the device does not have, so merging the same backup
 * twice adds nothing the second time, and nothing of the device's is changed:
 * - a download over - saved, failed or cancelled - by its link and when it was asked for; one still to come is the device's
 *   own, its fetched parts no part of a backup;
 * - a command by its name and arguments, a shortcut by what it holds, a watched channel by its link.
 *
 * Each gets an id of its own here.
 */
fun planDownloadsMerge(backup: BackedUpDownloads, local: BackedUpDownloads): DownloadsMergePlan {
    val history = local.history.mapTo(HashSet()) { it.url to it.createdAt }
    val commands = local.commands.mapTo(HashSet()) { it.name to it.arguments }
    val shortcuts = local.shortcuts.mapTo(HashSet()) { it.content }
    val watches = local.watches.mapTo(HashSet()) { it.url }
    return DownloadsMergePlan(
        history = backup.history
            .filter { download -> DownloadStatus.entries.any { it.name == download.status && it.isFinished } && history.add(download.url to download.createdAt) }
            .map { it.copy(id = 0) },
        commands = backup.commands.filter { commands.add(it.name to it.arguments) }.map { it.copy(id = 0) },
        shortcuts = backup.shortcuts.filter { shortcuts.add(it.content) }.map { it.copy(id = 0) },
        watches = backup.watches.filter { watches.add(it.url) }.map { it.copy(id = 0) },
    )
}
