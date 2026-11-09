package com.lhacenmed.sona.core.downloader

import android.system.Os
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import okhttp3.OkHttpClient
import okhttp3.Request

/** Where a zip's bytes are read from, by their place in it: a file here, or one on a server read a part at a time. */
internal interface ZipSource {
    val size: Long

    fun read(offset: Long, length: Int): ByteArray

    fun open(offset: Long, length: Long): InputStream
}

/** One entry of a zip, as its index lists it. */
internal class ZipEntryInfo(
    val name: String,
    val isDeflated: Boolean,
    val crc: Long,
    val compressedSize: Long,
    val headerOffset: Long,
    /** A Unix symbolic link, its content the path it leads to. */
    val isSymlink: Boolean,
) {
    val isDirectory: Boolean get() = name.endsWith('/')
}

private const val EndOfIndexSignature = 0x06054b50
private const val IndexEntrySignature = 0x02014b50
private const val EndOfIndexSize = 22
private const val MaxCommentSize = 0xFFFF
private const val LocalHeaderSize = 30

/** Every entry [ZipSource] holds, read from its index at its end - all of it there is to read to know them. */
internal fun ZipSource.entries(): List<ZipEntryInfo> {
    val tailLength = minOf(size, (EndOfIndexSize + MaxCommentSize).toLong()).toInt()
    val tail = read(size - tailLength, tailLength).littleEndian()
    val end = (tailLength - EndOfIndexSize downTo 0).firstOrNull { tail.getInt(it) == EndOfIndexSignature }
        ?: throw IOException("Not a zip")
    val count = tail.getShort(end + 10).toInt() and 0xFFFF
    val indexSize = tail.getInt(end + 12).toLong() and 0xFFFFFFFFL
    val indexOffset = tail.getInt(end + 16).toLong() and 0xFFFFFFFFL
    val index = read(indexOffset, indexSize.toInt()).littleEndian()
    var at = 0
    return List(count) {
        if (index.getInt(at) != IndexEntrySignature) throw IOException("Damaged zip index")
        val madeBy = index.getShort(at + 4).toInt() and 0xFFFF
        val nameLength = index.getShort(at + 28).toInt() and 0xFFFF
        val extraLength = index.getShort(at + 30).toInt() and 0xFFFF
        val commentLength = index.getShort(at + 32).toInt() and 0xFFFF
        val unixMode = index.getInt(at + 38) ushr 16
        val name = String(index.array(), at + 46, nameLength, Charsets.UTF_8)
        ZipEntryInfo(
            name = name,
            isDeflated = (index.getShort(at + 10).toInt() and 0xFFFF) == 8,
            crc = index.getInt(at + 16).toLong() and 0xFFFFFFFFL,
            compressedSize = index.getInt(at + 20).toLong() and 0xFFFFFFFFL,
            headerOffset = index.getInt(at + 42).toLong() and 0xFFFFFFFFL,
            // Made on Unix, its mode a link's.
            isSymlink = madeBy ushr 8 == 3 && unixMode and 0xF000 == 0xA000,
        ).also { at += 46 + nameLength + extraLength + commentLength }
    }
}

/** What [entry] holds, as it was before it was zipped. */
internal fun ZipSource.contentOf(entry: ZipEntryInfo): InputStream {
    val header = read(entry.headerOffset, LocalHeaderSize).littleEndian()
    val dataOffset = entry.headerOffset + LocalHeaderSize +
        (header.getShort(26).toInt() and 0xFFFF) + (header.getShort(28).toInt() and 0xFFFF)
    val stored = open(dataOffset, entry.compressedSize)
    return if (entry.isDeflated) InflaterInputStream(stored, Inflater(true), 65_536) else stored
}

/** Unzips this file into [targetDir], its links kept as links - never anything outside it, whatever a name says. */
internal fun File.unzipTo(targetDir: File) {
    FileZipSource(this).use { zip ->
        val root = targetDir.canonicalPath + File.separator
        for (entry in zip.entries()) {
            val target = File(targetDir, entry.name)
            if (!(target.canonicalPath + File.separator).startsWith(root)) continue
            when {
                entry.isDirectory -> target.mkdirs()
                entry.isSymlink -> {
                    target.parentFile?.mkdirs()
                    Os.symlink(zip.contentOf(entry).use { String(it.readBytes(), Charsets.UTF_8) }, target.path)
                }
                else -> {
                    target.parentFile?.mkdirs()
                    zip.contentOf(entry).use { input -> target.outputStream().use(input::copyTo) }
                }
            }
        }
    }
}

internal class FileZipSource(file: File) : ZipSource, AutoCloseable {
    private val file = RandomAccessFile(file, "r")
    override val size: Long = this.file.length()

    override fun read(offset: Long, length: Int): ByteArray = ByteArray(length).also {
        file.seek(offset)
        file.readFully(it)
    }

    /** Read straight from the file, never more of it held than a read asks for: an entry can be tens of megabytes. */
    override fun open(offset: Long, length: Long): InputStream = object : InputStream() {
        private var position = offset
        private val end = offset + length

        override fun read(): Int = ByteArray(1).let { if (read(it, 0, 1) < 0) -1 else it[0].toInt() and 0xFF }

        override fun read(buffer: ByteArray, start: Int, count: Int): Int {
            if (position >= end) return -1
            file.seek(position)
            val read = file.read(buffer, start, minOf(count.toLong(), end - position).toInt())
            if (read > 0) position += read
            return read
        }
    }

    override fun close() = file.close()
}

/** A zip on a server that answers for a part of a file - as Maven Central does - read without downloading the rest. */
internal class HttpZipSource(private val client: OkHttpClient, private val url: String) : ZipSource {
    override val size: Long = client.newCall(Request.Builder().url(url).head().build()).execute().use { response ->
        if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
        response.header("Content-Length")?.toLongOrNull() ?: throw IOException("No size for $url")
    }

    override fun read(offset: Long, length: Int): ByteArray = open(offset, length.toLong()).use { it.readBytes() }

    override fun open(offset: Long, length: Long): InputStream {
        val request = Request.Builder().url(url).header("Range", "bytes=$offset-${offset + length - 1}").build()
        val response = client.newCall(request).execute()
        if (response.code != 206) {
            response.close()
            throw IOException("HTTP ${response.code} for a part of $url")
        }
        return checkNotNull(response.body).byteStream()
    }
}

private fun ByteArray.littleEndian(): ByteBuffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
