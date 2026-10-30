package com.lhacenmed.sona.feature.backup

import android.content.Context
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
import com.lhacenmed.sona.feature.backup.archive.UnsupportedBackupException
import com.lhacenmed.sona.feature.backup.library.LocalLibrary
import com.lhacenmed.sona.feature.backup.library.planLibraryMerge
import com.lhacenmed.sona.feature.backup.media.CloudMediaStore
import com.lhacenmed.sona.feature.backup.media.MediaFingerprints
import com.lhacenmed.sona.feature.backup.media.MediaPlan
import com.lhacenmed.sona.feature.backup.media.MediaRestorer
import com.lhacenmed.sona.feature.backup.media.reconcileMedia
import com.lhacenmed.sona.feature.scanner.MediaScanner
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.util.Optional
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

private const val RESTORE_DIRECTORY_NAME = "restore"
private const val INCOMING_DIRECTORY_NAME = ".incoming"
private const val FILES_TO_RESTORE_NAME = "files-to-restore"

/** Where a backup or a restore is, for its notification and its screen. */
sealed interface BackupProgress {
    /** Reading the library's files for their fingerprints - [done] of [total]. */
    data class Fingerprinting(val done: Int, val total: Int) : BackupProgress

    /** Writing the backup, or putting the library and settings back. */
    data object Working : BackupProgress

    /** Bringing missing files back from the cloud - [done] of [total]. */
    data class RestoringFiles(val done: Int, val total: Int) : BackupProgress
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
 * every setting, and the user's own covers, background and font. The library's music and videos are listed by
 * fingerprint, not carried: a restore compares that list with this device and brings back, from the cloud, only
 * what it lacks.
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
    private val cloudMediaStore: Optional<CloudMediaStore>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    /** Where an opened backup is unpacked until it is restored - kept across a restart, so a restore can resume. */
    private val restoreDirectory get() = File(context.noBackupFilesDir, RESTORE_DIRECTORY_NAME)

    /** Encrypts every backup from now on with a key made from [passphrase] - or, for null, none. */
    suspend fun encryptBackups(passphrase: CharArray?) {
        val key = withContext(Dispatchers.Default) { passphrase?.let(BackupCipher::newKey) }
        backupSettings.setKey(key)
    }

    /**
     * Makes a backup, and keeps it on this device. [skipIfUnchanged] - for an automatic one - writes nothing when
     * it would hold just what the last one did. Whether one was written.
     */
    suspend fun backUp(skipIfUnchanged: Boolean, onProgress: (BackupProgress) -> Unit): Boolean = withContext(ioDispatcher) {
        val trackFiles = backupDao.trackFiles()
        val pathOf = trackFiles.associate { it.id to it.path }
        val media = trackFiles.mapIndexedNotNull { index, track ->
            onProgress(BackupProgress.Fingerprinting(index, trackFiles.size))
            val fingerprint = fingerprints.of(track.path) ?: return@mapIndexedNotNull null
            val file = File(track.path)
            BackedUpMedia(fingerprint, track.path, file.length(), file.lastModified(), track.isVideo)
        }
        onProgress(BackupProgress.Working)

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

        val content = BackupArchive.contentFingerprint(library, settings, files)
        if (skipIfUnchanged && content == backupSettings.lastBackupContent()) return@withContext false
        val key = backupSettings.key()
        val createdAt = System.currentTimeMillis()
        val manifest = BackupManifest(BACKUP_FORMAT_VERSION, createdAt, appVersionCode())
        localBackups.add(createdAt) { output ->
            BackupArchive.write(key?.let { BackupCipher.encrypting(output, it) } ?: output, manifest, library, settings, files)
        }
        backupSettings.recordBackup(createdAt, content)
        true
    }

    /**
     * Opens the backup [open] reads - with [passphrase] where it is encrypted with one this device does not hold -
     * and works out what restoring it would do.
     */
    suspend fun open(open: () -> InputStream, passphrase: CharArray?): OpenedBackup = withContext(ioDispatcher) {
        var isEncrypted = false
        try {
            open().buffered().use { input ->
                val salt = BackupCipher.saltOf(input)
                val contents = if (salt == null) {
                    BackupArchive.extract(input, restoreDirectory)
                } else {
                    isEncrypted = true
                    val key = backupSettings.key()?.takeIf { it.salt.contentEquals(salt) }
                        ?: passphrase?.let { BackupCipher.keyFor(it, salt) }
                        ?: return@withContext OpenedBackup.NeedsPassphrase
                    BackupArchive.extract(BackupCipher.decrypting(input, key), restoreDirectory)
                }
                OpenedBackup.Ready(previewOf(contents))
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
        onProgress(BackupProgress.Working)
        val contents = BackupArchive.read(restoreDirectory)
        val filesToRestore = File(restoreDirectory, FILES_TO_RESTORE_NAME).takeIf { it.isFile }?.readLines()?.toSet().orEmpty()
        // A fresh install has not read its library yet; every track the backup names is looked for in it.
        mediaScanner.rescan()
        val plan = mediaPlanOf(contents)
        val playlistIds = mergeLibrary(contents, plan.present)
        restoreSettingsAndFiles(contents, playlistIds)

        val missing = plan.missing.filter { it.media.fingerprint in filesToRestore }
        if (missing.isNotEmpty()) {
            val restorer = MediaRestorer(
                cloud = cloudMediaStore.get(),
                incoming = File(localBackups.directory, INCOMING_DIRECTORY_NAME),
                primaryStorage = Environment.getExternalStorageDirectory(),
            )
            val restored = missing.mapIndexed { index, item ->
                onProgress(BackupProgress.RestoringFiles(index, missing.size))
                restorer.restore(item.media).also { fingerprints.remember(it, item.media.fingerprint) }
            }
            onProgress(BackupProgress.Working)
            scanIntoMediaStore(restored)
            mediaScanner.rescan()
            mergeLibrary(contents, mediaPlanOf(contents).present)
        }
        restoreDirectory.deleteRecursively()
    }

    private suspend fun previewOf(contents: BackupContents) = RestorePreview(
        createdAt = contents.manifest.createdAt,
        playlistCount = contents.library.playlists.size,
        playedTrackCount = contents.library.playStats.count { it.playCount > 0 },
        media = mediaPlanOf(contents),
    )

    private suspend fun mediaPlanOf(contents: BackupContents): MediaPlan = reconcileMedia(
        media = contents.library.media,
        libraryPaths = backupDao.trackFiles().map { it.path },
        sizeOf = { path -> File(path).takeIf { it.isFile }?.length() },
        fingerprintOf = fingerprints::of,
        cloudFingerprints = if (cloudMediaStore.isPresent) cloudMediaStore.get().fingerprints() else emptySet(),
    )

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
