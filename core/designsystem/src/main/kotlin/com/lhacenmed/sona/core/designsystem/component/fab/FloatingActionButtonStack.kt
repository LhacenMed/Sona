package com.lhacenmed.sona.core.designsystem.component.fab

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.lhacenmed.sona.core.designsystem.R
import com.lhacenmed.sona.core.designsystem.component.LocalPlayerSheetHeight
import com.lhacenmed.sona.core.designsystem.component.PlayerSheetHeight
import com.lhacenmed.sona.core.designsystem.component.WindowOverlay
import com.lhacenmed.sona.core.designsystem.theme.ExpressiveMotion
import com.lhacenmed.sona.core.designsystem.theme.SonaComponentStyle
import com.lhacenmed.sona.core.designsystem.theme.pressedCornerRadius
import com.lhacenmed.sona.core.designsystem.theme.rememberPressFraction
import com.lhacenmed.sona.core.designsystem.theme.roundedShape
import kotlinx.coroutines.launch

/** How tall a screen's primary FAB is: Material's medium FAB - see [SonaFloatingActionButtonMenu]. */
internal val PrimaryButtonSize = 80.dp

/** How tall the scroll-to-top button is: Material's FAB. Its small FAB is deprecated in M3 Expressive. */
private val ScrollToTopButtonSize = 56.dp

/**
 * The gap between two FABs in the stack, and between the stack and the player or navigation bar below it:
 * Material's FAB margin, the keyline every section of a screen keeps from the edge.
 */
private val StackSpacing = SonaComponentStyle.ContentHorizontalPadding

/** The corners the scroll-to-top button rests with: Material's FAB corner. */
private val ScrollToTopCornerRadius = 16.dp

/**
 * Everything a window's FABs are drawn from - see [FloatingActionButtonStack]. All of it is state, read
 * where it is drawn: what is drawn over the window is composed apart from the screen and the player, so
 * anything it only captured from them would stay as it was when first drawn.
 */
@Stable
internal class FloatingActionButtonStackState {
    val lists = mutableStateListOf<ScreenList>()

    /** The list the stack follows: the one the window shows most of - see [screenList]. */
    val currentList: ScreenList? by derivedStateOf {
        lists.filter { it.visibleArea > 0f }.maxByOrNull { it.visibleArea }
    }

    /** The screen's own FAB as it was last composed, if it has one - see [SonaFloatingActionButtonMenu]. */
    var primaryButton: PrimaryButton? by mutableStateOf(null)

    /** The player the stack stands on - see [LocalPlayerSheetHeight]. */
    var playerSheet: PlayerSheetHeight by mutableStateOf(PlayerSheetHeight(current = { 0.dp }, collapsed = 0.dp))

    /** The navigation bar the stack stands on while there is no mini player. */
    var navigationBarHeight: Dp by mutableStateOf(0.dp)

    /**
     * How high the stack's bottom stands above the window's bottom at this moment: a FAB's margin above the
     * player's top, or above the navigation bar while the player is lower. Read where the stack is laid out,
     * so following the player recomposes nothing.
     */
    val standingHeight: Dp
        get() = max(playerSheet.value, navigationBarHeight) + StackSpacing
}

internal val LocalFloatingActionButtonStack = staticCompositionLocalOf<FloatingActionButtonStackState?> { null }

/**
 * Every FAB the screen [content] shows, stacked at the window's bottom end: the screen's own FAB, if it
 * has one, and above it a button back to the top of the list the screen shows - see [screenList].
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
 * the screen has rather than which are showing, so one showing never moves the others.
 *
 * The way back to the top shows once the list is a quarter of a screen from it, and steps aside while the
 * screen's own FAB has its menu open. Both are told by how the list has scrolled alone - see [ScreenList] -
 * so nothing around the list, the player included, changes when they show.
 */
@Composable
fun FloatingActionButtonStack(content: @Composable () -> Unit) {
    val stack = remember { FloatingActionButtonStackState() }
    // Handed over as they are composed: the window overlay is composed above the player that provides them.
    stack.playerSheet = LocalPlayerSheetHeight.current
    stack.navigationBarHeight = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
    CompositionLocalProvider(LocalFloatingActionButtonStack provides stack, content = content)
    WindowOverlay { FloatingActionButtons(stack) }
}

/** What [FloatingActionButtonStack] draws over the window - everything read from [stack]'s state. */
@Composable
private fun BoxScope.FloatingActionButtons(stack: FloatingActionButtonStackState) {
    ExpressiveMotion {
        val primaryButton = stack.primaryButton
        val scrollToTopLift = if (primaryButton != null) PrimaryButtonSize + StackSpacing else 0.dp
        val stackHeightPx = with(LocalDensity.current) { (scrollToTopLift + ScrollToTopButtonSize).toPx() }
        val isShownState = remember(stack, stackHeightPx) {
            derivedStateOf {
                val list = stack.currentList
                !stack.playerSheet.isRaised && list?.isFastScrolling?.invoke() != true &&
                    list?.isNearEnd(stackHeightPx) != true
            }
        }
        val isScrollToTopShown by remember(stack, isShownState) {
            derivedStateOf {
                isShownState.value && stack.currentList?.isAwayFromTop == true && stack.primaryButton?.expanded != true
            }
        }
        val isShown by isShownState
        val anchor = Modifier
            .align(Alignment.BottomEnd)
            .offset { IntOffset(0, -stack.standingHeight.roundToPx()) }
            .padding(end = SonaComponentStyle.ContentHorizontalPadding)
        val scope = rememberCoroutineScope()

        ScrollToTopButton(
            visible = isScrollToTopShown,
            onClick = { stack.currentList?.let { list -> scope.launch { list.scrollToTop() } } },
            modifier = anchor.padding(bottom = scrollToTopLift),
        )
        primaryButton?.let { PrimaryButtonMenu(button = it, isShown = isShown, anchor = anchor) }
    }
}

/** The way back to the top of the screen's list: Material's FAB, pressing as every press in Sona does. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScrollToTopButton(visible: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressFraction by rememberPressFraction(interactionSource)
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.animateFloatingActionButton(visible = visible, alignment = Alignment.Center),
        shape = roundedShape(pressedCornerRadius(pressFraction, restingRadius = ScrollToTopCornerRadius)),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        interactionSource = interactionSource,
    ) {
        Icon(imageVector = Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.fab_scroll_to_top))
    }
}
