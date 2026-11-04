package com.lhacenmed.sona.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lhacenmed.sona.core.model.VideoSubtitle

/**
 * What the user settled on for a video's subtitles - see [VideoSubtitle].
 *
 * Tied to no track row, as a scan drops the row of a video on a card that is out, and an attached subtitle is not
 * to go with it: it goes only as the user deletes the video.
 */
@Entity(tableName = "video_subtitles")
data class VideoSubtitleEntity(
    @PrimaryKey val trackId: Long,
    val attachedName: String?,
    val offsetMs: Long,
    val choice: String?,
)

fun VideoSubtitleEntity.toDomain() = VideoSubtitle(attachedName, offsetMs, choice)

fun VideoSubtitle.toEntity(trackId: Long) = VideoSubtitleEntity(trackId, attachedName, offsetMs, choice)
