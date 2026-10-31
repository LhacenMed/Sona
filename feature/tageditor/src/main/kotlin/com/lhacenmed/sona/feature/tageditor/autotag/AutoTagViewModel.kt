package com.lhacenmed.sona.feature.tageditor.autotag

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.model.Folder
import com.lhacenmed.sona.core.model.FolderPicks
import com.lhacenmed.sona.core.model.Track
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * What an auto-tag run would cover - the tracks [trackIds] names, or the whole library, which leaves out the
 * excluded folders already - narrowed to the folders the user keeps in it, and what it would find for them; then
 * the run itself, once [start]ed, whoever started it.
 */
@HiltViewModel(assistedFactory = AutoTagViewModel.Factory::class)
class AutoTagViewModel @AssistedInject constructor(
    @Assisted private val trackIds: List<Long>?,
    libraryRepository: LibraryRepository,
    private val autoTagger: AutoTagger,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(trackIds: List<Long>?): AutoTagViewModel
    }

    val session: AutoTagSession? get() = autoTagger.session

    var choices by mutableStateOf(AutoTagChoices())

    /** The folders left out of the run, by path - none to begin with, so a folder that appears meanwhile is in it. */
    /** What is left out of the run - folders and files, by path, see [FolderPicks]. */
    var leftOut by mutableStateOf(emptySet<String>())
        private set

    // Each starts from what the library already holds, so the screen is whole from its first frame.

    /** Every track the run could cover - videos, played for their sound and seldom songs, never among them. */
    val coverableTracks: StateFlow<List<Track>> =
        combine(libraryRepository.tracks, libraryRepository.tracksById, ::coverableTracksOf)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), coverableTracksOf(libraryRepository.tracks.value, libraryRepository.tracksById.value))

    /** The folders [coverableTracks] are in, each with how many of them it holds. */
    val folders: StateFlow<List<Pair<Folder, Int>>> =
        combine(libraryRepository.mediaFolders, coverableTracks, ::foldersOf)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), foldersOf(libraryRepository.mediaFolders.value, coverableTracks.value))

    /** The tracks the run would look up as things stand. */
    val tracks: StateFlow<List<Track>> =
        combine(coverableTracks, snapshotFlow { leftOut }, ::tracksIn)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), tracksIn(coverableTracks.value, leftOut))

    private fun coverableTracksOf(library: LibraryContent<Track>, byId: Map<Long, Track>): List<Track> =
        if (trackIds == null) library.itemsOrEmpty else trackIds.mapNotNull(byId::get).filterNot { it.isVideo }

    /** What is picked for the run, as the folder picker keys it - see [FolderPicks]. */
    val picked: Set<String> get() = FolderPicks(coverableTracks.value).allBut(leftOut)

    /** Keeps the folders and files [picked] in the run, and every other one out of it. */
    fun keep(picked: Set<String>) {
        leftOut = FolderPicks(coverableTracks.value).allBut(picked)
    }

    fun start() {
        val tracks = tracks.value
        if (tracks.isNotEmpty() && choices.findsAnything) autoTagger.start(tracks, choices)
    }

    fun stop() = autoTagger.stop()

    fun retryUnreachable() = autoTagger.retryUnreachable()

    fun save() = autoTagger.save()

    fun discard() = autoTagger.discard()
}

private fun foldersOf(folders: LibraryContent<Folder>, tracks: List<Track>): List<Pair<Folder, Int>> {
    val counts = tracks.groupingBy { it.folderPath }.eachCount()
    return folders.itemsOrEmpty.mapNotNull { folder -> counts[folder.path]?.let { folder to it } }
}

private fun tracksIn(tracks: List<Track>, leftOut: Set<String>): List<Track> =
    tracks.filterNot { it.folderPath in leftOut || it.path in leftOut }
