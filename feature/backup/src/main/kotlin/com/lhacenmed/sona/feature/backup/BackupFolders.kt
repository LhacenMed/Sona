package com.lhacenmed.sona.feature.backup

import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.data.itemsOrNull
import com.lhacenmed.sona.core.model.Folder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * A library folder of one kind - its music, or its videos, as the Folders and Videos tabs list them - and whether
 * those files are backed up to Drive. A folder holding both is two of these, chosen apart.
 */
data class BackupFolder(
    val path: String,
    val name: String,
    val fileCount: Int,
    val isVideo: Boolean,
    val isBackedUp: Boolean,
)

/**
 * Which of the library's folders have their files backed up to Drive: every one, until the user leaves it out.
 * Leaving a folder out keeps its files off Drive, and lets go of those already there once no backup kept still
 * lists them; its files are still listed in every backup, for a restore to find them on the device.
 */
@Singleton
class BackupFolders @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val backupSettings: BackupSettings,
) {

    /** Every library folder - its music folders, then its video folders, each by name - or null while they are read. */
    val folders: Flow<List<BackupFolder>?> =
        combine(libraryRepository.folders, libraryRepository.videoFolders, backupSettings.foldersLeftOut) { music, videos, leftOut ->
            val musicFolders = music.itemsOrNull ?: return@combine null
            val videoFolders = videos.itemsOrNull ?: return@combine null
            (musicFolders + videoFolders).map { it.toBackupFolder(leftOut) }.sortedWith(compareBy({ it.isVideo }, { it.name.lowercase() }))
        }

    /** Whether the files of [folderPath] of that kind are backed up, with [leftOut] the folders the user left out. */
    internal fun isBackedUp(folderPath: String, isVideo: Boolean, leftOut: Set<String>): Boolean = leftOutEntry(folderPath, isVideo) !in leftOut

    suspend fun setBackedUp(folder: BackupFolder, isBackedUp: Boolean) =
        backupSettings.setFolderLeftOut(leftOutEntry(folder.path, folder.isVideo), isLeftOut = !isBackedUp)

    private fun Folder.toBackupFolder(leftOut: Set<String>) =
        BackupFolder(path, name, trackCount, isVideo, isBackedUp(path, isVideo, leftOut))
}

/** How a folder left out is stored: by its kind and its path, so its music and its videos are left out apart. */
private fun leftOutEntry(folderPath: String, isVideo: Boolean) = (if (isVideo) "videos:" else "music:") + folderPath
