package com.lhacenmed.sona.feature.backup

import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.data.itemsOrNull
import com.lhacenmed.sona.core.model.Folder
import com.lhacenmed.sona.core.model.FolderPicks
import com.lhacenmed.sona.core.model.Track
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
 * The library's music and videos as the folder picker offers them, and [picked] the keys of those backed up - each
 * kind keyed apart, by [BackupFolders.MUSIC_KEY_PREFIX] or [BackupFolders.VIDEOS_KEY_PREFIX], see [FolderPicks].
 */
data class BackupFilePicks(val music: List<Track>, val videos: List<Track>, val picked: Set<String>)

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
        combine(libraryRepository.mediaFolders, libraryRepository.mediaVideoFolders, backupSettings.foldersLeftOut) { music, videos, leftOut ->
            val musicFolders = music.itemsOrNull ?: return@combine null
            val videoFolders = videos.itemsOrNull ?: return@combine null
            (musicFolders + videoFolders).map { it.toBackupFolder(leftOut) }.sortedWith(compareBy({ it.isVideo }, { it.name.lowercase() }))
        }

    /** The library's files, and which are backed up - null while they are read. */
    val filePicks: Flow<BackupFilePicks?> =
        combine(libraryRepository.tracks, libraryRepository.videos, backupSettings.foldersLeftOut) { music, videos, leftOut ->
            val musicFiles = music.itemsOrNull ?: return@combine null
            val videoFiles = videos.itemsOrNull ?: return@combine null
            BackupFilePicks(musicFiles, videoFiles, musicPicksOf(musicFiles).allBut(leftOut) + videoPicksOf(videoFiles).allBut(leftOut))
        }

    /**
     * Whether the file at [path], in [folderPath], of that kind is backed up, with [leftOut] the folders and files the
     * user left out.
     */
    internal fun isBackedUp(folderPath: String, path: String, isVideo: Boolean, leftOut: Set<String>): Boolean =
        leftOutEntry(folderPath, isVideo) !in leftOut && leftOutEntry(path, isVideo) !in leftOut

    /**
     * Backs up the folders and files [picked] of [files] - see [BackupFilePicks] - and leaves the rest out; what was
     * left out of a folder no longer in the library stays left out, should it come back.
     */
    suspend fun backUpOnly(picked: Set<String>, files: BackupFilePicks) {
        val musicPicks = musicPicksOf(files.music)
        val videoPicks = videoPicksOf(files.videos)
        val elsewhere = backupSettings.foldersLeftOutNow().filterNotTo(HashSet()) { musicPicks.owns(it) || videoPicks.owns(it) }
        backupSettings.setFoldersLeftOut(elsewhere + musicPicks.allBut(picked) + videoPicks.allBut(picked))
    }

    companion object {
        const val MUSIC_KEY_PREFIX = "music:"
        const val VIDEOS_KEY_PREFIX = "videos:"
    }

    private fun Folder.toBackupFolder(leftOut: Set<String>) =
        BackupFolder(path, name, trackCount, isVideo, isBackedUp = leftOutEntry(path, isVideo) !in leftOut)
}

/** How a folder or a file left out is stored: by its kind and its path, so a folder's music and its videos are left out apart. */
private fun leftOutEntry(path: String, isVideo: Boolean) =
    (if (isVideo) BackupFolders.VIDEOS_KEY_PREFIX else BackupFolders.MUSIC_KEY_PREFIX) + path

private fun musicPicksOf(music: List<Track>) = FolderPicks(music, BackupFolders.MUSIC_KEY_PREFIX)

private fun videoPicksOf(videos: List<Track>) = FolderPicks(videos, BackupFolders.VIDEOS_KEY_PREFIX)
