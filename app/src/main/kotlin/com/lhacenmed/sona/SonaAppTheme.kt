package com.lhacenmed.sona

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.data.LibraryRepository
import com.lhacenmed.sona.core.designsystem.theme.AppTheme
import com.lhacenmed.sona.core.designsystem.theme.CustomFontLoader
import com.lhacenmed.sona.core.designsystem.theme.DefaultThemeColor
import com.lhacenmed.sona.core.designsystem.theme.ThemeColors
import com.lhacenmed.sona.core.designsystem.theme.ThemeConfig
import com.lhacenmed.sona.core.designsystem.theme.extractThemeColor
import com.lhacenmed.sona.core.designsystem.theme.palette.ThemePalettes
import com.lhacenmed.sona.core.datastore.ThemeChoices
import com.lhacenmed.sona.core.datastore.ThemeSettings
import com.lhacenmed.sona.core.model.AppFont
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.withContext

/**
 * How long after a track change its colour waits before it is applied: the player's cover slide, and
 * the frame or two the slide itself waits for before it starts.
 */
private const val RecolorHoldMillis = CoverSlideDurationMillis + 100L

/** Whether this device has wallpaper colours to give: Material You arrived with Android 12. */
private val hasWallpaperColors = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * The theme the whole process is drawn with: the stored theme choices, and the playing cover's colour
 * while they ask for it - the "app layer" ArchiveTune itself does this work in, since only this layer can
 * see the settings, the player and the library together.
 *
 * A singleton on the application scope rather than a ViewModel because the theme is process state, not
 * screen state - the same config colours the library, the player and every pushed screen. As a ViewModel
 * it existed once per activity, so only the activity that happened to own it was themed, and navigating
 * re-ran the palette extraction from scratch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SonaAppTheme @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope scope: CoroutineScope,
    themeSettings: ThemeSettings,
    repository: LibraryRepository,
    playbackController: PlaybackController,
) : AppTheme {

    // Narrowed to the playing track's id before touching the library: the playback state also
    // carries a position that ticks twice a second, and this used to linearly scan the whole track
    // table on every one of those ticks looking for a single row.
    private val currentCoverArtUri = combine(
        playbackController.playbackState.map { it.libraryTrackId }.distinctUntilChanged(),
        repository.tracksById,
    ) { trackId, tracksById ->
        tracksById[trackId]?.coverArtUri
    }.distinctUntilChanged()

    /**
     * The playing cover's colour while the theme takes one, and null otherwise - or while nothing with a
     * cover is playing. Nothing is loaded or extracted while the theme takes none.
     *
     * A new colour recolours the whole app over a few hundred milliseconds, recomposing everything that
     * reads a theme colour on every one of those frames, the player around the cover included. A track
     * change is also when the new cover slides in, so a recolour started as soon as the colour was ready
     * took the frames the slide runs on. The colour is still worked out at once, off the main thread,
     * while the cover slides; only applying it waits for [RecolorHoldMillis]. A cover that changes again
     * meanwhile - skipping through several - cancels it, so only the last one recolours.
     */
    private val coverColor: Flow<Color?> = themeSettings.choices.flow
        .map { it.usesCoverColors }
        .distinctUntilChanged()
        .flatMapLatest { usesCoverColors ->
            if (!usesCoverColors) {
                flowOf(null)
            } else {
                currentCoverArtUri.withIndex().mapLatest { (index, coverArtUri) ->
                    val changedAt = SystemClock.uptimeMillis()
                    val color = coverArtUri?.takeIf { it.isNotEmpty() }?.let { loadBitmap(it)?.extractThemeColor() }
                    // The first cover is the one already showing - nothing slides in for it.
                    if (index > 0) delay(RecolorHoldMillis - (SystemClock.uptimeMillis() - changedAt))
                    color
                }
            }
        }

    /**
     * The picked font while the theme is set in it, and null otherwise - or when it cannot be read. Read
     * from the moment the theme exists, alongside the library, and kept for the process, so no screen
     * ever reads it again or waits for it.
     */
    private val customFontFamily: Flow<FontFamily?> = themeSettings.choices.flow
        .map { choices -> choices.customFont?.uri?.takeIf { choices.font == AppFont.CUSTOM } }
        .distinctUntilChanged()
        .mapLatest { uri -> uri?.let { CustomFontLoader.loadFontFamily(context, it) } }
        .shareIn(scope, SharingStarted.Eagerly, replay = 1)

    /** Whether [config] has been fully worked out at least once - what [awaitReady] waits for. */
    private val isResolved = MutableStateFlow(false)

    // Kept warm briefly after the last screen stops reading it, which is exactly what spans an activity
    // handover: the pushed screen starts from an already-resolved config instead of re-extracting the
    // cover's colour and animating up from the default on the way in.
    override val config: StateFlow<ThemeConfig> = combine(themeSettings.choices.flow, coverColor, customFontFamily, ::configOf)
        // Marked resolved only once the config is in place, so whatever waits for it finds it there.
        .transform { config ->
            emit(config)
            isResolved.value = true
        }
        .stateIn(
            scope,
            SharingStarted.WhileSubscribed(5000),
            configOf(themeSettings.choices.value, coverColor = null, customFontFamily = null),
        )

    override suspend fun awaitReady() {
        isResolved.first { it }
    }

    private suspend fun loadBitmap(uri: String): Bitmap? = withContext(Dispatchers.IO) {
        val imageLoader = SingletonImageLoader.get(context)
        val request = ImageRequest.Builder(context)
            .data(uri)
            .allowHardware(false) // Palette needs to read raw pixels off a software bitmap.
            .build()
        (imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap()
    }
}

/** Dynamic colours are the wallpaper's and the cover's together; without them, each is the user's to choose. */
private val ThemeChoices.usesCoverColors: Boolean get() = dynamicColors || coverColors

private fun configOf(choices: ThemeChoices, coverColor: Color?, customFontFamily: FontFamily?) = ThemeConfig(
    mode = choices.mode,
    pureBlack = choices.pureBlack,
    colors = colorsOf(choices, coverColor),
    font = choices.font,
    customFontFamily = customFontFamily,
)

/**
 * Where the colours come from: the playing cover's, while the theme takes them and one is playing; the
 * wallpaper's where it takes those and the device has them; and the chosen palette otherwise. Dynamic
 * colours with nothing playing on a device without wallpaper colours keep the one seed they always had.
 */
private fun colorsOf(choices: ThemeChoices, coverColor: Color?): ThemeColors = when {
    coverColor != null -> ThemeColors.Seed(coverColor)
    (choices.dynamicColors || choices.wallpaperColors) && hasWallpaperColors -> ThemeColors.Wallpaper
    choices.dynamicColors -> ThemeColors.Seed(DefaultThemeColor)
    else -> ThemeColors.Palette(ThemePalettes.seedsOf(choices.colorPalette))
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppThemeModule {
    @Binds
    abstract fun bindAppTheme(theme: SonaAppTheme): AppTheme
}
