package com.lhacenmed.sona.core.model

import java.io.File

/**
 * What the user settled on for one video's subtitles, kept for as long as the video is.
 *
 * [attachedName] is the name of the file attached to the video, whose copy its [VideoSubtitleStore] holds, and
 * [offsetMs] how much later than the file says its lines show - earlier while negative. [choice] is what the user
 * picked in the player, in the form the player reads it; null while nothing was, which leaves it to decide.
 */
data class VideoSubtitle(
    val attachedName: String? = null,
    val offsetMs: Long = 0L,
    val choice: String? = null,
)

/** Where one part of the app - the library, or the Private Folder - keeps its videos' subtitles, by each video's id in it. */
interface VideoSubtitleStore {
    /** Where the copies of attached files lie. */
    val directory: File

    suspend fun read(videoId: Long): VideoSubtitle

    suspend fun write(videoId: Long, subtitle: VideoSubtitle)

    /** The copy of the file named [attachedName] attached to the video [videoId]. */
    fun attachedFile(videoId: Long, attachedName: String): File =
        File(directory, "$videoId.${File(attachedName).extension.lowercase()}")

    /** Removes the copy of whichever file is attached to the video [videoId]. */
    fun deleteAttachedFile(videoId: Long) {
        directory.listFiles { file -> file.nameWithoutExtension == videoId.toString() }?.forEach(File::delete)
    }
}
