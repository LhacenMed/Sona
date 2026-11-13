package com.lhacenmed.sona.feature.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.lhacenmed.sona.core.downloader.DownloadEvent
import com.lhacenmed.sona.core.downloader.LiveDownloads
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/** How long a read at the end of a part still growing waits for more of it, before it looks again. */
private const val GrowthWaitMs = 100L

/**
 * A download played as it downloads - see [LiveDownloads] - by either player: each of its parts read as it grows, a video's
 * sound and picture played together, and played on from the file it saved once it is over (see [followLiveDownloads]).
 */
@OptIn(UnstableApi::class)
@Singleton
class LiveDownloadSources @Inject constructor(private val live: LiveDownloads) {

    /** [upstream], reading a download's part by its address as it grows - every other address as [upstream] reads it. */
    internal fun dataSourceFactory(upstream: DataSource.Factory): DataSource.Factory = DataSource.Factory {
        val other = upstream.createDataSource()
        val growing = GrowingPartDataSource()
        object : DataSource by other {
            private var opened: DataSource? = null

            override fun addTransferListener(transferListener: TransferListener) {
                other.addTransferListener(transferListener)
                growing.addTransferListener(transferListener)
            }

            override fun open(dataSpec: DataSpec): Long {
                val source = if (dataSpec.uri.scheme == LiveDownloads.SCHEME) growing else other
                opened = source
                return source.open(dataSpec)
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = checkNotNull(opened).read(buffer, offset, length)

            override fun getUri(): Uri? = opened?.uri

            override fun getResponseHeaders(): Map<String, List<String>> = opened?.responseHeaders.orEmpty()

            override fun close() {
                opened?.close()
                opened = null
            }
        }
    }

    /**
     * [delegate], making a download's address the sources of its parts - its sound and picture played as one - and every other
     * item as [delegate] makes it, each read through [dataSourceFactory].
     */
    internal fun mediaSourceFactory(delegate: MediaSource.Factory, dataSourceFactory: DataSource.Factory): MediaSource.Factory = object : MediaSource.Factory by delegate {
        private val progressive = ProgressiveMediaSource.Factory(dataSourceFactory)

        override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory = apply {
            delegate.setDrmSessionManagerProvider(drmSessionManagerProvider)
            progressive.setDrmSessionManagerProvider(drmSessionManagerProvider)
        }

        override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory = apply {
            delegate.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
            progressive.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        }

        override fun createMediaSource(mediaItem: MediaItem): MediaSource {
            val address = mediaItem.localConfiguration?.uri?.toString().orEmpty()
            val id = LiveDownloads.parse(address)?.takeIf { it.second == null }?.first ?: return delegate.createMediaSource(mediaItem)
            val partCount = live.downloads.value[id]?.parts?.size?.coerceAtLeast(1) ?: 1
            // Each carries the item itself, so the player knows it as it was asked for.
            val sources = (0 until partCount).map { index ->
                progressive.createMediaSource(mediaItem.buildUpon().setUri(LiveDownloads.addressOf(id, index)).build())
            }
            return sources.singleOrNull() ?: MergingMediaSource(*sources.toTypedArray())
        }
    }

    /**
     * One part of a download, read as it is fetched: at its end, it waits for more until the part is whole - see
     * [LiveDownloads] - so playing catches up with the download rather than ending where it has got to.
     */
    private inner class GrowingPartDataSource : BaseDataSource(/* isNetwork = */ false) {
        private var file: RandomAccessFile? = null
        private var uri: Uri? = null
        private var id = 0L
        private var part: DownloadEvent.Part? = null
        private var bytesRemaining = C.LENGTH_UNSET.toLong()

        override fun open(dataSpec: DataSpec): Long {
            val (id, index) = LiveDownloads.parse(dataSpec.uri.toString()) ?: throw IOException("Not a download: ${dataSpec.uri}")
            val part = live.downloads.value[id]?.parts?.getOrNull(index ?: 0) ?: throw FileNotFoundException("Not downloading: ${dataSpec.uri}")
            this.id = id
            this.part = part
            uri = dataSpec.uri
            transferInitializing(dataSpec)
            // What it is fetched into until it is whole, then the whole of it - waited for while neither is there yet.
            var source = part.growing.takeIf { it.exists() } ?: part.whole.takeIf { it.exists() }
            while (source == null) {
                if (live.downloads.value[id]?.isOver != false) throw FileNotFoundException("Never downloaded: ${part.whole}")
                waitForGrowth()
                source = part.growing.takeIf { it.exists() } ?: part.whole.takeIf { it.exists() }
            }
            file = RandomAccessFile(source, "r").apply { seek(dataSpec.position) }
            bytesRemaining = dataSpec.length
            transferStarted(dataSpec)
            return bytesRemaining
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0 || bytesRemaining == 0L) return if (length == 0) 0 else C.RESULT_END_OF_INPUT
            val file = checkNotNull(file)
            val wanted = if (bytesRemaining == C.LENGTH_UNSET.toLong()) length else min(length.toLong(), bytesRemaining).toInt()
            while (true) {
                // Whether it was whole is asked before reading: an end read after it was whole is the end.
                val isWhole = live.downloads.value[id]?.isWhole(checkNotNull(part)) ?: true
                val read = file.read(buffer, offset, wanted)
                if (read > 0) {
                    if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
                    bytesTransferred(read)
                    return read
                }
                if (isWhole) return C.RESULT_END_OF_INPUT
                waitForGrowth()
            }
        }

        override fun getUri(): Uri? = uri

        override fun close() {
            uri = null
            file?.close()
            file = null
            transferEnded()
        }
    }
}

/** Waits for a part to grow - ended by the player cancelling the load, as it interrupts its loading thread. */
private fun waitForGrowth() {
    try {
        Thread.sleep(GrowthWaitMs)
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        throw InterruptedIOException("Stopped waiting for the download").apply { initCause(e) }
    }
}

/** The download this item plays as it downloads - by its address, kept as it plays on from the file saved - null for any other. */
internal val MediaItem.liveDownloadId: Long? get() = LiveDownloads.parse(mediaId)?.first

/**
 * Has this player play on, from the file it saved, each download it plays as it downloads once that is over - at the same
 * moment, the item otherwise as it was.
 */
internal suspend fun Player.followLiveDownloads(live: LiveDownloads) {
    live.downloads.collect { downloads ->
        for (index in 0 until mediaItemCount) {
            val item = getMediaItemAt(index)
            val (id, _) = LiveDownloads.parse(item.localConfiguration?.uri?.toString().orEmpty()) ?: continue
            val saved = downloads[id]?.saved ?: continue
            val isCurrent = index == currentMediaItemIndex
            val positionMs = currentPosition
            replaceMediaItem(index, item.buildUpon().setUri(Uri.fromFile(saved)).build())
            if (isCurrent) seekTo(index, positionMs)
        }
    }
}
