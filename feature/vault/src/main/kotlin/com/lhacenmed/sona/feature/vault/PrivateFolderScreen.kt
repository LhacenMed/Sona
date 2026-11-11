package com.lhacenmed.sona.feature.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.designsystem.component.LocalBottomContentPadding
import com.lhacenmed.sona.core.designsystem.component.SonaCoverArt
import com.lhacenmed.sona.core.designsystem.component.SonaListRow
import com.lhacenmed.sona.core.designsystem.component.SonaTabRow
import com.lhacenmed.sona.core.designsystem.component.SonaTopAppBar
import com.lhacenmed.sona.core.designsystem.component.SonaVideoRow
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.fastscroll.FastScroller
import com.lhacenmed.sona.core.designsystem.component.sheet.SonaSortSheet
import com.lhacenmed.sona.core.designsystem.component.sheet.sortAction
import com.lhacenmed.sona.core.designsystem.component.topBarBackground
import com.lhacenmed.sona.core.designsystem.theme.SonaComponentStyle
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.core.model.sort.SortCriterion
import com.lhacenmed.sona.core.model.sort.SortOrder
import com.lhacenmed.sona.core.navigation.Screen
import com.lhacenmed.sona.core.vault.VaultRepository
import com.lhacenmed.sona.core.vault.VaultState
import com.lhacenmed.sona.core.vault.data.VaultItem
import com.lhacenmed.sona.feature.playback.PlaybackController
import com.lhacenmed.sona.feature.playback.PlaybackSpace
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons

/**
 * The Private Folder, open: its Videos and Tracks. Settings opens it on the screen it stands at - see
 * [rememberPrivateFolderOpener] - so setting it up and its PIN are screens of their own before this one.
 * Locking it - leaving the app, or leaving this screen - ends whatever of it was playing, and shows nothing
 * of what it held: locked while away, it hands its place back to the PIN screen as the app returns.
 *
 * Its media plays in the app's own players - the video player, and the player over this screen, which is
 * the folder's alone: see [Screen.isPrivate].
 */
data object PrivateFolderScreen : Screen {
    override val isPrivate: Boolean get() = true

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val viewModel: PrivateFolderViewModel = hiltViewModel()
        val vault by viewModel.state.collectAsStateWithLifecycle()
        val entryScreen = vault.entryScreen()
        val isOpen = entryScreen == PrivateFolderScreen

        LaunchedEffect(entryScreen) {
            if (entryScreen != null && !isOpen) {
                navigator.go(entryScreen)
                navigator.close()
            }
        }

        if (isOpen) {
            PrivateFolderContent(viewModel)
        } else {
            SonaTopAppBar(title = "Private Folder", onNavigateBack = navigator::back)
        }
    }
}

private val tabTitles = listOf("Videos", "Tracks")

@Composable
private fun PrivateFolderContent(viewModel: PrivateFolderViewModel) {
    val navigator = LocalNavigator.current
    val pagerState = rememberPagerState(pageCount = { tabTitles.size })
    val scope = rememberCoroutineScope()
    val playingItemId by viewModel.playingItemId.collectAsStateWithLifecycle()
    val resumePositionsMs = viewModel.resumePositionsMs.collectAsStateWithLifecycle()
    var optionsItem by remember { mutableStateOf<VaultItem?>(null) }
    // The tab whose list is being sorted - its videos, or its tracks - while its sheet is open.
    var sortingVideos by remember { mutableStateOf<Boolean?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // The bar and the tabs are one section, lifting as one once the list leaves its top - as the library's do.
        Column(modifier = Modifier.topBarBackground()) {
            SonaTopAppBar(
                title = "Private Folder",
                drawsBackground = false,
                onNavigateBack = navigator::back,
                actions = listOf(
                    sortAction { sortingVideos = pagerState.settledPage == 0 },
                    TopBarAction(label = "Settings", icon = SonaIcons.Settings) { navigator.go(PrivateFolderSettingsScreen) },
                ),
            )
            SonaTabRow(
                tabTitles = tabTitles,
                selectedPosition = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
                onTabClick = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
                modifier = Modifier.padding(horizontal = SonaComponentStyle.ContentHorizontalPadding, vertical = 8.dp),
            )
        }
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            val isVideo = page == 0
            val items by (if (isVideo) viewModel.videos else viewModel.tracks).collectAsStateWithLifecycle()
            val sectionOf by (if (isVideo) viewModel.videoSections else viewModel.trackSections).collectAsStateWithLifecycle()
            VaultItemList(
                items = items,
                sectionOf = sectionOf,
                isVideo = isVideo,
                playingItemId = playingItemId,
                resumePositionMsOf = { item -> resumePositionsMs.value[item.id] ?: 0L },
                trackOf = viewModel::trackOf,
                onPlay = { item -> viewModel.play(item, items.orEmpty()) },
                onOpenOptions = { optionsItem = it },
            )
        }
    }

    optionsItem?.let { item -> VaultItemOptions(item = item, onDismissRequest = { optionsItem = null }) }

    sortingVideos?.let { isVideo ->
        val order by viewModel.sortOrder(isVideo).collectAsStateWithLifecycle()
        order?.let { initialOrder ->
            SonaSortSheet(
                criteria = viewModel.sortCriteria,
                initialOrder = initialOrder,
                onApply = { chosen, _ -> viewModel.setSortOrder(isVideo, chosen) },
                onDismiss = { sortingVideos = null },
            )
        }
    }
}

/**
 * One tab's items - nothing while still being read, so the list never flashes empty first - with the section each sits
 * in under the tab's sort, [sectionOf], named in the fast scroller's popup.
 */
@Composable
private fun VaultItemList(
    items: List<VaultItem>?,
    sectionOf: (VaultItem) -> String?,
    isVideo: Boolean,
    playingItemId: Long?,
    resumePositionMsOf: (VaultItem) -> Long,
    trackOf: (VaultItem) -> Track,
    onPlay: (VaultItem) -> Unit,
    onOpenOptions: (VaultItem) -> Unit,
) {
    if (items == null) return
    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (isVideo) "No private videos" else "No private tracks", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Choose \"Add to Private Folder\" from a ${if (isVideo) "video" else "track"}'s options in your library.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }
    val listState = rememberLazyListState()
    FastScroller(
        listState = listState,
        modifier = Modifier.fillMaxSize(),
        sectionAt = { index -> items.getOrNull(index)?.let(sectionOf) },
    ) { overscrollEffect ->
        LazyColumn(
            state = listState,
            overscrollEffect = overscrollEffect,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = LocalBottomContentPadding.current),
        ) {
            items(items, key = VaultItem::id) { item ->
                val isCurrent = item.id == playingItemId
                val track = remember(item) { trackOf(item) }
                if (item.isVideo) {
                    SonaVideoRow(
                        video = track,
                        resumePositionMs = { resumePositionMsOf(item) },
                        isCurrent = { isCurrent },
                        isPlaying = { false },
                        selection = null,
                        selectionKey = null,
                        onClick = { onPlay(item) },
                        onOpenOptions = { onOpenOptions(item) },
                    )
                    return@items
                }
                SonaListRow(
                    title = item.title,
                    subtitle = item.subtitle,
                    selection = null,
                    selectionKey = null,
                    onClick = { onPlay(item) },
                    onOpenOptions = { onOpenOptions(item) },
                    isCurrent = isCurrent,
                ) {
                    SonaCoverArt(coverArtUri = track.coverArtUri, contentDescription = null, isCurrent = isCurrent)
                }
            }
        }
    }
}

@HiltViewModel
internal class PrivateFolderViewModel @Inject constructor(
    private val vault: VaultRepository,
    private val playbackController: PlaybackController,
) : ViewModel() {

    val state: StateFlow<VaultState> = vault.state

    val videos: StateFlow<List<VaultItem>?> =
        vault.items(isVideo = true).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val tracks: StateFlow<List<VaultItem>?> =
        vault.items(isVideo = false).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** What either tab can be sorted by. */
    val sortCriteria: List<SortCriterion> get() = vault.sortCriteria

    /** The section each video - or, for [trackSections], each track - sits in under its tab's sort. */
    val videoSections: StateFlow<(VaultItem) -> String?> =
        vault.sections(isVideo = true).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000)) { null }

    val trackSections: StateFlow<(VaultItem) -> String?> =
        vault.sections(isVideo = false).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000)) { null }

    private val videoSortOrder: StateFlow<SortOrder?> =
        vault.sortOrder(isVideo = true).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val trackSortOrder: StateFlow<SortOrder?> =
        vault.sortOrder(isVideo = false).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** How the videos' tab - or, unless [isVideo], the tracks' - is sorted: null until read. */
    fun sortOrder(isVideo: Boolean): StateFlow<SortOrder?> = if (isVideo) videoSortOrder else trackSortOrder

    fun setSortOrder(isVideo: Boolean, order: SortOrder) {
        viewModelScope.launch { vault.setSortOrder(isVideo, order) }
    }

    /** Where each video was left - see [VaultRepository.resumePositionsMs]. */
    val resumePositionsMs: StateFlow<Map<Long, Long>> =
        vault.resumePositionsMs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** The item playing, which its row marks - null while the library plays. */
    val playingItemId: StateFlow<Long?> = playbackController.playbackState
        .map { playback -> playback.currentTrackId.takeIf { playback.space == PlaybackSpace.Private } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Locked - the app left - nothing of the folder plays on.
        viewModelScope.launch {
            vault.state.collect { state ->
                if (state !is VaultState.Created || !state.isUnlocked) playbackController.endPrivatePlayback()
            }
        }
    }

    fun trackOf(item: VaultItem): Track = vault.trackOf(item)

    /**
     * Plays [items] - the tab's - from [item], as a library list plays: a video watched in the video player, a
     * track in the player over the folder. The one playing pauses or resumes instead, and a video playing as
     * audio is watched again.
     */
    fun play(item: VaultItem, items: List<VaultItem>) {
        if (item.id == playingItemId.value) {
            if (item.isVideo) playbackController.watchCurrent() else playbackController.togglePlayPause()
            return
        }
        val index = items.indexOfFirst { it.id == item.id }
        if (index >= 0) playbackController.playPrivateTracks(items.map(vault::trackOf), index)
    }

    /** Leaving the folder - not turning the screen, which keeps this - locks it and ends its playback. */
    override fun onCleared() {
        vault.lock()
        playbackController.endPrivatePlayback()
    }
}
