/*
 * The way a file is split and its parts shared out is AB Download Manager's (https://github.com/amir1376/ab-download-manager) -
 * its `splitToRange`, `RangedPart` and `PartSplitSupport`. Copyright AmirHossein Abdolmotallebi, licensed under the Apache
 * License, Version 2.0: https://www.apache.org/licenses/LICENSE-2.0
 */
package com.lhacenmed.sona.core.downloader.transfer

/** How many connections a download makes at once: its parts' over HTTP, as its fragments' are through yt-dlp. */
internal const val ConnectionsPerDownload = 8

/** The smallest part a file is split into - smaller is not worth a connection of its own. */
internal const val MinPartBytes = 512L * 1024

/** How far ahead of where it reads a part's connection holds bytes as its own: what no split may take from it. */
private const val ClaimBytes = 128L * 8192

/**
 * The bytes from [from] to [to], both included, of a file fetched in parts at once - [current] the next one to fetch. Its [to]
 * draws closer as another connection takes the rest of it - see [FileParts.next].
 */
internal class FilePart(val from: Long, to: Long, current: Long = from) {
    @Volatile var to: Long = to
        internal set

    @Volatile var current: Long = current
        internal set

    /** The last byte its connection holds as its own - where a split may begin no sooner than past. */
    internal var claimedTo: Long = current - 1

    val isComplete: Boolean get() = current > to
}

/**
 * The parts of one file, shared out among the connections fetching it - AB Download Manager's dynamic parts: a connection free
 * takes a part no one fetches, and once none is left, half of what is left of the largest one being fetched, so every
 * connection keeps fetching to the end. Safe to call from every connection at once.
 */
internal class FileParts(parts: List<FilePart>) {
    private val all = parts.toMutableList()
    private val fetching = mutableSetOf<FilePart>()

    /** Every part as it stands this moment, none of them mid-split - what is recorded to carry on from. */
    fun snapshot(): List<FilePart> = synchronized(this) { all.map { FilePart(it.from, it.to, it.current) } }

    /** Whether every byte of the file is fetched. */
    val isComplete: Boolean get() = synchronized(this) { all.all { it.isComplete } }

    /** [part] is fetched by no one any longer, unfinished: another connection's to take. */
    fun release(part: FilePart): Unit = synchronized(this) { fetching -= part }

    /** The bytes fetched of the whole file so far. */
    val doneBytes: Long get() = synchronized(this) { all.sumOf { it.current - it.from } }

    /**
     * The part a connection free fetches next - one no one fetches, else one split off the largest being fetched - and [done],
     * the one it fetched, no longer its own. Null once there is none worth a connection.
     */
    fun next(done: FilePart?): FilePart? = synchronized(this) {
        done?.let(fetching::remove)
        val waiting = all.firstOrNull { !it.isComplete && it !in fetching } ?: split()
        waiting?.also(fetching::add)
    }

    /** How many bytes [part]'s connection may read now, at most [wanted]: those it holds, its hold reaching further if it must. */
    fun claim(part: FilePart, wanted: Long): Long = synchronized(this) {
        if (part.claimedTo - part.current + 1 < wanted) part.claimedTo = minOf(part.to, part.claimedTo + maxOf(wanted, ClaimBytes))
        (part.claimedTo - part.current + 1).coerceAtLeast(0)
    }

    /** Half of what lies past the hold of the part being fetched that has the most left there, as a part of its own. */
    private fun split(): FilePart? {
        val largest = fetching.maxByOrNull { it.to - it.claimedTo } ?: return null
        val unheld = largest.to - largest.claimedTo
        val half = unheld / 2 + unheld % 2
        if (unheld - half < MinPartBytes) return null
        val newPart = FilePart(from = largest.claimedTo + half + 1, to = largest.to)
        largest.to = newPart.from - 1
        all += newPart
        return newPart
    }
}

/** [size] bytes cut into at most [maxParts] parts of near the same size, none smaller than [minPartBytes] - AB Download Manager's. */
internal fun splitToRange(size: Long, minPartBytes: Long, maxParts: Int): List<LongRange> {
    require(size >= 1 && minPartBytes >= 1 && maxParts >= 1)
    val count = minOf(maxParts.toLong(), (size + minPartBytes - 1) / minPartBytes)
    val partSize = size / count
    var start = 0L
    return (1..count).map { index ->
        val end = start + partSize - 1 + if (index <= size % count) 1 else 0
        (start..end).also { start = end + 1 }
    }
}
