package com.lhacenmed.sona.feature.backup.media

import com.lhacenmed.sona.core.data.fingerprint.fingerprintOf
import com.lhacenmed.sona.feature.backup.BackedUpMedia
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class MediaRestorerTest {

    private val root = createTempDirectory("restore").toFile()
    private val storage = File(root, "storage").apply { mkdirs() }

    /** A cloud keeping [files] by fingerprint, counting its downloads. */
    private class Cloud(private val files: Map<String, ByteArray>) : CloudMediaStore {
        var downloads = 0

        override suspend fun keeps(fingerprints: Collection<String>) = fingerprints.filterTo(mutableSetOf()) { it in files }

        override suspend fun download(fingerprint: String, file: File) {
            downloads++
            file.writeBytes(files.getValue(fingerprint))
        }
    }

    private fun restorer(cloud: CloudMediaStore) = MediaRestorer(cloud, File(root, "incoming"), primaryStorage = storage)

    private fun media(path: String, bytes: ByteArray) =
        BackedUpMedia(fingerprintOf(bytes.inputStream()), path, bytes.size.toLong(), modifiedAt = 1_000_000L, isVideo = false)

    @Test
    fun `a missing file is put back where it was, as it was`() = runBlocking {
        val bytes = "song".toByteArray()
        val media = media(File(storage, "Music/a.mp3").path, bytes)

        val restored = restorer(Cloud(mapOf(media.fingerprint to bytes))).restore(media)

        assertEquals(File(storage, "Music/a.mp3"), restored)
        assertEquals("song", restored.readText())
        assertEquals(1_000_000L, restored.lastModified())
    }

    @Test
    fun `a different file at its place keeps it, and the restored one takes the next name`() = runBlocking {
        File(storage, "Music").mkdirs()
        File(storage, "Music/a.mp3").writeText("someone else")
        val bytes = "song".toByteArray()
        val media = media(File(storage, "Music/a.mp3").path, bytes)

        val restored = restorer(Cloud(mapOf(media.fingerprint to bytes))).restore(media)

        assertEquals(File(storage, "Music/a (1).mp3"), restored)
        assertEquals("someone else", File(storage, "Music/a.mp3").readText())
    }

    @Test
    fun `a file already in place is not downloaded again`() = runBlocking {
        val bytes = "song".toByteArray()
        val media = media(File(storage, "Music/a.mp3").path, bytes)
        val cloud = Cloud(mapOf(media.fingerprint to bytes))

        restorer(cloud).restore(media)
        restorer(cloud).restore(media)

        assertEquals(1, cloud.downloads)
        assertFalse(File(storage, "Music/a (1).mp3").exists())
    }

    @Test
    fun `a download that is not the file it should be is refused`() {
        val media = media(File(storage, "Music/a.mp3").path, "song".toByteArray())
        val cloud = Cloud(mapOf(media.fingerprint to "damaged".toByteArray()))

        assertThrows(IOException::class.java) { runBlocking { restorer(cloud).restore(media) } }
        assertFalse(File(storage, "Music/a.mp3").exists())
    }

    @Test
    fun `a file from a volume this device lacks goes to its primary storage`() = runBlocking {
        val bytes = "song".toByteArray()
        val media = media("/storage/0000-NONE/Music/a.mp3", bytes)

        val restored = restorer(Cloud(mapOf(media.fingerprint to bytes))).restore(media)

        assertEquals(File(storage, "Music/a.mp3"), restored)
    }
}
