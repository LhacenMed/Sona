package com.lhacenmed.sona.feature.playback

import androidx.media3.common.Player
import com.lhacenmed.sona.core.model.RepeatMode

/**
 * How a [RepeatMode] reaches the player.
 *
 * [RepeatMode.ONE] and [RepeatMode.STOP_AFTER_CURRENT] both repeat the current track, so both are
 * `REPEAT_MODE_ONE`; what separates them is whether the player pauses when the track comes round
 * again. There is deliberately no mapping back from the player's int, because it cannot distinguish
 * those two - the stored mode is the only thing that can, and every reader uses it.
 */
internal fun RepeatMode.toPlayerRepeatMode(): Int = when (this) {
    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
    RepeatMode.ALL -> Player.REPEAT_MODE_ALL
    RepeatMode.ONE, RepeatMode.STOP_AFTER_CURRENT -> Player.REPEAT_MODE_ONE
}

/** Whether the player should stop rather than play the current track again. */
internal fun RepeatMode.stopsAfterCurrentTrack(): Boolean = this == RepeatMode.STOP_AFTER_CURRENT
