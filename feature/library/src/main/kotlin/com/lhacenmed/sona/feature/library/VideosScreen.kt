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
import com.lhacenmed.sona.core.designsystem.component.SelectionState
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.feature.library.options.OptionsSheet
import com.lhacenmed.sona.feature.library.options.OptionsTarget

/**
 * Every folder holding a video, each opening on its videos - played for their sound alone, as any track is.
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
            message = "Sona plays the sound of the videos on your device.",
            modifier = modifier,
            action = EmptyStateAction(label = "Allow", icon = SonaIcons.Video) { permissionLauncher.launch(runtimePermission) },
        )
        return
    }

    val folders by viewModel.videoFolders.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val folderSections by viewModel.videoFolderSections.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    var optionsTarget by remember { mutableStateOf<OptionsTarget.ForFolder?>(null) }

    LibraryList(
        content = folders,
        selection = selection,
        hasPermission = true,
        isScanning = isScanning,
        emptyTitle = "No videos found",
        emptyMessage = searchEmptyMessage(searchQuery),
        key = { it.path },
        loadingIcon = SonaIcons.Video,
        sectionOf = folderSections,
        modifier = modifier,
        listState = listState,
        onRefresh = rememberLibraryRefresh(viewModel),
    ) { folder ->
        FolderRow(
            folder = folder,
            selection = selection,
            isCurrent = { playback.marks(folder) },
            isPlaying = { playback.isPlaying },
            onClick = { navigator.go(FolderDetailScreen(folder.path, isVideo = true)) },
            onOpenOptions = { optionsTarget = OptionsTarget.ForFolder(folder) },
        )
    }

    optionsTarget?.let { target ->
        OptionsSheet(target = target, onDismissRequest = { optionsTarget = null })
    }
}
