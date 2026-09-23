package com.lhacenmed.sona.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * Bottom Sheet
 * Modified from [ViMusic](https://github.com/vfsfitvnm/ViMusic), by way of ArchiveTune.
 *
 * The sheet is dragged from wherever nothing else claims the gesture: its collapsed bar always, and
 * its expanded content when [isContentDraggable]. A sheet whose content scrolls passes `false` and
 * moves the sheet through [BottomSheetState.nestedScrollConnection] instead - a list
 * under a drag detector would leave the two racing for the same swipe, each with its own velocity,
 * and which one won would depend on whether the list happened to be able to scroll at that instant.
 *
 * [swipeUpSheet] is the sheet nested in this one that an upward swipe over the expanded content raises
 * instead - the queue in the player. Expanded, this sheet is as tall as it goes, so the swipe would
 * otherwise move nothing; downward, the swipe still closes this one. The same detector serves both, so
 * the swipe still has a single owner.
 */
@Composable
internal fun BottomSheet(
    state: BottomSheetState,
    backgroundColor: Color,
    collapsedContent: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    onCollapsedContentClick: (() -> Unit)? = null,
    isContentDraggable: Boolean = true,
    swipeUpSheet: BottomSheetState? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    // Material puts a content colour on every surface it draws; this sheet is a Box and draws its own,
    // so it has to say. Left unsaid it is Color.Black - the default of the composition local every
    // icon button, text and press ripple in here reads - which is why they came out black on a dark
    // sheet. Everything the player, the queue and the lyrics draw sits under this.
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .offset {
                        val y =
                            (state.expandedBound - state.value)
                                .roundToPx()
                                .coerceAtLeast(0)
                        IntOffset(x = 0, y = y)
                    }.clip(
                        RoundedCornerShape(
                            topStart = if (!state.isExpanded) 16.dp else 0.dp,
                            topEnd = if (!state.isExpanded) 16.dp else 0.dp,
                        ),
                    ).background(
                        backgroundColor.copy(
                            alpha = backgroundColor.alpha * state.progress.coerceIn(0f, 1f),
                        ),
                    ),
        ) {
            if (state.isExpandedOrExpanding) {
                BackHandler(onBack = state::collapseSoft)
            }

            if (!state.isCollapsed) {
                BoxWithConstraints(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .then(
                                if (isContentDraggable) {
                                    Modifier.bottomSheetDraggable(state, onDismiss, swipeUpSheet)
                                } else {
                                    Modifier
                                },
                            ).graphicsLayer {
                                alpha = ((state.progress - 0.25f) * 4).coerceIn(0f, 1f)
                            },
                    content = content,
                )
            }

            if (!state.isExpanded && (onDismiss == null || !state.isDismissed)) {
                Box(
                    modifier =
                        Modifier
                            .graphicsLayer {
                                alpha = 1f - (state.progress * 4).coerceAtMost(1f)
                            }.bottomSheetDraggable(state, onDismiss)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onCollapsedContentClick ?: state::expandSoft,
                            ).fillMaxWidth()
                            .height(state.collapsedBound),
                    content = collapsedContent,
                )
            }
        }
    }
}

@Stable
internal class BottomSheetState(
    draggableState: DraggableState,
    private val coroutineScope: CoroutineScope,
    private val animatable: Animatable<Dp, AnimationVector1D>,
    private val onAnchorChanged: (Int) -> Unit,
    val collapsedBound: Dp,
    initialAnchor: Int,
) : DraggableState by draggableState {
    private val dismissedBound: Dp
        get() = animatable.lowerBound!!

    val expandedBound: Dp
        get() = animatable.upperBound!!

    val value by animatable.asState()

    private var targetAnchor by mutableIntStateOf(initialAnchor)

    val isDismissed by derivedStateOf {
        value == animatable.lowerBound!!
    }

    val isCollapsed by derivedStateOf {
        value == collapsedBound
    }

    val isExpanded by derivedStateOf {
        value == animatable.upperBound
    }

    val isExpandedOrExpanding: Boolean
        get() = targetAnchor == EXPANDED_ANCHOR

    val progress by derivedStateOf {
        1f - (animatable.upperBound!! - animatable.value) / (animatable.upperBound!! - collapsedBound)
    }

    private fun updateAnchor(anchor: Int) {
        targetAnchor = anchor
        onAnchorChanged(anchor)
    }

    private fun collapse(animationSpec: AnimationSpec<Dp>) {
        updateAnchor(COLLAPSED_ANCHOR)
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.animateTo(collapsedBound, animationSpec)
        }
    }

    private fun expand(animationSpec: AnimationSpec<Dp>) {
        updateAnchor(EXPANDED_ANCHOR)
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.animateTo(animatable.upperBound!!, animationSpec)
        }
    }

    fun collapseSoft() {
        collapse(BottomSheetSoftAnimationSpec)
    }

    fun expandSoft() {
        expand(BottomSheetSoftAnimationSpec)
    }

    fun dismiss() {
        updateAnchor(DISMISSED_ANCHOR)
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.animateTo(animatable.lowerBound!!, BottomSheetAnimationSpec)
        }
    }

    fun performFling(
        velocity: Float,
        onDismiss: (() -> Unit)?,
    ) {
        if (velocity > 250) {
            expand(BottomSheetAnimationSpec)
        } else if (velocity < -250) {
            if (value < collapsedBound && onDismiss != null) {
                dismiss()
                onDismiss.invoke()
            } else {
                collapse(BottomSheetAnimationSpec)
            }
        } else {
            val l0 = dismissedBound
            val l1 = (collapsedBound - dismissedBound) / 2
            val l2 = (expandedBound - collapsedBound) / 2
            val l3 = expandedBound

            when (value) {
                in l0..l1 -> {
                    if (onDismiss != null) {
                        dismiss()
                        onDismiss.invoke()
                    } else {
                        collapse(BottomSheetAnimationSpec)
                    }
                }

                in l1..l2 -> {
                    collapse(BottomSheetAnimationSpec)
                }

                in l2..l3 -> {
                    expand(BottomSheetAnimationSpec)
                }

                else -> {
                    Unit
                }
            }
        }
    }

    /**
     * The connection a scrolling child hands the sheet, so one drag can run out of list and carry on
     * into the sheet without the finger lifting.
     *
     * Where the child sits is asked outright through [isAtTop] rather than inferred from what it just
     * consumed: a drag down that begins with the list already at its top is the sheet's from its very
     * first move, so pulling the sheet closed works anywhere on it and needs no scroll spent finding
     * out the list had nowhere to go. A drag up is the sheet's too for as long as it sits below its
     * full height - it is put back before the list scrolls again - and the list's from then on.
     *
     * Nothing is remembered between gestures: what the sheet takes is decided from where the sheet and
     * the list are at that moment, so no gesture can leave the next one primed to move the sheet.
     */
    fun nestedScrollConnection(isAtTop: () -> Boolean): NestedScrollConnection =
        object : NestedScrollConnection {
            /**
             * Whether the sheet sits below its full height with the list at its top - which only a
             * drag down over the list puts it in, so it is this gesture that has been moving it and
             * this gesture that has to put it back or let it go. A sheet still animating open over a
             * list scrolled somewhere else is not this, and nothing here disturbs it.
             */
            private val isSheetPartlyClosed: Boolean
                get() = !isExpanded && isAtTop()

            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val belongsToSheet =
                    when {
                        available.y > 0f -> isAtTop()
                        available.y < 0f -> isSheetPartlyClosed
                        else -> false
                    }
                if (!belongsToSheet) return Offset.Zero
                dispatchRawDelta(available.y)
                return available
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // Whatever downward drag the list had no room for, when it ran out part-way through
                // this one event - so the hand-over lands on the same frame the list stops moving.
                if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                dispatchRawDelta(available.y)
                return available
            }

            override suspend fun onPreFling(available: Velocity): Velocity =
                if (isSheetPartlyClosed) {
                    // This gesture moved the sheet, so it settles on an anchor rather than being
                    // left wherever the finger happened to leave it.
                    performFling(-available.y, null)
                    available
                } else {
                    Velocity.Zero
                }
        }
}

internal const val EXPANDED_ANCHOR = 2
internal const val COLLAPSED_ANCHOR = 1
internal const val DISMISSED_ANCHOR = 0

/**
 * A sheet state that starts at [initialAnchor].
 *
 * The first placement snaps rather than animates: every activity builds its own sheet, and one sliding
 * up each time a screen opens would read as the player arriving, when it was already there. Later bound
 * changes - a rotation, a taller peek - still animate, as ArchiveTune's do.
 */
@Composable
internal fun rememberBottomSheetState(
    dismissedBound: Dp,
    expandedBound: Dp,
    collapsedBound: Dp = dismissedBound,
    initialAnchor: Int = DISMISSED_ANCHOR,
): BottomSheetState {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    var previousAnchor by rememberSaveable {
        mutableIntStateOf(initialAnchor)
    }
    val animatable =
        remember {
            Animatable(0.dp, Dp.VectorConverter)
        }
    val isPlaced = remember { booleanArrayOf(false) }

    return remember(dismissedBound, expandedBound, collapsedBound, coroutineScope) {
        val initialValue =
            when (previousAnchor) {
                EXPANDED_ANCHOR -> expandedBound
                COLLAPSED_ANCHOR -> collapsedBound
                DISMISSED_ANCHOR -> dismissedBound
                else -> error("Unknown BottomSheet anchor")
            }

        animatable.updateBounds(dismissedBound.coerceAtMost(expandedBound), expandedBound)
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (isPlaced[0]) {
                animatable.animateTo(initialValue, BottomSheetAnimationSpec)
            } else {
                isPlaced[0] = true
                animatable.snapTo(initialValue)
            }
        }

        BottomSheetState(
            draggableState =
                DraggableState { delta ->
                    coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                        animatable.snapTo(animatable.value - with(density) { delta.toDp() })
                    }
                },
            onAnchorChanged = { previousAnchor = it },
            coroutineScope = coroutineScope,
            animatable = animatable,
            collapsedBound = collapsedBound,
            initialAnchor = previousAnchor,
        )
    }
}

/**
 * Drags [state] - or, for a swipe up while [state] is expanded, [swipeUpSheet] when there is one.
 *
 * Which sheet a drag moves is settled by its first movement and kept to its end, so one gesture never
 * hands over half-way, and it is that sheet the release flings.
 */
internal fun Modifier.bottomSheetDraggable(
    state: BottomSheetState,
    onDismiss: (() -> Unit)? = null,
    swipeUpSheet: BottomSheetState? = null,
): Modifier =
    this.pointerInput(state, swipeUpSheet) {
        val velocityTracker = VelocityTracker()
        var draggedSheet: BottomSheetState? = null

        fun settle() {
            val sheet = draggedSheet ?: state
            draggedSheet = null
            val velocity = -velocityTracker.calculateVelocity().y
            velocityTracker.resetTracking()
            sheet.performFling(velocity, onDismiss.takeIf { sheet === state })
        }

        detectVerticalDragGestures(
            onVerticalDrag = { change, dragAmount ->
                velocityTracker.addPointerInputChange(change)
                val sheet =
                    draggedSheet ?: when {
                        dragAmount == 0f -> return@detectVerticalDragGestures
                        dragAmount < 0f && swipeUpSheet != null && state.isExpanded -> swipeUpSheet
                        else -> state
                    }.also { draggedSheet = it }
                sheet.dispatchRawDelta(dragAmount)
            },
            onDragCancel = { settle() },
            onDragEnd = { settle() },
        )
    }
