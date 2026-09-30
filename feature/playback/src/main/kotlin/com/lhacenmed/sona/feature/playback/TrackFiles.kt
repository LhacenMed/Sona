package com.lhacenmed.sona.feature.playback

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * The files the player reads, and the one way to change one it may be reading: [write].
 *
 * A tag written into a file can move the audio after it, while the player reads a track at byte offsets it
 * worked out when it opened it - read on in the moved file, it would decode garbage and stop. So a file the
 * player holds a track of - the one playing, and the next, which it opens ahead - is copied as it stood before
 * it is written, and the player reads on from that copy at the offsets it knows until it has moved on from the
 * track: playback carries on untouched, and every later play reads the new file. A file the player does not
 * hold is written as it is, with nothing copied.
 *
 * Every read the player makes passes through [dataSourceFactory], which also keeps it from reading a file
 * halfway through being written.
 */
@OptIn(UnstableApi::class)
@Singleton
class TrackFiles @Inject constructor(@ApplicationContext private val context: Context) {

    /** Held for writing while a file is changed, and for reading around every read the player makes. */
    private val lock = ReentrantReadWriteLock()

    private val copiesDir = File(context.cacheDir, "playing-track-copies").apply { deleteRecursively() }

    /** The files, by uri, the player holds a track of - see [hold]. */
    @Volatile private var heldUris: Set<String> = emptySet()

    /** The copy the player reads instead of each file changed while it held a track of it, by the file's uri. */
    private val copies = ConcurrentHashMap<String, File>()

    /**
     * Runs [write], a change to the file at [uri], with the player reading on from a copy of it where it holds
     * a track of it. Blocks, so it is called off the main thread.
     */
    fun <T> write(uri: Uri, write: () -> T): T = lock.write {
        val key = uri.toString()
        if (key in heldUris && !copies.containsKey(key)) copies[key] = copyOf(uri)
        write()
    }

    /**
     * Tells which files, by uri, the player now holds a track of - every copy of a file no longer among them
     * is let go. A copy still being read is only unlinked: what reads it keeps it until it closes it.
     */
    internal fun hold(uris: Set<String>) {
        heldUris = uris
        (copies.keys - uris).forEach { key -> copies.remove(key)?.delete() }
    }

    /** [upstream], reading each file through its copy wherever [write] made one. */
    internal fun dataSourceFactory(upstream: DataSource.Factory): DataSource.Factory =
        DataSource.Factory { CopyAwareDataSource(upstream.createDataSource()) }

    private fun copyOf(uri: Uri): File {
        copiesDir.mkdirs()
        val copy = File.createTempFile("track", null, copiesDir)
        checkNotNull(context.contentResolver.openInputStream(uri)) { "Cannot read $uri" }.use { input ->
            copy.outputStream().use(input::copyTo)
        }
        return copy
    }

    /**
     * One read of a file, from the file itself until a copy of it is made - then from the copy, at the same
     * offset, from its next read on. Opened after the copy is made, it reads the copy from the start.
     */
    private inner class CopyAwareDataSource(private val upstream: DataSource) : DataSource {
        private var source: DataSource? = null
        private var dataSpec: DataSpec? = null
        private var isReadingCopy = false
        private var bytesRead = 0L

        override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

        override fun open(dataSpec: DataSpec): Long = lock.read {
            this.dataSpec = dataSpec
            bytesRead = 0L
            val copy = copies[dataSpec.uri.toString()]
            isReadingCopy = copy != null
            val opened = if (copy != null) FileDataSource() else upstream
            source = opened
            opened.open(if (copy != null) dataSpec.withUri(Uri.fromFile(copy)) else dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = lock.read {
            val spec = checkNotNull(dataSpec)
            if (!isReadingCopy) copies[spec.uri.toString()]?.let { readOn(from = it, spec) }
            checkNotNull(source).read(buffer, offset, length).also { if (it > 0) bytesRead += it }
        }

        /** Carries on from [from] exactly where the file was left. */
        private fun readOn(from: File, spec: DataSpec) {
            upstream.close()
            val remaining = if (spec.length == C.LENGTH_UNSET.toLong()) C.LENGTH_UNSET.toLong() else spec.length - bytesRead
            source = FileDataSource().apply {
                open(spec.buildUpon().setUri(Uri.fromFile(from)).setPosition(spec.position + bytesRead).setLength(remaining).build())
            }
            isReadingCopy = true
        }

        override fun getUri(): Uri? = dataSpec?.uri

        override fun getResponseHeaders(): Map<String, List<String>> = source?.responseHeaders.orEmpty()

        override fun close() {
            try {
                source?.close()
            } finally {
                source = null
            }
        }
    }
}
