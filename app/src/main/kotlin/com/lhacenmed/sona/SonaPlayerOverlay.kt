package com.lhacenmed.sona

import androidx.compose.runtime.Composable
import com.lhacenmed.sona.core.navigation.LocalNavigator
import com.lhacenmed.sona.core.navigation.PlayerOverlay
import com.lhacenmed.sona.feature.library.AlbumDetailScreen
import com.lhacenmed.sona.feature.library.ArtistDetailScreen
import com.lhacenmed.sona.feature.library.options.OptionsSheet
import com.lhacenmed.sona.feature.library.options.OptionsTarget
import com.lhacenmed.sona.feature.player.BottomSheetPlayerHost
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/** The player laid over every activity, opening the library's album and artist screens from its links. */
class SonaPlayerOverlay @Inject constructor() : PlayerOverlay {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        BottomSheetPlayerHost(
            onGoToAlbum = { albumId -> navigator.go(AlbumDetailScreen(albumId)) },
            onGoToArtist = { artistId -> navigator.go(ArtistDetailScreen(artistId)) },
            // The player and its queue open the same options sheet every track row in the library
            // opens - the app is where the two features are introduced to each other.
            trackOptionsSheet = { track, onDismissRequest ->
                OptionsSheet(
                    target = OptionsTarget.ForTrack(track),
                    onDismissRequest = onDismissRequest,
                )
            },
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerOverlayModule {
    @Binds
    abstract fun bindPlayerOverlay(overlay: SonaPlayerOverlay): PlayerOverlay
}
