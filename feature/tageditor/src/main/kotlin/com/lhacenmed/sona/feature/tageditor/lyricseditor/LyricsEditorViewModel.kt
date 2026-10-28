package com.lhacenmed.sona.feature.tageditor.lyricseditor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.playback.PlaybackController
import com.lhacenmed.sona.feature.tageditor.OpenTagDrafts
import com.lhacenmed.sona.feature.tageditor.SaveOutcome
import com.lhacenmed.sona.feature.tageditor.TrackTagsRepository
import com.lhacenmed.sona.feature.tageditor.saveOutcomeOf
import com.lhacenmed.sona.feature.tageditor.tags.TagField
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The lyrics editor on its own, for the track [trackId]: on the lyrics its file holds, saved back into the file - or,
 * where it [editsTagDraft], on the lyrics of the draft a tag editor has open for it, kept back into the draft, to be
 * saved with its tags.
 */
@HiltViewModel(assistedFactory = LyricsEditorViewModel.Factory::class)
class LyricsEditorViewModel @AssistedInject constructor(
    @Assisted private val trackId: Long,
    @Assisted val editsTagDraft: Boolean,
    libraryRepository: LibraryRepository,
    private val trackTagsRepository: TrackTagsRepository,
    private val playbackController: PlaybackController,
    openTagDrafts: OpenTagDrafts,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(trackId: Long, editsTagDraft: Boolean): LyricsEditorViewModel
    }

    /** The tag editor's draft the lyrics are kept into, where they are - null for one no longer open. */
    private val draft = if (editsTagDraft) openTagDrafts[trackId] else null

    /** The editor, once the file's lyrics have been read - at once on a draft's, and never on a draft no longer open. */
    var editor by mutableStateOf<LyricsEditorState?>(null)
        private set

    var isSaving by mutableStateOf(false)
        private set

    var saveOutcome by mutableStateOf<SaveOutcome?>(null)
        private set

    /** Whether the editor was opened on a tag editor's draft no longer open - with nothing left to keep the lyrics in. */
    val isDraftGone: Boolean get() = editsTagDraft && draft == null

    init {
        if (draft != null) {
            editor = LyricsEditorState(draft.track, draft.tags[TagField.LYRICS], playbackController, viewModelScope)
        } else if (!editsTagDraft) {
            // The library's own copy of the track is already in memory, so the editor is whole from its first frame.
            libraryRepository.tracksById.value[trackId]?.let(::openOnFile)
                ?: viewModelScope.launch { openOnFile(libraryRepository.tracksById.map { it[trackId] }.filterNotNull().first()) }
        }
    }

    /** Opens the editor on [track] at once, its lines following as the file is read. */
    private fun openOnFile(track: Track) {
        val editor = LyricsEditorState(track, initialLyrics = null, playbackController, viewModelScope)
        this.editor = editor
        viewModelScope.launch { editor.takeFileLyrics(trackTagsRepository.readLyrics(track)) }
    }

    /** Has the playing track repeat while the editor is in front - see [PlaybackController.holdCurrentTrack]. */
    fun holdPlayingTrack(): () -> Unit = playbackController.holdCurrentTrack()

    /** Writes the lyrics into the file - or keeps them in the draft; [saveOutcome] tells how it went. */
    fun save() {
        val editor = editor ?: return
        if (isSaving) return
        if (draft != null) {
            draft.setField(TagField.LYRICS, editor.lyrics)
            saveOutcome = SaveOutcome.Saved
            return
        }
        isSaving = true
        viewModelScope.launch {
            saveOutcome = saveOutcomeOf("The lyrics could not be saved") {
                trackTagsRepository.saveLyrics(editor.track, editor.lyrics)
            }
            isSaving = false
        }
    }

    fun onSaveOutcomeHandled() {
        saveOutcome = null
    }
}
