package com.lhacenmed.sona.feature.backup.media

import com.lhacenmed.sona.feature.backup.BackedUpMedia
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaReconcilerTest {

    /** The device's files, by path: each one's size and fingerprint - and all the same length. */
    private class Device(vararg files: Pair<String, String>) {
        val fingerprints = files.toMap()
        val reads = mutableListOf<String>()

        suspend fun reconcile(media: List<BackedUpMedia>, cloud: Set<String> = emptySet(), placeOf: (String) -> String = { it }) = reconcileMedia(
            media = media,
            libraryFiles = fingerprints.keys.map { LibraryFile(it, durationMs = 60_000) },
            placeOf = placeOf,
            sizeOf = { path -> fingerprints[path]?.length?.toLong() },
            fingerprintOf = { path -> reads += path; fingerprints[path] },
            cloudFingerprints = cloud,
        )
    }

    private fun media(path: String, fingerprint: String, durationMs: Long = 0L) =
        BackedUpMedia(fingerprint, path, fingerprint.length.toLong(), modifiedAt = 0L, isVideo = false, durationMs = durationMs)

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
    fun `a file changed at its place is that file, and no older copy is restored beside it`() = runBlocking {
        val plan = Device("/m/a.mp3" to "bbbbbb").reconcile(listOf(media("/m/a.mp3", "aaaa")), cloud = setOf("aaaa"))
        assertEquals(mapOf("/m/a.mp3" to "/m/a.mp3"), plan.present)
        assertEquals(emptyList<MissingMedia>(), plan.missing)
    }

    @Test
    fun `a file at the place its missing volume maps to is that file`() = runBlocking {
        val plan = Device("/storage/emulated/0/a.mp3" to "bbbbbb").reconcile(
            listOf(media("/storage/SD/a.mp3", "aaaa")),
            cloud = setOf("aaaa"),
            placeOf = { it.replace("/storage/SD", "/storage/emulated/0") },
        )
        assertEquals(mapOf("/storage/SD/a.mp3" to "/storage/emulated/0/a.mp3"), plan.present)
    }

    @Test
    fun `a file moved and changed is found by its name and length`() = runBlocking {
        val plan = Device("/m/new/a.mp3" to "bbbbbb").reconcile(listOf(media("/m/a.mp3", "aaaa", durationMs = 60_500)), cloud = setOf("aaaa"))
        assertEquals(mapOf("/m/a.mp3" to "/m/new/a.mp3"), plan.present)
    }

    @Test
    fun `a file another backed-up file has for its place is not taken for a moved one`() = runBlocking {
        val plan = Device("/m/x/a.mp3" to "bbbbbb").reconcile(
            listOf(media("/m/a.mp3", "aaaa", durationMs = 60_000), media("/m/x/a.mp3", "bbbbbb", durationMs = 60_000)),
            cloud = setOf("aaaa"),
        )
        assertEquals(listOf(MissingMedia(media("/m/a.mp3", "aaaa", durationMs = 60_000), emptyList())), plan.missing)
    }

    @Test
    fun `a backup that kept no lengths never takes a name alone for a file`() = runBlocking {
        val plan = Device("/m/new/a.mp3" to "bbbbbb").reconcile(listOf(media("/m/a.mp3", "aaaa")), cloud = setOf("aaaa"))
        assertEquals(emptyMap<String, String>(), plan.present)
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
