package com.lhacenmed.sona.feature.library

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.designsystem.component.SelectionState
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.model.PlaybackParent
import com.lhacenmed.sona.feature.library.R
import com.lhacenmed.sona.feature.library.options.OptionsSheet
import com.lhacenmed.sona.feature.library.options.OptionsTarget

/**
 * Every video, listed as the tracks tab's tracks are, and opened in the video player when tapped. The folders
 * holding them are the Folders tab's.
 */
@Composable
fun VideosScreen(
    viewModel: LibraryViewModel,
    selection: SelectionState,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val videoSections by viewModel.videoSections.collectAsStateWithLifecycle()
    val resumePositionsMs = viewModel.resumePositionsMs.collectAsStateWithLifecycle()
    var optionsTarget by remember { mutableStateOf<OptionsTarget.ForTrack?>(null) }

    LibraryList(
        content = videos,
        selection = selection,
        hasPermission = hasPermission,
        isScanning = isScanning,
        emptyTitle = stringResource(R.string.library_no_videos_found),
        emptyMessage = searchEmptyMessage(searchQuery),
        key = { it.id },
        sectionOf = videoSections,
        isCurrent = { playback.marks(it) },
        isPlaying = { playback.isPlaying },
        modifier = modifier,
        listState = listState,
        onRefresh = rememberLibraryRefresh(viewModel),
    ) { video ->
        TrackRow(
            track = video,
            // Lambdas, so changing videos recomposes two rows instead of the whole list.
            isCurrent = { playback.marks(video) },
            isPlaying = { playback.isPlaying },
            selection = selection,
            onClick = { viewModel.onVideoClick(video) },
            resumePositionMs = { resumePositionsMs.value[video.id] ?: 0L },
            onOpenOptions = {
                optionsTarget = OptionsTarget.ForTrack(
                    video,
                    queueSource = viewModel.tabTracksOf(video),
                    queueParent = PlaybackParent.Videos,
                )
            },
        )
    }

    optionsTarget?.let { target ->
        OptionsSheet(target = target, onDismissRequest = { optionsTarget = null })
    }
}
