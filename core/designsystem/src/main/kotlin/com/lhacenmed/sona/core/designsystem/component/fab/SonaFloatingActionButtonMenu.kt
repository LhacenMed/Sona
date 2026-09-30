package com.lhacenmed.sona.core.designsystem.component.fab

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.lhacenmed.sona.core.designsystem.R
import com.lhacenmed.sona.core.designsystem.theme.LocalIsRounded
import com.lhacenmed.sona.core.designsystem.theme.SonaComponentStyle
import com.lhacenmed.sona.core.designsystem.theme.pressedCornerRadius
import com.lhacenmed.sona.core.designsystem.theme.rememberPressFraction

/** The corners it rests with: the medium FAB's. */
private val RestingCornerRadius = 20.dp

/** The corners it opens into: Material's close button, a circle. */
private val ExpandedCornerRadius = 28.dp

/** The room Material's menu keeps to the end of and below its button - given back, so the button sits on the keylines. */
private val MenuInset = 16.dp

/** How much of the app the scrim dims while the menu is open: Material's modal scrim. */
private const val ScrimOpacity = 0.32f

/**
 * A menu item's icon: Material's `ListItemIconSize`. Set rather than left to the glyph, which may be
 * drawn at any size - the library's section glyphs are 48dp - so every item's icon matches.
 */
private val MenuItemIconSize = 24.dp

/** One of a [SonaFloatingActionButtonMenu]'s items: filled with the open button's own colour while [isSelected]. */
class FloatingActionButtonMenuChoice(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val isSelected: Boolean = false,
)

/**
 * What a screen's FAB is at this moment: its [icon], what it does at a tap - [onClick] - and the [choices] its
 * long press opens. [visible] is whether the screen has it showing right now; it steps aside without the
 * stack giving up its place.
 */
class FloatingActionButtonMenuContent(
    val icon: ImageVector,
    val contentDescription: String,
    val visible: Boolean,
    val onClick: () -> Unit,
    val choices: List<FloatingActionButtonMenuChoice>,
)

/**
 * A screen's own FAB as [FloatingActionButtonStack] holds it: handed over once, and read by the stack where it
 * draws it - its [content] then, and whether its menu is [expanded].
 */
internal class PrimaryButton(
    private val latestContent: State<() -> FloatingActionButtonMenuContent?>,
    expandedState: MutableState<Boolean>,
) {
    /** What it is right now, or null while the screen has none - read in the stack's own composition. */
    val content: FloatingActionButtonMenuContent?
        get() = latestContent.value()

    var expanded: Boolean by expandedState
}

/**
 * A screen's own FAB: Material's expressive FAB menu, as a medium FAB, standing at the bottom of the
 * screen's [FloatingActionButtonStack] - which draws it over the whole window and steps it aside with the
 * rest of the stack. Shown and hidden by its own motion - [animateFloatingActionButton], growing out of and
 * shrinking into its centre, as Auxio's does.
 *
 * [content] is what it is at this moment - null while the screen has no FAB at all, such as one the user has
 * turned off, which the stack closes the gap under. It is read where the stack draws it, in the stack's own
 * composition, so it reads whatever changes from state - as a window overlay's content does - rather than
 * capturing values: then a change is drawn in the very frame the state changes in, without the screen
 * recomposing for it.
 *
 * A tap does what the button is for. A long press opens its menu of choices instead: the button shrinks
 * towards its top end into Material's close button and the choices unfold above it, over a scrim across the
 * whole app. Picking one closes it; so does a tap on the close button, anywhere on the scrim, or back.
 * Material's button knows only a tap, so the long press is read here, ahead of it, and the rest of that
 * press is kept from it - it would otherwise close the menu the moment the finger lifts.
 *
 * Its corners answer a press as everything else in Sona does, tightening to
 * [SonaComponentStyle.PressedCornerRadius] while held. Material's button reports no press of its own, so
 * the long press reports it here too.
 *
 * Hidden, it takes no touches and closes its menu, so it never comes back open over whatever hid it; it
 * stays composed, so showing it again is only its motion.
 */
@Composable
fun SonaFloatingActionButtonMenu(content: () -> FloatingActionButtonMenuContent?) {
    val stack = checkNotNull(LocalFloatingActionButtonStack.current) { "A FAB needs a FloatingActionButtonStack" }
    val latestContent = rememberUpdatedState(content)
    val expandedState = rememberSaveable { mutableStateOf(false) }
    val button = remember { PrimaryButton(latestContent, expandedState) }
    BackHandler(enabled = button.expanded) { button.expanded = false }
    DisposableEffect(stack, button) {
        stack.primaryButton = button
        onDispose { if (stack.primaryButton === button) stack.primaryButton = null }
    }
}

/**
 * [button] as the stack draws it - as its [content] is now - standing at [anchor]: shown while the screen has
 * it [FloatingActionButtonMenuContent.visible] and the stack [isShown].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BoxScope.PrimaryButtonMenu(
    button: PrimaryButton,
    content: FloatingActionButtonMenuContent,
    isShown: Boolean,
    anchor: Modifier,
) {
    val latestContent by rememberUpdatedState(content)
    val isVisible = content.visible && isShown
    LaunchedEffect(isVisible) { if (!isVisible) button.expanded = false }

    val haptics by rememberUpdatedState(LocalHapticFeedback.current)
    // Remembered, so the long press is not started over whenever the button is drawn again.
    val openMenu = remember(button) {
        {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            button.expanded = true
        }
    }
    val openLabel = stringResource(R.string.fab_menu_open)
    val closeLabel = stringResource(R.string.fab_menu_close)
    val interactionSource = remember { MutableInteractionSource() }
    val pressFraction = rememberPressFraction(interactionSource)
    val isRounded = LocalIsRounded.current
    // Read as it draws, so a press morphs the corners without recomposing the button. Square throughout
    // while round mode is off.
    val cornerRadius: (Float) -> Dp = remember(pressFraction, isRounded) {
        if (isRounded) {
            { progress -> lerp(pressedCornerRadius(pressFraction.value, RestingCornerRadius), ExpandedCornerRadius, progress) }
        } else {
            { 0.dp }
        }
    }
    val expanded = button.expanded

    MenuScrim(expanded = expanded, closeLabel = closeLabel, onClose = { button.expanded = false })
    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = anchor.offset(x = MenuInset, y = MenuInset),
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                // Checking it is a tap on the closed button, which is its own action rather than the menu.
                onCheckedChange = { checked ->
                    if (checked) latestContent.onClick() else button.expanded = false
                },
                modifier = Modifier
                    .semantics { if (!expanded) onLongClick(label = openLabel) { openMenu(); true } }
                    .animateFloatingActionButton(visible = isVisible, alignment = Alignment.Center)
                    .then(if (expanded) Modifier else Modifier.longPress(interactionSource, openMenu)),
                contentAlignment = Alignment.TopEnd,
                containerSize = ToggleFloatingActionButtonDefaults.containerSize(PrimaryButtonSize),
                containerCornerRadius = cornerRadius,
            ) {
                val isClose = checkedProgress > 0.5f
                Icon(
                    imageVector = if (isClose) Icons.Filled.Close else content.icon,
                    contentDescription = if (isClose) closeLabel else content.contentDescription,
                    modifier = Modifier.animateIcon(
                        checkedProgress = { checkedProgress },
                        size = ToggleFloatingActionButtonDefaults.iconSizeMedium(),
                    ),
                )
            }
        },
    ) {
        content.choices.forEach { choice ->
            FloatingActionButtonMenuItem(
                onClick = {
                    button.expanded = false
                    choice.onClick()
                },
                text = { Text(text = choice.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                icon = { Icon(imageVector = choice.icon, contentDescription = null, modifier = Modifier.size(MenuItemIconSize)) },
                modifier = Modifier.semantics { selected = choice.isSelected },
                containerColor = if (choice.isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
            )
        }
    }
}

/**
 * The scrim across the whole window while the menu is open, fading in and out with it. Open, it takes
 * every touch, and a tap closes the menu; fading out, it takes none, so the app answers at once.
 */
@Composable
private fun MenuScrim(expanded: Boolean, closeLabel: String, onClose: () -> Unit) {
    val opacity by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "scrimOpacity",
    )
    if (opacity == 0f) return
    val currentOnClose by rememberUpdatedState(onClose)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = opacity }
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = ScrimOpacity))
            .then(
                if (expanded) {
                    Modifier
                        .pointerInput(Unit) { detectTapGestures { currentOnClose() } }
                        .semantics { onClick(label = closeLabel) { currentOnClose(); true } }
                } else {
                    Modifier
                },
            ),
    )
}

/**
 * Calls [onLongPress] once a press has been held for the platform's long-press timeout, then keeps the
 * rest of that press - its lifting included - from whatever sits beneath, so it is never also a tap.
 * Every press is reported to [interactionSource], long or not.
 *
 * Read on the initial pass, before the button beneath sees the press at all.
 */
private fun Modifier.longPress(interactionSource: MutableInteractionSource, onLongPress: () -> Unit): Modifier =
    pointerInput(interactionSource, onLongPress) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val press = PressInteraction.Press(down.position)
            interactionSource.tryEmit(press)
            try {
                var ended = false
                withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    waitForUpOrCancellation(PointerEventPass.Initial)
                    ended = true
                }
                if (!ended) {
                    onLongPress()
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            } finally {
                interactionSource.tryEmit(PressInteraction.Release(press))
            }
        }
    }
