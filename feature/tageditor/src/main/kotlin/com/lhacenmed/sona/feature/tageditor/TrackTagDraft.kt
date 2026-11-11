package com.lhacenmed.sona.feature.tageditor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.tageditor.lookup.CatalogueMatch
import com.lhacenmed.sona.feature.tageditor.tags.CoverChoice
import com.lhacenmed.sona.feature.tageditor.tags.TagFileInfo
import com.lhacenmed.sona.feature.tageditor.tags.TrackTags
import kotlinx.coroutines.CoroutineScope

/**
 * [track]'s tags as a [SongDraft] over the [original] its [file] holds - made from the track alone, so whatever shows it
 * has it from its first frame, and taking the file's tags as they are read - the one state the tag editor edits a track
 * in, and an auto-tag run each of its tracks. Nothing here reaches the file: whoever holds the draft saves it.
 */
@Stable
class TrackTagDraft(
    val track: Track,
    scope: CoroutineScope,
) : SongDraft(track.durationMs, scope) {
    /** What the user has made of the draft - its tags, its cover and the match they came from - to come back to. */
    data class Edits(val tags: TrackTags, val cover: CoverChoice, val appliedMatch: CatalogueMatch?)

    /** What the file held as it was read, or null until it has been - see [takeFile]. */
    var file by mutableStateOf<TagFileInfo?>(null)
        private set

    val original: TrackTags get() = file?.tags ?: TrackTags()

    /** Takes what the file holds, read, as what the draft starts from - before anything can be applied over it. */
    fun takeFile(info: TagFileInfo) {
        file = info
        tags = info.tags
    }

    val hasChanges: Boolean get() = tags != original || cover != CoverChoice.Own

    val edits: Edits get() = Edits(tags, cover, appliedMatch)

    /** Goes back to [edits], as the draft stood when they were taken. */
    fun restore(edits: Edits) {
        tags = edits.tags
        cover = edits.cover
        appliedMatch = edits.appliedMatch
    }
}
