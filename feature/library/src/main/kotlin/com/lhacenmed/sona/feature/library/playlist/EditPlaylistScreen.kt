package com.lhacenmed.sona.feature.library.playlist

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.common.cover.rankedCoverArtUris
import com.lhacenmed.sona.core.data.LibraryContent
import com.lhacenmed.sona.core.data.playlist.coverArtUris
import com.lhacenmed.sona.core.designsystem.component.CoverArtDefaults
import com.lhacenmed.sona.core.designsystem.component.DetailSectionHeader
import com.lhacenmed.sona.core.designsystem.component.FastScroller
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.SonaPlaylistCover
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.SonaTrackRow
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.TopBarSearch
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.model.PlaylistCover
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.library.LibraryListContent
import com.lhacenmed.sona.feature.library.PLAYLIST_NAME_TAKEN_MESSAGE
import com.lhacenmed.sona.feature.library.filterItems
import com.lhacenmed.sona.feature.library.isPlaylistNameTaken
import com.lhacenmed.sona.feature.library.matchesSearch
import com.lhacenmed.sona.feature.library.options.toast
import com.lhacenmed.sona.feature.library.searchEmptyMessage

/**
 * Editing the playlist [playlistId]: its name and its cover, previewed as they are changed and saved
 * together - back leaves both as they were.
 *
 * The cover is the stacked covers of its tracks by default, or one image: its first or last track in
 * its current sort, followed as that changes; one track from anywhere in the library; or an image from
 * the device. Favorites takes a cover like any other playlist, but keeps its name.
 */
data class EditPlaylistScreen(val playlistId: Long) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val context = LocalContext.current
        val viewModel = hiltViewModel<EditPlaylistViewModel, EditPlaylistViewModel.Factory>(
            creationCallback = { factory -> factory.create(playlistId) },
        )
        val playlist by viewModel.playlist.collectAsStateWithLifecycle()
        val takenNames by viewModel.takenNames.collectAsStateWithLifecycle()
        val playlistTracks by viewModel.playlistTracks.collectAsStateWithLifecycle()
        val tracksById by viewModel.tracksById.collectAsStateWithLifecycle()
        var isChoosingTrack by rememberSaveable { mutableStateOf(false) }
        var isSaving by remember { mutableStateOf(false) }

        val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) viewModel.chooseImage(uri.toString())
        }

        if (isChoosingTrack) {
            val libraryTracks by viewModel.libraryTracks.collectAsStateWithLifecycle()
            CoverTrackChooser(
                playlistTracks = playlistTracks,
                libraryTracks = libraryTracks,
                onTrackChosen = { track ->
                    viewModel.chooseTrack(track)
                    isChoosingTrack = false
                },
                onBack = { isChoosingTrack = false },
            )
            return
        }

        val saved = playlist
        val isRenamable = saved?.isBuiltIn == false
        val isNameTaken = isPlaylistNameTaken(viewModel.name, takenNames)
        val canSave = viewModel.isDraftReady && saved != null && !isSaving &&
            viewModel.name.isNotBlank() && !isNameTaken &&
            (viewModel.name.trim() != saved.name || viewModel.cover != saved.cover)

        val chosenTrack = viewModel.chosenTrackId?.let(tracksById::get)
        val stackedCoverArtUris = remember(playlistTracks) { rankedCoverArtUris(playlistTracks.map { it.coverArtUri }) }
        fun coverArtUrisOf(cover: PlaylistCover) =
            cover.coverArtUris(stackedCoverArtUris, playlistTracks, chosenTrack?.coverArtUri)

        Column(modifier = Modifier.fillMaxSize()) {
            SonaTopAppBar(
                title = "Edit playlist",
                onNavigateBack = navigator::back,
                actions = listOf(
                    TopBarAction(label = "Save", icon = Icons.Filled.Check, enabled = canSave) {
                        isSaving = true
                        viewModel.save { succeeded ->
                            isSaving = false
                            if (succeeded) navigator.close() else context.toast("Could not save playlist")
                        }
                    },
                ),
            )
            if (!viewModel.isDraftReady) return@Column

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = LocalBottomContentPadding.current),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SonaPlaylistCover(
                    coverArtUris = coverArtUrisOf(viewModel.cover),
                    seed = playlistId.hashCode(),
                    size = CoverArtDefaults.DetailHeaderSize,
                    cornerRadius = CoverArtDefaults.DetailHeaderCornerRadius,
                    modifier = Modifier.padding(16.dp),
                )
                OutlinedTextField(
                    value = viewModel.name,
                    onValueChange = viewModel::rename,
                    label = { Text("Name") },
                    singleLine = true,
                    enabled = isRenamable,
                    isError = isNameTaken,
                    supportingText = when {
                        !isRenamable -> ({ Text("Favorites keeps its name") })
                        isNameTaken -> ({ Text(PLAYLIST_NAME_TAKEN_MESSAGE) })
                        else -> null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
                DetailSectionHeader(title = "Cover")
                Column(modifier = Modifier.selectableGroup()) {
                    CoverOptionRow(
                        label = "Stacked covers",
                        description = "The covers of its tracks",
                        coverArtUris = stackedCoverArtUris,
                        seed = playlistId.hashCode(),
                        selected = viewModel.cover == PlaylistCover.Stacked,
                        onClick = { viewModel.selectCover(PlaylistCover.Stacked) },
                    )
                    CoverOptionRow(
                        label = "First track",
                        description = playlistTracks.firstOrNull()?.title ?: NO_TRACKS_DESCRIPTION,
                        coverArtUris = coverArtUrisOf(PlaylistCover.FirstTrack),
                        seed = playlistId.hashCode(),
                        selected = viewModel.cover == PlaylistCover.FirstTrack,
                        onClick = { viewModel.selectCover(PlaylistCover.FirstTrack) },
                    )
                    CoverOptionRow(
                        label = "Last track",
                        description = playlistTracks.lastOrNull()?.title ?: NO_TRACKS_DESCRIPTION,
                        coverArtUris = coverArtUrisOf(PlaylistCover.LastTrack),
                        seed = playlistId.hashCode(),
                        selected = viewModel.cover == PlaylistCover.LastTrack,
                        onClick = { viewModel.selectCover(PlaylistCover.LastTrack) },
                    )
                    // Choosing again once chosen, or with nothing chosen yet, opens the choice; otherwise
                    // the track or image chosen before is simply selected again.
                    val isTrackSelected = viewModel.cover is PlaylistCover.OfTrack
                    CoverOptionRow(
                        label = "A track",
                        description = chosenTrack?.title ?: "Choose from the library",
                        coverArtUris = chosenTrack?.coverArtUri?.let(::listOf).orEmpty(),
                        seed = playlistId.hashCode(),
                        selected = isTrackSelected,
                        onClick = {
                            if (isTrackSelected || chosenTrack == null) {
                                isChoosingTrack = true
                            } else {
                                viewModel.selectCover(PlaylistCover.OfTrack(chosenTrack.id))
                            }
                        },
                    )
                    val chosenImageUri = viewModel.chosenImageUri
                    val isImageSelected = viewModel.cover is PlaylistCover.Image
                    CoverOptionRow(
                        label = "An image",
                        description = if (chosenImageUri == null) "Choose from your photos" else "From your photos",
                        coverArtUris = chosenImageUri?.let(::listOf).orEmpty(),
                        seed = playlistId.hashCode(),
                        selected = isImageSelected,
                        onClick = {
                            if (isImageSelected || chosenImageUri == null) {
                                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            } else {
                                viewModel.selectCover(PlaylistCover.Image(chosenImageUri))
                            }
                        },
                    )
                }
            }
        }
    }
}

private const val NO_TRACKS_DESCRIPTION = "No tracks yet"

/** One cover the playlist can take: a preview of it, what it is, and whether it is the one chosen. */
@Composable
private fun CoverOptionRow(
    label: String,
    description: String,
    coverArtUris: List<String>,
    seed: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SonaPlaylistCover(coverArtUris = coverArtUris, seed = seed, size = CoverArtDefaults.ListSize)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        RadioButton(selected = selected, onClick = null)
    }
}

/**
 * Choosing the one track whose cover the playlist takes: its own tracks first, then the whole library,
 * both narrowed by a search. Back closes the search first, then the chooser.
 */
@Composable
private fun CoverTrackChooser(
    playlistTracks: List<Track>,
    libraryTracks: LibraryContent<Track>,
    onTrackChosen: (Track) -> Unit,
    onBack: () -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize()) {
        SonaTopAppBar(
            title = "Choose a track",
            onNavigateBack = onBack,
            actions = listOf(TopBarAction(label = "Search", icon = Icons.Filled.Search) { searchQuery = "" }),
            search = searchQuery?.let { query ->
                TopBarSearch(query = query, onQueryChange = { searchQuery = it }, onClose = { searchQuery = null })
            },
        )
        val visiblePlaylistTracks = playlistTracks.filter { matchesSearch(searchQuery, it.title, it.artist) }
        LibraryListContent(
            content = libraryTracks.filterItems { matchesSearch(searchQuery, it.title, it.artist) },
            // Reached from a playlist, so the library has already loaded and been permitted.
            hasPermission = true,
            isScanning = false,
            emptyTitle = "No tracks found",
            emptyMessage = searchEmptyMessage(searchQuery),
            loadingIcon = SonaIcons.Song,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { visibleLibraryTracks ->
            FastScroller(listState = listState, modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = LocalBottomContentPadding.current),
                ) {
                    if (visiblePlaylistTracks.isNotEmpty()) {
                        item(key = "playlist-header") { DetailSectionHeader(title = "In this playlist") }
                        items(items = visiblePlaylistTracks, key = { "playlist-${it.id}" }) { track ->
                            CoverTrackRow(track = track, onClick = { onTrackChosen(track) })
                        }
                        item(key = "library-header") { DetailSectionHeader(title = "All tracks") }
                    }
                    items(items = visibleLibraryTracks, key = { "library-${it.id}" }) { track ->
                        CoverTrackRow(track = track, onClick = { onTrackChosen(track) })
                    }
            }
            }
        }
    }
}

/** A track to choose: the library's own row, with nothing to select, play or open options for. */
@Composable
private fun CoverTrackRow(track: Track, onClick: () -> Unit) {
    SonaTrackRow(
        track = track,
        isCurrent = { false },
        isPlaying = { false },
        selection = null,
        selectionKey = null,
        onClick = onClick,
        onOpenOptions = null,
    )
}
