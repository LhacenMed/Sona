package com.lhacenmed.sona.feature.tageditor.autotag

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lhacenmed.sona.core.designsystem.component.SelectionState
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.tageditor.TrackTagDraft
import com.lhacenmed.sona.feature.tageditor.tags.CoverChoice
import com.lhacenmed.sona.feature.tageditor.tags.TagField

/** What a run is doing: looking its tracks up, waiting on the user to review them, or saving those picked. */
enum class AutoTagPhase { MATCHING, REVIEWING, SAVING }

/** One track of a run: its draft and what the run made of it, once looked up, and why it could not be saved, where it could not. */
@Stable
class AutoTagItem(val track: Track) {
    var draft by mutableStateOf<TrackTagDraft?>(null)
        internal set

    var outcome by mutableStateOf<AutoTagOutcome?>(null)
        internal set

    var saveFailure by mutableStateOf<String?>(null)
        internal set

    /** Whether saving would change the file - the one kind of track there is anything to pick for. */
    val hasChanges: Boolean get() = draft?.hasChanges == true

    /** What saving would change, by name - "Album, Year, Cover, Lyrics" - or why there is nothing to. */
    val summary: String
        get() {
            val draft = draft
            saveFailure?.let { return "Not saved: $it" }
            if (draft == null || !draft.hasChanges) {
                return when (outcome) {
                    AutoTagOutcome.UNREACHABLE -> "The catalogues could not be reached"
                    AutoTagOutcome.NOT_FOUND -> "No match found"
                    else -> "Nothing to change"
                }
            }
            val changed = TagField.entries.filter { draft.tags[it] != draft.original[it] }.map { it.label } +
                listOfNotNull("Cover".takeIf { draft.cover != CoverChoice.Own })
            return changed.joinToString(", ")
        }
}

/**
 * One auto-tag run over [tracks]: each track's draft as it is looked up, which of them are picked to be saved, and
 * how far the run is. Kept by [AutoTagger] for as long as the run lasts, whatever screen shows it.
 */
@Stable
class AutoTagSession internal constructor(tracks: List<Track>, val choices: AutoTagChoices) {
    /** Every track still in the run, in the order they were asked for - a track saved leaves it. */
    val items = mutableStateListOf<AutoTagItem>().apply { addAll(tracks.map(::AutoTagItem)) }

    /** The tracks to save, by id: those looked up convincingly to begin with, then as the user picks them. */
    val picks = SelectionState()

    var phase by mutableStateOf(AutoTagPhase.MATCHING)
        internal set

    /** How many of the [total] tracks this phase is for it has done - looked up, or saved. */
    var done by mutableIntStateOf(0)
        internal set

    var total by mutableIntStateOf(tracks.size)
        internal set

    /** The tracks looked up, by what the run made of them. */
    val groups: Map<AutoTagOutcome, List<AutoTagItem>> by derivedStateOf {
        items.filter { it.outcome != null }.groupBy { checkNotNull(it.outcome) }
    }

    /** The picked tracks saving would change - what it writes. */
    val pickedItems: List<AutoTagItem> by derivedStateOf {
        items.filter { it.hasChanges && picks.isSelected(it.track.id) }
    }

    /** The ids of every track there is anything to save for - what Select all picks. */
    val changedTrackIds: List<Long> by derivedStateOf {
        items.filter { it.hasChanges }.map { it.track.id }
    }
}
