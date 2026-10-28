package com.lhacenmed.sona.feature.tageditor

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The drafts a tag editor has open, by track - so a screen opened over the editor, each in an activity of its own,
 * edits the very draft the editor shows: the lyrics editor, on its lyrics. Reached from the main thread only.
 */
@Singleton
class OpenTagDrafts @Inject constructor() {
    private val drafts = mutableMapOf<Long, TrackTagDraft>()

    operator fun get(trackId: Long): TrackTagDraft? = drafts[trackId]

    /** Holds [draft] open, for its track, until the returned release is called. */
    fun hold(draft: TrackTagDraft): () -> Unit {
        val trackId = draft.track.id
        drafts[trackId] = draft
        return { if (drafts[trackId] === draft) drafts.remove(trackId) }
    }
}
