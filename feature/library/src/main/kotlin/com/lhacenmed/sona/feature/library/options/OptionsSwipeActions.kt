package com.lhacenmed.sona.feature.library.options

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.lhacenmed.sona.core.designsystem.component.swipe.SwipeAction
import com.lhacenmed.sona.core.designsystem.component.swipe.SwipeActionTone
import com.lhacenmed.sona.core.designsystem.component.swipe.SwipeActions
import com.lhacenmed.sona.core.designsystem.icon.SonaIcons

/**
 * The swipe actions a library list gives the row for [target]: towards the row's end it plays next,
 * towards its start it joins the queue - an options sheet's two quickest actions, carried out by [actions]
 * exactly as the sheet carries them out, toast included.
 *
 * Both act on [target]'s own tracks alone, so a track needs no more than `OptionsTarget.ForTrack(track)`.
 */
@Composable
internal fun rememberQueueSwipeActions(actions: OptionsActions, target: OptionsTarget): SwipeActions =
    remember(actions, target) {
        SwipeActions(
            startToEnd = SwipeAction(SonaIcons.PlayNext, SwipeActionTone.Primary) {
                actions.perform(target, OptionsAction.PLAY_NEXT)
            },
            endToStart = SwipeAction(SonaIcons.QueueAdd, SwipeActionTone.Secondary) {
                actions.perform(target, OptionsAction.QUEUE_ADD)
            },
        )
    }
