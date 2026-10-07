package com.lhacenmed.sona.core.designsystem.component.fab

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.lhacenmed.sona.core.designsystem.R
import com.lhacenmed.sona.core.designsystem.component.LocalPlayerSheetHeight
import com.lhacenmed.sona.core.designsystem.component.PlayerSheetHeight
import com.lhacenmed.sona.core.designsystem.component.SonaPlayingIndicatorBox
import com.lhacenmed.sona.core.designsystem.component.WindowOverlay
import com.lhacenmed.sona.core.designsystem.component.screen.LocalScreenLists
import com.lhacenmed.sona.core.designsystem.component.screen.PlayingRow
import com.lhacenmed.sona.core.designsystem.component.screen.ScreenLists
import com.lhacenmed.sona.core.designsystem.component.screen.screenList
import com.lhacenmed.sona.core.designsystem.theme.ExpressiveMotion
import com.lhacenmed.sona.core.designsystem.theme.SonaComponentStyle
import com.lhacenmed.sona.core.designsystem.theme.pressedCornerRadius
import com.lhacenmed.sona.core.designsystem.theme.rememberPressFraction
import com.lhacenmed.sona.core.designsystem.theme.roundedShape
import kotlinx.coroutines.launch

/** How tall a screen's primary FAB is: Material's medium FAB - see [SonaFloatingActionButtonMenu]. */
internal val PrimaryButtonSize = 80.dp

/** How tall the scroll button is: Material's FAB. Its small FAB is deprecated in M3 Expressive. */
private val ScrollButtonSize = 56.dp

/**
 * The gap between two FABs in the stack, and between the stack and the player or navigation bar below it:
 * Material's FAB margin, the keyline every section of a screen keeps from the edge.
 */
internal val StackSpacing = SonaComponentStyle.ContentHorizontalPadding

/** The corners the scroll button rests with: Material's FAB corner. */
private val ScrollButtonCornerRadius = 16.dp

/**
 * A screen's own buttons, as [FloatingActionButtonStack] holds them - a [SonaFloatingActionButtonMenu] or
 * [SonaExtendedFloatingActionButtons]. [height] is how tall they stand, read where the stack draws them: 0
 * while the screen has turned them off, which the stack closes the gap under.
 */
internal sealed interface ScreenButtons {
    val height: Dp
}

/** Where the scroll button takes the screen's list: back to its top, or to the row playing in it. */
private sealed interface ScrollTarget {
    data object Top : ScrollTarget

    data class Playing(val row: PlayingRow) : ScrollTarget
}

/**
 * Everything a window's FABs are drawn from - see [FloatingActionButtonStack]. All of it is state, read
 * where it is drawn: what is drawn over the window is composed apart from the screen and the player, so
 * anything it only captured from them would stay as it was when first drawn.
 */
@Stable
internal class FloatingActionButtonStackState {
    /** The screen's lists, the stack following the one it shows most of - see [screenList]. */
    val screenLists = ScreenLists()

    /** The screen's own buttons, if it has any - see [ScreenButtons]. */
    var screenButtons: ScreenButtons? by mutableStateOf(null)

    /** The player the stack stands on - see [LocalPlayerSheetHeight]. */
    var playerSheet: PlayerSheetHeight by mutableStateOf(PlayerSheetHeight(current = { 0.dp }, collapsed = 0.dp))

    /** The navigation bar the stack stands on while there is no mini player. */
    var navigationBarHeight: Dp by mutableStateOf(0.dp)

    /** How much of the window's bottom stands covered at this moment: by the player, or by the navigation bar while the player is lower. */
    val coveredHeight: Dp
        get() = max(playerSheet.value, navigationBarHeight)

    /**
     * How high the stack's bottom stands above the window's bottom at this moment: a FAB's margin above
     * [coveredHeight]. Read where the stack is laid out, so following the player recomposes nothing.
     */
    val standingHeight: Dp
        get() = coveredHeight + StackSpacing
}

internal val LocalFloatingActionButtonStack = staticCompositionLocalOf<FloatingActionButtonStackState?> { null }

/**
 * Every FAB the screen [content] shows, stacked at the window's bottom end: the screen's own buttons, if it
 * has any - see [ScreenButtons] - and above them a scroll button for the list the screen shows - see
 * [screenList].
 *
 * They are drawn on the [WindowOverlay], over the player, on the content keyline, and move on Material's
 * expressive springs - see [ExpressiveMotion]. Every activity's content is wrapped in one, inside the
 * player, so every screen - and every list - has them.
 *
 * The stack stands on the player - see [LocalPlayerSheetHeight] - a FAB's margin above its top, or above
 * the navigation bar while there is no mini player, and follows it frame by frame: it rides up as the
 * mini player slides in and back down as it slides away, and rides the player's top as it is dragged up.
 *
 * The whole stack steps aside together: while the player is raised over the screen, while the list is
 * fast scrolled, and once the list has come within the stack's height of its end - where the stack would
 * cover its last rows - so a list needs no room of its own for it. How tall it is is set by which buttons
 * the screen has rather than which are showing, so one showing never moves the others - while a screen whose
 * own FAB is turned off has none, and the way back to the top takes its place.
 *
 * The scroll button takes the list back to its top once it is a quarter of a screen from it. Nearer the top,
 * it takes the list to the row it marks as playing instead, while that row is off screen - see
 * [com.lhacenmed.sona.core.designsystem.component.screen.PlayingRow] - showing the playing bars that row's
 * cover shows. It steps aside while the screen's own FAB has its menu open. Where it goes is told by how
 * the list has scrolled alone - see [ScreenList] - so nothing around the list changes when it shows.
 */
@Composable
fun FloatingActionButtonStack(content: @Composable () -> Unit) {
    val stack = remember { FloatingActionButtonStackState() }
    // Handed over as they are composed: the window overlay is composed above the player that provides them.
    stack.playerSheet = LocalPlayerSheetHeight.current
    stack.navigationBarHeight = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
    CompositionLocalProvider(
        LocalFloatingActionButtonStack provides stack,
        LocalScreenLists provides stack.screenLists,
        content = content,
    )
    WindowOverlay { FloatingActionButtons(stack) }
}

/** What [FloatingActionButtonStack] draws over the window - everything read from [stack]'s state. */
@Composable
private fun BoxScope.FloatingActionButtons(stack: FloatingActionButtonStackState) {
    ExpressiveMotion {
        val screenButtons = stack.screenButtons
        // Read here, in the stack's own composition, so a change to them is drawn in the frame it happens in.
        val screenButtonsHeight = screenButtons?.height ?: 0.dp
        val scrollButtonLift = if (screenButtonsHeight > 0.dp) screenButtonsHeight + StackSpacing else 0.dp
        val density = LocalDensity.current
        val stackHeightPx = with(density) { (scrollButtonLift + ScrollButtonSize).toPx() }
        val windowHeightPx = LocalWindowInfo.current.containerSize.height.toFloat()
        val isShownState = remember(stack, stackHeightPx) {
            derivedStateOf {
                val list = stack.screenLists.current
                !stack.playerSheet.isRaised && list?.isFastScrolling?.invoke() != true &&
                    list?.isNearEnd(stackHeightPx) != true
            }
        }
        val scrollTarget by remember(stack, isShownState, density, windowHeightPx) {
            derivedStateOf {
                val list = stack.screenLists.current
                when {
                    list == null || !isShownState.value || (stack.screenButtons as? PrimaryButton)?.expanded == true -> null
                    list.isAwayFromTop -> ScrollTarget.Top
                    else -> {
                        val uncoveredBottomPx = windowHeightPx - with(density) { stack.coveredHeight.toPx() }
                        list.playingRow()?.takeUnless { list.showsRow(it.index, uncoveredBottomPx) }?.let(ScrollTarget::Playing)
                    }
                }
            }
        }
        val isShown by isShownState
        val anchor = Modifier
            .align(Alignment.BottomEnd)
            .offset { IntOffset(0, -stack.standingHeight.roundToPx()) }
            .padding(end = SonaComponentStyle.ContentHorizontalPadding)
        val scope = rememberCoroutineScope()

        ScrollButton(
            target = scrollTarget,
            onClick = { target ->
                stack.screenLists.current?.let { list ->
                    scope.launch {
                        when (target) {
                            ScrollTarget.Top -> list.scrollToTop()
                            is ScrollTarget.Playing -> list.scrollToRow(target.row.index)
                        }
                    }
                }
            },
            modifier = anchor.padding(bottom = scrollButtonLift),
        )
        when (screenButtons) {
            is PrimaryButton -> screenButtons.content?.let { content ->
                PrimaryButtonMenu(button = screenButtons, content = content, isShown = isShown, anchor = anchor)
            }
            is ExtendedButtons -> ExtendedButtonColumn(buttons = screenButtons.buttons, isShown = isShown, anchor = anchor)
            null -> Unit
        }
    }
}

/** What the scroll button last pointed to, kept while it hides - so its icon never changes on its way out. */
private class LastScrollTarget {
    var value: ScrollTarget = ScrollTarget.Top
}

/**
 * The scroll button: Material's FAB, pressing as every press in Sona does - shown while it has a [target],
 * an arrow while that is the list's top and the playing bars while it is the playing row, crossfading as it
 * turns from one to the other.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScrollButton(target: ScrollTarget?, onClick: (ScrollTarget) -> Unit, modifier: Modifier) {
    val lastTarget = remember { LastScrollTarget() }
    if (target != null) lastTarget.value = target
    val shownTarget = lastTarget.value
    val scrollToPlayingLabel = stringResource(R.string.fab_scroll_to_playing)
    val interactionSource = remember { MutableInteractionSource() }
    val pressFraction by rememberPressFraction(interactionSource)
    FloatingActionButton(
        onClick = { onClick(shownTarget) },
        modifier = modifier.animateFloatingActionButton(visible = target != null, alignment = Alignment.Center),
        shape = roundedShape(pressedCornerRadius(pressFraction, restingRadius = ScrollButtonCornerRadius)),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        interactionSource = interactionSource,
    ) {
        Crossfade(targetState = shownTarget is ScrollTarget.Playing, label = "scrollButtonIcon") { isToPlaying ->
            if (isToPlaying) {
                SonaPlayingIndicatorBox(
                    isActive = true,
                    isPlaying = (shownTarget as? ScrollTarget.Playing)?.row?.isPlaying == true,
                    color = LocalContentColor.current,
                    modifier = Modifier.semantics { contentDescription = scrollToPlayingLabel },
                )
            } else {
                Icon(imageVector = Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.fab_scroll_to_top))
            }
        }
    }
}
