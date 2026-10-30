package com.lhacenmed.sona.feature.backup.drive

import android.content.Context
import com.lhacenmed.sona.core.common.di.IoDispatcher
import com.lhacenmed.sona.feature.backup.BackedUpMedia
import com.lhacenmed.sona.feature.backup.BackupDestination
import com.lhacenmed.sona.feature.backup.StoredBackup
import com.lhacenmed.sona.feature.backup.archive.BackupCipher
import com.lhacenmed.sona.feature.backup.archive.BackupKey
import com.lhacenmed.sona.feature.backup.media.CloudMediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What each of Sona's files in Drive is, kept in its app properties - so it is found by what it is, not its name. */
private const val KIND = "sona"
private const val ROOT_FOLDER = "folder"
private const val BACKUP = "backup"
private const val MEDIA_FILE = "mediaFile"

private const val ROOT_FOLDER_NAME = "Sona Backup"

/** A folder the library's files are kept in: music or videos, plain or sealed with the backups' key. */
private enum class MediaFolder(val kind: String, val folderName: String) {
    MUSIC("music", "Music"),
    VIDEOS("videos", "Videos"),
    SEALED_MUSIC("sealedMusic", "Music (encrypted)"),
    SEALED_VIDEOS("sealedVideos", "Videos (encrypted)"),
    ;

    companion object {
        fun of(isSealed: Boolean, isVideo: Boolean) = when {
            isSealed && isVideo -> SEALED_VIDEOS
            isSealed -> SEALED_MUSIC
            isVideo -> VIDEOS
            else -> MUSIC
        }

        fun all(isSealed: Boolean) = listOf(of(isSealed, isVideo = false), of(isSealed, isVideo = true))
    }
}

/** Drive has no room for the files a backup would add. */
class DriveFullException : IOException()

/**
 * Backups kept in the user's Google Drive, in a "Sona Backup" folder they can see: the backups themselves, and -
 * in its Music and Videos folders - one copy of each of the library's files, named by fingerprint, for a restore
 * to bring back. Files of encrypted backups are kept sealed, in folders of their own.
 */
@Singleton
class DriveBackups @Inject internal constructor(
    @ApplicationContext private val context: Context,
    private val api: DriveApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BackupDestination {

    private val folderIds = mutableMapOf<String, String>()
    private val folderLock = Mutex()

    override suspend fun list(): List<StoredBackup> =
        api.find(mapOf(KIND to BACKUP), rootFolder())
            .map { StoredBackup(it.id, it.name, it.createdAt, it.sizeBytes) }
            .sortedByDescending { it.createdAt }

    override suspend fun add(name: String, write: (OutputStream) -> Unit) {
        val file = File(context.cacheDir, name)
        try {
            withContext(ioDispatcher) { file.outputStream().buffered().use(write) }
            api.upload(file, name, rootFolder(), mapOf(KIND to BACKUP))
        } finally {
            file.delete()
        }
    }

    override suspend fun open(backup: StoredBackup): InputStream = api.open(backup.id)

    override suspend fun delete(backup: StoredBackup) = api.delete(backup.id)

    /** The library's files kept here for backups sealed with [key] - or plain ones, for null. */
    internal fun media(key: BackupKey?) = DriveMedia(api, key, context.cacheDir, ioDispatcher) { isVideo ->
        val folder = MediaFolder.of(isSealed = key != null, isVideo)
        folder(folder.kind, folder.folderName, rootFolder())
    }

    /** Deletes the folders of sealed files - and them with them - once no encrypted backup is kept to need them. */
    internal suspend fun deleteSealedMedia() {
        MediaFolder.all(isSealed = true).forEach { folder ->
            api.find(mapOf(KIND to folder.kind)).forEach { api.delete(it.id) }
            folderLock.withLock { folderIds.remove(folder.kind) }
        }
    }

    private suspend fun rootFolder() = folder(ROOT_FOLDER, ROOT_FOLDER_NAME, parentId = null)

    /** The folder of [kind] - found, or made the first time - remembered for as long as the app runs. */
    private suspend fun folder(kind: String, name: String, parentId: String?): String = folderLock.withLock {
        folderIds.getOrPut(kind) {
            api.find(mapOf(KIND to kind)).firstOrNull()?.id ?: api.createFolder(name, mapOf(KIND to kind), parentId)
        }
    }
}

/**
 * The library's files in Drive - music in one folder, videos in another - each kept once under its fingerprint,
 * or, sealed with [key], under a name made from it with the key, so the name says nothing of which file it is.
 */
internal class DriveMedia(
    private val api: DriveApi,
    private val key: BackupKey?,
    private val cacheDir: File,
    private val ioDispatcher: CoroutineDispatcher,
    private val folderId: suspend (isVideo: Boolean) -> String,
) : CloudMediaStore {

    /** Every file kept, music and videos alike, by name to its id - read once, then kept up to date. */
    private var stored: MutableMap<String, String>? = null

    override suspend fun keeps(fingerprints: Collection<String>): Set<String> {
        val stored = stored()
        return fingerprints.filterTo(mutableSetOf()) { nameOf(it) in stored }
    }

    override suspend fun download(fingerprint: String, file: File) {
        val id = stored().getValue(nameOf(fingerprint))
        if (key == null) return api.download(id, file)
        val sealed = File(file.path + ".sealed")
        try {
            api.download(id, sealed)
            withContext(ioDispatcher) {
                sealed.inputStream().buffered().use { input ->
                    BackupCipher.saltOf(input)
                    BackupCipher.decrypting(input, key).use { plain -> file.outputStream().use { plain.copyTo(it) } }
                }
            }
        } finally {
            sealed.delete()
        }
    }

    /**
     * Uploads each of [media] not kept yet - into the music or the videos folder - [onProgress] hearing how many of
     * how many are done, having first made sure Drive has room for them all, so a backup never stops halfway for
     * want of space.
     */
    suspend fun upload(media: List<BackedUpMedia>, onProgress: (done: Int, total: Int) -> Unit) {
        val stored = stored()
        val missing = media.distinctBy { it.fingerprint }.filter { nameOf(it.fingerprint) !in stored }
        if (missing.isEmpty()) return
        api.freeBytes()?.let { free -> if (free < missing.sumOf { it.sizeBytes }) throw DriveFullException() }
        missing.forEachIndexed { index, item ->
            onProgress(index, missing.size)
            val name = nameOf(item.fingerprint)
            stored[name] = upload(File(item.path), name, folderId(item.isVideo)).id
        }
    }

    /** Deletes every file kept here that is not one of [fingerprints]. */
    suspend fun keepOnly(fingerprints: Set<String>) {
        val wanted = fingerprints.mapTo(HashSet(), ::nameOf)
        val stored = stored()
        stored.keys.filter { it !in wanted }.forEach { name -> api.delete(stored.remove(name)!!) }
    }

    /**
     * [file] uploaded as [name] - sealed first, where there is a key, into a copy in the cache kept until its upload
     * is done: sealing is never the same twice, so an upload carried on in a later run must send the same copy.
     */
    private suspend fun upload(file: File, name: String, folder: String): DriveFile {
        if (key == null) return api.upload(file, name, folder, mapOf(KIND to MEDIA_FILE))
        val sealed = File(cacheDir, "$name.sealed")
        if (!sealed.isFile) {
            val partial = File(cacheDir, "$name.sealed.part")
            withContext(ioDispatcher) {
                partial.outputStream().buffered().use { output ->
                    BackupCipher.encrypting(output, key).use { encrypting -> file.inputStream().use { it.copyTo(encrypting) } }
                }
                if (!partial.renameTo(sealed)) throw IOException("Could not seal $name")
            }
        }
        return api.upload(sealed, name, folder, mapOf(KIND to MEDIA_FILE)).also { sealed.delete() }
    }

    private suspend fun stored(): MutableMap<String, String> = stored ?: mutableMapOf<String, String>().also { all ->
        listOf(false, true).forEach { isVideo ->
            api.find(mapOf(KIND to MEDIA_FILE), folderId(isVideo)).associateTo(all) { it.name to it.id }
        }
        stored = all
    }

    private fun nameOf(fingerprint: String) = key?.let { BackupCipher.blobName(it, fingerprint) } ?: fingerprint
}
