package com.lhacenmed.sona.feature.backup.media

import com.lhacenmed.sona.feature.backup.BackedUpMedia
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaReconcilerTest {

    /** The device's files, by path: each one's size and fingerprint. */
    private class Device(vararg files: Pair<String, String>) {
        val fingerprints = files.toMap()
        val reads = mutableListOf<String>()

        suspend fun reconcile(media: List<BackedUpMedia>, cloud: Set<String> = emptySet()) = reconcileMedia(
            media = media,
            libraryPaths = fingerprints.keys.toList(),
            sizeOf = { path -> fingerprints[path]?.length?.toLong() },
            fingerprintOf = { path -> reads += path; fingerprints[path] },
            cloudFingerprints = cloud,
        )
    }

    private fun media(path: String, fingerprint: String) =
        BackedUpMedia(fingerprint, path, fingerprint.length.toLong(), modifiedAt = 0L, isVideo = false)

    @Test
    fun `a file at its own path with its bytes is present there`() = runBlocking {
        val plan = Device("/m/a.mp3" to "aaaa").reconcile(listOf(media("/m/a.mp3", "aaaa")))
        assertEquals(mapOf("/m/a.mp3" to "/m/a.mp3"), plan.present)
        assertEquals(emptyList<MissingMedia>(), plan.missing)
    }

    @Test
    fun `a file moved elsewhere is present where it now is`() = runBlocking {
        val plan = Device("/m/new/a.mp3" to "aaaa").reconcile(listOf(media("/m/a.mp3", "aaaa")))
        assertEquals(mapOf("/m/a.mp3" to "/m/new/a.mp3"), plan.present)
    }

    @Test
    fun `a different file at the path is not taken for it`() = runBlocking {
        val plan = Device("/m/a.mp3" to "bbbb").reconcile(listOf(media("/m/a.mp3", "aaaa")), cloud = setOf("aaaa"))
        assertEquals(emptyMap<String, String>(), plan.present)
        assertEquals(listOf(MissingMedia(media("/m/a.mp3", "aaaa"), emptyList())), plan.missing)
    }

    @Test
    fun `the same bytes twice in a backup are one file on the device`() = runBlocking {
        val plan = Device("/m/a.mp3" to "aaaa").reconcile(listOf(media("/m/a.mp3", "aaaa"), media("/m/copy.mp3", "aaaa")))
        assertEquals(mapOf("/m/a.mp3" to "/m/a.mp3", "/m/copy.mp3" to "/m/a.mp3"), plan.present)
    }

    @Test
    fun `the same missing bytes twice are restored once`() = runBlocking {
        val plan = Device().reconcile(listOf(media("/m/a.mp3", "aaaa"), media("/m/copy.mp3", "aaaa")), cloud = setOf("aaaa"))
        assertEquals(listOf(MissingMedia(media("/m/a.mp3", "aaaa"), listOf("/m/copy.mp3"))), plan.missing)
    }

    @Test
    fun `a file neither here nor in the cloud is unavailable`() = runBlocking {
        val plan = Device().reconcile(listOf(media("/m/a.mp3", "aaaa")))
        assertEquals(listOf(media("/m/a.mp3", "aaaa")), plan.unavailable)
    }

    @Test
    fun `only files of the same size are fingerprinted`() = runBlocking {
        val device = Device("/m/other.mp3" to "bb", "/m/same-size.mp3" to "cccc")
        device.reconcile(listOf(media("/m/a.mp3", "aaaa")))
        assertEquals(listOf("/m/same-size.mp3"), device.reads)
    }
}
