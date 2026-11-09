package com.lhacenmed.sona.core.downloader

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZipArchiveTest {

    private val compressed = "usr/lib/python3.12/os.py" to "import abc\n".repeat(500).toByteArray()
    private val stored = "usr/etc/tls/cert.pem" to "-----BEGIN CERTIFICATE-----".toByteArray()

    private fun zip(): File = File.createTempFile("archive", ".zip").also { file ->
        ZipOutputStream(file.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("usr/lib/"))
            out.closeEntry()
            out.putNextEntry(ZipEntry(compressed.first))
            out.write(compressed.second)
            out.closeEntry()
            out.putNextEntry(
                ZipEntry(stored.first).apply {
                    method = ZipEntry.STORED
                    size = stored.second.size.toLong()
                    crc = CRC32().apply { update(stored.second) }.value
                },
            )
            out.write(stored.second)
            out.closeEntry()
        }
    }

    @Test
    fun `its index lists every entry, as it was zipped`() {
        val file = zip()
        val entries = FileZipSource(file).use { it.entries() }
        assertEquals(listOf("usr/lib/", compressed.first, stored.first), entries.map { it.name })
        assertTrue(entries[0].isDirectory)
        assertTrue(entries[1].isDeflated)
        assertFalse(entries[2].isDeflated)
        assertFalse(entries.any { it.isSymlink })
        file.delete()
    }

    @Test
    fun `each entry reads back as it was, compressed or stored, its checksum the index's`() {
        val file = zip()
        FileZipSource(file).use { zip ->
            for ((name, content) in listOf(compressed, stored)) {
                val entry = zip.entries().first { it.name == name }
                val read = zip.contentOf(entry).use { it.readBytes() }
                assertEquals(content.toList(), read.toList())
                assertEquals(entry.crc, CRC32().apply { update(read) }.value)
            }
        }
        file.delete()
    }
}
