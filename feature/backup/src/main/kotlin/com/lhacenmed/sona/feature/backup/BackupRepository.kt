package com.lhacenmed.sona.feature.backup

import android.content.Context
import android.net.ConnectivityManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import androidx.core.content.pm.PackageInfoCompat
import androidx.room.withTransaction
import com.lhacenmed.sona.core.common.di.IoDispatcher
import com.lhacenmed.sona.core.data.background.CustomBackgroundImages
import com.lhacenmed.sona.core.data.playlist.PlaylistCoverImages
import com.lhacenmed.sona.core.database.SonaDatabase
import com.lhacenmed.sona.core.database.dao.BackupDao
import com.lhacenmed.sona.core.database.entity.PlaylistCoverSource
import com.lhacenmed.sona.core.datastore.PlayerStyleSettings
import com.lhacenmed.sona.core.datastore.SettingsBackup
import com.lhacenmed.sona.core.designsystem.theme.CustomFontLoader
import com.lhacenmed.sona.feature.backup.archive.BackupArchive
import com.lhacenmed.sona.feature.backup.archive.BackupCipher
import com.lhacenmed.sona.feature.backup.archive.BackupContents
import com.lhacenmed.sona.feature.backup.archive.BackupEntries
import com.lhacenmed.sona.feature.backup.archive.BackupKey
import com.lhacenmed.sona.feature.backup.archive.UnsupportedBackupException
import com.lhacenmed.sona.feature.backup.drive.DriveBackups
import com.lhacenmed.sona.feature.backup.library.LocalLibrary
import com.lhacenmed.sona.feature.backup.library.planLibraryMerge
import com.lhacenmed.sona.core.data.fingerprint.MediaFingerprints
import com.lhacenmed.sona.feature.backup.media.MediaPlan
import com.lhacenmed.sona.feature.backup.media.MediaRestorer
import com.lhacenmed.sona.feature.backup.media.reconcileMedia
import com.lhacenmed.sona.feature.scanner.MediaScanner
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

private const val RESTORE_DIRECTORY_NAME = "restore"
private const val INCOMING_DIRECTORY_NAME = ".incoming"
private const val FILES_TO_RESTORE_NAME = "files-to-restore"
private const val RESTORE_KEY_NAME = "key"

/** How a backup ended. */
enum class BackupOutcome {
    /** Nothing written: it would have held just what the last one did. */
    UNCHANGED,

    SAVED,

    /** Saved, its music and videos waiting for Wi-Fi to go to Drive. */
    SAVED_FILES_WAITING,

    /** Saved, some of its music and videos not uploaded for an error - for the next backup to try again. */
    SAVED_FILES_LEFT,

    /** Saved, some of its music and videos not uploaded for want of room in Drive. */
    SAVED_DRIVE_FULL,
}

/** What restoring an opened backup would do, worked out before anything changes. */
data class RestorePreview(
    val createdAt: Long,
    val playlistCount: Int,
    val playedTrackCount: Int,
    val media: MediaPlan,
)

/** How opening a backup went. */
sealed interface OpenedBackup {
    data class Ready(val preview: RestorePreview) : OpenedBackup

    /** Encrypted with a passphrase this device does not hold. */
    data object NeedsPassphrase : OpenedBackup

    data object WrongPassphrase : OpenedBackup

    /** Made by a later version of Sona. */
    data object Unsupported : OpenedBackup

    /** Not a backup, or a damaged one. */
    data object Unreadable : OpenedBackup
}

/**
 * Backs the user's library up, and restores one - the one place either is done.
 *
 * A backup holds what the user made - playlists, favorites, hand-made orders, play history, resume positions -
 * every setting, and the user's own covers, background and font. It is kept on this device, and in Google Drive
 * once that is connected - one at each, the latest, in place of the one before. The library's music and videos
 * are listed by fingerprint, not carried: Drive keeps one copy of each file, and a restore compares the list with
 * this device and brings back, from Drive, only what it lacks.
 *
 * A restore merges rather than replaces, and every step of it keeps what is already there, so it can run again
 * from the start - after being cut short, or once restored files are in - and arrive at the same library.
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: SonaDatabase,
    private val backupDao: BackupDao,
    private val settingsBackup: SettingsBackup,
    private val playerStyleSettings: PlayerStyleSettings,
    private val playlistCoverImages: PlaylistCoverImages,
    private val customBackgroundImages: CustomBackgroundImages,
    private val fingerprints: MediaFingerprints,
    private val localBackups: LocalBackups,
    private val backupSettings: BackupSettings,
    private val mediaScanner: MediaScanner,
    private val driveBackups: DriveBackups,
    private val backupFolders: BackupFolders,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    /**
     * One backup at a time - a user's, an automatic one, the one waiting for Wi-Fi - and no deleting while one runs:
     * two at once would upload the same files twice, and tidying Drive could take files a backup is still uploading.
     */
    private val runs = Mutex()

    /** Where an opened backup is unpacked until it is restored - kept across a restart, so a restore can resume. */
    private val restoreDirectory get() = File(context.noBackupFilesDir, RESTORE_DIRECTORY_NAME)

    /** Encrypts every backup from now on with a key made from [passphrase] - or, for null, none. */
    suspend fun encryptBackups(passphrase: CharArray?) {
        val key = withContext(Dispatchers.Default) { passphrase?.let(BackupCipher::newKey) }
        backupSettings.setKey(key)
    }

    /**
     * Makes a backup, and keeps it on this device - and in Drive, with the library's files Drive does not have yet,
     * uploaded over Wi-Fi, or on mobile data too where the user allows it. Nothing is written when the last backup
     * already holds the same and is kept whole everywhere it should be - see [isCurrent].
     *
     * It goes in [BackupStep] order, each step said to [onProgress]: the library read, the files uploaded, then the
     * backup saved - on this device, then in Drive - last, so the backup Drive keeps never lists a file it lacks.
     */
    suspend fun backUp(onProgress: (BackupProgress) -> Unit): BackupOutcome = runs.withLock { withContext(ioDispatcher) {
        val isDriveConnected = backupSettings.isDriveConnectedNow()
        val foldersLeftOut = backupSettings.foldersLeftOutNow()
        val trackFiles = backupDao.trackFiles()
        val pathOf = trackFiles.associate { it.id to it.path }
        val media = trackFiles.mapIndexedNotNull { index, track ->
            onProgress(BackupProgress(BackupStep.CHECKING_FILES, index, trackFiles.size))
            val fingerprint = fingerprints.of(track.path) ?: return@mapIndexedNotNull null
            val file = File(track.path)
            BackedUpMedia(fingerprint, track.path, file.length(), file.lastModified(), track.isVideo, backupFolders.isBackedUp(track.folderPath, track.path, track.isVideo, foldersLeftOut))
        }
        onProgress(BackupProgress(BackupStep.READING_LIBRARY))

        val playlists = backupDao.playlists()
        val coverFiles = playlists.mapNotNull { playlist ->
            val file = playlist.coverImageUri?.takeIf { playlist.coverSource == PlaylistCoverSource.IMAGE }?.let(::fileOf)
            file?.takeIf { it.isFile }?.let { playlist.id to it }
        }.toMap()
        val playlistTracks = backupDao.playlistTracks().groupBy { it.playlistId }
        val library = BackupLibrary(
            playlists = playlists.map { playlist ->
                BackedUpPlaylist(
                    id = playlist.id,
                    name = playlist.name,
                    isBuiltIn = playlist.isBuiltIn,
                    createdAt = playlist.createdAt,
                    modifiedAt = playlist.modifiedAt,
                    coverSource = playlist.coverSource,
                    coverTrackPath = playlist.coverTrackId?.let(pathOf::get),
                    coverImage = playlist.id.toString().takeIf { playlist.id in coverFiles },
                    tracks = playlistTracks[playlist.id].orEmpty().mapNotNull { row ->
                        pathOf[row.trackId]?.let { BackedUpPlaylistTrack(it, row.addedAt) }
                    },
                )
            },
            favoriteCollections = backupDao.favoriteCollections().map { BackedUpFavoriteCollection(it.collection, it.favoritedAt) },
            arrangements = backupDao.arrangements().mapNotNull { row ->
                pathOf[row.trackId]?.let { BackedUpArrangement(row.list, row.instanceId, it, row.position) }
            },
            playStats = backupDao.playStats().mapNotNull { row ->
                pathOf[row.trackId]?.let { BackedUpPlayStats(it, row.playCount, row.lastPlayedAt) }
            },
            resumePositions = backupDao.resumePositions().mapNotNull { row ->
                pathOf[row.trackId]?.let { BackedUpResumePosition(it, row.positionMs) }
            },
            media = media,
        )
        val settings = settingsBackup.read()
        val files = buildMap<String, () -> InputStream?> {
            coverFiles.forEach { (playlistId, file) -> put(BackupEntries.cover(playlistId.toString())) { file.inputStream() } }
            playerStyleSettings.appearance.value.customBackground.imageUri?.let { uri ->
                // An image picked before copies were kept is read through its grant, as long as that still holds.
                put(BackupEntries.BACKGROUND) { runCatching { context.contentResolver.openInputStream(Uri.parse(uri)) }.getOrNull() }
            }
            CustomFontLoader.fontDirectory(context).listFiles { file -> file.isFile && file.extension == "ttf" }.orEmpty().forEach { font ->
                put(BackupEntries.font(font.name)) { font.inputStream() }
            }
        }

        val key = backupSettings.key()
        // Sealed with another key, or none, the same library is another backup.
        val content = BackupArchive.contentFingerprint(library, settings, files) + key?.salt?.toBase64().orEmpty()
        onProgress(BackupProgress(BackupStep.CHECKING_LAST_BACKUP))
        if (isCurrent(content, key, media, isDriveConnected)) return@withContext BackupOutcome.UNCHANGED
        val createdAt = System.currentTimeMillis()
        val manifest = BackupManifest(BACKUP_FORMAT_VERSION, createdAt, appVersionCode())
        val name = backupFileName(createdAt)
        val write: (OutputStream) -> Unit = { output ->
            BackupArchive.write(key?.let { BackupCipher.encrypting(output, it) } ?: output, manifest, library, settings, files)
        }
        // The backup on this device waits on nothing of Drive's: one Drive fails is still saved here, then reported.
        var driveFailure: IOException? = null
        val outcome = when {
            !isDriveConnected -> BackupOutcome.SAVED
            !backupSettings.usesMobileDataNow() && !isOnWifi() -> BackupOutcome.SAVED_FILES_WAITING
            else -> try {
                val upload = driveBackups.media(key).upload(media.filter { it.isKeptInDrive }, onProgress)
                when {
                    upload.leftForSpace > 0 -> BackupOutcome.SAVED_DRIVE_FULL
                    upload.failed > 0 -> BackupOutcome.SAVED_FILES_LEFT
                    else -> BackupOutcome.SAVED
                }
            } catch (e: IOException) {
                driveFailure = e
                BackupOutcome.SAVED
            }
        }
        onProgress(BackupProgress(BackupStep.SAVING_ON_DEVICE))
        val localChecksum = keep(localBackups, name, write)
        driveFailure?.let { throw it }
        var driveChecksum: String? = null
        if (isDriveConnected) {
            onProgress(BackupProgress(BackupStep.SAVING_TO_DRIVE))
            driveChecksum = keep(driveBackups, name, write)
            onProgress(BackupProgress(BackupStep.CLEANING_DRIVE))
            collectDriveMedia(key)
        }
        // A backup whose files did not all go is not the last word: the next automatic one is made all the same.
        if (outcome != BackupOutcome.SAVED) return@withContext outcome
        backupSettings.recordBackup(createdAt, LastBackup(content, name, localChecksum, driveChecksum))
        BackupOutcome.SAVED
    } }

    /**
     * Whether the last backup already holds [content], and is kept whole everywhere backups go: on this device and,
     * while connected, in Drive - with every file it keeps there - each byte for byte as written. One that is not
     * is replaced by the backup made instead, so a damaged one is never kept.
     */
    private suspend fun isCurrent(content: String, key: BackupKey?, media: List<BackedUpMedia>, isDriveConnected: Boolean): Boolean {
        val last = backupSettings.lastBackup()?.takeIf { it.content == content } ?: return false
        if (!isKeptWhole(localBackups, last.name, last.localChecksum)) return false
        if (!isDriveConnected) return true
        return try {
            val driveFiles = media.filter { it.isKeptInDrive }.map { it.fingerprint }.toSet()
            isKeptWhole(driveBackups, last.name, last.driveChecksum ?: return false) &&
                driveBackups.media(key).keeps(driveFiles).size == driveFiles.size
        } catch (_: IOException) {
            false // Drive out of reach: the backup is made, and says so where Drive is needed.
        }
    }

    /** Whether the backup [destination] keeps is the one named [name], byte for byte as written - its [checksum]. */
    private suspend fun isKeptWhole(destination: BackupDestination, name: String, checksum: String): Boolean {
        val kept = destination.list().firstOrNull()?.takeIf { it.name == name } ?: return false
        return destination.checksumOf(kept) == checksum
    }

    /**
     * Deletes a backup from this device, as [onDevice], and from Drive, as [inDrive] - each where given - and then from
     * Drive the files no backup kept still lists.
     */
    suspend fun delete(onDevice: StoredBackup?, inDrive: StoredBackup?) = runs.withLock {
        onDevice?.let { localBackups.delete(it) }
        if (inDrive == null) return@withLock
        driveBackups.delete(inDrive)
        collectDriveMedia(backupSettings.key())
    }

    /**
     * Adds the backup [write] writes to [destination], as [name], in place of every one kept there before - only once
     * it is kept whole, so there is never none. The checksum of what it kept.
     */
    private suspend fun keep(destination: BackupDestination, name: String, write: (OutputStream) -> Unit): String {
        val checksum = destination.add(name, write)
        destination.list().filter { it.name != name }.forEach { destination.delete(it) }
        return checksum
    }

    /**
     * Deletes from Drive each file no backup still kept - here or there - lists. Files of encrypted backups are
     * named with their key, so their folder is tidied only when every encrypted backup kept opens with [key], and
     * emptied once none is kept at all.
     */
    private suspend fun collectDriveMedia(key: BackupKey?) {
        val plainFiles = HashSet<String>()
        val sealedFiles = HashSet<String>()
        var hasSealedBackups = false
        var areSealedBackupsReadable = true
        try {
            for (destination in listOf(localBackups, driveBackups)) {
                for (backup in destination.list()) {
                    destination.open(backup).buffered().use { input ->
                        val salt = BackupCipher.saltOf(input)
                        when {
                            salt == null -> plainFiles += BackupArchive.driveFingerprints(input)
                            key != null && key.salt.contentEquals(salt) -> {
                                hasSealedBackups = true
                                sealedFiles += BackupArchive.driveFingerprints(BackupCipher.decrypting(input, key))
                            }
                            else -> {
                                hasSealedBackups = true
                                areSealedBackupsReadable = false
                            }
                        }
                    }
                }
            }
            driveBackups.media(null).keepOnly(plainFiles)
            when {
                !hasSealedBackups -> driveBackups.deleteSealedMedia()
                key != null && areSealedBackupsReadable -> driveBackups.media(key).keepOnly(sealedFiles)
            }
        } catch (_: IOException) {
            // Tidying is not the backup: what it leaves now, the next backup tidies.
        }
    }

    /** On Wi-Fi - or any network that costs nothing, which is what Android says a metered one is not. */
    private fun isOnWifi(): Boolean =
        context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered == false

    /**
     * Opens the backup [open] reads - with [passphrase] where it is encrypted with one this device does not hold -
     * and works out what restoring it would do.
     */
    suspend fun open(open: suspend () -> InputStream, passphrase: CharArray?): OpenedBackup = withContext(ioDispatcher) {
        var isEncrypted = false
        try {
            open().buffered().use { input ->
                val salt = BackupCipher.saltOf(input)
                val key = salt?.let {
                    isEncrypted = true
                    backupSettings.key()?.takeIf { it.salt.contentEquals(salt) }
                        ?: passphrase?.let { BackupCipher.keyFor(it, salt) }
                        ?: return@withContext OpenedBackup.NeedsPassphrase
                }
                val contents = BackupArchive.extract(key?.let { BackupCipher.decrypting(input, it) } ?: input, restoreDirectory)
                // Kept beside what it opened, for the restore to open the backup's files in Drive with it too.
                key?.let { File(restoreDirectory, RESTORE_KEY_NAME).writeText(it.salt.toBase64() + "\n" + it.bytes.toBase64()) }
                OpenedBackup.Ready(previewOf(contents, key))
            }
        } catch (_: UnsupportedBackupException) {
            OpenedBackup.Unsupported
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // A wrong key fails the first segment's check; anything else is a file that is not a whole backup.
            if (isEncrypted) OpenedBackup.WrongPassphrase else OpenedBackup.Unreadable
        }
    }

    /** Chooses, by fingerprint, which of the files this device is missing the next [restore] brings back. */
    suspend fun chooseFilesToRestore(fingerprints: Set<String>) = withContext(ioDispatcher) {
        File(restoreDirectory, FILES_TO_RESTORE_NAME).writeText(fingerprints.joinToString("\n"))
    }

    /**
     * Restores the backup [open] last opened: the library merged into this device's, every setting, the user's
     * own files - and the missing files chosen with [chooseFilesToRestore], brought back from the cloud, the
     * library merged again once they are in.
     */
    suspend fun restore(onProgress: (BackupProgress) -> Unit) = withContext(ioDispatcher) {
        onProgress(BackupProgress(BackupStep.READING_BACKUP))
        val contents = BackupArchive.read(restoreDirectory)
        val filesToRestore = File(restoreDirectory, FILES_TO_RESTORE_NAME).takeIf { it.isFile }?.readLines()?.toSet().orEmpty()
        val key = File(restoreDirectory, RESTORE_KEY_NAME).takeIf { it.isFile }?.readLines()?.let { (salt, bytes) ->
            BackupKey(salt.fromBase64(), bytes.fromBase64())
        }
        // A fresh install has not read its library yet; every track the backup names is looked for in it.
        onProgress(BackupProgress(BackupStep.FINDING_FILES))
        mediaScanner.rescan()
        val plan = mediaPlanOf(contents, key)
        onProgress(BackupProgress(BackupStep.RESTORING_LIBRARY))
        val playlistIds = mergeLibrary(contents, plan.present)
        restoreSettingsAndFiles(contents, playlistIds)

        val missing = plan.missing.filter { it.media.fingerprint in filesToRestore }
        if (missing.isNotEmpty()) {
            val restorer = MediaRestorer(
                cloud = driveBackups.media(key),
                incoming = File(localBackups.directory, INCOMING_DIRECTORY_NAME),
                primaryStorage = Environment.getExternalStorageDirectory(),
            )
            val restored = missing.mapIndexed { index, item ->
                onProgress(BackupProgress(BackupStep.DOWNLOADING_FILES, index, missing.size))
                restorer.restore(item.media).also { fingerprints.remember(it, item.media.fingerprint) }
            }
            onProgress(BackupProgress(BackupStep.ADDING_FILES))
            scanIntoMediaStore(restored)
            mediaScanner.rescan()
            mergeLibrary(contents, mediaPlanOf(contents, key).present)
        }
        restoreDirectory.deleteRecursively()
    }

    private suspend fun previewOf(contents: BackupContents, key: BackupKey?) = RestorePreview(
        createdAt = contents.manifest.createdAt,
        playlistCount = contents.library.playlists.size,
        playedTrackCount = contents.library.playStats.count { it.playCount > 0 },
        media = mediaPlanOf(contents, key),
    )

    /** How the backup's files stand against this device - and, while Drive is connected and reachable, against Drive. */
    private suspend fun mediaPlanOf(contents: BackupContents, key: BackupKey?): MediaPlan {
        val media = contents.library.media
        val inDrive = if (backupSettings.isDriveConnectedNow()) {
            try {
                driveBackups.media(key).keeps(media.map { it.fingerprint })
            } catch (_: IOException) {
                emptySet()
            }
        } else {
            emptySet()
        }
        return reconcileMedia(
            media = media,
            libraryPaths = backupDao.trackFiles().map { it.path },
            sizeOf = { path -> File(path).takeIf { it.isFile }?.length() },
            fingerprintOf = fingerprints::of,
            cloudFingerprints = inDrive,
        )
    }

    /** Merges the backup's library into this device's, its tracks found through [present]. Its playlists' ids here. */
    private suspend fun mergeLibrary(contents: BackupContents, present: Map<String, String>): Map<Long, Long> {
        val idByPath = backupDao.trackFiles().associate { it.path to it.id }
        val local = LocalLibrary(
            playlists = backupDao.playlists(),
            arrangedLists = backupDao.arrangements().mapTo(HashSet()) { it.list to it.instanceId },
            playStats = backupDao.playStats().associateBy { it.trackId },
            resumePositionTrackIds = backupDao.resumePositions().mapTo(HashSet()) { it.trackId },
        )
        val plan = planLibraryMerge(contents.library, local) { path -> present[path]?.let(idByPath::get) }
        // Copied in before the playlists are written, so none is ever written pointing at a cover not yet there.
        val newPlaylists = plan.newPlaylists.map { new ->
            val image = new.coverImage?.let { contents.file(BackupEntries.cover(it)) }
            when {
                image != null -> new.playlist.copy(coverImageUri = playlistCoverImages.import(Uri.fromFile(image).toString()))
                new.playlist.coverSource == PlaylistCoverSource.IMAGE -> new.playlist.copy(coverSource = PlaylistCoverSource.STACKED)
                else -> new.playlist
            }
        }
        database.withTransaction {
            backupDao.insertPlaylists(newPlaylists)
            backupDao.insertPlaylistTracks(plan.playlistTracks)
            plan.playlistsModifiedAt.forEach { (playlistId, modifiedAt) -> backupDao.raiseModifiedAt(playlistId, modifiedAt) }
            backupDao.insertFavoriteCollections(plan.favoriteCollections)
            backupDao.insertArrangements(plan.arrangements)
            backupDao.upsertPlayStats(plan.playStats)
            backupDao.insertResumePositions(plan.resumePositions)
        }
        return plan.playlistIds
    }

    /** Puts the backup's settings in place, with the player's background and the custom font it carries. */
    private suspend fun restoreSettingsAndFiles(contents: BackupContents, playlistIds: Map<Long, Long>) {
        val previousBackground = playerStyleSettings.appearance.value.customBackground.imageUri
        settingsBackup.restore(contents.settings, playlistIds)
        val background = contents.file(BackupEntries.BACKGROUND)?.let { customBackgroundImages.import(Uri.fromFile(it).toString()) }
        playerStyleSettings.setCustomBackgroundImage(background)
        previousBackground?.takeIf { it != background }?.let { customBackgroundImages.release(it) }

        if (contents.fonts.isNotEmpty()) {
            val fontDirectory = CustomFontLoader.fontDirectory(context)
            fontDirectory.deleteRecursively()
            fontDirectory.mkdirs()
            contents.fonts.forEach { it.copyTo(File(fontDirectory, it.name)) }
        }
    }

    /** Has Android read [files] in - waiting for it - so the library's next read finds them. */
    private suspend fun scanIntoMediaStore(files: List<File>) = suspendCancellableCoroutine { continuation ->
        val remaining = AtomicInteger(files.size)
        MediaScannerConnection.scanFile(context, files.map { it.path }.toTypedArray(), null) { _, _ ->
            if (remaining.decrementAndGet() == 0) continuation.resume(Unit)
        }
    }

    private fun appVersionCode(): Long =
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
}

private fun fileOf(uri: String): File? = Uri.parse(uri).takeIf { it.scheme == "file" }?.path?.let(::File)

private fun ByteArray.toBase64(): String = Base64.getEncoder().encodeToString(this)

private fun String.fromBase64(): ByteArray = Base64.getDecoder().decode(this)
