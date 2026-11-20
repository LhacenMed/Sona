package com.lhacenmed.sona.core.model

/** Where the user marked each video, to come back to - kept by the space the video is in: the library's, the Private Folder's. */
interface VideoBookmarkStore {
    /** [videoId]'s bookmarks, earliest first. */
    suspend fun read(videoId: Long): List<Long>

    suspend fun add(videoId: Long, positionMs: Long)

    suspend fun remove(videoId: Long, positionMs: Long)
}
