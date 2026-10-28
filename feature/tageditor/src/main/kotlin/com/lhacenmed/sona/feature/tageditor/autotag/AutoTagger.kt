package com.lhacenmed.sona.feature.tageditor.autotag

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.designsystem.component.toast
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.tageditor.TrackEdit
import com.lhacenmed.sona.feature.tageditor.TrackTagDraft
import com.lhacenmed.sona.feature.tageditor.TrackTagsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * How many tracks are looked up at once: enough to keep every catalogue and lyrics source busy, few enough to stay
 * inside what they let one device ask - past it, they stop answering for a while.
 */
private const val TracksAtOnce = 3

/** How far a working run is - what its notification shows. */
data class AutoTagProgress(val phase: AutoTagPhase, val done: Int, val total: Int)

/**
 * Tags many tracks at once - the library's, a folder's, a selection's: every track looked up as the tag editor looks
 * one up, the best of everything found put into its draft unasked - see [autoFill] - then the drafts the user keeps
 * written into their files together.
 *
 * One run at a time, held here rather than by a screen, so it carries on - with [AutoTagService] keeping the app
 * alive and the shade told how far it is - while the user is elsewhere, in the app or out of it; its results wait in
 * memory until they are saved or let go. Nothing reaches a file before [save].
 */
@Singleton
class AutoTagger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackTagsRepository: TrackTagsRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    /** The run, from [start] until it is let go - see [discard]. */
    var session by mutableStateOf<AutoTagSession?>(null)
        private set

    /** Where the run is while it works, or null while it waits on the user - or there is none. */
    val progress: Flow<AutoTagProgress?> = snapshotFlow {
        session?.takeIf { it.phase != AutoTagPhase.REVIEWING }?.let { AutoTagProgress(it.phase, it.done, it.total) }
    }

    /** The run's lookups and its drafts', on the main thread, where its state changes - cancelled with the run. */
    private var runScope: CoroutineScope? = null

    private var work: Job? = null

    /** Lets any run go, then looks [tracks] up, filling each with what [choices] asks for. */
    fun start(tracks: List<Track>, choices: AutoTagChoices) {
        discard()
        val session = AutoTagSession(tracks, choices)
        this.session = session
        runScope = CoroutineScope(SupervisorJob(appScope.coroutineContext.job) + Dispatchers.Main.immediate)
        lookUp(session, session.items.toList())
    }

    /** Looks up again every track no catalogue could be reached for. */
    fun retryUnreachable() {
        val session = session?.takeIf { it.phase == AutoTagPhase.REVIEWING } ?: return
        lookUp(session, session.items.filter { it.outcome == AutoTagOutcome.UNREACHABLE })
    }

    /** Stops looking tracks up, what was found so far kept to be reviewed - the tracks not reached left out. */
    fun stop() {
        val session = session?.takeIf { it.phase == AutoTagPhase.MATCHING } ?: return
        work?.cancel()
        session.items.removeAll { it.outcome == null }
        session.phase = AutoTagPhase.REVIEWING
    }

    /**
     * Writes every picked track's draft into its file. A track saved leaves the run; one that could not be says why,
     * still picked, to be saved again. The run ends once no track is left in it.
     */
    fun save() {
        val session = session?.takeIf { it.phase == AutoTagPhase.REVIEWING } ?: return
        val scope = runScope ?: return
        val picked = session.pickedItems.associateBy { it.track.id }
        if (picked.isEmpty()) return
        session.phase = AutoTagPhase.SAVING
        session.done = 0
        session.total = picked.size
        work = scope.launch {
            var failures = 0
            val edits = picked.values.map { item -> checkNotNull(item.draft).let { TrackEdit(item.track, it.tags, it.cover) } }
            try {
                trackTagsRepository.saveAll(edits) { edit, failure ->
                    val item = picked.getValue(edit.track.id)
                    if (failure == null) {
                        session.items.remove(item)
                        session.picks.deselectAll(listOf(item.track.id))
                    } else {
                        item.saveFailure = failure.message ?: "The tags could not be saved"
                        failures++
                    }
                    session.done++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Only the library's reading the files back can fail here - every file has been written by then.
            } finally {
                session.phase = AutoTagPhase.REVIEWING
            }
            val saved = picked.size - failures
            context.toast(
                listOfNotNull(
                    "Saved ${tracksLabel(saved)}".takeIf { saved > 0 },
                    "${tracksLabel(failures)} could not be saved".takeIf { failures > 0 },
                ).joinToString(" · "),
            )
            if (session.items.isEmpty()) discard()
        }
        AutoTagService.start(context)
    }

    /**
     * Picks the track [trackId] to be saved where its draft now changes its file, and unpicks it where it does not -
     * what keeping its review does.
     */
    fun keepReview(trackId: Long) {
        val session = session ?: return
        val item = session.items.firstOrNull { it.track.id == trackId } ?: return
        if (item.hasChanges) session.picks.selectAll(listOf(trackId)) else session.picks.deselectAll(listOf(trackId))
    }

    /** Lets the run go, whatever it is doing - unsaved drafts with it. */
    fun discard() {
        runScope?.cancel()
        runScope = null
        work = null
        session = null
    }

    private fun lookUp(session: AutoTagSession, items: List<AutoTagItem>) {
        val scope = runScope ?: return
        if (items.isEmpty()) return
        session.phase = AutoTagPhase.MATCHING
        session.done = 0
        session.total = items.size
        work = scope.launch {
            val gate = Semaphore(TracksAtOnce)
            items.map { item -> launch { gate.withPermit { lookUp(session, item, scope) } } }.joinAll()
            session.phase = AutoTagPhase.REVIEWING
        }
        AutoTagService.start(context)
    }

    private suspend fun lookUp(session: AutoTagSession, item: AutoTagItem, scope: CoroutineScope) {
        val draft = TrackTagDraft(item.track, scope).apply { takeFile(trackTagsRepository.read(item.track)) }
        // Asked only where a cover is being found: it loads the file's pictures.
        val hasOwnCover = session.choices.findsCover && trackTagsRepository.hasCover(item.track)
        val outcome = draft.autoFill(session.choices, hasOwnCover)
        item.draft = draft
        item.outcome = outcome
        item.saveFailure = null
        if (outcome == AutoTagOutcome.READY) session.picks.selectAll(listOf(item.track.id))
        session.done++
    }
}

/** "1 track", "12 tracks". */
internal fun tracksLabel(count: Int): String = if (count == 1) "1 track" else "$count tracks"
