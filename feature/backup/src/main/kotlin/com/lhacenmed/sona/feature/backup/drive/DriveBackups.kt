package com.lhacenmed.sona.feature.backup.drive

import android.content.Context
import com.lhacenmed.sona.core.common.di.IoDispatcher
import com.lhacenmed.sona.feature.backup.BackedUpMedia
import com.lhacenmed.sona.feature.backup.BackupDestination
import com.lhacenmed.sona.feature.backup.BackupProgress
import com.lhacenmed.sona.feature.backup.StoredBackup
import com.lhacenmed.sona.feature.backup.archive.BackupCipher
import com.lhacenmed.sona.feature.backup.archive.BackupKey
import com.lhacenmed.sona.feature.backup.media.CloudMediaStore
import com.lhacenmed.sona.core.data.fingerprint.fingerprintOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What each of Sona's files in Drive is, kept in its app properties - so it is found by what it is, not its name. */
private const val KIND = "sona"
private const val ROOT_FOLDER = "folder"
private const val BACKUP = "backup"
private const val MEDIA_FILE = "mediaFile"
private const val SEALED_MEDIA_FILE = "sealedMediaFile"

/** How many files upload at once: enough to keep a connection full, few enough for what Drive takes from one app. */
private const val PARALLEL_UPLOADS = 4

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

/** How uploading the library's files ended: how many did not go - for want of room in Drive, or for an error. */
internal data class MediaUpload(val leftForSpace: Int, val failed: Int)

/**
 * Backups kept in the user's Google Drive, in a "Sona Backup" folder they can see: the backups themselves, and -
 * in its Music and Videos folders - one copy of each of the library's files, named by fingerprint, for a restore
 * to bring back. Files of encrypted backups are kept sealed, in folders of their own.
 */
@Singleton
class DriveBackups @Inject internal constructor(
    @ApplicationContext private val context: Context,
    private val api: DriveApi,
    private val access: DriveAccess,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BackupDestination {

    private val folderIds = mutableMapOf<String, String>()
    private var folderAccount: String? = null
    private val folderLock = Mutex()

    override suspend fun list(): List<StoredBackup> =
        inFolder(::rootFolder) { folderId -> api.find(mapOf(KIND to BACKUP), folderId) }
            .map { StoredBackup(it.id, it.name, it.createdAt, it.sizeBytes) }
            .sortedByDescending { it.createdAt }

    /** Keeps the backup only once Drive holds the very bytes written - one that arrived otherwise is deleted. */
    override suspend fun add(name: String, write: (OutputStream) -> Unit): String {
        val file = File(context.cacheDir, name)
        try {
            val written = withContext(ioDispatcher) {
                file.outputStream().buffered().use(write)
                fingerprintOf(file)
            }
            val uploaded = inFolder(::rootFolder) { folderId -> api.upload(file, name, folderId, mapOf(KIND to BACKUP)) }
            val kept = api.checksumOf(uploaded.id)
            if (kept != null && kept != written) {
                api.delete(uploaded.id)
                throw IOException("Drive kept $name damaged")
            }
            return written
        } finally {
            file.delete()
        }
    }

    override suspend fun checksumOf(backup: StoredBackup): String? = api.checksumOf(backup.id)

    override suspend fun open(backup: StoredBackup): InputStream = api.open(backup.id)

    override suspend fun delete(backup: StoredBackup) = api.delete(backup.id)

    /** The library's files kept here for backups sealed with [key] - or plain ones, for null. */
    internal fun media(key: BackupKey?) = DriveMedia(api, key, context.cacheDir, ioDispatcher) { isVideo, upload ->
        val folder = MediaFolder.of(isSealed = key != null, isVideo)
        inFolder({ folder(folder.kind, folder.folderName, rootFolder()) }, upload)
    }

    /** Deletes the folders of sealed files - and them with them - once no encrypted backup is kept to need them. */
    internal suspend fun deleteSealedMedia() {
        MediaFolder.all(isSealed = true).forEach { folder ->
            api.find(mapOf(KIND to folder.kind)).forEach { api.delete(it.id) }
            folderLock.withLock { folderIds.remove(folder.kind) }
        }
    }

    private suspend fun rootFolder() = folder(ROOT_FOLDER, ROOT_FOLDER_NAME, parentId = null)

    /**
     * [block] run in the folder [folderOf] gives - and once more, in the folder found or made again, where Drive says
     * it is gone: deleted in Drive since it was found, it and the folders remembered with it are let go of.
     */
    private suspend fun <T> inFolder(folderOf: suspend () -> String, block: suspend (folderId: String) -> T): T {
        val folderId = folderOf()
        return try {
            block(folderId)
        } catch (e: DriveHttpException) {
            if (e.code != HTTP_NOT_FOUND) throw e
            // Only the first of the uploads under way to find it gone lets go; the rest use what that one found again.
            folderLock.withLock { if (folderId in folderIds.values) folderIds.clear() }
            block(folderOf())
        }
    }

    /** The folder of [kind] - found, or made the first time - remembered for as long as the app runs, for its account. */
    private suspend fun folder(kind: String, name: String, parentId: String?): String = folderLock.withLock {
        // Another account has folders of its own: those found for the last one are let go of.
        val account = access.account.value
        if (account != folderAccount) {
            folderIds.clear()
            folderAccount = account
        }
        folderIds.getOrPut(kind) {
            api.find(mapOf(KIND to kind)).firstOrNull()?.id ?: api.createFolder(name, mapOf(KIND to kind), parentId)
        }
    }
}

/**
 * The library's files in Drive - music in one folder, videos in another - each kept once under its fingerprint,
 * or, sealed with [key], under a name made from it with the key, so the name says nothing of which file it is.
 *
 * A file is kept once it is anywhere in Drive under its name and kind - whatever folder holds it - so nothing is
 * uploaded twice; a copy beyond the first, left by an older version, is let go of when Drive is tidied.
 */
internal class DriveMedia(
    private val api: DriveApi,
    private val key: BackupKey?,
    private val cacheDir: File,
    private val ioDispatcher: CoroutineDispatcher,
    /** Runs an upload in the music or the videos folder - see [DriveBackups.inFolder]. */
    private val inFolder: suspend (isVideo: Boolean, upload: suspend (folderId: String) -> DriveFile) -> DriveFile,
) : CloudMediaStore {

    /** Plain and sealed files are told apart by kind, so tidying the one never takes the other. */
    private val fileKind = if (key == null) MEDIA_FILE else SEALED_MEDIA_FILE

    /** Every file kept, music and videos alike, by name to its id - read once, then kept up to date. */
    private var stored: MutableMap<String, String>? = null

    /** The ids of copies of a file beyond its first. */
    private val extraCopies = mutableListOf<String>()
    private val storedLock = Mutex()

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
     * Uploads each of [media] not kept yet - into the music or the videos folder - [PARALLEL_UPLOADS] at a time,
     * [onProgress] hearing, byte by byte, how far they are - counted with those Drive already has. As many go as
     * Drive has room for, not none when not all fit; one that fails is left for the next backup, and the rest go on.
     */
    suspend fun upload(media: List<BackedUpMedia>, onProgress: (BackupProgress) -> Unit): MediaUpload = coroutineScope {
        val stored = stored()
        val (kept, missing) = media.distinctBy { it.fingerprint }.partition { nameOf(it.fingerprint) in stored }
        if (missing.isEmpty()) return@coroutineScope MediaUpload(leftForSpace = 0, failed = 0)
        var room = api.freeBytes() ?: Long.MAX_VALUE
        val fitting = missing.filter { item -> (item.sizeBytes <= room).also { fits -> if (fits) room -= item.sizeBytes } }
        val meter = UploadMeter(
            filesDone = kept.size,
            filesTotal = kept.size + fitting.size,
            bytesDone = kept.sumOf { it.sizeBytes },
            bytesTotal = (kept + fitting).sumOf { it.sizeBytes },
            onChange = onProgress,
        )
        var failed = 0
        val slots = Semaphore(PARALLEL_UPLOADS)
        meter.report()
        fitting.map { item ->
            launch {
                slots.withPermit {
                    val name = nameOf(item.fingerprint)
                    try {
                        val id = inFolder(item.isVideo) { folderId ->
                            upload(File(item.path), name, folderId) { position -> meter.moved(name, position) }
                        }.id
                        storedLock.withLock { stored[name] = id }
                        meter.finished(name, item.sizeBytes)
                    } catch (e: DriveAccessLostException) {
                        throw e // No file can go on without access: every upload stops.
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        storedLock.withLock { failed++ }
                        meter.failed(name)
                    }
                }
            }
        }.joinAll()
        MediaUpload(leftForSpace = missing.size - fitting.size, failed = failed)
    }

    /** Deletes every file kept here that is not one of [fingerprints], and every copy of a file beyond its first. */
    suspend fun keepOnly(fingerprints: Set<String>) {
        val wanted = fingerprints.mapTo(HashSet(), ::nameOf)
        val stored = stored()
        (stored.filterKeys { it !in wanted }.values + extraCopies).forEach { api.delete(it) }
        stored.keys.retainAll(wanted)
        extraCopies.clear()
    }

    /**
     * [file] uploaded as [name] - sealed first, where there is a key, into a copy in the cache kept until its upload
     * is done: sealing is never the same twice, so an upload carried on in a later run must send the same copy.
     */
    private suspend fun upload(file: File, name: String, folder: String, onProgress: (position: Long) -> Unit): DriveFile {
        if (key == null) return api.upload(file, name, folder, mapOf(KIND to fileKind), onProgress)
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
        return api.upload(sealed, name, folder, mapOf(KIND to fileKind), onProgress).also { sealed.delete() }
    }

    /** Every file of this kind in Drive, wherever it is - one for each name, the copies beyond it set aside. */
    private suspend fun stored(): MutableMap<String, String> = stored ?: mutableMapOf<String, String>().also { all ->
        api.find(mapOf(KIND to fileKind)).forEach { file -> if (all.putIfAbsent(file.name, file.id) != null) extraCopies += file.id }
        stored = all
    }

    private fun nameOf(fingerprint: String) = key?.let { BackupCipher.blobName(it, fingerprint) } ?: fingerprint
}
