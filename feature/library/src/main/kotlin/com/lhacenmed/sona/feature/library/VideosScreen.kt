package com.lhacenmed.sona.feature.library

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lhacenmed.sona.core.common.permission.AppPermission
import com.lhacenmed.sona.core.data.itemsOrEmpty
import com.lhacenmed.sona.core.designsystem.component.SelectionState
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.model.PlaybackParent
import com.lhacenmed.sona.feature.library.options.OptionsSheet
import com.lhacenmed.sona.feature.library.options.OptionsTarget

/**
 * Every video, listed as the tracks tab's tracks are, and opened in the video player when tapped. The folders
 * holding them are the Folders tab's.
 *
 * Reading videos takes a permission of its own, asked for here rather than as the app opens: until it is
 * given, the tab offers it in the place its list will fill. It is checked again each time the app comes
 * back, so one granted in Android's settings shows the videos on return.
 */
@Composable
fun VideosScreen(
    viewModel: LibraryViewModel,
    selection: SelectionState,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val permission = AppPermission.VIDEO_LIBRARY
    var canReadVideos by remember { mutableStateOf(permission.isGranted(context)) }
    val onAccessChecked = { granted: Boolean ->
        if (granted && !canReadVideos) viewModel.onVideoAccessGranted()
        canReadVideos = granted
    }
    LifecycleResumeEffect(Unit) {
        onAccessChecked(permission.isGranted(context))
        onPauseOrDispose {}
    }
    val runtimePermission = checkNotNull(permission.runtimePermission)
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onAccessChecked(granted)
        // Declined with no rationale left to give: Android will not ask again, so its page is where it is granted.
        if (!granted && activity?.shouldShowRequestPermissionRationale(runtimePermission) == false) {
            context.startActivity(permission.settingsIntent(context))
        }
    }

    if (!canReadVideos) {
        EmptyLibraryState(
            title = "Allow access to videos",
            message = "Sona plays the videos on your device.",
            modifier = modifier,
            action = EmptyStateAction(label = "Allow", icon = SonaIcons.Video) { permissionLauncher.launch(runtimePermission) },
        )
        return
    }

    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val videoSections by viewModel.videoSections.collectAsStateWithLifecycle()
    var optionsTarget by remember { mutableStateOf<OptionsTarget.ForTrack?>(null) }

    LibraryList(
        content = videos,
        selection = selection,
        hasPermission = true,
        isScanning = isScanning,
        emptyTitle = "No videos found",
        emptyMessage = searchEmptyMessage(searchQuery),
        key = { it.id },
        loadingIcon = SonaIcons.Video,
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
            onOpenOptions = {
                optionsTarget = OptionsTarget.ForTrack(
                    video,
                    queueSource = videos.itemsOrEmpty,
                    queueParent = PlaybackParent.Videos,
                )
            },
        )
    }

    optionsTarget?.let { target ->
        OptionsSheet(target = target, onDismissRequest = { optionsTarget = null })
    }
}
