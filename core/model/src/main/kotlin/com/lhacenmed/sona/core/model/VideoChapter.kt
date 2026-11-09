package com.lhacenmed.sona.core.model

/** One of a video's chapters, as its site marks them: where it starts, and what it is called. It runs until the next one starts. */
data class VideoChapter(val startMs: Long, val title: String)

/** The chapter [positionMs] falls in - the last to start by then - or null for a video without chapters. */
fun List<VideoChapter>.indexAt(positionMs: Long): Int? {
    if (isEmpty()) return null
    return indexOfLast { it.startMs <= positionMs }.coerceAtLeast(0)
}
