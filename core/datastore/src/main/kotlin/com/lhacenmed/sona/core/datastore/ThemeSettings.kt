package com.lhacenmed.sona.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lhacenmed.sona.core.common.di.ApplicationScope
import com.lhacenmed.sona.core.model.AppFont
import com.lhacenmed.sona.core.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

private val Context.dataStore by preferencesDataStore(name = "theme_settings")

private val THEME_MODE = stringPreferencesKey("theme_mode")
private val PURE_BLACK = booleanPreferencesKey("pure_black")

// The master switch keeps the key it had when it was the only colour setting, so whoever turned it off
// before still gets what that meant - the wallpaper's colours alone - from the defaults below it.
private val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_theme_enabled")
private val WALLPAPER_COLORS = booleanPreferencesKey("wallpaper_colors")
private val COVER_COLORS = booleanPreferencesKey("cover_colors")
private val COLOR_PALETTE = stringPreferencesKey("color_palette")

private val FONT = stringPreferencesKey("font")
private val CUSTOM_FONT_URI = stringPreferencesKey("custom_font_uri")
private val CUSTOM_FONT_NAME = stringPreferencesKey("custom_font_name")

private val ROUND_MODE = booleanPreferencesKey("round_mode")

/**
 * How the app is themed: light or dark, where its colours come from, and the typeface it is set in -
 * ArchiveTune's theme preferences - plus round mode.
 *
 * Only stored choices: the colours actually in use depend on the playing cover and the device too, and
 * are worked out by whatever themes the app from these.
 */
@Singleton
class ThemeSettings @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {

    private val dataStore = context.dataStore
    private val cache = PreferencesCache(dataStore, scope)

    internal suspend fun awaitLoaded() = cache.awaitLoaded()

    /** Every choice the app's theme is drawn from, read together - what the theme follows. */
    val choices: Setting<ThemeChoices> = cache.setting { preferences ->
        ThemeChoices(
            mode = preferences.themeMode(),
            pureBlack = preferences.pureBlack(),
            dynamicColors = preferences.dynamicColors(),
            wallpaperColors = preferences.wallpaperColors(),
            coverColors = preferences.coverColors(),
            colorPalette = preferences[COLOR_PALETTE],
            font = preferences.font(),
            customFont = preferences.customFont(),
        )
    }

    val themeMode: Setting<ThemeMode> = cache.setting { it.themeMode() }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    /** ArchiveTune's pure black: black surfaces while the theme is dark. */
    val pureBlack: Setting<Boolean> = cache.setting { it.pureBlack() }

    suspend fun setPureBlack(enabled: Boolean) {
        dataStore.edit { it[PURE_BLACK] = enabled }
    }

    /**
     * The wallpaper's colours, recoloured by the playing cover - and, while on, [wallpaperColors],
     * [coverColors] and [colorPalette] stand aside for it.
     */
    val dynamicColors: Setting<Boolean> = cache.setting { it.dynamicColors() }

    suspend fun setDynamicColors(enabled: Boolean) {
        dataStore.edit { it[DYNAMIC_COLORS] = enabled }
    }

    /** Material You: the system's colours from the wallpaper. While on, [colorPalette] stands aside for it. */
    val wallpaperColors: Setting<Boolean> = cache.setting { it.wallpaperColors() }

    suspend fun setWallpaperColors(enabled: Boolean) {
        dataStore.edit { it[WALLPAPER_COLORS] = enabled }
    }

    /** Colours from the playing track's cover, over whatever colours the app has without one. */
    val coverColors: Setting<Boolean> = cache.setting { it.coverColors() }

    suspend fun setCoverColors(enabled: Boolean) {
        dataStore.edit { it[COVER_COLORS] = enabled }
    }

    /**
     * The chosen palette, written as ArchiveTune writes its `CustomThemeColorKey`: a preset's id, or a
     * custom theme's encoded seeds. Read by the theme's palette codec, which knows every form.
     */
    val colorPalette: Setting<String?> = cache.setting { it[COLOR_PALETTE] }

    suspend fun setColorPalette(palette: String) {
        dataStore.edit { it[COLOR_PALETTE] = palette }
    }

    val font: Setting<AppFont> = cache.setting { it.font() }

    suspend fun setFont(font: AppFont) {
        dataStore.edit { it[FONT] = font.name }
    }

    /** The picked `.ttf`, as a document uri the app holds a persisted grant to, and its file name. */
    val customFont: Setting<CustomFont?> = cache.setting { it.customFont() }

    /** Stores [customFont] and makes it the font, in one edit, so the font is never custom without one. */
    suspend fun setCustomFont(customFont: CustomFont) {
        dataStore.edit {
            it[CUSTOM_FONT_URI] = customFont.uri
            it[CUSTOM_FONT_NAME] = customFont.name
            it[FONT] = AppFont.CUSTOM.name
        }
    }

    /** Whether covers are drawn with rounded corners (Auxio's `UISettings.roundMode`). */
    val roundMode: Setting<Boolean> = cache.setting { it[ROUND_MODE] ?: true }

    suspend fun setRoundMode(enabled: Boolean) {
        dataStore.edit { it[ROUND_MODE] = enabled }
    }
}

private fun Preferences.themeMode() = enum(THEME_MODE, ThemeMode.SYSTEM)
private fun Preferences.pureBlack() = this[PURE_BLACK] ?: false
private fun Preferences.dynamicColors() = this[DYNAMIC_COLORS] ?: true
private fun Preferences.wallpaperColors() = this[WALLPAPER_COLORS] ?: true
private fun Preferences.coverColors() = this[COVER_COLORS] ?: false
private fun Preferences.font() = enum(FONT, AppFont.DEFAULT)
private fun Preferences.customFont() =
    this[CUSTOM_FONT_URI]?.let { CustomFont(uri = it, name = this[CUSTOM_FONT_NAME].orEmpty()) }

/** Every theme choice at once - see [ThemeSettings.choices]; each is described on its own setting there. */
data class ThemeChoices(
    val mode: ThemeMode,
    val pureBlack: Boolean,
    val dynamicColors: Boolean,
    val wallpaperColors: Boolean,
    val coverColors: Boolean,
    val colorPalette: String?,
    val font: AppFont,
    val customFont: CustomFont?,
)

/** A font file the user picked: where it is, and what it is called. */
data class CustomFont(val uri: String, val name: String)
