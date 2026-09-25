package com.lhacenmed.sona.core.designsystem.component.swipe

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * What a swipe reveals and does: the [icon] shown on the [tone]'s colour as the row slides aside, and
 * [onSwipe], run when the row is let go past the arm - Gmail's swipe to archive.
 */
@Immutable
data class SwipeAction(
    val icon: ImageVector,
    val tone: SwipeActionTone,
    val onSwipe: () -> Unit,
)

/**
 * The colour a [SwipeAction] is revealed on, from the theme as it is: [Danger] for what takes something
 * away, [Primary] and [Secondary] for what adds to it.
 */
enum class SwipeActionTone { Primary, Secondary, Danger }

/**
 * A row's actions for a swipe towards its end - rightwards, left to right - and towards its start. A
 * side left null has nothing to do, and holds back the moment it is pulled.
 */
@Immutable
data class SwipeActions(
    val startToEnd: SwipeAction?,
    val endToStart: SwipeAction?,
)

/**
 * The swipe actions a list hands its rows, or null in a list whose rows do not swipe - told through the
 * composition, as [com.lhacenmed.sona.core.designsystem.component.LocalDragHandle] is, so a row is drawn
 * the same wherever it is listed and only the lists that swipe say so.
 */
val LocalSwipeActions = compositionLocalOf<SwipeActions?> { null }

internal val SwipeActionTone.containerColor: Color
    @Composable get() = when (this) {
        SwipeActionTone.Primary -> MaterialTheme.colorScheme.primaryContainer
        SwipeActionTone.Secondary -> MaterialTheme.colorScheme.secondaryContainer
        SwipeActionTone.Danger -> MaterialTheme.colorScheme.errorContainer
    }

internal val SwipeActionTone.contentColor: Color
    @Composable get() = when (this) {
        SwipeActionTone.Primary -> MaterialTheme.colorScheme.onPrimaryContainer
        SwipeActionTone.Secondary -> MaterialTheme.colorScheme.onSecondaryContainer
        SwipeActionTone.Danger -> MaterialTheme.colorScheme.onErrorContainer
    }
