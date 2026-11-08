package com.lhacenmed.sona.core.model

/**
 * What a collection's tracks add up to - what it can be sorted by beyond its name and counts: how long they
 * play, how many times they were listened to through, and when the first of them joined the library.
 */
data class CollectionStats(
    val durationMs: Long = 0L,
    val playCount: Int = 0,
    val dateAddedSeconds: Long = 0L,
) {
    /** These and [other]'s tracks together - a folder's and those of the folders listed with it. */
    operator fun plus(other: CollectionStats) = CollectionStats(
        durationMs = durationMs + other.durationMs,
        playCount = playCount + other.playCount,
        dateAddedSeconds = minOf(dateAddedSeconds, other.dateAddedSeconds),
    )
}
