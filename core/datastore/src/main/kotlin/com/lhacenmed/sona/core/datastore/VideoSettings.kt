package com.lhacenmed.sona.core.datastore

import android.content.Context
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

private val Context.videoDataStore by preferencesDataStore(name = "video_settings")

private val ORIENTATION = stringPreferencesKey("orientation")
private val KEEP_ORIENTATION = booleanPreferencesKey("keep_orientation")
private val ASPECT = stringPreferencesKey("aspect")
private val KEEP_ASPECT = booleanPreferencesKey("keep_aspect")
private val MUTED = booleanPreferencesKey("muted")
private val SHOW_CLOCK = booleanPreferencesKey("show_clock")
private val RESUME = booleanPreferencesKey("resume")
private val GESTURES = booleanPreferencesKey("gestures")
private val CONTINUOUS_PLAY = booleanPreferencesKey("continuous_play")
private val DOUBLE_TAP_SEEK = booleanPreferencesKey("double_tap_seek")
private val DOUBLE_TAP_SEEK_SECONDS = intPreferencesKey("double_tap_seek_seconds")
private val LONG_PRESS_SPEED_UP = booleanPreferencesKey("long_press_speed_up")
private val LONG_PRESS_SPEED = floatPreferencesKey("long_press_speed")
private val LONG_PRESS_VIBRATION = booleanPreferencesKey("long_press_vibration")
private val ZOOM_PAN = booleanPreferencesKey("zoom_pan")

/** How the video player behaves - PLAYit's Video settings. */
@Singleton
class VideoSettings @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {

    private val dataStore = context.videoDataStore
    private val cache = PreferencesCache(dataStore, scope)

    internal suspend fun awaitLoaded() = cache.awaitLoaded()

    /** The way the screen stands as a video opens. */
    val orientation: Setting<VideoOrientation> = cache.setting { it.enum(ORIENTATION, VideoOrientation.Default) }

    suspend fun setOrientation(orientation: VideoOrientation) {
        dataStore.edit { it[ORIENTATION] = orientation.name }
    }

    /**
     * Whether the rotation button's choice becomes [orientation], so every video opens standing as the last was left -
     * on until the user would rather each open as Settings says.
     */
    val keepOrientation: Setting<Boolean> = cache.setting { it[KEEP_ORIENTATION] ?: true }

    suspend fun setKeepOrientation(enabled: Boolean) {
        dataStore.edit { it[KEEP_ORIENTATION] = enabled }
    }

    /** How a video is fitted to the screen as it opens. */
    val aspect: Setting<VideoAspect> = cache.setting { it.enum(ASPECT, VideoAspect.Default) }

    suspend fun setAspect(aspect: VideoAspect) {
        dataStore.edit { it[ASPECT] = aspect.name }
    }

    /**
     * Whether the aspect button's choice becomes [aspect], so every video is fitted as the last was - on until the
     * user would rather each open fitted to the screen.
     */
    val keepAspect: Setting<Boolean> = cache.setting { it[KEEP_ASPECT] ?: true }

    suspend fun setKeepAspect(enabled: Boolean) {
        dataStore.edit { it[KEEP_ASPECT] = enabled }
    }

    /** Whether videos play silenced - the mute button's choice, kept from one video to the next. */
    val muted: Setting<Boolean> = cache.setting { it[MUTED] ?: false }

    suspend fun setMuted(muted: Boolean) {
        dataStore.edit { it[MUTED] = muted }
    }

    /** Whether the time and battery level stay on screen while the controls are hidden. */
    val showClock: Setting<Boolean> = cache.setting { it[SHOW_CLOCK] ?: false }

    suspend fun setShowClock(enabled: Boolean) {
        dataStore.edit { it[SHOW_CLOCK] = enabled }
    }

    /** Whether a video carries on from where it was left, rather than from its start. */
    val resume: Setting<Boolean> = cache.setting { it[RESUME] ?: true }

    suspend fun setResume(enabled: Boolean) {
        dataStore.edit { it[RESUME] = enabled }
    }

    /** Whether dragging over the video seeks, and changes the brightness and the volume. */
    val gestures: Setting<Boolean> = cache.setting { it[GESTURES] ?: true }

    suspend fun setGestures(enabled: Boolean) {
        dataStore.edit { it[GESTURES] = enabled }
    }

    /** Whether the next video plays when one ends, rather than the player pausing at its end. */
    val continuousPlay: Setting<Boolean> = cache.setting { it[CONTINUOUS_PLAY] ?: true }

    suspend fun setContinuousPlay(enabled: Boolean) {
        dataStore.edit { it[CONTINUOUS_PLAY] = enabled }
    }

    /**
     * Whether a double tap on either side of the video seeks back or forward [doubleTapSeekSeconds], and one in the
     * middle pauses or plays it.
     */
    val doubleTapSeek: Setting<Boolean> = cache.setting { it[DOUBLE_TAP_SEEK] ?: true }

    suspend fun setDoubleTapSeek(enabled: Boolean) {
        dataStore.edit { it[DOUBLE_TAP_SEEK] = enabled }
    }

    val doubleTapSeekSeconds: Setting<Int> = cache.setting { it[DOUBLE_TAP_SEEK_SECONDS] ?: DOUBLE_TAP_SEEK_SECONDS_DEFAULT }

    suspend fun setDoubleTapSeekSeconds(seconds: Int) {
        dataStore.edit { it[DOUBLE_TAP_SEEK_SECONDS] = seconds }
    }

    /** Whether holding a finger on the video plays it at [longPressSpeed] until it lifts. */
    val longPressSpeedUp: Setting<Boolean> = cache.setting { it[LONG_PRESS_SPEED_UP] ?: true }

    suspend fun setLongPressSpeedUp(enabled: Boolean) {
        dataStore.edit { it[LONG_PRESS_SPEED_UP] = enabled }
    }

    val longPressSpeed: Setting<Float> = cache.setting { it[LONG_PRESS_SPEED] ?: LONG_PRESS_SPEED_DEFAULT }

    suspend fun setLongPressSpeed(speed: Float) {
        dataStore.edit { it[LONG_PRESS_SPEED] = speed }
    }

    /** Whether the device vibrates as a long press speeds the video up. */
    val longPressVibration: Setting<Boolean> = cache.setting { it[LONG_PRESS_VIBRATION] ?: true }

    suspend fun setLongPressVibration(enabled: Boolean) {
        dataStore.edit { it[LONG_PRESS_VIBRATION] = enabled }
    }

    /** Whether two fingers zoom the video, and pan it while it is enlarged. */
    val zoomPan: Setting<Boolean> = cache.setting { it[ZOOM_PAN] ?: true }

    suspend fun setZoomPan(enabled: Boolean) {
        dataStore.edit { it[ZOOM_PAN] = enabled }
    }

    companion object {
        /** The seek lengths a double tap may be set to, in seconds. */
        val DoubleTapSeekSecondsChoices = listOf(5, 10, 15, 20, 30)

        /** The speeds a long press may be set to play at. */
        val LongPressSpeedChoices = listOf(1.5f, 2f, 2.5f, 3f)

        private const val DOUBLE_TAP_SEEK_SECONDS_DEFAULT = 10
        private const val LONG_PRESS_SPEED_DEFAULT = 2f
    }
}
