package com.lhacenmed.sona.feature.library.options

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.lhacenmed.sona.core.designsystem.component.swipe.SwipeAction
import com.lhacenmed.sona.core.designsystem.component.swipe.SwipeActionTone
import com.lhacenmed.sona.core.designsystem.component.swipe.SwipeActions
import com.lhacenmed.sona.core.model.PlaybackParent
import com.lhacenmed.sona.feature.library.options.OptionsAction.DELETE
import com.lhacenmed.sona.feature.library.options.OptionsAction.DELETE_FROM_DEVICE
import com.lhacenmed.sona.feature.library.options.OptionsAction.EXCLUDE
import com.lhacenmed.sona.feature.library.options.OptionsAction.PLAY
import com.lhacenmed.sona.feature.library.options.OptionsAction.PLAY_NEXT
import com.lhacenmed.sona.feature.library.options.OptionsAction.QUEUE_ADD
import com.lhacenmed.sona.feature.library.options.OptionsAction.REMOVE_FROM_PLAYLIST
import com.lhacenmed.sona.feature.library.options.OptionsAction.SHUFFLE

/**
 * Which two of a row's options its swipes carry out: [startToEnd] for a swipe towards the row's end -
 * rightwards, left to right - and [endToStart] for one towards its start. Each is carried out exactly as
 * the options sheet carries it out, toast included, and drawn with the sheet's own icon for it.
 */
internal enum class OptionsSwipe(val startToEnd: OptionsAction, val endToStart: OptionsAction) {
    /** Plays next, or joins the queue - a list's rows, whose tracks join what is already playing. */
    QUEUE(startToEnd = PLAY_NEXT, endToStart = QUEUE_ADD),

    /** Plays, or shuffles - a collection started whole from its row, as its header's Play and Shuffle start it. */
    PLAYBACK(startToEnd = PLAY, endToStart = SHUFFLE),
}

/**
 * The swipe actions a list gives the row for [target], as [swipe] lays them out. A side whose action
 * [target] disables - a playlist with no tracks has nothing to play - holds back, as its sheet greys it out.
 */
@Composable
internal fun rememberOptionsSwipeActions(swipe: OptionsSwipe, actions: OptionsActions, target: OptionsTarget): SwipeActions =
    remember(swipe, actions, target) {
        swipe.toSwipeActions(disabled = target.disabledActions()) { action -> actions.perform(target, action) }
    }

/**
 * The swipe actions a list gives the row for the collection [parent] names, when the row has no
 * [OptionsTarget] of its own - Most played. See [OptionsActions.perform].
 */
@Composable
internal fun rememberOptionsSwipeActions(swipe: OptionsSwipe, actions: OptionsActions, parent: PlaybackParent): SwipeActions =
    remember(swipe, actions, parent) {
        swipe.toSwipeActions { action -> actions.perform(parent, action) }
    }

private fun OptionsSwipe.toSwipeActions(
    disabled: Set<OptionsAction> = emptySet(),
    perform: (OptionsAction) -> Unit,
): SwipeActions {
    fun swipeActionOf(action: OptionsAction) =
        action.takeUnless { it in disabled }?.let { SwipeAction(it.icon, it.swipeTone) { perform(it) } }
    return SwipeActions(startToEnd = swipeActionOf(startToEnd), endToStart = swipeActionOf(endToStart))
}

/** What an action is revealed on: the primary colour for the one a row mostly swipes for, danger for what takes something away. */
private val OptionsAction.swipeTone: SwipeActionTone
    get() = when (this) {
        PLAY, PLAY_NEXT -> SwipeActionTone.Primary
        DELETE, DELETE_FROM_DEVICE, REMOVE_FROM_PLAYLIST, EXCLUDE -> SwipeActionTone.Danger
        else -> SwipeActionTone.Secondary
    }
