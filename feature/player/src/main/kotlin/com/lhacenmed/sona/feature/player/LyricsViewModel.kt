package com.lhacenmed.sona.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.data.lyrics.DictionaryState
import com.lhacenmed.sona.core.data.lyrics.JapaneseDictionary
import com.lhacenmed.sona.core.datastore.LyricsBackgroundStyle
import com.lhacenmed.sona.core.datastore.LyricsSettings
import com.lhacenmed.sona.core.datastore.Setting
import com.lhacenmed.sona.core.model.Track
import com.lhacenmed.sona.feature.tageditor.TrackTagsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The lyrics sheet's lyrics and the stored settings it is drawn with. */
@HiltViewModel
class LyricsViewModel @Inject constructor(
    private val trackTagsRepository: TrackTagsRepository,
    private val lyricsSettings: LyricsSettings,
    japaneseDictionary: JapaneseDictionary,
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
    /** Japanese is romanized exactly while its dictionary is downloaded. */
    val romanizeJapanese: StateFlow<Boolean> = japaneseDictionary.state
        .map { it == DictionaryState.Installed }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), japaneseDictionary.state.value == DictionaryState.Installed)
    val romanizeKorean: StateFlow<Boolean> = lyricsSettings.romanizeKorean.state()
    val romanizeChinese: StateFlow<Boolean> = lyricsSettings.romanizeChinese.state()
    val romanizeHindi: StateFlow<Boolean> = lyricsSettings.romanizeHindi.state()
    val romanizeOtherLanguages: StateFlow<Boolean> = lyricsSettings.romanizeOtherLanguages.state()

    /** The lyrics [track]'s file holds, read again whenever Sona writes them - blank where it holds none. */
    fun lyrics(track: Track): Flow<String> = trackTagsRepository.lyrics(track)

    fun setShowLyricsPlayerControls(enabled: Boolean) {
        viewModelScope.launch { lyricsSettings.setShowLyricsPlayerControls(enabled) }
    }

    private fun <T> Setting<T>.state(): StateFlow<T> =
        flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), value)
}
