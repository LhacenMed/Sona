package com.lhacenmed.sona.feature.settings.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.data.lyrics.DictionaryState
import com.lhacenmed.sona.core.data.lyrics.JapaneseDictionary
import com.lhacenmed.sona.core.datastore.LyricsSettings
import com.lhacenmed.sona.core.datastore.Setting
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The stored lyrics settings the [LyricsScreen] and [LyricsAnimationScreen] rows are connected to. */
@HiltViewModel
class LyricsSettingsViewModel @Inject constructor(
    private val lyricsSettings: LyricsSettings,
    private val japaneseDictionary: JapaneseDictionary,
) : ViewModel() {

    val lyricsClick: StateFlow<Boolean> = lyricsSettings.lyricsClick.state()
    val lyricsScroll: StateFlow<Boolean> = lyricsSettings.lyricsScroll.state()
    val lyricsLineBlur: StateFlow<Boolean> = lyricsSettings.lyricsLineBlur.state()
    val lyricsTextSize: StateFlow<Float> = lyricsSettings.lyricsTextSize.state()
    val lyricsLineSpacing: StateFlow<Float> = lyricsSettings.lyricsLineSpacing.state()
    val showLyricsPlayerControls: StateFlow<Boolean> = lyricsSettings.showLyricsPlayerControls.state()
    val lrcBounceEnabled: StateFlow<Boolean> = lyricsSettings.lrcBounceEnabled.state()
    val bounceFactor: StateFlow<Float> = lyricsSettings.bounceFactor.state()
    val glowFactor: StateFlow<Float> = lyricsSettings.glowFactor.state()
    val fillTransitionWidth: StateFlow<Float> = lyricsSettings.fillTransitionWidth.state()
    /** The Japanese dictionary - romanizing Japanese is on exactly while it is downloaded. */
    val japaneseDictionaryState: StateFlow<DictionaryState> = japaneseDictionary.state
    val romanizeKorean: StateFlow<Boolean> = lyricsSettings.romanizeKorean.state()
    val romanizeChinese: StateFlow<Boolean> = lyricsSettings.romanizeChinese.state()
    val romanizeHindi: StateFlow<Boolean> = lyricsSettings.romanizeHindi.state()
    val romanizeOtherLanguages: StateFlow<Boolean> = lyricsSettings.romanizeOtherLanguages.state()
    val findMissingLyrics: StateFlow<Boolean> = lyricsSettings.findMissingLyrics.state()

    fun setLyricsClick(enabled: Boolean) = write { setLyricsClick(enabled) }
    fun setLyricsScroll(enabled: Boolean) = write { setLyricsScroll(enabled) }
    fun setLyricsLineBlur(enabled: Boolean) = write { setLyricsLineBlur(enabled) }
    fun setLyricsTextSize(size: Float) = write { setLyricsTextSize(size) }
    fun setLyricsLineSpacing(spacing: Float) = write { setLyricsLineSpacing(spacing) }
    fun setShowLyricsPlayerControls(enabled: Boolean) = write { setShowLyricsPlayerControls(enabled) }
    fun setLrcBounceEnabled(enabled: Boolean) = write { setLrcBounceEnabled(enabled) }
    fun setBounceFactor(factor: Float) = write { setBounceFactor(factor) }
    fun setGlowFactor(factor: Float) = write { setGlowFactor(factor) }
    fun setFillTransitionWidth(width: Float) = write { setFillTransitionWidth(width) }
    fun setRomanizeKorean(enabled: Boolean) = write { setRomanizeKorean(enabled) }
    fun setRomanizeChinese(enabled: Boolean) = write { setRomanizeChinese(enabled) }
    fun setRomanizeHindi(enabled: Boolean) = write { setRomanizeHindi(enabled) }
    fun setRomanizeOtherLanguages(enabled: Boolean) = write { setRomanizeOtherLanguages(enabled) }
    fun setFindMissingLyrics(enabled: Boolean) = write { setFindMissingLyrics(enabled) }

    fun downloadJapaneseDictionary() = japaneseDictionary.download()
    fun cancelJapaneseDictionary() = japaneseDictionary.cancel()
    fun removeJapaneseDictionary() = japaneseDictionary.remove()

    /** Forgets every stored and edited lyric, reporting how it went to [onFinished]. */

    private fun write(change: suspend LyricsSettings.() -> Unit) {
        viewModelScope.launch { lyricsSettings.change() }
    }

    private fun <T> Setting<T>.state(): StateFlow<T> =
        flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), value)
}
