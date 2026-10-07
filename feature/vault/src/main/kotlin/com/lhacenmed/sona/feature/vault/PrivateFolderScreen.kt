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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.lhacenmed.sona.core.designsystem.component.TopBarAction
import com.lhacenmed.sona.core.designsystem.component.screen.screenList
import com.lhacenmed.sona.core.designsystem.theme.SonaComponentStyle
import com.lhacenmed.sona.core.navigation.LocalNavigator
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

/**
 * The Private Folder - reached by pulling past the end of Settings. One screen for all it can be: set up the
 * first time, asking for the PIN while locked, and its Videos and Tracks once open. Locking it - leaving the
 * app, or leaving this screen - brings the PIN back in place, and ends whatever of it was playing, with
 * nothing of what it held left showing or heard.
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
        val current = vault
        val isOpen = current is VaultState.Created && current.isUnlocked

        Column(modifier = Modifier.fillMaxSize()) {
            SonaTopAppBar(
                title = "Private Folder",
                onNavigateBack = navigator::back,
                actions = if (isOpen) {
                    listOf(TopBarAction(label = "Change PIN", icon = Icons.Filled.Password, onClick = { navigator.go(VaultChangePinScreen) }))
                } else {
                    emptyList()
                },
            )
            when (current) {
                VaultState.Loading -> Unit
                VaultState.NotCreated -> VaultSetupFlow(opensFolder = true)
                is VaultState.Created -> if (current.isUnlocked) PrivateFolderContent(viewModel) else VaultUnlockFlow(current)
            }
        }
    }
}

@Composable
private fun PrivateFolderContent(viewModel: PrivateFolderViewModel) {
    val tabTitles = listOf("Videos", "Tracks")
    val pagerState = rememberPagerState(pageCount = { tabTitles.size })
    val scope = rememberCoroutineScope()
    val playingItemId by viewModel.playingItemId.collectAsStateWithLifecycle()
    var optionsItem by remember { mutableStateOf<VaultItem?>(null) }

    SonaTabRow(
        tabTitles = tabTitles,
        selectedPosition = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
        onTabClick = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
        modifier = Modifier.padding(horizontal = SonaComponentStyle.ContentHorizontalPadding, vertical = 8.dp),
    )
    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
        val isVideo = page == 0
        val items by (if (isVideo) viewModel.videos else viewModel.tracks).collectAsStateWithLifecycle()
        VaultItemList(
            items = items,
            isVideo = isVideo,
            playingItemId = playingItemId,
            coverOf = viewModel::coverOf,
            onPlay = { item -> viewModel.play(item, items.orEmpty()) },
            onOpenOptions = { optionsItem = it },
        )
    }

    optionsItem?.let { item -> VaultItemOptions(item = item, onDismissRequest = { optionsItem = null }) }
}

/** One tab's items - nothing while still being read, so the list never flashes empty first. */
@Composable
private fun VaultItemList(
    items: List<VaultItem>?,
    isVideo: Boolean,
    playingItemId: Long?,
    coverOf: (VaultItem) -> String?,
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
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().screenList(listState),
        contentPadding = PaddingValues(bottom = LocalBottomContentPadding.current),
    ) {
        items(items, key = VaultItem::id) { item ->
            val isCurrent = item.id == playingItemId
            SonaListRow(
                title = item.title,
                subtitle = item.subtitle,
                selection = null,
                selectionKey = null,
                onClick = { onPlay(item) },
                onOpenOptions = { onOpenOptions(item) },
                isCurrent = isCurrent,
            ) {
                SonaCoverArt(coverArtUri = coverOf(item), contentDescription = null, isCurrent = isCurrent)
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

    fun coverOf(item: VaultItem): String? = vault.trackOf(item).coverArtUri

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
