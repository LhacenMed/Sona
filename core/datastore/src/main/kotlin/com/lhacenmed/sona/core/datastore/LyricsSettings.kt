package com.lhacenmed.sona.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lhacenmed.sona.core.common.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

private val Context.lyricsDataStore by preferencesDataStore(name = "lyrics_settings")

private val LYRICS_CLICK = booleanPreferencesKey("lyricsClick")
private val LYRICS_SCROLL = booleanPreferencesKey("lyricsScrollKey")
private val LYRICS_TEXT_SIZE = floatPreferencesKey("lyricsTextSize")
private val LYRICS_LINE_SPACING = floatPreferencesKey("lyricsLineSpacing")
private val LYRICS_LINE_BLUR = booleanPreferencesKey("lyricsLineBlur")
private val LYRICS_BACKGROUND_STYLE = stringPreferencesKey("lyricsBackgroundStyle")
private val SHOW_LYRICS_PLAYER_CONTROLS = booleanPreferencesKey("showLyricsPlayerControls")
private val LYRICS_V2_BOUNCE_FACTOR = floatPreferencesKey("lyricsV2BounceFactor")
private val LYRICS_V2_GLOW_FACTOR = floatPreferencesKey("lyricsV2GlowFactor")
private val LYRICS_V2_FILL_TRANSITION_WIDTH = floatPreferencesKey("lyricsV2FillTransitionWidth")
private val LYRICS_V2_LRC_BOUNCE_ENABLED = booleanPreferencesKey("lyricsV2LrcBounceEnabled")
private val LYRICS_ROMANIZE_JAPANESE = booleanPreferencesKey("lyricsRomanizeJapanese")
private val LYRICS_ROMANIZE_KOREAN = booleanPreferencesKey("lyricsRomanizeKorean")
private val LYRICS_ROMANIZE_CHINESE = booleanPreferencesKey("lyricsRomanizeChinese")
private val LYRICS_ROMANIZE_HINDI = booleanPreferencesKey("lyricsRomanizeHindi")
private val LYRICS_ROMANIZE_OTHER_LANGUAGES = booleanPreferencesKey("lyricsRomanizeOtherLanguages")
private val PRELOAD_QUEUE_LYRICS_ENABLED = booleanPreferencesKey("preload_queue_lyrics_enabled")
private val QUEUE_LYRICS_PRELOAD_COUNT = intPreferencesKey("queue_lyrics_preload_count")

/**
 * How lyrics look and behave (ported from ArchiveTune's lyrics preferences, under the same keys and
 * defaults). The V2 renderer is the only one Sona has, so ArchiveTune's lyrics mode is not stored.
 */
@Singleton
class LyricsSettings @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {

    private val dataStore = context.lyricsDataStore
    private val cache = PreferencesCache(dataStore, scope)

    internal suspend fun awaitLoaded() = cache.awaitLoaded()

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    /** Tapping a synced line seeks to it. */
    val lyricsClick: Setting<Boolean> = cache.setting { it[LYRICS_CLICK] ?: true }

    suspend fun setLyricsClick(enabled: Boolean) = set(LYRICS_CLICK, enabled)

    val lyricsScroll: Setting<Boolean> = cache.setting { it[LYRICS_SCROLL] ?: true }

    suspend fun setLyricsScroll(enabled: Boolean) = set(LYRICS_SCROLL, enabled)

    /** In sp. */
    val lyricsTextSize: Setting<Float> = cache.setting { it[LYRICS_TEXT_SIZE] ?: 26f }

    suspend fun setLyricsTextSize(size: Float) = set(LYRICS_TEXT_SIZE, size)

    /** A multiple of the text size. */
    val lyricsLineSpacing: Setting<Float> = cache.setting { it[LYRICS_LINE_SPACING] ?: 1.3f }

    suspend fun setLyricsLineSpacing(spacing: Float) = set(LYRICS_LINE_SPACING, spacing)

    val lyricsLineBlur: Setting<Boolean> = cache.setting { it[LYRICS_LINE_BLUR] ?: true }

    suspend fun setLyricsLineBlur(enabled: Boolean) = set(LYRICS_LINE_BLUR, enabled)

    val lyricsBackgroundStyle: Setting<LyricsBackgroundStyle> = cache.setting { preferences ->
        preferences[LYRICS_BACKGROUND_STYLE]?.let { name -> runCatching { LyricsBackgroundStyle.valueOf(name) }.getOrNull() }
            ?: LyricsBackgroundStyle.DEFAULT
    }

    suspend fun setLyricsBackgroundStyle(style: LyricsBackgroundStyle) = set(LYRICS_BACKGROUND_STYLE, style.name)

    val showLyricsPlayerControls: Setting<Boolean> = cache.setting { it[SHOW_LYRICS_PLAYER_CONTROLS] ?: true }

    suspend fun setShowLyricsPlayerControls(enabled: Boolean) = set(SHOW_LYRICS_PLAYER_CONTROLS, enabled)

    /** How far a sung word lifts, from 0 to 2 times the default. */
    val bounceFactor: Setting<Float> = cache.setting { it[LYRICS_V2_BOUNCE_FACTOR] ?: 1f }

    suspend fun setBounceFactor(factor: Float) = set(LYRICS_V2_BOUNCE_FACTOR, factor)

    /** How brightly a sung word glows, from 0 to 2 times the default. */
    val glowFactor: Setting<Float> = cache.setting { it[LYRICS_V2_GLOW_FACTOR] ?: 1f }

    suspend fun setGlowFactor(factor: Float) = set(LYRICS_V2_GLOW_FACTOR, factor)

    /** The width, in dp, of the soft edge the fill sweeps across a word with. */
    val fillTransitionWidth: Setting<Float> = cache.setting { it[LYRICS_V2_FILL_TRANSITION_WIDTH] ?: 8f }

    suspend fun setFillTransitionWidth(width: Float) = set(LYRICS_V2_FILL_TRANSITION_WIDTH, width)

    /** Whether line-synced lyrics bounce word by word as each line starts. */
    val lrcBounceEnabled: Setting<Boolean> = cache.setting { it[LYRICS_V2_LRC_BOUNCE_ENABLED] ?: true }

    suspend fun setLrcBounceEnabled(enabled: Boolean) = set(LYRICS_V2_LRC_BOUNCE_ENABLED, enabled)

    val romanizeJapanese: Setting<Boolean> = cache.setting { it[LYRICS_ROMANIZE_JAPANESE] ?: true }

    suspend fun setRomanizeJapanese(enabled: Boolean) = set(LYRICS_ROMANIZE_JAPANESE, enabled)

    val romanizeKorean: Setting<Boolean> = cache.setting { it[LYRICS_ROMANIZE_KOREAN] ?: true }

    suspend fun setRomanizeKorean(enabled: Boolean) = set(LYRICS_ROMANIZE_KOREAN, enabled)

    val romanizeChinese: Setting<Boolean> = cache.setting { it[LYRICS_ROMANIZE_CHINESE] ?: true }

    suspend fun setRomanizeChinese(enabled: Boolean) = set(LYRICS_ROMANIZE_CHINESE, enabled)

    val romanizeHindi: Setting<Boolean> = cache.setting { it[LYRICS_ROMANIZE_HINDI] ?: true }

    suspend fun setRomanizeHindi(enabled: Boolean) = set(LYRICS_ROMANIZE_HINDI, enabled)

    val romanizeOtherLanguages: Setting<Boolean> = cache.setting { it[LYRICS_ROMANIZE_OTHER_LANGUAGES] ?: true }

    suspend fun setRomanizeOtherLanguages(enabled: Boolean) = set(LYRICS_ROMANIZE_OTHER_LANGUAGES, enabled)

    /** Read the lyrics of the tracks coming up next, so they are there before those tracks play. */
    val preloadQueueLyricsEnabled: Setting<Boolean> = cache.setting { it[PRELOAD_QUEUE_LYRICS_ENABLED] ?: true }

    suspend fun setPreloadQueueLyricsEnabled(enabled: Boolean) = set(PRELOAD_QUEUE_LYRICS_ENABLED, enabled)

    /** How many upcoming tracks are preloaded, from 0 (off) to 10. */
    val queueLyricsPreloadCount: Setting<Int> = cache.setting { it[QUEUE_LYRICS_PRELOAD_COUNT] ?: 1 }

    suspend fun setQueueLyricsPreloadCount(count: Int) = set(QUEUE_LYRICS_PRELOAD_COUNT, count)
}
