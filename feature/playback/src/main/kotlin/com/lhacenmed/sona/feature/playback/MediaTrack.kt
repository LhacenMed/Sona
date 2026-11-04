package com.lhacenmed.sona.feature.playback

import androidx.media3.common.C
import androidx.media3.common.Tracks

/**
 * One of the audio or subtitle tracks inside the file playing - found by where it lies in the player's tracks, the
 * same for as long as the file is - as far as anything choosing between them needs to know it.
 */
data class MediaTrack(
    val groupIndex: Int,
    val trackIndex: Int,
    /** Its language's code, or null where the file does not say. */
    val language: String?,
    /** The name the file gives it - "Commentary", say. */
    val label: String?,
    /** How many channels it has - 0 for a subtitle track. */
    val channelCount: Int,
    /** A subtitle track only for what is not in the film's own language, shown even with subtitles unasked for. */
    val isForced: Boolean,
    val isSelected: Boolean,
)

/** The tracks of [type] the device can play, in the order the file has them. */
internal fun Tracks.mediaTracksOf(type: @C.TrackType Int): List<MediaTrack> = buildList {
    groups.forEachIndexed { groupIndex, group ->
        if (group.type != type) return@forEachIndexed
        for (trackIndex in 0 until group.length) {
            if (!group.isTrackSupported(trackIndex)) continue
            val format = group.getTrackFormat(trackIndex)
            add(
                MediaTrack(
                    groupIndex = groupIndex,
                    trackIndex = trackIndex,
                    language = format.language?.takeUnless { it == C.LANGUAGE_UNDETERMINED },
                    label = format.label,
                    channelCount = format.channelCount.coerceAtLeast(0),
                    isForced = format.selectionFlags and C.SELECTION_FLAG_FORCED != 0,
                    isSelected = group.isTrackSelected(trackIndex),
                ),
            )
        }
    }
}
