package com.lhacenmed.sona.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lhacenmed.sona.core.common.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

private val Context.playerStyleDataStore by preferencesDataStore(name = "player_style_settings")

private val PLAYER_STYLE = stringPreferencesKey("player_style")
private val PLAYER_SLIDER_STYLE = stringPreferencesKey("player_slider_style")
private val PLAYER_BACKGROUND_STYLE = stringPreferencesKey("player_background_style")
private val MINI_PLAYER_BACKGROUND_STYLE = stringPreferencesKey("mini_player_background_style")
private val PLAYER_BUTTONS_STYLE = stringPreferencesKey("player_buttons_style")
private val HIDE_PLAYER_THUMBNAIL = booleanPreferencesKey("hide_player_thumbnail")
private val SWIPE_TO_CHANGE_TRACK = booleanPreferencesKey("swipe_thumbnail")
private val CUSTOM_BACKGROUND_IMAGE_URI = stringPreferencesKey("player_custom_image_uri")
private val CUSTOM_BACKGROUND_BLUR = floatPreferencesKey("player_custom_blur")
private val CUSTOM_BACKGROUND_CONTRAST = floatPreferencesKey("player_custom_contrast")
private val CUSTOM_BACKGROUND_BRIGHTNESS = floatPreferencesKey("player_custom_brightness")

/**
 * How the player looks - ArchiveTune's player appearance preferences, with its defaults.
 *
 * Read as one [PlayerAppearance], since the player draws with all of it at once and a change to any
 * part of it is a change to what the player draws.
 */
@Singleton
class PlayerStyleSettings @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {

    private val dataStore = context.playerStyleDataStore
    private val cache = PreferencesCache(dataStore, scope)

    internal suspend fun awaitLoaded() = cache.awaitLoaded()

    val appearance: Setting<PlayerAppearance> = cache.setting { preferences ->
        PlayerAppearance(
            // DEFAULT for a style no longer offered, too, such as the ones dropped.
            style = preferences.enum(PLAYER_STYLE, PlayerStyle.DEFAULT),
            sliderStyle = preferences.enum(PLAYER_SLIDER_STYLE, PlayerSliderStyle.CIRCULAR),
            background = preferences.enum(PLAYER_BACKGROUND_STYLE, PlayerBackgroundStyle.DEFAULT),
            miniPlayerBackground = preferences.enum(MINI_PLAYER_BACKGROUND_STYLE, MiniPlayerBackgroundStyle.THEME),
            buttonsStyle = preferences.enum(PLAYER_BUTTONS_STYLE, PlayerButtonsStyle.DEFAULT),
            hideThumbnail = preferences[HIDE_PLAYER_THUMBNAIL] ?: false,
            swipeToChangeTrack = preferences[SWIPE_TO_CHANGE_TRACK] ?: true,
            customBackground = CustomBackground(
                imageUri = preferences[CUSTOM_BACKGROUND_IMAGE_URI],
                blur = (preferences[CUSTOM_BACKGROUND_BLUR] ?: 0f).coerceIn(CustomBackground.BLUR_RANGE),
                contrast = (preferences[CUSTOM_BACKGROUND_CONTRAST] ?: 1f).coerceIn(CustomBackground.TONE_RANGE),
                brightness = (preferences[CUSTOM_BACKGROUND_BRIGHTNESS] ?: 1f).coerceIn(CustomBackground.TONE_RANGE),
            ),
        )
    }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    suspend fun setStyle(style: PlayerStyle) = set(PLAYER_STYLE, style.name)

    suspend fun setSliderStyle(style: PlayerSliderStyle) = set(PLAYER_SLIDER_STYLE, style.name)

    suspend fun setBackground(style: PlayerBackgroundStyle) = set(PLAYER_BACKGROUND_STYLE, style.name)

    suspend fun setMiniPlayerBackground(style: MiniPlayerBackgroundStyle) =
        set(MINI_PLAYER_BACKGROUND_STYLE, style.name)

    suspend fun setButtonsStyle(style: PlayerButtonsStyle) = set(PLAYER_BUTTONS_STYLE, style.name)

    suspend fun setHideThumbnail(enabled: Boolean) = set(HIDE_PLAYER_THUMBNAIL, enabled)

    suspend fun setSwipeToChangeTrack(enabled: Boolean) = set(SWIPE_TO_CHANGE_TRACK, enabled)

    /** The custom background's image, or none - a document uri the app holds a persisted read grant to. */
    suspend fun setCustomBackgroundImage(uri: String?) {
        dataStore.edit {
            if (uri == null) it.remove(CUSTOM_BACKGROUND_IMAGE_URI) else it[CUSTOM_BACKGROUND_IMAGE_URI] = uri
        }
    }

    suspend fun setCustomBackgroundBlur(blur: Float) = set(CUSTOM_BACKGROUND_BLUR, blur)

    suspend fun setCustomBackgroundContrast(contrast: Float) = set(CUSTOM_BACKGROUND_CONTRAST, contrast)

    suspend fun setCustomBackgroundBrightness(brightness: Float) = set(CUSTOM_BACKGROUND_BRIGHTNESS, brightness)
}

/** Everything the player is drawn with that the user chooses. */
data class PlayerAppearance(
    val style: PlayerStyle,
    val sliderStyle: PlayerSliderStyle,
    val background: PlayerBackgroundStyle,
    val miniPlayerBackground: MiniPlayerBackgroundStyle,
    val buttonsStyle: PlayerButtonsStyle,
    /** ArchiveTune's "Hide player thumbnail": the app's logo in place of the cover. */
    val hideThumbnail: Boolean,
    /** Swiping the cover, or the mini player, changes track. */
    val swipeToChangeTrack: Boolean,
    val customBackground: CustomBackground,
)

/** The image [PlayerBackgroundStyle.CUSTOM] draws, and how it is adjusted - ArchiveTune's customized background. */
data class CustomBackground(
    val imageUri: String?,
    /** In dp. */
    val blur: Float,
    /** A multiple of the image's own. */
    val contrast: Float,
    /** A multiple of the image's own. */
    val brightness: Float,
) {
    companion object {
        val BLUR_RANGE = 0f..50f
        val TONE_RANGE = 0.5f..2f
    }
}
