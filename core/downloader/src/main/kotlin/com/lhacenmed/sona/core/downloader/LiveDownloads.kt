package com.lhacenmed.sona.core.downloader

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A download as it is played while it downloads: the [parts] it fetches into - a video's sound and picture, or the whole - each
 * as it grows, how far the last is - [fraction] - and, once it is over, the file it [saved], which playing switches to.
 */
data class LiveDownload(
    val parts: List<DownloadEvent.Part> = emptyList(),
    val fraction: Float? = null,
    val isOver: Boolean = false,
    val saved: File? = null,
) {
    /** Whether it can be played yet: its first part begun - both of a video's, where its sound and picture are fetched apart. */
    val isPlayable: Boolean get() = !isOver && (parts.size >= 2 || parts.singleOrNull()?.let { !SeparateStream.containsMatchIn(it.whole.name) } == true)

    /** How much of it is downloaded, from 0 to 1, while it is not over - what a seek bar shows of it. */
    val downloadedFraction: Float? get() = if (isOver) null else fraction ?: 0f

    /** Whether [part] has all of itself: whole where it was fetched, or the download over. */
    fun isWhole(part: DownloadEvent.Part): Boolean = isOver || (part.growing != part.whole && !part.growing.exists())
}

/** How yt-dlp names one of a video's streams fetched apart, to be merged: "Title [id].f399.mp4". */
private val SeparateStream = Regex("""\.f[\w-]+\.\w+$""")

/**
 * Every download running, as it may be played while it downloads - the one record the runner writes and the players read. A
 * download is played by its address, `sona-download://<id>/<part>` - see [addressOf].
 */
@Singleton
class LiveDownloads @Inject constructor() {
    private val _downloads = MutableStateFlow<Map<Long, LiveDownload>>(emptyMap())

    /** Every download running, or just over, by id. */
    val downloads: StateFlow<Map<Long, LiveDownload>> = _downloads.asStateFlow()

    private fun update(id: Long, change: (LiveDownload) -> LiveDownload) = _downloads.update { it + (id to change(it[id] ?: LiveDownload())) }

    /** [id] begins to download into [part]. */
    fun addPart(id: Long, part: DownloadEvent.Part) = update(id) { live -> live.copy(parts = live.parts.filterNot { it.whole == part.whole } + part) }

    /** How far [id]'s last part is, from 0 to 1. */
    fun setFraction(id: Long, fraction: Float?) = update(id) { it.copy(fraction = fraction) }

    /** [id] is over, having [saved] its file - null where it saved none, failed or stopped. */
    fun finish(id: Long, saved: File?) = update(id) { it.copy(isOver = true, saved = saved) }

    /** Forgets [id], played no longer - or started again. */
    fun forget(id: Long) = _downloads.update { it - id }

    companion object {
        const val SCHEME = "sona-download"

        /** The address [id] is played by - its [part] alone, where it is one of several. */
        fun addressOf(id: Long, part: Int? = null): String = "$SCHEME://$id" + (part?.let { "/$it" } ?: "")

        /** The download and the part [address] plays - null for any other address. */
        fun parse(address: String): Pair<Long, Int?>? {
            if (!address.startsWith("$SCHEME://")) return null
            val path = address.removePrefix("$SCHEME://").split('/')
            return path[0].toLongOrNull()?.let { it to path.getOrNull(1)?.toIntOrNull() }
        }
    }
}
