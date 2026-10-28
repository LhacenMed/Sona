package com.lhacenmed.sona.feature.library.uncounted

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.SonaEmptyState
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.fab.ExtendedFloatingActionButtonContent
import com.lhacenmed.sona.core.designsystem.component.fab.SonaExtendedFloatingActionButtons
import com.lhacenmed.sona.core.designsystem.component.rememberSelectionState
import com.lhacenmed.sona.core.designsystem.component.screen.screenList
import com.lhacenmed.sona.core.model.Album
import com.lhacenmed.sona.core.model.Artist
import com.lhacenmed.sona.core.model.Folder
import com.lhacenmed.sona.core.model.Genre
import com.lhacenmed.sona.core.model.Playlist
import com.lhacenmed.sona.core.model.toStorageKey
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.library.AlbumRow
import com.lhacenmed.sona.feature.library.ArtistRow
import com.lhacenmed.sona.feature.library.FolderRow
import com.lhacenmed.sona.feature.library.GenreRow
import com.lhacenmed.sona.feature.library.PlaylistRow
import com.lhacenmed.sona.feature.library.pluralCount
import com.lhacenmed.sona.feature.library.selection.selectionKeyOf

/**
 * The collections whose plays are kept out of Most played - chosen from each one's options - listed as the library
 * lists them, and let back in many at a time: pressing them picks them, as a list's rows are selected, and the
 * screen's button then has their plays count again.
 */
data object UncountedCollectionsScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel: UncountedCollectionsViewModel = hiltViewModel()
        val collections by viewModel.collections.collectAsStateWithLifecycle()
        val picks = rememberSelectionState()
        // The rows select by the library's own keys; each stands for the collection it plays as.
        val pickedParents = collections.filter { selectionKeyOf(it.item)?.let(picks::isSelected) == true }.map { it.parent }

        BackHandler(enabled = picks.isActive) { picks.clear() }

        SonaExtendedFloatingActionButtons(
            listOf(
                ExtendedFloatingActionButtonContent(
                    label = "Count ${pluralCount(pickedParents.size, "collection")} again",
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    onClick = {
                        viewModel.countAgain(pickedParents)
                        picks.clear()
                    },
                    isPrimary = true,
                    isVisible = pickedParents.isNotEmpty(),
                ),
            ),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            SonaTopAppBar(title = "Not counted in Most played", onNavigateBack = navigator::back)
            if (collections.isEmpty()) {
                SonaEmptyState(
                    title = "Every collection counts",
                    message = "Choose Don't count in Most played from a playlist's, album's, artist's, genre's or folder's options " +
                        "to keep plays from it out of Most played. Played from anywhere else, its tracks still count.",
                )
                return@Column
            }
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().screenList(listState),
                contentPadding = PaddingValues(bottom = LocalBottomContentPadding.current),
            ) {
                items(collections, key = { it.parent.toStorageKey() }) { collection ->
                    val toggle = { selectionKeyOf(collection.item)?.let(picks::toggle) ?: Unit }
                    when (val item = collection.item) {
                        is Album -> AlbumRow(item, picks, isCurrent = { false }, isPlaying = { false }, onClick = toggle, onOpenOptions = null)
                        is Artist -> ArtistRow(item, picks, isCurrent = { false }, isPlaying = { false }, onClick = toggle, onOpenOptions = null)
                        is Genre -> GenreRow(item, picks, isCurrent = { false }, isPlaying = { false }, onClick = toggle, onOpenOptions = null)
                        is Playlist ->
                            PlaylistRow(item, picks, isCurrent = { false }, isPlaying = { false }, onClick = toggle, onOpenOptions = null)
                        is Folder -> FolderRow(item, picks, isCurrent = { false }, isPlaying = { false }, onClick = toggle, onOpenOptions = null)
                    }
                }
            }
        }
    }
}
