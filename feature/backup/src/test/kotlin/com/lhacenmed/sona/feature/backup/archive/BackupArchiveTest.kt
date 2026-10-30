package com.lhacenmed.sona.feature.backup.archive

import com.lhacenmed.sona.core.database.entity.PlaylistCoverSource
import com.lhacenmed.sona.feature.backup.BACKUP_FORMAT_VERSION
import com.lhacenmed.sona.feature.backup.BackedUpMedia
import com.lhacenmed.sona.feature.backup.BackedUpPlaylist
import com.lhacenmed.sona.feature.backup.BackedUpPlaylistTrack
import com.lhacenmed.sona.feature.backup.BackupLibrary
import com.lhacenmed.sona.feature.backup.BackupManifest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupArchiveTest {

    private val directory = createTempDirectory("backup").toFile()

    private val manifest = BackupManifest(BACKUP_FORMAT_VERSION, createdAt = 1L, appVersionCode = 2L)

    private val library = BackupLibrary(
        playlists = listOf(
            BackedUpPlaylist(7, "Road", false, 1L, 2L, PlaylistCoverSource.IMAGE, null, "7", listOf(BackedUpPlaylistTrack("/m/a.mp3", 3L))),
        ),
        favoriteCollections = emptyList(),
        arrangements = emptyList(),
        playStats = emptyList(),
        resumePositions = emptyList(),
        media = listOf(BackedUpMedia("aaaa", "/m/a.mp3", 4L, 5L, isVideo = false)),
    )

    private val settings: Map<String, Map<String, Any>> = mapOf(
        "theme_settings" to mapOf("pure_black" to true, "accent" to 3, "seen" to 9L, "blur" to 0.5f, "font" to "x", "hidden" to setOf("a", "b")),
    )

    private val files = mapOf(BackupEntries.cover("7") to { ByteArrayInputStream(byteArrayOf(1, 2, 3)) })

    private fun written(key: BackupKey? = null): ByteArray {
        val output = ByteArrayOutputStream()
        BackupArchive.write(key?.let { BackupCipher.encrypting(output, it) } ?: output, manifest, library, settings, files)
        return output.toByteArray()
    }

    @Test
    fun `a backup reads back as it was written`() {
        val contents = BackupArchive.extract(written().inputStream(), directory)

        assertEquals(manifest, contents.manifest)
        assertEquals(library, contents.library)
        assertEquals(settings, contents.settings)
        assertArrayEquals(byteArrayOf(1, 2, 3), contents.file(BackupEntries.cover("7"))?.readBytes())
        assertNull(contents.file(BackupEntries.BACKGROUND))
    }

    @Test
    fun `an encrypted backup opens with its passphrase, and with nothing else`() {
        val key = BackupCipher.newKey("correct horse".toCharArray())
        val sealed = written(key).inputStream().buffered()
        val salt = checkNotNull(BackupCipher.saltOf(sealed))

        val contents = BackupArchive.extract(BackupCipher.decrypting(sealed, BackupCipher.keyFor("correct horse".toCharArray(), salt)), directory)
        assertEquals(library, contents.library)

        val again = written(key).inputStream().buffered()
        val wrongKey = BackupCipher.keyFor("wrong".toCharArray(), checkNotNull(BackupCipher.saltOf(again)))
        assertThrows(IOException::class.java) { BackupArchive.extract(BackupCipher.decrypting(again, wrongKey), directory) }
    }

    @Test
    fun `a plain backup is not taken for an encrypted one`() {
        assertNull(BackupCipher.saltOf(written().inputStream().buffered()))
    }

    @Test
    fun `a backup in a later format is refused`() {
        val output = ByteArrayOutputStream()
        BackupArchive.write(output, manifest.copy(formatVersion = BACKUP_FORMAT_VERSION + 1), library, settings, files)

        assertThrows(UnsupportedBackupException::class.java) { BackupArchive.extract(output.toByteArray().inputStream(), directory) }
    }

    @Test
    fun `only the files of folders backed up count as kept in Drive`() {
        val leftOut = BackedUpMedia("bbbb", "/m/left-out/b.mp3", 4L, 5L, isVideo = false, isKeptInDrive = false)
        val output = ByteArrayOutputStream()
        BackupArchive.write(output, manifest, library.copy(media = library.media + leftOut), settings, files)

        assertEquals(setOf("aaaa"), BackupArchive.driveFingerprints(output.toByteArray().inputStream()))
    }

    @Test
    fun `a sealed file's name is the same under its key, and says nothing without it`() {
        val key = BackupCipher.keyFor("one".toCharArray(), ByteArray(16))
        val otherKey = BackupCipher.keyFor("two".toCharArray(), ByteArray(16))

        assertEquals(BackupCipher.blobName(key, "aaaa"), BackupCipher.blobName(key, "aaaa"))
        assertNotEquals(BackupCipher.blobName(key, "aaaa"), BackupCipher.blobName(otherKey, "aaaa"))
        assertNotEquals("aaaa", BackupCipher.blobName(key, "aaaa"))
    }

    @Test
    fun `the content fingerprint changes with the content, and only with it`() {
        val same = BackupArchive.contentFingerprint(library, settings, files)

        assertEquals(same, BackupArchive.contentFingerprint(library, settings, files))
        assertNotEquals(same, BackupArchive.contentFingerprint(library.copy(media = emptyList()), settings, files))
    }
}
