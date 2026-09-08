package com.lhacenmed.sona

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.lhacenmed.sona.core.database.dao.TrackDao
import com.lhacenmed.sona.core.designsystem.theme.DefaultThemeColor
import com.lhacenmed.sona.core.designsystem.theme.extractThemeColor
import com.lhacenmed.sona.core.datastore.ThemeSettings
import com.lhacenmed.sona.feature.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * Extracts a Material color seed from the currently playing track's artwork (see
 * `:core:designsystem`'s dynamic theming pipeline) and republishes it for [MainActivity] to feed
 * into [com.lhacenmed.sona.core.designsystem.theme.SonaTheme]. This is the "app layer" ArchiveTune
 * itself does this work in - `:core:designsystem` only knows how to turn a color into a scheme.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppThemeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    themeSettings: ThemeSettings,
    trackDao: TrackDao,
    playbackController: PlaybackController,
) : ViewModel() {

    private val currentCoverArtUri = combine(
        playbackController.playbackState,
        trackDao.observeAll(),
    ) { playback, entities ->
        entities.find { it.id == playback.currentTrackId }?.coverArtUri
    }.distinctUntilChanged()

    val themeColor: StateFlow<androidx.compose.ui.graphics.Color> = combine(
        themeSettings.dynamicThemeEnabled,
        currentCoverArtUri,
    ) { enabled, coverArtUri -> enabled to coverArtUri }
        .mapLatest { (enabled, coverArtUri) ->
            if (!enabled || coverArtUri.isNullOrEmpty()) {
                DefaultThemeColor
            } else {
                loadBitmap(coverArtUri)?.extractThemeColor() ?: DefaultThemeColor
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DefaultThemeColor)

    private suspend fun loadBitmap(uri: String): Bitmap? = withContext(Dispatchers.IO) {
        val imageLoader = SingletonImageLoader.get(context)
        val request = ImageRequest.Builder(context)
            .data(uri)
            .allowHardware(false) // Palette needs to read raw pixels off a software bitmap.
            .build()
        (imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap()
    }
}
