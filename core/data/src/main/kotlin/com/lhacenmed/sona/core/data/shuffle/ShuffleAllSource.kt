package com.lhacenmed.sona.core.data.shuffle

import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.data.isLoading
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.datastore.ShuffleSettings
import com.lhacenmed.sona.core.model.PlaybackParent
import com.lhacenmed.sona.core.model.Track
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the shuffle-all button and the launcher shortcut shuffle, named as the library has it now. */
sealed interface ShuffleAllSource {
    /** The collection shuffled, or null for every track - which is also what the queue plays from. */
    val parent: PlaybackParent?

    data object AllTracks : ShuffleAllSource {
        override val parent: PlaybackParent? get() = null
    }

    data class Collection(override val parent: PlaybackParent, val name: String) : ShuffleAllSource
}

/**
 * The one place the shuffle-all source is read and chosen - by the library's button, the settings and
 * the player alike, so each of them always agrees on what a shuffle-all plays.
 *
 * A chosen collection since gone from the library - a deleted playlist, an album rescanned away - reads
 * as [ShuffleAllSource.AllTracks] rather than leaving a button that plays nothing. The choice itself is
 * kept, so it holds again should the collection come back.
 */
@Singleton
class ShuffleAllSourceRepository @Inject constructor(
    private val repository: LibraryRepository,
    private val shuffleSettings: ShuffleSettings,
    @ApplicationScope private val scope: CoroutineScope,
) {

    /** Held for the whole process, so a screen opening shows the source already named. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val source: StateFlow<ShuffleAllSource> = shuffleSettings.shuffleAllSource.flow
        .flatMapLatest(::sourceOf)
        .stateIn(scope, SharingStarted.Eagerly, ShuffleAllSource.AllTracks)

    /**
     * The source as it stands, waited for rather than read from [source] - which starts out as every
     * track until the library names the chosen collection. What playing it, as the app opens, needs.
     */
    suspend fun current(): ShuffleAllSource = sourceOf(shuffleSettings.shuffleAllSource.value).first()

    /**
     * Makes [parent] - every track, while null - what a shuffle-all plays from now on. Written on the
     * application's scope, so a screen closing as it chooses cannot cancel the choice.
     */
    fun choose(parent: PlaybackParent?) {
        scope.launch { shuffleSettings.setShuffleAllSource(parent) }
    }

    /** [parent]'s tracks - every track, while null - in the order its own list shows them, once read. */
    suspend fun tracks(parent: PlaybackParent?): List<Track> =
        (parent?.let(repository::collectionTracks) ?: repository.tracks).first { !it.isLoading }.itemsOrEmpty

    private fun sourceOf(parent: PlaybackParent?): Flow<ShuffleAllSource> =
        if (parent == null) {
            flowOf(ShuffleAllSource.AllTracks)
        } else {
            repository.collectionName(parent).map { name ->
                if (name == null) ShuffleAllSource.AllTracks else ShuffleAllSource.Collection(parent, name)
            }
        }
}
