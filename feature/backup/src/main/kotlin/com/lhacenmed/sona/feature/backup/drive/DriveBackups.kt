package com.lhacenmed.sona.feature.backup.drive

import android.content.Context
import com.lhacenmed.sona.core.common.di.IoDispatcher
import com.lhacenmed.sona.feature.backup.BackedUpMedia
import com.lhacenmed.sona.feature.backup.BackupDestination
import com.lhacenmed.sona.feature.backup.BackupProgress
import com.lhacenmed.sona.feature.backup.BackupStep
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
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
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

/** What else a library file in Drive keeps in its app properties - see [DriveMedia]. */
private const val MEDIA_IDENTITY = "identity"
private const val MEDIA_PATH = "path"
private const val SEALED_CHECKSUM = "sealedChecksum"

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
 * in its Music and Videos folders - one copy of each of the library's files, under its own name, for a restore to
 * bring back. Files of encrypted backups are kept sealed, in folders of their own.
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
 * The library's files in Drive - music in one folder, videos in another - one Drive file for each, seen under the
 * file's own name. Sealed with [key], it goes under a name made from its fingerprint with the key instead, so the
 * name says nothing of which file it is.
 *
 * What a Drive file is, is kept in its properties: its [MEDIA_IDENTITY], and the path it came from. So a file moved
 * or renamed is not sent again, only its details catching up, and one whose tags changed updates its Drive file rather
 * than adding another - see [planDriveMedia].
 *
 * A file counts as kept only where Drive's own checksum of what it holds is the one it should be: an upload is checked
 * as it ends, and everything Drive holds as it is listed, so a damaged file is found and sent again however it got
 * that way. A copy of a file beyond its best, left by an older version, is let go of when Drive is tidied.
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

    /** Every file kept, music and videos alike, by identity - read once, then kept up to date. */
    private var stored: MutableMap<String, StoredMedia>? = null

    /** The ids of copies of a file beyond its best. */
    private val extraCopies = mutableListOf<String>()
    private val storedLock = Mutex()

    /** Those of [fingerprints] Drive has to give back: all but a file known damaged, which a restore would only refuse. */
    override suspend fun keeps(fingerprints: Collection<String>): Set<String> {
        val stored = stored()
        return fingerprints.filterTo(mutableSetOf()) { stored[identityOf(it)]?.isDamaged == false }
    }

    /** Whether Drive already holds each of [media] whole, under the name and path it has now: nothing is left to do. */
    suspend fun isSettled(media: List<BackedUpMedia>): Boolean =
        planDriveMedia(wantedOf(media), stored().values).all { it is MediaStep.Keep && !it.needsDetails }

    override suspend fun download(fingerprint: String, file: File) {
        val id = stored().getValue(identityOf(fingerprint)).id
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
     * Brings Drive to what [media] asks - see [planDriveMedia] - [PARALLEL_UPLOADS] files at a time, [onProgress]
     * hearing, byte by byte, how far they are - counted with those Drive already has. As many go as Drive has room
     * for, not none when not all fit; one that fails is left for the next backup, and the rest go on.
     */
    suspend fun upload(media: List<BackedUpMedia>, onProgress: (BackupProgress) -> Unit): MediaUpload = coroutineScope {
        val steps = planDriveMedia(wantedOf(media), stored().values)
        val kept = steps.filterIsInstance<MediaStep.Keep>()
        val uploads = steps.filterIsInstance<MediaStep.Upload>()
        val slots = Semaphore(PARALLEL_UPLOADS)
        updateDetails(kept.filter { it.needsDetails }, slots, onProgress)
        if (uploads.isEmpty()) return@coroutineScope MediaUpload(leftForSpace = 0, failed = 0)
        // A file updated takes about the room it already had; only a new one needs more.
        var room = api.freeBytes() ?: Long.MAX_VALUE
        val fitting = uploads.filter { step ->
            val size = step.wanted.media.sizeBytes
            step.replacing != null || (size <= room).also { fits -> if (fits) room -= size }
        }
        val meter = UploadMeter(
            filesDone = kept.size,
            filesTotal = kept.size + fitting.size,
            bytesDone = kept.sumOf { it.wanted.media.sizeBytes },
            bytesTotal = (kept + fitting).sumOf { it.wanted.media.sizeBytes },
            onChange = onProgress,
        )
        var failed = 0
        meter.report()
        fitting.map { step ->
            launch {
                slots.withPermit {
                    val identity = step.wanted.identity
                    try {
                        val uploaded = inFolder(step.wanted.media.isVideo) { folderId ->
                            send(step, folderId) { position -> meter.moved(identity, position) }
                        }
                        storedLock.withLock {
                            step.replacing?.let { stored().remove(it.identity) }
                            stored()[identity] = uploaded.toStoredMedia()
                        }
                        meter.finished(identity, step.wanted.media.sizeBytes)
                    } catch (e: DriveAccessLostException) {
                        throw e // No file can go on without access: every upload stops.
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        storedLock.withLock { failed++ }
                        meter.failed(identity)
                    }
                }
            }
        }.joinAll()
        MediaUpload(leftForSpace = uploads.size - fitting.size, failed = failed)
    }

    /** Brings each of [steps] up to its name and path. One that fails stays as it is, for the next backup to try again. */
    private suspend fun updateDetails(steps: List<MediaStep.Keep>, slots: Semaphore, onProgress: (BackupProgress) -> Unit) = coroutineScope {
        val done = AtomicInteger()
        steps.map { step ->
            launch {
                slots.withPermit {
                    try {
                        api.updateDetails(step.stored.id, step.wanted.name, propertiesOf(step.wanted))
                        storedLock.withLock { stored()[step.wanted.identity] = step.stored.copy(name = step.wanted.name, pathId = step.wanted.pathId) }
                    } catch (e: DriveAccessLostException) {
                        throw e
                    } catch (_: IOException) {
                        // Only the details: the file itself is safe in Drive.
                    }
                    onProgress(BackupProgress(BackupStep.UPLOADING_FILES, done.incrementAndGet(), steps.size))
                }
            }
        }.joinAll()
    }

    /** Deletes every file kept here that is not one of [fingerprints], and every copy of a file beyond its best. */
    suspend fun keepOnly(fingerprints: Set<String>) {
        val wanted = fingerprints.mapTo(HashSet(), ::identityOf)
        val stored = stored()
        (stored.filterKeys { it !in wanted }.values.map { it.id } + extraCopies).forEach { api.delete(it) }
        stored.keys.retainAll(wanted)
        extraCopies.clear()
    }

    /** Each of [media] as Drive should know it, once for each file's bytes. */
    private fun wantedOf(media: List<BackedUpMedia>): List<WantedMedia> = media.distinctBy { it.fingerprint }.map { item ->
        val identity = identityOf(item.fingerprint)
        WantedMedia(
            media = item,
            identity = identity,
            name = if (key == null) File(item.path).name else identity,
            pathId = key?.let { BackupCipher.blobName(it, item.path) } ?: fingerprintOf(item.path.byteInputStream()),
        )
    }

    private fun propertiesOf(wanted: WantedMedia) = mapOf(KIND to fileKind, MEDIA_IDENTITY to wanted.identity, MEDIA_PATH to wanted.pathId)

    /**
     * What [step] uploads, checked as it arrives - sealed first, where there is a key, into a copy in the cache kept until
     * its upload is done: sealing is never the same twice, so an upload carried on in a later run must send the same copy.
     */
    private suspend fun send(step: MediaStep.Upload, folder: String, onProgress: (position: Long) -> Unit): DriveFile {
        val wanted = step.wanted
        val file = File(wanted.media.path)
        val properties = propertiesOf(wanted)
        val replacing = step.replacing?.id
        if (key == null) {
            return api.upload(file, wanted.name, folder, properties, onProgress, replacing, wanted.identity).checkedAgainst(wanted.media.fingerprint)
        }
        val sealed = sealedCopyOf(file, wanted, key)
        val checksum = withContext(ioDispatcher) { fingerprintOf(sealed) }
        return api.upload(sealed, wanted.name, folder, properties + (SEALED_CHECKSUM to checksum), onProgress, replacing, wanted.identity)
            .checkedAgainst(checksum)
            .also { sealed.delete() }
    }

    /** The sealed copy of [file] in the cache, made now where there is none - only of the very bytes [wanted] names. */
    private suspend fun sealedCopyOf(file: File, wanted: WantedMedia, key: BackupKey): File {
        val sealed = File(cacheDir, "${wanted.identity}.sealed")
        if (sealed.isFile) return sealed
        val partial = File(cacheDir, "${wanted.identity}.sealed.part")
        withContext(ioDispatcher) {
            val digest = MessageDigest.getInstance("SHA-256")
            partial.outputStream().buffered().use { output ->
                BackupCipher.encrypting(output, key).use { encrypting ->
                    DigestInputStream(file.inputStream(), digest).use { it.copyTo(encrypting) }
                }
            }
            if (digest.digest().joinToString("") { "%02x".format(it) } != wanted.media.fingerprint) {
                partial.delete()
                throw IOException("${file.name} changed while it was read")
            }
            if (!partial.renameTo(sealed)) throw IOException("Could not seal ${wanted.identity}")
        }
        return sealed
    }

    /** This file - unless Drive's checksum of it is not [expected]: it is deleted, and goes again with the next backup. */
    private suspend fun DriveFile.checkedAgainst(expected: String): DriveFile {
        val kept = checksum ?: api.checksumOf(id)
        if (kept != null && kept != expected) {
            api.delete(id)
            throw IOException("Drive kept $name damaged")
        }
        return this
    }

    /** Every file of this kind in Drive, wherever it is - one for each identity, its best, the other copies set aside. */
    private suspend fun stored(): MutableMap<String, StoredMedia> = stored ?: mutableMapOf<String, StoredMedia>().also { all ->
        api.find(mapOf(KIND to fileKind)).map { it.toStoredMedia() }.forEach { file ->
            val other = all[file.identity]
            when {
                other == null -> all[file.identity] = file
                file.isWhole && !other.isWhole -> {
                    extraCopies += other.id
                    all[file.identity] = file
                }
                else -> extraCopies += file.id
            }
        }
        stored = all
    }

    /**
     * What this file is, by its properties - or, for one sent before they were kept, its name. Its bytes should have
     * its identity for a checksum where it is plain, and the checksum it was sent with where it is sealed.
     */
    private fun DriveFile.toStoredMedia(): StoredMedia {
        val identity = properties[MEDIA_IDENTITY] ?: name
        val expected = if (key == null) identity else properties[SEALED_CHECKSUM]
        val isDamaged = expected != null && checksum != null && checksum != expected
        return StoredMedia(id, name, identity, properties[MEDIA_PATH], isWhole = expected != null && !isDamaged, isDamaged = isDamaged)
    }

    private fun identityOf(fingerprint: String) = key?.let { BackupCipher.blobName(it, fingerprint) } ?: fingerprint
}
