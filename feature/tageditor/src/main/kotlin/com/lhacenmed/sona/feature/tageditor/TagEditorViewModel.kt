package com.lhacenmed.sona.feature.tageditor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.core.model.UnknownNames
import com.lhacenmed.sona.feature.playback.PlaybackController
import com.lhacenmed.sona.feature.tageditor.autotag.AutoTagger
import com.lhacenmed.sona.feature.tageditor.lookup.TrackQueries
import com.lhacenmed.sona.feature.tageditor.tags.CoverChoice
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The track [trackId]'s tags as a [TrackTagDraft] - AutomaTag's editor - and what keeping them does.
 *
 * Opened on the track itself, the draft is made from its file and reaches it only on [save]: the moment it opens,
 * the track is looked up in the catalogues and its lyrics on the web, both at once, under the likeliest reading of
 * its names. [isInAutoTagRun], it is the run's draft for the track, already filled - see [AutoTagger] - and keeping
 * it keeps it in the run, to be saved with the rest.
 */
@HiltViewModel(assistedFactory = TagEditorViewModel.Factory::class)
class TagEditorViewModel @AssistedInject constructor(
    @Assisted private val trackId: Long,
    @Assisted val isInAutoTagRun: Boolean,
    libraryRepository: LibraryRepository,
    private val trackTagsRepository: TrackTagsRepository,
    private val playbackController: PlaybackController,
    private val autoTagger: AutoTagger,
    private val openTagDrafts: OpenTagDrafts,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(trackId: Long, isInAutoTagRun: Boolean): TagEditorViewModel
    }

    /**
     * The draft - null until the file's tags have been read, or for a run's track the run no longer holds. Held
     * open while the editor is, for the lyrics editor opened over it - see [OpenTagDrafts].
     */
    var draft by mutableStateOf<TrackTagDraft?>(null)
        private set

    private var releaseDraft: () -> Unit = {}

    /**
     * What the draft held as the editor opened it - what leaving without keeping goes back to. Snapshot state, as
     * [hasChanges] is read against it: set once the file has been read, it has the screen look again.
     */
    private var opened by mutableStateOf<TrackTagDraft.Edits?>(null)

    var isSaving by mutableStateOf(false)
        private set

    /**
     * How the last save ended, until the screen has acted on it - see [onSaveOutcomeHandled]. Set together
     * with [isSaving] turning false, so whatever the screen holds while saving is let go before it acts.
     */
    var saveOutcome by mutableStateOf<SaveOutcome?>(null)
        private set

    /** Whether the draft was changed since the editor opened it. */
    val hasChanges: Boolean
        get() {
            val draft = draft ?: return false
            val opened = opened ?: return false
            return draft.tags != opened.tags || draft.cover != opened.cover
        }

    /** Whether the track is the one playing, and playing. */
    val isPlaying: StateFlow<Boolean> = playbackController.playbackState
        .map { it.libraryTrackId == trackId && it.isPlaying }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Has the playing track repeat while the editor is in front - see [PlaybackController.holdCurrentTrack]. */
    fun holdPlayingTrack(): () -> Unit = playbackController.holdCurrentTrack()

    /** Pauses or plays on the track where it is the one playing; otherwise starts it, on its own. */
    fun togglePlayback() {
        val track = draft?.track ?: return
        if (playbackController.playbackState.value.libraryTrackId == track.id) {
            playbackController.togglePlayPause()
        } else {
            playbackController.playTracks(listOf(track), startIndex = 0, shuffled = false, startPositionMs = 0L)
        }
    }

    /**
     * The chosen cover's picture, on its way from the moment it is chosen - downloaded or scaled down while the
     * rest is edited, so saving seldom waits for it. Null for the file's own.
     */
    private var preparedCover: Deferred<ByteArray?>? = null

    init {
        if (isInAutoTagRun) {
            draft = autoTagger.session?.items?.firstOrNull { it.track.id == trackId }?.draft
            opened = draft?.edits
            draft?.let { releaseDraft = openTagDrafts.hold(it) }
        } else {
            // The library's own copy of the track is already in memory, so the editor is whole from its first frame.
            libraryRepository.tracksById.value[trackId]?.let(::openOnFile)
                ?: viewModelScope.launch { openOnFile(libraryRepository.tracksById.map { it[trackId] }.filterNotNull().first()) }
        }
    }

    /** Makes the draft for [track] at once, then has it take what its file holds, and looks the track up. */
    private fun openOnFile(track: Track) {
        val draft = TrackTagDraft(track, viewModelScope)
        this.draft = draft
        releaseDraft = openTagDrafts.hold(draft)
        viewModelScope.launch {
            // Read before anything can be applied over them - a matter of milliseconds, as the file is local.
            draft.takeFile(trackTagsRepository.read(track))
            opened = draft.edits
            draft.lookUp(TrackQueries.of(track), album = track.album.takeUnless { it == UnknownNames.ALBUM })
            snapshotFlow { draft.cover }.collect(::prepareCover)
        }
    }

    /** Writes the draft into the file - or, [isInAutoTagRun], keeps it in the run; [saveOutcome] tells how it went. */
    fun save() {
        val draft = draft ?: return
        if (isSaving) return
        if (isInAutoTagRun) {
            autoTagger.keepReview(trackId)
            saveOutcome = SaveOutcome.Saved
            return
        }
        isSaving = true
        viewModelScope.launch {
            val outcome = saveOutcomeOf("The tags could not be saved") {
                trackTagsRepository.save(draft.track, draft.tags, preparedCover?.await())
            }
            // A cover that could not be had is fetched afresh, so saving again tries it again.
            if (outcome != SaveOutcome.Saved && preparedCover?.isCancelled == true) prepareCover(draft.cover)
            isSaving = false
            saveOutcome = outcome
        }
    }

    /** Leaves the draft as the editor opened it - for a run's, which outlives the editor. */
    fun discardChanges() {
        opened?.let { draft?.restore(it) }
    }

    fun onSaveOutcomeHandled() {
        saveOutcome = null
    }

    override fun onCleared() {
        releaseDraft()
    }

    private fun prepareCover(choice: CoverChoice) {
        preparedCover?.cancel()
        preparedCover = if (choice == CoverChoice.Own) null else viewModelScope.async { trackTagsRepository.coverBytes(choice) }
    }
}
