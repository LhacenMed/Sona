package com.lhacenmed.sona.feature.library.playlist

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.designsystem.component.SonaTabRow
import com.lhacenmed.sona.core.designsystem.component.rememberSelectionState
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.designsystem.theme.SonaComponentStyle
import com.lhacenmed.sona.core.model.PlaybackParent
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.feature.library.LibraryList
import com.lhacenmed.sona.feature.library.TrackRow
import com.lhacenmed.sona.feature.library.options.OptionsSheet
import com.lhacenmed.sona.feature.library.options.OptionsTarget
import com.lhacenmed.sona.feature.library.searchEmptyMessage
import com.lhacenmed.sona.feature.library.searchedFor
import com.lhacenmed.sona.feature.library.whileNotSearched
import com.lhacenmed.sona.feature.library.selection.SelectionKey
import kotlinx.coroutines.launch

/** What a playlist can take one by one, as the library's own tabs list them. */
private enum class MediaTab(val label: String) {
    TRACKS("Tracks"),
    VIDEOS("Videos"),
}

/**
 * Picking tracks and videos one by one from the whole library, to add to the playlist [playlistId].
 *
 * One selection runs across both tabs, so music and videos are gathered in a single add, and one search
 * narrows both. Select all works on the tab on screen.
 */
data class AddTracksScreen(val playlistId: Long) : Screen {

    @Composable
    override fun Content() {
        val viewModel: PlaylistPickerViewModel = hiltViewModel()
        val tracks by viewModel.tracks.collectAsStateWithLifecycle()
        val videos by viewModel.videos.collectAsStateWithLifecycle()
        val playback by viewModel.playback.collectAsStateWithLifecycle()
        val trackSections by viewModel.trackSections.collectAsStateWithLifecycle()
        val videoSections by viewModel.videoSections.collectAsStateWithLifecycle()
        val resumePositionsMs = viewModel.resumePositionsMs.collectAsStateWithLifecycle()
        val selection = rememberSelectionState()
        var searchQuery by remember { mutableStateOf<String?>(null) }
        val pagerState = rememberPagerState(pageCount = { MediaTab.entries.size })
        val scope = rememberCoroutineScope()
        var optionsTarget by remember { mutableStateOf<OptionsTarget.ForTrack?>(null) }
        val visibleTracks = tracks.searchedFor(searchQuery) { listOf(it.title, it.artist, it.album) }
        val visibleVideos = videos.searchedFor(searchQuery) { listOf(it.title, it.artist, it.album) }

        PlaylistTrackPicker(
            title = "Add tracks",
            playlistId = playlistId,
            selection = selection,
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            visibleKeys = {
                val visible = if (MediaTab.entries[pagerState.currentPage] == MediaTab.VIDEOS) visibleVideos else visibleTracks
                visible.itemsOrEmpty.map { SelectionKey.Track(it.id) }
            },
            tabs = {
                SonaTabRow(
                    tabTitles = MediaTab.entries.map { it.label },
                    selectedPosition = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
                    onTabClick = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
                    modifier = Modifier.padding(horizontal = SonaComponentStyle.ContentHorizontalPadding, vertical = 8.dp),
                )
            },
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                // Both tabs kept composed, so a list keeps its place while the other is on screen.
                beyondViewportPageCount = MediaTab.entries.size - 1,
            ) { page ->
                val isVideos = MediaTab.entries[page] == MediaTab.VIDEOS
                val shown = if (isVideos) visibleVideos else visibleTracks
                LibraryList(
                    content = shown,
                    selection = selection,
                    // Reached from a playlist, so the library has already loaded and been permitted.
                    hasPermission = true,
                    isScanning = false,
                    emptyTitle = if (isVideos) "No videos found" else "No tracks found",
                    emptyMessage = searchEmptyMessage(searchQuery),
                    key = { it.id },
                    loadingIcon = if (isVideos) SonaIcons.Video else SonaIcons.Song,
                    sectionOf = (if (isVideos) videoSections else trackSections).whileNotSearched(searchQuery),
                    isCurrent = { playback.marks(it) },
                    isPlaying = { playback.isPlaying },
                    modifier = Modifier.fillMaxSize(),
                ) { track ->
                    TrackRow(
                        track = track,
                        isCurrent = { playback.marks(track) },
                        isPlaying = { playback.isPlaying },
                        selection = selection,
                        onClick = { viewModel.onTrackClick(track) },
                        resumePositionMs = { resumePositionsMs.value[track.id] ?: 0L },
                        onOpenOptions = {
                            optionsTarget = OptionsTarget.ForTrack(
                                track,
                                queueSource = viewModel.tabTracksOf(track),
                                queueParent = PlaybackParent.Videos.takeIf { isVideos },
                            )
                        },
                    )
                }
            }
        }

        optionsTarget?.let { target ->
            OptionsSheet(target = target, onDismissRequest = { optionsTarget = null })
        }
    }
}
