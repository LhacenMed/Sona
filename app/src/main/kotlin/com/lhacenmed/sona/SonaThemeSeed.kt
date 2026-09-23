package com.lhacenmed.sona

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.graphics.Color
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.designsystem.theme.AppThemeSeed
import com.lhacenmed.sona.core.designsystem.theme.DefaultThemeColor
import com.lhacenmed.sona.core.designsystem.theme.extractThemeColor
import com.lhacenmed.sona.core.datastore.ThemeSettings
import com.lhacenmed.sona.feature.playback.PlaybackController
import com.lhacenmed.sona.feature.player.swiper.CoverSlideDurationMillis
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.withContext

/**
 * How long after a track change its colour waits before it is applied: the player's cover slide, and
 * the frame or two the slide itself waits for before it starts.
 */
private const val RecolorHoldMillis = CoverSlideDurationMillis + 100L

/**
 * Extracts a Material colour seed from the playing track's artwork, for the whole process.
 *
 * This is the "app layer" ArchiveTune itself does this work in - `:core:designsystem` only knows how
 * to turn a colour into a scheme, and only this layer can see both the player and the library.
 *
 * It is a singleton on the application scope rather than a ViewModel because the theme is process
 * state, not screen state - the same seed colours the library, the player and every pushed screen.
 * As a ViewModel it existed once per activity, so only the activity that happened to own it was
 * themed, and navigating re-ran the palette extraction from scratch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SonaThemeSeed @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope scope: CoroutineScope,
    themeSettings: ThemeSettings,
    repository: LibraryRepository,
    playbackController: PlaybackController,
) : AppThemeSeed {

    // Narrowed to the playing track's id before touching the library: the playback state also
    // carries a position that ticks twice a second, and this used to linearly scan the whole track
    // table on every one of those ticks looking for a single row.
    private val currentCoverArtUri = combine(
        playbackController.playbackState.map { it.currentTrackId }.distinctUntilChanged(),
        repository.tracksById,
    ) { trackId, tracksById ->
        tracksById[trackId]?.coverArtUri
    }.distinctUntilChanged()

    /**
     * The seed, for the cover that is playing - applied once that cover is in place.
     *
     * A new seed recolours the whole app over a few hundred milliseconds, recomposing everything that
     * reads a theme colour on every one of those frames, the player around the cover included. A track
     * change is also when the new cover slides in, so a recolour started as soon as the colour was
     * ready took the frames the slide runs on. The colour is still worked out at once, off the main
     * thread, while the cover slides; only applying it waits for [RecolorHoldMillis]. A cover that
     * changes again meanwhile - skipping through several - cancels it, so only the last one recolours.
     */
    override val color: StateFlow<Color> = themeSettings.dynamicThemeEnabled.flow
        .flatMapLatest { enabled ->
            if (!enabled) {
                flowOf(DefaultThemeColor)
            } else {
                currentCoverArtUri.withIndex().mapLatest { (index, coverArtUri) ->
                    val changedAt = SystemClock.uptimeMillis()
                    val color = if (coverArtUri.isNullOrEmpty()) {
                        DefaultThemeColor
                    } else {
                        loadBitmap(coverArtUri)?.extractThemeColor() ?: DefaultThemeColor
                    }
                    // The first cover is the one already showing - nothing slides in for it.
                    if (index > 0) delay(RecolorHoldMillis - (SystemClock.uptimeMillis() - changedAt))
                    color
                }
            }
        }
        // Kept warm briefly after the last screen stops reading it, which is exactly what spans an
        // activity handover: the pushed screen starts collecting an already-resolved colour instead
        // of re-extracting the palette and animating up from the default on the way in.
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), DefaultThemeColor)

    private suspend fun loadBitmap(uri: String): Bitmap? = withContext(Dispatchers.IO) {
        val imageLoader = SingletonImageLoader.get(context)
        val request = ImageRequest.Builder(context)
            .data(uri)
            .allowHardware(false) // Palette needs to read raw pixels off a software bitmap.
            .build()
        (imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ThemeSeedModule {
    @Binds
    abstract fun bindAppThemeSeed(seed: SonaThemeSeed): AppThemeSeed
}
