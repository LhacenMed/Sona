package com.lhacenmed.sona.feature.settings.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.sona.core.datastore.CustomFont
import com.lhacenmed.sona.core.datastore.EffectSettings
import com.lhacenmed.sona.core.datastore.LyricsBackgroundStyle
import com.lhacenmed.sona.core.datastore.LyricsSettings
import com.lhacenmed.sona.core.datastore.MiniPlayerBackgroundStyle
import com.lhacenmed.sona.core.datastore.PlayerAppearance
import com.lhacenmed.sona.core.datastore.PlayerBackgroundStyle
import com.lhacenmed.sona.core.datastore.PlayerButtonsStyle
import com.lhacenmed.sona.core.datastore.PlayerSliderStyle
import com.lhacenmed.sona.core.datastore.PlayerStyle
import com.lhacenmed.sona.core.datastore.PlayerStyleSettings
import com.lhacenmed.sona.core.datastore.ThemeChoices
import com.lhacenmed.sona.core.datastore.ThemeSettings
import com.lhacenmed.sona.core.datastore.stateIn
import com.lhacenmed.sona.core.model.AppFont
import com.lhacenmed.sona.core.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The stored appearance settings every appearance screen is connected to - the [AppearanceScreen] and
 * the palette, theme creator and custom background screens it opens.
 */
@HiltViewModel
class AppearanceSettingsViewModel @Inject constructor(
    private val themeSettings: ThemeSettings,
    private val playerStyleSettings: PlayerStyleSettings,
    private val lyricsSettings: LyricsSettings,
    private val effectSettings: EffectSettings,
) : ViewModel() {

    val theme: StateFlow<ThemeChoices> = themeSettings.choices.stateIn(viewModelScope)
    val player: StateFlow<PlayerAppearance> = playerStyleSettings.appearance.stateIn(viewModelScope)
    val lyricsBackground: StateFlow<LyricsBackgroundStyle> = lyricsSettings.lyricsBackgroundStyle.stateIn(viewModelScope)
    val roundMode: StateFlow<Boolean> = themeSettings.roundMode.stateIn(viewModelScope)
    val disableAnimations: StateFlow<Boolean> = effectSettings.disableAnimations.stateIn(viewModelScope)
    val forceHighRefreshRate: StateFlow<Boolean> = effectSettings.forceHighRefreshRate.stateIn(viewModelScope)

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    fun setThemeMode(mode: ThemeMode) = write { themeSettings.setThemeMode(mode) }
    fun setPureBlack(enabled: Boolean) = write { themeSettings.setPureBlack(enabled) }
    fun setDynamicColors(enabled: Boolean) = write { themeSettings.setDynamicColors(enabled) }
    fun setWallpaperColors(enabled: Boolean) = write { themeSettings.setWallpaperColors(enabled) }
    fun setCoverColors(enabled: Boolean) = write { themeSettings.setCoverColors(enabled) }
    fun setColorPalette(palette: String) = write { themeSettings.setColorPalette(palette) }
    fun setFont(font: AppFont) = write { themeSettings.setFont(font) }
    fun setCustomFont(customFont: CustomFont) = write { themeSettings.setCustomFont(customFont) }
    fun setRoundMode(enabled: Boolean) = write { themeSettings.setRoundMode(enabled) }

    fun setPlayerStyle(style: PlayerStyle) = write { playerStyleSettings.setStyle(style) }
    fun setSliderStyle(style: PlayerSliderStyle) = write { playerStyleSettings.setSliderStyle(style) }
    fun setPlayerBackground(style: PlayerBackgroundStyle) = write { playerStyleSettings.setBackground(style) }
    fun setLyricsBackground(style: LyricsBackgroundStyle) = write { lyricsSettings.setLyricsBackgroundStyle(style) }
    fun setMiniPlayerBackground(style: MiniPlayerBackgroundStyle) =
        write { playerStyleSettings.setMiniPlayerBackground(style) }
    fun setButtonsStyle(style: PlayerButtonsStyle) = write { playerStyleSettings.setButtonsStyle(style) }
    fun setHideThumbnail(enabled: Boolean) = write { playerStyleSettings.setHideThumbnail(enabled) }
    fun setSwipeToChangeTrack(enabled: Boolean) = write { playerStyleSettings.setSwipeToChangeTrack(enabled) }
    fun setCustomBackgroundImage(uri: String?) = write { playerStyleSettings.setCustomBackgroundImage(uri) }
    fun setCustomBackgroundBlur(blur: Float) = write { playerStyleSettings.setCustomBackgroundBlur(blur) }
    fun setCustomBackgroundContrast(contrast: Float) = write { playerStyleSettings.setCustomBackgroundContrast(contrast) }
    fun setCustomBackgroundBrightness(brightness: Float) =
        write { playerStyleSettings.setCustomBackgroundBrightness(brightness) }

    fun setDisableAnimations(enabled: Boolean) = write { effectSettings.setDisableAnimations(enabled) }
    fun setForceHighRefreshRate(enabled: Boolean) = write { effectSettings.setForceHighRefreshRate(enabled) }
}
