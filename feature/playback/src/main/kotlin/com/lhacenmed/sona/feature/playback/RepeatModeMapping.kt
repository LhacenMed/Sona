package com.lhacenmed.sona.feature.playback

import com.lhacenmed.sona.core.common.R as CommonR
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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

/**
 * The icon [this] is drawn with in the notification, which cannot dim a toggle that is off: on, it is drawn with the dot that
 * says so - the players' own drawings, see `PlayerIcons`.
 */
@get:DrawableRes
val RepeatMode.iconRes: Int
    get() = when (this) {
        RepeatMode.STOP_AFTER_CURRENT -> CommonR.drawable.icon_player_repeat_stop
        RepeatMode.ONE -> CommonR.drawable.icon_player_repeat_one_on
        RepeatMode.ALL -> CommonR.drawable.icon_player_repeat_on
        RepeatMode.OFF -> CommonR.drawable.icon_player_repeat
    }

/** The icon [this] is drawn with on a widget's transport row, which dims it while it is off - as the players draw it. */
@get:DrawableRes
val RepeatMode.transportIconRes: Int
    get() = when (this) {
        RepeatMode.OFF, RepeatMode.ALL -> CommonR.drawable.icon_player_repeat
        RepeatMode.ONE -> CommonR.drawable.icon_player_repeat_one
        RepeatMode.STOP_AFTER_CURRENT -> CommonR.drawable.icon_player_repeat_stop
    }

/** What [this] is named by beside [iconRes]. */
@get:StringRes
val RepeatMode.labelRes: Int
    get() = when (this) {
        RepeatMode.STOP_AFTER_CURRENT -> R.string.playback_action_repeat_one_stop
        RepeatMode.ONE -> R.string.playback_action_repeat_one
        RepeatMode.ALL -> R.string.playback_action_repeat_all
        RepeatMode.OFF -> R.string.playback_action_repeat_off
    }
