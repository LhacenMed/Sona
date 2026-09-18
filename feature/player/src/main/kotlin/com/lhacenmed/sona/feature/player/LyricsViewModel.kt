package com.lhacenmed.sona.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.data.lyrics.LyricsRepository
import com.lhacenmed.sona.core.database.entity.LyricsEntity
import com.lhacenmed.sona.core.datastore.LyricsBackgroundStyle
import com.lhacenmed.sona.core.datastore.LyricsSettings
import com.lhacenmed.sona.core.datastore.Setting
import com.lhacenmed.sona.core.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The lyrics sheet's lyrics and the stored settings it is drawn with. */
@HiltViewModel
class LyricsViewModel @Inject constructor(
    private val lyricsRepository: LyricsRepository,
    private val lyricsSettings: LyricsSettings,
) : ViewModel() {

    val lyricsClick: StateFlow<Boolean> = lyricsSettings.lyricsClick.state()
    val lyricsScroll: StateFlow<Boolean> = lyricsSettings.lyricsScroll.state()
    val lyricsTextSize: StateFlow<Float> = lyricsSettings.lyricsTextSize.state()
    val lyricsLineSpacing: StateFlow<Float> = lyricsSettings.lyricsLineSpacing.state()
    val lyricsLineBlur: StateFlow<Boolean> = lyricsSettings.lyricsLineBlur.state()
    val lyricsBackgroundStyle: StateFlow<LyricsBackgroundStyle> = lyricsSettings.lyricsBackgroundStyle.state()
    val showLyricsPlayerControls: StateFlow<Boolean> = lyricsSettings.showLyricsPlayerControls.state()
    val bounceFactor: StateFlow<Float> = lyricsSettings.bounceFactor.state()
    val glowFactor: StateFlow<Float> = lyricsSettings.glowFactor.state()
    val fillTransitionWidth: StateFlow<Float> = lyricsSettings.fillTransitionWidth.state()
    val lrcBounceEnabled: StateFlow<Boolean> = lyricsSettings.lrcBounceEnabled.state()
    val romanizeJapanese: StateFlow<Boolean> = lyricsSettings.romanizeJapanese.state()
    val romanizeKorean: StateFlow<Boolean> = lyricsSettings.romanizeKorean.state()
    val romanizeChinese: StateFlow<Boolean> = lyricsSettings.romanizeChinese.state()
    val romanizeHindi: StateFlow<Boolean> = lyricsSettings.romanizeHindi.state()
    val romanizeOtherLanguages: StateFlow<Boolean> = lyricsSettings.romanizeOtherLanguages.state()

    fun lyrics(trackId: Long): Flow<LyricsEntity?> = lyricsRepository.lyrics(trackId)

    fun loadLyrics(track: Track) {
        viewModelScope.launch { lyricsRepository.loadLyrics(track) }
    }

    fun updateLyrics(trackId: Long, lyrics: String) {
        viewModelScope.launch { lyricsRepository.updateLyrics(trackId, lyrics) }
    }

    fun setShowLyricsPlayerControls(enabled: Boolean) {
        viewModelScope.launch { lyricsSettings.setShowLyricsPlayerControls(enabled) }
    }

    private fun <T> Setting<T>.state(): StateFlow<T> =
        flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), value)
}
