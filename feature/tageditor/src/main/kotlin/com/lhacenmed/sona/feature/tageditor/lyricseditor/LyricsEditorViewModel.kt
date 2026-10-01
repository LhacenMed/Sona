package com.lhacenmed.sona.feature.tageditor.lyricseditor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.feature.playback.PlaybackController
import com.lhacenmed.sona.feature.tageditor.SaveOutcome
import com.lhacenmed.sona.feature.tageditor.TrackTagsRepository
import com.lhacenmed.sona.feature.tageditor.saveOutcomeOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The lyrics editor on its own, for the track [trackId]: on the lyrics its file holds, saved back into the file. */
@HiltViewModel(assistedFactory = LyricsEditorViewModel.Factory::class)
class LyricsEditorViewModel @AssistedInject constructor(
    @Assisted private val trackId: Long,
    libraryRepository: LibraryRepository,
    private val trackTagsRepository: TrackTagsRepository,
    private val playbackController: PlaybackController,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(trackId: Long): LyricsEditorViewModel
    }

    /** The editor, once the file's lyrics have been read. */
    var editor by mutableStateOf<LyricsEditorState?>(null)
        private set

    var isSaving by mutableStateOf(false)
        private set

    var saveOutcome by mutableStateOf<SaveOutcome?>(null)
        private set

    init {
        viewModelScope.launch {
            val track = libraryRepository.tracksById.map { it[trackId] }.filterNotNull().first()
            editor = LyricsEditorState(track, trackTagsRepository.readLyrics(track), playbackController, viewModelScope)
        }
    }

    /** Has the playing track repeat while the editor is in front - see [PlaybackController.holdCurrentTrack]. */
    fun holdPlayingTrack(): () -> Unit = playbackController.holdCurrentTrack()

    /** Writes the lyrics into the file; [saveOutcome] tells how it went. */
    fun save() {
        val editor = editor ?: return
        if (isSaving) return
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
