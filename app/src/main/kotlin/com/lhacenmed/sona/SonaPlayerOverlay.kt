package com.lhacenmed.sona

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.PlayerOverlay
import com.lhacenmed.sona.feature.equalizer.EqualizerScreen
import com.lhacenmed.sona.feature.library.AlbumDetailScreen
import com.lhacenmed.sona.feature.library.ArtistDetailScreen
import com.lhacenmed.sona.feature.library.options.OptionsSheet
import com.lhacenmed.sona.feature.library.options.OptionsTarget
import com.lhacenmed.sona.feature.playback.PlaybackController
import com.lhacenmed.sona.feature.playback.PlaybackSpace
import com.lhacenmed.sona.feature.player.BottomSheetPlayerHost
import com.lhacenmed.sona.feature.tageditor.lyricseditor.LyricsEditorScreen
import com.lhacenmed.sona.feature.vault.VaultTrackOptionsSheet
import com.lhacenmed.sona.feature.video.openVideoPlayer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * The player laid over every activity - the library's, or over a Private Folder screen the folder's, each with
 * the options its tracks have: the library's album and artist screens, its lyrics editor and its options sheet;
 * the folder's own options.
 *
 * Whichever activity is in front opens the video player as a video starts being watched - see
 * [PlaybackController.videoPlayerOpenings] - so it opens wherever a video was started from, and only once.
 */
class SonaPlayerOverlay @Inject constructor(
    private val playbackController: PlaybackController,
) : PlayerOverlay {

    @Composable
    override fun Content(isPrivate: Boolean, content: @Composable () -> Unit) {
        val navigator = LocalNavigator.current
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(lifecycleOwner) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                playbackController.videoPlayerOpenings.collect { context.openVideoPlayer() }
            }
        }

        if (isPrivate) {
            BottomSheetPlayerHost(
                space = PlaybackSpace.Private,
                onGoToAlbum = null,
                onGoToArtist = null,
                onOpenEqualizer = { navigator.go(EqualizerScreen) },
                onEditLyrics = null,
                trackOptionsSheet = { track, onDismissRequest -> VaultTrackOptionsSheet(track, onDismissRequest) },
                content = content,
            )
        } else {
            BottomSheetPlayerHost(
                space = PlaybackSpace.Library,
                onGoToAlbum = { albumId -> navigator.go(AlbumDetailScreen(albumId)) },
                onGoToArtist = { artistId -> navigator.go(ArtistDetailScreen(artistId)) },
                onOpenEqualizer = { navigator.go(EqualizerScreen) },
                // Over the lyrics sheet, which is still open on the way back, with the lyrics as saved.
                onEditLyrics = { trackId -> navigator.go(LyricsEditorScreen(trackId)) },
                // The player and its queue open the same options sheet every track row in the library
                // opens - the app is where the two features are introduced to each other.
                trackOptionsSheet = { track, onDismissRequest ->
                    OptionsSheet(
                        target = OptionsTarget.ForTrack(track),
                        onDismissRequest = onDismissRequest,
                    )
                },
                content = content,
            )
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerOverlayModule {
    @Binds
    abstract fun bindPlayerOverlay(overlay: SonaPlayerOverlay): PlayerOverlay
}
